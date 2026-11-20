package com.wearosignal.app.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.R
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_VOICE_SECONDS = 120

/**
 * Full-screen voice recorder overlay: starts AAC/M4A recording immediately, displays elapsed
 * seconds, and lets the user send (✓) or cancel (✕).
 */
@Composable
fun VoiceRecorderOverlay(
  onSend: (File) -> Unit,
  onCancel: () -> Unit
) {
  val context = LocalContext.current
  val outputFile = remember { File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a") }
  var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
  var seconds by remember { mutableIntStateOf(0) }
  var levels by remember { mutableStateOf(List(13) { 0.12f }) }
  var error by remember { mutableStateOf<String?>(null) }
  var finished by remember { mutableStateOf(false) }

  fun stopAndRelease(deleteFile: Boolean) {
    val r = recorder
    recorder = null
    if (r != null) {
      runCatching { r.stop() }
      runCatching { r.release() }
    }
    if (deleteFile) {
      outputFile.delete()
    }
  }

  DisposableEffect(Unit) {
    try {
      val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }
      r.setAudioSource(MediaRecorder.AudioSource.MIC)
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      r.setAudioSamplingRate(44100)
      r.setAudioEncodingBitRate(64000)
      r.setAudioChannels(1)
      r.setOutputFile(outputFile.absolutePath)
      r.prepare()
      r.start()
      recorder = r
    } catch (t: Throwable) {
      error = t.message ?: "Mic error"
      outputFile.delete()
    }
    onDispose {
      if (!finished) {
        stopAndRelease(deleteFile = true)
      }
    }
  }

  LaunchedEffect(recorder) {
    val active = recorder ?: return@LaunchedEffect
    var ticks = 0
    while (seconds < MAX_VOICE_SECONDS && recorder === active) {
      delay(80)
      ticks++
      if (ticks % 12 == 0) {
        seconds++
      }
      val amp = runCatching { active.maxAmplitude }.getOrDefault(0)
      // Normalize microphone amplitude (0..32767) with boosted speech sensitivity
      val norm = ((amp / 14000f).coerceIn(0.12f, 1f))
      levels = levels.drop(1) + norm
    }
    if (seconds >= MAX_VOICE_SECONDS && !finished) {
      finished = true
      stopAndRelease(deleteFile = false)
      onSend(outputFile)
    }
  }

  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xF2000000)),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (error != null) {
        Text(text = error!!, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption2)
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onCancel, colors = ButtonDefaults.secondaryButtonColors()) {
          Text("✕")
        }
      } else {
        Text(
          text = String.format("%d:%02d", seconds / 60, seconds % 60),
          style = MaterialTheme.typography.title2,
          color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Live dynamic microphone waveform bars
        val barColor = MaterialTheme.colors.primary
        Canvas(modifier = Modifier.width(120.dp).height(34.dp)) {
          val count = levels.size
          val slotW = size.width / count
          val barW = slotW * 0.56f
          levels.forEachIndexed { idx, level ->
            val barH = (size.height * level).coerceAtLeast(4.dp.toPx())
            val x = idx * slotW + (slotW - barW) / 2f
            val y = (size.height - barH) / 2f
            drawRoundRect(
              color = if (level > 0.55f) Color(0xFF4FC3F7) else barColor,
              topLeft = Offset(x, y),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "● ${stringResource(R.string.thread_voice_recording)}",
          color = Color(0xFFFF5252),
          style = MaterialTheme.typography.caption3
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Button(
            onClick = {
              finished = true
              stopAndRelease(deleteFile = true)
              onCancel()
            },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
          ) {
            Text("✕", style = MaterialTheme.typography.title3)
          }
          Button(
            onClick = {
              finished = true
              stopAndRelease(deleteFile = false)
              if (outputFile.exists() && outputFile.length() > 0L) {
                onSend(outputFile)
              } else {
                outputFile.delete()
                onCancel()
              }
            },
            enabled = seconds >= 1,
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
          ) {
            Text("✓", style = MaterialTheme.typography.title3)
          }
        }
      }
    }
  }
}

/**
 * Compact playable audio pill inside a chat bubble for received or sent voice notes.
 */
@Composable
fun AudioPlayPill(path: String, contentColor: Color, modifier: Modifier = Modifier) {
  var playing by remember(path) { mutableStateOf(false) }
  var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }

  fun stopPlayback() {
    player?.let {
      runCatching { it.stop() }
      runCatching { it.release() }
    }
    player = null
    playing = false
  }

  DisposableEffect(path) {
    onDispose { stopPlayback() }
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0x33000000))
      .clickable {
        if (playing) {
          stopPlayback()
        } else {
          try {
            val mp = MediaPlayer().apply {
              setDataSource(path)
              setOnCompletionListener { stopPlayback() }
              setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
              }
              prepare()
              start()
            }
            player = mp
            playing = true
          } catch (_: Throwable) {
            stopPlayback()
          }
        }
      }
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = stringResource(if (playing) R.string.thread_audio_stop else R.string.thread_audio_play),
      style = MaterialTheme.typography.body2,
      color = contentColor
    )
  }
}

/**
 * Renders GIFs and WebP stickers (static or animated) using Android 11+ ImageDecoder + AnimatedImageDrawable.
 */
@Composable
fun AnimatedMediaView(path: String, modifier: Modifier = Modifier) {
  val drawable = remember(path) {
    runCatching {
      ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path)))
    }.getOrNull()
  }

  DisposableEffect(drawable) {
    (drawable as? AnimatedImageDrawable)?.start()
    onDispose {
      (drawable as? AnimatedImageDrawable)?.stop()
    }
  }

  if (drawable != null) {
    AndroidView(
      factory = { ctx ->
        ImageView(ctx).apply {
          adjustViewBounds = true
          scaleType = ImageView.ScaleType.FIT_CENTER
          setImageDrawable(drawable)
        }
      },
      update = { view ->
        if (view.drawable !== drawable) {
          view.setImageDrawable(drawable)
        }
      },
      modifier = modifier
    )
  }
}

/** Tappable video pill inside a chat bubble; opens [VideoPlayerOverlay]. */
@Composable
fun VideoPlayPill(contentColor: Color, onPlay: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0x33000000))
      .clickable(onClick = onPlay)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = stringResource(R.string.thread_video_play),
      style = MaterialTheme.typography.body2,
      color = contentColor
    )
  }
}

/** Full-screen video player overlay; tap anywhere or press ✕ to close. */
@Composable
fun VideoPlayerOverlay(path: String, onDismiss: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { ctx ->
        VideoView(ctx).apply {
          setVideoURI(Uri.fromFile(File(path)))
          setOnPreparedListener { mp ->
            mp.isLooping = true
            start()
          }
          setOnErrorListener { _, _, _ ->
            onDismiss()
            true
          }
        }
      },
      modifier = Modifier.fillMaxSize()
    )
    Button(
      onClick = onDismiss,
      colors = ButtonDefaults.secondaryButtonColors(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
        .size(32.dp)
    ) {
      Text("✕", style = MaterialTheme.typography.caption1)
    }
  }
}

/**
 * Full-screen quick emoji grid with 9 user-customizable favorites.
 * Tapping ＋ toggles edit mode so the user can tap any of the 9 slots to replace it with their favorite emoji.
 */
@Composable
fun QuickEmojiOverlay(
  favorites: List<String>,
  onPick: (String) -> Unit,
  onReplaceFavorite: (index: Int) -> Unit,
  onDismiss: () -> Unit
) {
  var editing by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xEB000000)).clickable(onClick = onDismiss),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (editing) {
        Text(
          text = stringResource(R.string.thread_emoji_edit_hint),
          style = MaterialTheme.typography.caption3,
          color = MaterialTheme.colors.primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
        )
      }
      favorites.chunked(3).forEachIndexed { rowIdx, row ->
        Row {
          row.forEachIndexed { colIdx, emoji ->
            val index = rowIdx * 3 + colIdx
            val slotMod = Modifier
              .padding(2.dp)
              .size(38.dp)
              .then(
                if (editing) Modifier.border(1.5.dp, MaterialTheme.colors.primary, CircleShape)
                else Modifier
              )
            Button(
              onClick = {
                if (editing) onReplaceFavorite(index) else onPick(emoji)
              },
              colors = ButtonDefaults.secondaryButtonColors(),
              modifier = slotMod
            ) {
              Text(text = emoji, style = MaterialTheme.typography.title3)
            }
          }
        }
      }
      Row {
        Button(
          onClick = { editing = !editing },
          colors = if (editing) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
          modifier = Modifier.padding(top = 3.dp).width(64.dp).height(32.dp)
        ) {
          Text(text = if (editing) "✓" else "＋", style = MaterialTheme.typography.title3)
        }
      }
    }
  }
}
