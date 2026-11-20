package com.wearosignal.app

import android.content.Context
import androidx.work.WorkManager
import com.wearosignal.app.account.AccountStore
import com.wearosignal.app.contacts.AvatarStore
import com.wearosignal.app.crypto.WatchProtocolStore
import com.wearosignal.app.db.WatchDatabase
import com.wearosignal.app.messages.AttachmentStore
import com.wearosignal.app.messages.EnvelopeProcessor
import com.wearosignal.app.messages.MessageRetriever
import com.wearosignal.app.messages.MessagesRepository
import com.wearosignal.app.net.SignalNet
import com.wearosignal.app.notify.NotificationPresenter
import com.wearosignal.app.poll.PollScheduler
import com.wearosignal.app.tile.Glanceables
import java.io.File

/**
 * Lazy app-wide singletons.
 */
object AppDeps {

  private lateinit var appContext: Context

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  /** For components without their own Context (e.g. Poller pushing tile/complication updates). */
  val context: Context
    get() = appContext

  val account: AccountStore by lazy { AccountStore(appContext) }
  val database: WatchDatabase by lazy { WatchDatabase(appContext) }
  val net: SignalNet by lazy { SignalNet(appContext, account) }
  val aciProtocolStore: WatchProtocolStore by lazy { WatchProtocolStore(database, account, "aci") }
  val pniProtocolStore: WatchProtocolStore by lazy { WatchProtocolStore(database, account, "pni") }
  val messages: MessagesRepository by lazy { MessagesRepository(database) }
  val retriever: MessageRetriever by lazy { MessageRetriever(EnvelopeProcessor(messages)) }
  val notifier: NotificationPresenter by lazy { NotificationPresenter(appContext) }
  val avatars: AvatarStore by lazy { AvatarStore(appContext) }
  val attachments: AttachmentStore by lazy { AttachmentStore(appContext) }

  /**
   * Unlinks the watch locally: stops scheduled work, disconnects the socket, wipes account keys,
   * clears all SQLite tables, removes cached avatars/attachments, and refreshes Wear glanceables.
   */
  fun clearAllLocalData(context: Context) {
    val ctx = context.applicationContext
    PollScheduler.cancel(ctx)
    WorkManager.getInstance(ctx).cancelAllWork()
    runCatching { net.authWebSocket.disconnect() }
    account.clear()
    val db = database.writableDatabase
    db.beginTransaction()
    try {
      listOf(
        "messages",
        "contacts",
        "directory",
        "groups",
        "reactions",
        "identities",
        "sessions",
        "one_time_prekeys",
        "signed_prekeys",
        "kyber_prekeys",
        "used_kyber_tuples",
        "sender_keys"
      ).forEach { table ->
        runCatching { db.delete(table, null, null) }
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    File(ctx.filesDir, "avatars").listFiles()?.forEach { it.delete() }
    File(ctx.filesDir, "attachments").listFiles()?.forEach { it.delete() }
    Glanceables.requestUpdate(ctx)
  }
}
