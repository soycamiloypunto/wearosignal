package com.wearosignal.app.messages

import com.wearosignal.app.AppDeps
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.network.NetworkResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.SignalServiceMessageSender.IndividualSendEvents
import org.whispersystems.signalservice.api.SignalServiceMessageSender.LegacyGroupEvents
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import okio.ByteString.Companion.toByteString
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.File
import java.util.Optional

/**
 * Sends text messages: 1:1 to a ServiceId, or to a group by fanning out pairwise sends
 * with the groupV2 context (no sender key). After a successful send, a sent transcript
 * goes to our other devices so the phone shows the message too.
 * Authenticated sends (no sealed sender). Synchronous — call from a background thread.
 */
object MessageSender {

  private val TAG = Log.tag(MessageSender::class)

  sealed interface Result {
    data object Success : Result
    data class Failure(val message: String) : Result
  }

  /** Sends to [peer]: a group id (when [isGroup]) or a ServiceId string. */
  fun send(peer: String, isGroup: Boolean, body: String): Result {
    return if (isGroup) sendToGroup(peer, body) else sendText(peer, body)
  }

  /**
   * Uploads [audioFile] (AAC/M4A) to Signal CDN as a voice note, sends it to [peer] (1:1 or group),
   * emits a sync transcript to our other devices, and records a playable local copy.
   */
  fun sendVoiceNote(peer: String, isGroup: Boolean, audioFile: File): Result {
    if (!AppDeps.account.isLinked) return Result.Failure("Not linked")
    if (!audioFile.exists() || audioFile.length() == 0L) return Result.Failure("Empty recording")

    val now = System.currentTimeMillis()
    val webSocket = AppDeps.net.authWebSocket
    return try {
      webSocket.connect()
      val paddedLength = PaddingInputStream.getPaddedSize(audioFile.length())
      val ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(paddedLength)
      val formRequest = WebSocketRequestMessage.get("/v4/attachments/form/upload?uploadLength=$ciphertextLength")
      val uploadForm = NetworkResult.fromWebSocketRequest(webSocket, formRequest, AttachmentUploadForm::class)
        .successOrThrow()
      val uploadSpec = AppDeps.net.authPushServiceSocket.getResumableUploadSpec(uploadForm)
      val pointer = audioFile.inputStream().use { stream ->
        val attachmentStream = SignalServiceAttachment.newStreamBuilder()
          .withStream(stream)
          .withContentType("audio/aac")
          .withLength(audioFile.length())
          .withVoiceNote(true)
          .withUploadTimestamp(now)
          .withResumableUploadSpec(uploadSpec)
          .build()
        AppDeps.net.messageSender.uploadAttachment(attachmentStream)
      }

      val expireTimer = AppDeps.messages.getPeerExpireTimer(peer)
      if (isGroup) {
        val (masterKeyBytes, revision) = AppDeps.database.readableDatabase.rawQuery(
          "SELECT master_key, revision FROM groups WHERE group_id = ?",
          arrayOf(peer)
        ).use { cursor ->
          if (!cursor.moveToFirst()) return Result.Failure("Unknown group")
          cursor.getBlob(0) to cursor.getInt(1)
        }
        val memberAcis = GroupStateResolver.cachedMembers(peer)
          ?: return Result.Failure("Group members not fetched yet — poll first")
        val recipients = memberAcis.mapNotNull { ServiceId.parseOrNull(it) }
        if (recipients.isEmpty()) return Result.Failure("No other members")

        val groupContext = SignalServiceGroupV2.newBuilder(GroupMasterKey(masterKeyBytes))
          .withRevision(revision)
          .build()
        val message = SignalServiceDataMessage.Builder()
          .withTimestamp(now)
          .withAttachments(listOf(pointer))
          .withExpiration(expireTimer)
          .asGroupMessage(groupContext)
          .build()

        val addresses = recipients.map { SignalServiceAddress(it) }
        val results = AppDeps.net.messageSender.sendDataMessage(
          addresses,
          addresses.map { null as SealedSenderAccess? },
          false,
          ContentHint.RESENDABLE,
          message,
          LegacyGroupEvents.EMPTY,
          null,
          null,
          true
        )
        if (results.any { it.isSuccess }) {
          sendSyncTranscript(message, Optional.empty(), recipients.toSet(), if (expireTimer > 0) now else 0L)
          val localCopy = AppDeps.attachments.saveLocalAudio(audioFile, now)
          storeOwnAudio(peer = peer, groupId = peer, audioPath = localCopy.path, sentAt = now, expireTimer = expireTimer)
          Log.i(TAG, "Group voice note sent")
          Result.Success
        } else {
          Result.Failure("Could not deliver")
        }
      } else {
        val serviceId = ServiceId.parseOrNull(peer) ?: return Result.Failure("Bad recipient")
        val address = SignalServiceAddress(serviceId)
        val message = SignalServiceDataMessage.Builder()
          .withTimestamp(now)
          .withAttachments(listOf(pointer))
          .withExpiration(expireTimer)
          .build()

        val result = AppDeps.net.messageSender.sendDataMessage(
          address,
          null,
          ContentHint.RESENDABLE,
          message,
          IndividualSendEvents.EMPTY,
          true,
          false
        )
        if (result.isSuccess) {
          sendSyncTranscript(message, Optional.of(address), setOf(serviceId), if (expireTimer > 0) now else 0L)
          val localCopy = AppDeps.attachments.saveLocalAudio(audioFile, now)
          storeOwnAudio(peer = peer, groupId = null, audioPath = localCopy.path, sentAt = now, expireTimer = expireTimer)
          Log.i(TAG, "Voice note sent to ${peer.take(12)}")
          Result.Success
        } else {
          Result.Failure("Could not deliver")
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Voice note send failed", t)
      Result.Failure(t.message ?: "Send failed")
    } finally {
      webSocket.disconnect()
    }
  }

  private fun storeOwnAudio(peer: String, groupId: String?, audioPath: String, sentAt: Long, expireTimer: Int = 0) {
    val selfAci = AppDeps.account.aci?.toString()
    val expiresAt = if (expireTimer > 0) sentAt + expireTimer * 1000L else 0L
    AppDeps.messages.insert(
      peer = peer,
      senderAci = selfAci ?: peer,
      groupId = groupId,
      body = "",
      sentAt = sentAt,
      serverAt = sentAt,
      fromSelf = true,
      attachmentType = "audio/aac",
      attachmentPath = audioPath,
      expireTimerSeconds = expireTimer,
      expiresAt = expiresAt
    )
  }

  /**
   * Reacts to the message identified by ([targetAuthorAci], [targetSentAt]) — Signal's message
   * key — with [emoji], or retracts our reaction when [remove]. Same fan-out as a text send.
   */
  fun sendReaction(
    peer: String,
    isGroup: Boolean,
    targetAuthorAci: String,
    targetSentAt: Long,
    emoji: String,
    remove: Boolean
  ): Result {
    val targetAuthor = ServiceId.parseOrNull(targetAuthorAci) ?: return Result.Failure("Bad target author")
    val reaction = SignalServiceDataMessage.Reaction(emoji, remove, targetAuthor, targetSentAt)
    return if (isGroup) sendToGroup(peer, body = null, reaction = reaction) else sendText(peer, body = null, reaction = reaction)
  }

  /**
   * Remote-deletes our own message for everyone (both sides) and syncs the deletion to our phone,
   * then deletes it locally on the watch.
   */
  fun deleteMessageForEveryone(peer: String, isGroup: Boolean, targetSentAt: Long): Result {
    if (!AppDeps.account.isLinked) return Result.Failure("Not linked")
    val selfAci = AppDeps.account.aci?.toString() ?: return Result.Failure("Not linked")
    val now = System.currentTimeMillis()
    val remoteDelete = SignalServiceDataMessage.RemoteDelete(targetSentAt)
    val webSocket = AppDeps.net.authWebSocket
    return try {
      webSocket.connect()
      if (isGroup) {
        val (masterKeyBytes, revision) = AppDeps.database.readableDatabase.rawQuery(
          "SELECT master_key, revision FROM groups WHERE group_id = ?",
          arrayOf(peer)
        ).use { cursor ->
          if (!cursor.moveToFirst()) return Result.Failure("Unknown group")
          cursor.getBlob(0) to cursor.getInt(1)
        }
        val memberAcis = GroupStateResolver.cachedMembers(peer)
          ?: return Result.Failure("Group members not fetched yet")
        val recipients = memberAcis.mapNotNull { ServiceId.parseOrNull(it) }
        if (recipients.isEmpty()) return Result.Failure("No other members")
        val groupContext = SignalServiceGroupV2.newBuilder(GroupMasterKey(masterKeyBytes))
          .withRevision(revision)
          .build()
        val message = SignalServiceDataMessage.Builder()
          .withTimestamp(now)
          .withRemoteDelete(remoteDelete)
          .asGroupMessage(groupContext)
          .build()
        val addresses = recipients.map { SignalServiceAddress(it) }
        val results = AppDeps.net.messageSender.sendDataMessage(
          addresses,
          addresses.map { null as SealedSenderAccess? },
          false,
          ContentHint.RESENDABLE,
          message,
          LegacyGroupEvents.EMPTY,
          null,
          null,
          true
        )
        if (results.any { it.isSuccess }) {
          sendSyncTranscript(message, Optional.empty(), recipients.toSet())
          AppDeps.messages.deleteMessage(peer, selfAci, targetSentAt)
          Result.Success
        } else {
          Result.Failure("Could not delete for everyone")
        }
      } else {
        val serviceId = ServiceId.parseOrNull(peer) ?: return Result.Failure("Bad recipient")
        val address = SignalServiceAddress(serviceId)
        val message = SignalServiceDataMessage.Builder()
          .withTimestamp(now)
          .withRemoteDelete(remoteDelete)
          .build()
        val result = AppDeps.net.messageSender.sendDataMessage(
          address,
          null,
          ContentHint.RESENDABLE,
          message,
          IndividualSendEvents.EMPTY,
          true,
          false
        )
        if (result.isSuccess) {
          sendSyncTranscript(message, Optional.of(address), setOf(serviceId))
          AppDeps.messages.deleteMessage(peer, selfAci, targetSentAt)
          Result.Success
        } else {
          Result.Failure("Could not delete for everyone")
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Remote delete failed", t)
      Result.Failure(t.message ?: "Delete failed")
    } finally {
      webSocket.disconnect()
    }
  }

  /**
   * Deletes a single message locally on the watch AND sends a Signal DeleteForMe sync message
   * so it is also deleted on our phone.
   */
  fun deleteMessageForMe(peer: String, isGroup: Boolean, authorAci: String, sentAt: Long) {
    AppDeps.messages.deleteMessage(peer, authorAci, sentAt)
    if (!AppDeps.account.isLinked) return
    val convId = buildConversationIdentifier(peer, isGroup) ?: return
    val authorId = ServiceId.parseOrNull(authorAci) ?: return
    val addressable = AddressableMessage(
      authorServiceIdBinary = authorId.toByteString(),
      sentTimestamp = sentAt
    )
    val deleteForMe = SyncMessage.DeleteForMe(
      messageDeletes = listOf(
        SyncMessage.DeleteForMe.MessageDeletes(
          conversation = convId,
          messages = listOf(addressable)
        )
      )
    )
    sendDeleteForMeSync(deleteForMe)
  }

  /**
   * Deletes an entire conversation locally on the watch (and unpins it) AND sends a Signal
   * DeleteForMe.ConversationDelete sync message so the chat is also deleted on our phone.
   */
  fun deleteConversationSynced(peer: String, isGroup: Boolean) {
    val recent = AppDeps.messages.recentAddressableMessages(peer, limit = 5)
    AppDeps.messages.deleteConversation(peer)
    AppDeps.account.unpin(peer)
    if (!AppDeps.account.isLinked) return
    val convId = buildConversationIdentifier(peer, isGroup) ?: return
    val addressableRecent = recent.mapNotNull { (aciStr, ts) ->
      ServiceId.parseOrNull(aciStr)?.let { sid ->
        AddressableMessage(authorServiceIdBinary = sid.toByteString(), sentTimestamp = ts)
      }
    }
    val deleteForMe = SyncMessage.DeleteForMe(
      conversationDeletes = listOf(
        SyncMessage.DeleteForMe.ConversationDelete(
          conversation = convId,
          mostRecentMessages = addressableRecent,
          isFullDelete = true
        )
      )
    )
    sendDeleteForMeSync(deleteForMe)
  }

  private fun buildConversationIdentifier(peer: String, isGroup: Boolean): ConversationIdentifier? {
    return if (isGroup) {
      val groupBytes = runCatching { android.util.Base64.decode(peer, android.util.Base64.DEFAULT) }.getOrNull() ?: return null
      ConversationIdentifier(threadGroupId = groupBytes.toByteString())
    } else {
      val sid = ServiceId.parseOrNull(peer) ?: return null
      ConversationIdentifier(threadServiceIdBinary = sid.toByteString())
    }
  }

  private fun sendDeleteForMeSync(deleteForMe: SyncMessage.DeleteForMe) {
    val webSocket = AppDeps.net.authWebSocket
    try {
      webSocket.connect()
      val content = Content(syncMessage = SyncMessage(deleteForMe = deleteForMe))
      AppDeps.net.messageSender.sendSyncMessage(content, true, Optional.empty())
      Log.i(TAG, "Sent DeleteForMe sync to phone")
    } catch (t: Throwable) {
      Log.w(TAG, "DeleteForMe sync failed (deleted locally)", t)
    } finally {
      webSocket.disconnect()
    }
  }

  /**
   * Sends to [recipient], a ServiceId string — either a bare-UUID ACI (replies, known contacts) or a
   * "PNI:"-prefixed PNI (a contact discovered by number, whose ACI Signal won't reveal until first contact).
   * The message carries [body], a [reaction], or both.
   */
  fun sendText(recipient: String, body: String?, reaction: SignalServiceDataMessage.Reaction? = null): Result {
    if (!AppDeps.account.isLinked) return Result.Failure("Not linked")

    val serviceId = ServiceId.parseOrNull(recipient) ?: return Result.Failure("Bad recipient")
    val now = System.currentTimeMillis()
    val expireTimer = if (body != null) AppDeps.messages.getPeerExpireTimer(recipient) else 0
    val message = SignalServiceDataMessage.Builder()
      .withTimestamp(now)
      .withBody(body)
      .withReaction(reaction)
      .withExpiration(expireTimer)
      .build()

    val webSocket = AppDeps.net.authWebSocket
    return try {
      webSocket.connect()
      val address = SignalServiceAddress(serviceId)
      val result = AppDeps.net.messageSender.sendDataMessage(
        address,
        null, // authenticated send, no sealed sender
        ContentHint.RESENDABLE,
        message,
        IndividualSendEvents.EMPTY,
        true, // urgent
        false // includePniSignature
      )

      if (result.isSuccess) {
        sendSyncTranscript(message, Optional.of(address), setOf(serviceId), if (expireTimer > 0) now else 0L)
        storeOwn(peer = recipient, groupId = null, body = body, reaction = reaction, sentAt = now, expireTimer = expireTimer)
        Log.i(TAG, "Message sent to ${recipient.take(12)}")
        Result.Success
      } else {
        Log.w(TAG, "Send unsuccessful: network=${result.isNetworkFailure} unregistered=${result.isUnregisteredFailure} identity=${result.identityFailure != null}")
        Result.Failure("Could not deliver")
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Send failed", t)
      Result.Failure(t.message ?: "Send failed")
    } finally {
      webSocket.disconnect()
    }
  }

  /** Sends to every cached member of [groupId] (pairwise fan-out with the groupV2 context). */
  fun sendToGroup(groupId: String, body: String?, reaction: SignalServiceDataMessage.Reaction? = null): Result {
    if (!AppDeps.account.isLinked) return Result.Failure("Not linked")

    val (masterKeyBytes, revision) = AppDeps.database.readableDatabase.rawQuery(
      "SELECT master_key, revision FROM groups WHERE group_id = ?",
      arrayOf(groupId)
    ).use { cursor ->
      if (!cursor.moveToFirst()) return Result.Failure("Unknown group")
      cursor.getBlob(0) to cursor.getInt(1)
    }

    val memberAcis = GroupStateResolver.cachedMembers(groupId)
      ?: return Result.Failure("Group members not fetched yet — poll first")
    val recipients = memberAcis.mapNotNull { ServiceId.parseOrNull(it) }
    if (recipients.isEmpty()) return Result.Failure("No other members")

    val now = System.currentTimeMillis()
    val expireTimer = if (body != null) AppDeps.messages.getPeerExpireTimer(groupId) else 0
    val groupContext = SignalServiceGroupV2.newBuilder(GroupMasterKey(masterKeyBytes))
      .withRevision(revision)
      .build()
    val message = SignalServiceDataMessage.Builder()
      .withTimestamp(now)
      .withBody(body)
      .withReaction(reaction)
      .withExpiration(expireTimer)
      .asGroupMessage(groupContext)
      .build()

    val webSocket = AppDeps.net.authWebSocket
    return try {
      webSocket.connect()
      val addresses = recipients.map { SignalServiceAddress(it) }
      val results = AppDeps.net.messageSender.sendDataMessage(
        addresses,
        addresses.map { null as SealedSenderAccess? }, // authenticated sends, no sealed sender
        false, // isRecipientUpdate
        ContentHint.RESENDABLE,
        message,
        LegacyGroupEvents.EMPTY,
        null, // partialListener
        null, // cancelationSignal
        true // urgent
      )

      val delivered = results.count { it.isSuccess }
      if (delivered > 0) {
        sendSyncTranscript(message, Optional.empty(), recipients.toSet(), if (expireTimer > 0) now else 0L)
        storeOwn(peer = groupId, groupId = groupId, body = body, reaction = reaction, sentAt = now, expireTimer = expireTimer)
        Log.i(TAG, "Group message sent to $delivered/${results.size} member(s)")
        Result.Success
      } else {
        Log.w(TAG, "Group send failed for all ${results.size} member(s)")
        Result.Failure("Could not deliver")
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Group send failed", t)
      Result.Failure(t.message ?: "Send failed")
    } finally {
      webSocket.disconnect()
    }
  }

  /**
   * Announces that messages were read (their thread was opened on the watch): a read sync to
   * our own devices so the phone clears its unread state, plus — only when [includeReceipts] —
   * READ receipts to the message authors. Best-effort: the reads are already recorded locally,
   * and a failure here just means the rest of Signal learns later (or, for receipts, never).
   */
  fun sendReadSignals(seen: List<SeenMessage>, includeReceipts: Boolean) {
    if (seen.isEmpty() || !AppDeps.account.isLinked) return

    // Incoming senders are always ACIs (PNI-sourced envelopes are dropped), but stay safe.
    val bySender: Map<ACI, List<Long>> = seen
      .mapNotNull { message -> ACI.parseOrNull(message.senderAci)?.let { it to message.sentAt } }
      .groupBy({ it.first }, { it.second })
    if (bySender.isEmpty()) return

    val webSocket = AppDeps.net.authWebSocket
    try {
      webSocket.connect()
      try {
        val reads = bySender.flatMap { (sender, timestamps) -> timestamps.map { ReadMessage(sender, it) } }
        AppDeps.net.messageSender.sendSyncMessage(SignalServiceSyncMessage.forRead(reads))
      } catch (t: Throwable) {
        Log.w(TAG, "Read sync failed; other devices will keep these unread", t)
      }
      if (includeReceipts) {
        val now = System.currentTimeMillis()
        for ((sender, timestamps) in bySender) {
          try {
            AppDeps.net.messageSender.sendReceipt(
              SignalServiceAddress(sender),
              null, // authenticated send, no sealed sender
              SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, timestamps, now),
              false // includePniSignature
            )
          } catch (t: Throwable) {
            Log.w(TAG, "Read receipt to ${sender.toString().take(8)} failed", t)
          }
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Read signals failed", t)
    } finally {
      webSocket.disconnect()
    }
  }

  /** Tells our other devices (the phone) about the send. Best-effort: the message already went out. */
  private fun sendSyncTranscript(
    message: SignalServiceDataMessage,
    destination: Optional<SignalServiceAddress>,
    recipients: Set<ServiceId>,
    expirationStartTimestamp: Long = 0L
  ) {
    try {
      val transcript = SentTranscriptMessage(
        destination,
        message.timestamp,
        Optional.of(message),
        expirationStartTimestamp,
        recipients.associateWith { false }, // no unidentified delivery
        false, // isRecipientUpdate
        Optional.empty(), // storyMessage
        emptySet(), // storyMessageRecipients
        Optional.empty() // editMessage
      )
      AppDeps.net.messageSender.sendSyncMessage(SignalServiceSyncMessage.forSentTranscript(transcript))
    } catch (t: Throwable) {
      Log.w(TAG, "Sent transcript failed; phone won't show this message", t)
    }
  }

  /** Records what we just sent: a message row for a text, a reaction row for a reaction. */
  private fun storeOwn(
    peer: String,
    groupId: String?,
    body: String?,
    reaction: SignalServiceDataMessage.Reaction?,
    sentAt: Long,
    expireTimer: Int = 0
  ) {
    val selfAci = AppDeps.account.aci?.toString()
    if (reaction != null) {
      AppDeps.messages.applyReaction(
        peer = peer,
        targetSentAt = reaction.targetSentTimestamp,
        targetAuthorAci = reaction.targetAuthor.toString(),
        reacterAci = selfAci ?: return,
        emoji = reaction.emoji,
        remove = reaction.isRemove
      )
    }
    if (body != null) {
      val expiresAt = if (expireTimer > 0) sentAt + expireTimer * 1000L else 0L
      AppDeps.messages.insert(
        peer = peer,
        senderAci = selfAci ?: peer,
        groupId = groupId,
        body = body,
        sentAt = sentAt,
        serverAt = sentAt,
        fromSelf = true,
        expireTimerSeconds = expireTimer,
        expiresAt = expiresAt
      )
    }
  }
}
