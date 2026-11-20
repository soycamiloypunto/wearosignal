package com.wearosignal.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.R
import com.wearosignal.app.messages.MessageRow
import com.wearosignal.app.messages.attachmentPlaceholder
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val INCOMING_BUBBLE = Color(0xFF2C2C2E)

/** The fixed reaction palette (Signal's defaults) — a full emoji picker is unusable at watch size. */
private val REACTION_EMOJIS = listOf("❤️", "👍", "👎", "😂", "😮", "😢")

/**
 * One conversation as a chat: own messages right in the accent colour, incoming left;
 * group messages carry the sender's avatar and colour. Opens scrolled to the latest.
 * Long-pressing a bubble opens the reaction palette.
 */
@Composable
fun ThreadScreen(
  title: String,
  isGroup: Boolean,
  messages: List<MessageRow>,
  favoriteEmojis: List<String>,
  quickReplies: List<String>,
  polling: Boolean,
  pollProgress: String?,
  pollStatus: String?,
  sendingVoice: Boolean = false,
  onPoll: () -> Unit,
  onReply: () -> Unit,
  onSendEmoji: (String) -> Unit,
  onReplaceFavoriteEmoji: (Int) -> Unit,
  onSendQuickReply: (String) -> Unit,
  onEditQuickReply: (Int) -> Unit,
  onSendVoiceNote: (File) -> Unit,
  onReact: (MessageRow, String) -> Unit,
  onReactCustom: (MessageRow) -> Unit
) {
  val context = LocalContext.current
  // items: title + messages + reply bar; messages load asynchronously,
  // so scroll to the latest when they arrive rather than at creation
  val listState = rememberScalingLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.scrollToItem(messages.size + 1)
    }
  }

  var reactingTo by remember { mutableStateOf<MessageRow?>(null) }
  var showEmojiPicker by remember { mutableStateOf(false) }
  var showVoiceRecorder by remember { mutableStateOf(false) }
  var playingVideoPath by remember { mutableStateOf<String?>(null) }
  var editingQuickReplies by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }
  val checkingText = stringResource(R.string.conversations_checking)
  val sendingVoiceText = stringResource(R.string.thread_voice_sending)

  val micPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      localError = null
      showVoiceRecorder = true
    } else {
      localError = context.getString(R.string.thread_voice_mic_needed)
    }
  }

  fun startVoiceRecording() {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      localError = null
      showVoiceRecorder = true
    } else {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    ScalingLazyColumn(state = listState) {
      item {
        Text(
          text = if (isGroup) "$title 👥" else title,
          style = MaterialTheme.typography.title3,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }

      items(messages.size) { i ->
        val message = messages[i]
        // Collapse repeated sender chrome when the same person sends several in a row.
        val firstOfRun = i == 0 || messages[i - 1].senderAci != message.senderAci
        Column {
          if (i == 0 || !sameDay(messages[i - 1].sentAt, message.sentAt)) {
            DayDivider(message.sentAt)
          }
          MessageBubble(
            message = message,
            isGroup = isGroup,
            showSender = firstOfRun,
            onLongPress = { reactingTo = message },
            onPlayVideo = { path -> playingVideoPath = path }
          )
        }
      }

      // One status line above the buttons: live progress while checking/sending, else the last failure.
      val statusLine = when {
        sendingVoice -> sendingVoiceText
        polling -> pollProgress ?: checkingText
        localError != null -> "⚠ $localError"
        pollStatus != null -> "⚠ $pollStatus"
        else -> null
      }
      if (statusLine != null) {
        item {
          Text(
            text = statusLine,
            style = MaterialTheme.typography.caption3,
            color = if (polling || sendingVoice) Color(0xFF9E9E9E) else Color(0xFFFFAB91),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
          )
        }
      }

      // Compact 3-button composer bar: 🎤 Voice Note, 😊 Emoji, Aa Text + ⟳ Refresh
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
          Button(
            onClick = { startVoiceRecording() },
            enabled = !sendingVoice,
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
          ) {
            Text("🎤", style = MaterialTheme.typography.body1)
          }
          Button(
            onClick = { showEmojiPicker = true },
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
          ) {
            Text("😊", style = MaterialTheme.typography.body1)
          }
          Button(
            onClick = onReply,
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
          ) {
            Text("Aa", style = MaterialTheme.typography.button)
          }
          Button(
            onClick = onPoll,
            enabled = !polling,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
          ) {
            if (polling) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
              Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.conversations_check),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      // 5 customizable quick replies right below the composer bar (scroll down to tap & send)
      items(quickReplies.size) { idx ->
        val replyText = quickReplies[idx]
        Chip(
          label = { Text(if (editingQuickReplies) "✎ $replyText" else replyText, maxLines = 1) },
          onClick = {
            if (editingQuickReplies) onEditQuickReply(idx) else onSendQuickReply(replyText)
          },
          colors = if (editingQuickReplies) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
      }

      item {
        Chip(
          label = {
            Text(
              stringResource(
                if (editingQuickReplies) R.string.thread_quick_done else R.string.thread_quick_edit
              )
            )
          },
          onClick = { editingQuickReplies = !editingQuickReplies },
          colors = ChipDefaults.secondaryChipColors(),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
        )
      }
    }

    if (showVoiceRecorder) {
      VoiceRecorderOverlay(
        onSend = { file ->
          showVoiceRecorder = false
          onSendVoiceNote(file)
        },
        onCancel = { showVoiceRecorder = false }
      )
    }

    if (showEmojiPicker) {
      QuickEmojiOverlay(
        favorites = favoriteEmojis,
        onPick = { emoji ->
          showEmojiPicker = false
          onSendEmoji(emoji)
        },
        onReplaceFavorite = { slotIndex ->
          onReplaceFavoriteEmoji(slotIndex)
        },
        onDismiss = { showEmojiPicker = false }
      )
    }

    playingVideoPath?.let { path ->
      VideoPlayerOverlay(
        path = path,
        onDismiss = { playingVideoPath = null }
      )
    }

    reactingTo?.let { message ->
      ReactionPalette(
        myReaction = message.myReaction,
        onPick = { emoji ->
          reactingTo = null
          onReact(message, emoji)
        },
        onPickCustom = {
          reactingTo = null
          onReactCustom(message)
        },
        onDismiss = { reactingTo = null }
      )
    }
  }
}

/**
 * Full-screen overlay with the reaction palette. Our current reaction is highlighted;
 * picking it again retracts it (the caller derives remove from [myReaction]).
 * ＋ opens the system input for any other emoji; a current reaction from outside
 * the palette shows next to it so it stays retractable.
 */
@Composable
private fun ReactionPalette(
  myReaction: String?,
  onPick: (String) -> Unit,
  onPickCustom: () -> Unit,
  onDismiss: () -> Unit
) {
  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onDismiss),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      REACTION_EMOJIS.chunked(3).forEach { rowEmojis ->
        Row {
          rowEmojis.forEach { emoji ->
            PaletteButton(
              label = emoji,
              highlighted = emoji == myReaction,
              onClick = { onPick(emoji) }
            )
          }
        }
      }
      Row {
        if (myReaction != null && myReaction !in REACTION_EMOJIS) {
          PaletteButton(label = myReaction, highlighted = true, onClick = { onPick(myReaction) })
        }
        PaletteButton(label = "＋", highlighted = false, onClick = onPickCustom)
      }
    }
  }
}

@Composable
private fun PaletteButton(label: String, highlighted: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    colors = if (highlighted) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
    modifier = Modifier.padding(3.dp).size(44.dp)
  ) {
    Text(text = label, style = MaterialTheme.typography.title3)
  }
}

/**
 * Signal-style receipt state: checkmarked circles — one hollow = sent,
 * two hollow = delivered, two filled (check knocked out) = read.
 */
@Composable
private fun ReceiptCircles(delivered: Boolean, read: Boolean, color: Color, knockout: Color) {
  val two = delivered || read
  val dotColor = if (read) color else color.copy(alpha = 0.65f)
  Canvas(modifier = Modifier.size(width = if (two) 17.dp else 8.dp, height = 8.dp)) {
    val radius = size.height / 2f
    val stroke = 1.dp.toPx()

    fun check(centerX: Float, checkColor: Color) {
      val path = Path().apply {
        moveTo(centerX - radius * 0.42f, radius * 1.02f)
        lineTo(centerX - radius * 0.08f, radius * 1.38f)
        lineTo(centerX + radius * 0.48f, radius * 0.62f)
      }
      drawPath(path, checkColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun circle(centerX: Float) {
      if (read) {
        // Filled circle, check knocked out in the bubble colour.
        drawCircle(dotColor, radius, Offset(centerX, radius))
        check(centerX, knockout)
      } else {
        drawCircle(dotColor, radius - stroke / 2f, Offset(centerX, radius), style = Stroke(stroke))
        check(centerX, dotColor)
      }
    }

    circle(radius)
    if (two) {
      circle(size.width - radius)
    }
  }
}

/** The image, animated GIF/sticker, audio pill, or video pill when downloaded, else a placeholder. */
@Composable
private fun AttachmentContent(
  message: MessageRow,
  contentColor: Color,
  onPlayVideo: (String) -> Unit,
  modifier: Modifier
) {
  val path = message.attachmentPath
  val isAudio = message.attachmentType?.startsWith("audio/") == true
  val isVideo = message.attachmentType?.startsWith("video/") == true
  val isImage = message.attachmentType?.startsWith("image/") == true
  val isAnimated = path != null && (path.endsWith(".gif", ignoreCase = true) || path.endsWith(".webp", ignoreCase = true))

  if (isAudio && path != null && File(path).exists()) {
    AudioPlayPill(path = path, contentColor = contentColor, modifier = modifier)
    return
  }

  if (isVideo && path != null && File(path).exists()) {
    VideoPlayPill(contentColor = contentColor, onPlay = { onPlayVideo(path) }, modifier = modifier)
    return
  }

  if (isAnimated && File(path!!).exists()) {
    AnimatedMediaView(
      path = path,
      modifier = modifier
        .padding(bottom = if (message.body.isNotEmpty()) 3.dp else 0.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
    )
    return
  }

  val bitmap = remember(path, isImage) {
    if (isImage && path != null) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
  }

  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = "Photo",
      modifier = modifier
        .padding(bottom = if (message.body.isNotEmpty()) 3.dp else 0.dp)
        .fillMaxWidth()
        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
        .clip(RoundedCornerShape(10.dp))
    )
  } else {
    Text(
      text = attachmentPlaceholder(message.attachmentType) + if (isImage || isAudio || isVideo) "…" else "",
      style = MaterialTheme.typography.body2,
      color = contentColor.copy(alpha = 0.7f),
      modifier = modifier
    )
  }
}

private fun sameDay(a: Long, b: Long): Boolean {
  val calA = Calendar.getInstance().apply { timeInMillis = a }
  val calB = Calendar.getInstance().apply { timeInMillis = b }
  return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
    calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayDivider(at: Long) {
  Text(
    text = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(at)),
    style = MaterialTheme.typography.caption3,
    color = Color(0xFF9E9E9E),
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
  message: MessageRow,
  isGroup: Boolean,
  showSender: Boolean,
  onLongPress: () -> Unit,
  onPlayVideo: (String) -> Unit
) {
  val fromSelf = message.fromSelf
  val bubbleShape = if (fromSelf) {
    RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
  } else {
    RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
  }

  Box(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
    Row(
      verticalAlignment = Alignment.Bottom,
      modifier = Modifier
        .align(if (fromSelf) Alignment.CenterEnd else Alignment.CenterStart)
        .widthIn(max = 150.dp)
    ) {
      if (isGroup && !fromSelf) {
        if (showSender) {
          Avatar(name = message.sender, colorKey = message.senderAci, avatarKey = message.senderAci, size = 20.dp)
        } else {
          Box(modifier = Modifier.widthIn(min = 20.dp)) {} // keep bubbles of a run aligned
        }
      }

      Column(modifier = Modifier.padding(start = if (isGroup && !fromSelf) 4.dp else 0.dp)) {
        if (isGroup && !fromSelf && showSender) {
          Text(
            text = message.sender,
            style = MaterialTheme.typography.caption3,
            color = senderColor(message.senderAci),
            modifier = Modifier.padding(start = 6.dp, bottom = 1.dp)
          )
        }
        Column(
          horizontalAlignment = Alignment.End,
          modifier = Modifier
            .clip(bubbleShape)
            .background(if (fromSelf) MaterialTheme.colors.primary else INCOMING_BUBBLE)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
          val contentColor = if (fromSelf) MaterialTheme.colors.onPrimary else Color.White
          if (message.attachmentType != null) {
            AttachmentContent(
              message = message,
              contentColor = contentColor,
              onPlayVideo = onPlayVideo,
              modifier = Modifier.align(Alignment.Start)
            )
          }
          if (message.body.isNotEmpty()) {
            Text(
              text = message.body,
              style = MaterialTheme.typography.body2,
              color = contentColor,
              modifier = Modifier.align(Alignment.Start)
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)),
              style = MaterialTheme.typography.caption3,
              color = contentColor.copy(alpha = 0.55f)
            )
            if (fromSelf) {
              Spacer(modifier = Modifier.width(3.dp))
              ReceiptCircles(
                delivered = message.delivered,
                read = message.read,
                color = contentColor,
                knockout = MaterialTheme.colors.primary
              )
            }
          }
        }
        if (message.reactions.isNotEmpty()) {
          Row(modifier = Modifier.padding(top = 1.dp).align(if (fromSelf) Alignment.End else Alignment.Start)) {
            message.reactions.forEach { reaction ->
              Text(
                text = if (reaction.count > 1) "${reaction.emoji} ${reaction.count}" else reaction.emoji,
                style = MaterialTheme.typography.caption3,
                color = Color.White,
                modifier = Modifier
                  .padding(end = 2.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (reaction.mine) MaterialTheme.colors.primaryVariant else INCOMING_BUBBLE)
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              )
            }
          }
        }
      }
    }
  }
}
