package com.wearosignal.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.AppDeps
import com.wearosignal.app.BuildConfig
import com.wearosignal.app.R
import com.wearosignal.app.poll.PollScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Settings: linked-account info, notification controls, privacy options, unlink action,
 * and (in debug builds only) emulator/phone-state override.
 */
@Composable
fun StatusScreen(onUnlinked: () -> Unit = {}) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val account = AppDeps.account
  val listState = rememberScalingLazyListState()
  var intervalMinutes by remember { mutableIntStateOf(account.pollIntervalMinutes) }
  var backgroundPolling by remember { mutableStateOf(account.backgroundPollingEnabled) }
  var readReceipts by remember { mutableStateOf(account.sendReadReceipts) }
  var lockscreenPrivacy by remember { mutableStateOf(account.lockscreenPrivacyEnabled) }
  var override by remember { mutableStateOf(account.phoneConnectedOverride) }
  var confirmUnlink by remember { mutableStateOf(false) }

  ScalingLazyColumn(state = listState) {
    item {
      Text(
        text = account.e164 ?: stringResource(R.string.status_not_linked),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
      )
    }
    item {
      val last = account.lastPollAt
      Text(
        text = if (last == 0L) {
          stringResource(R.string.status_never_polled)
        } else {
          stringResource(R.string.status_last_poll, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last)))
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      )
    }
    item {
      Chip(
        label = {
          Text(
            stringResource(
              if (backgroundPolling) R.string.status_notifications_on else R.string.status_notifications_off
            )
          )
        },
        secondaryLabel = {
          Text(
            if (backgroundPolling) {
              stringResource(R.string.status_polls_every, intervalMinutes)
            } else {
              stringResource(R.string.status_phone_forwards)
            }
          )
        },
        onClick = {
          backgroundPolling = !backgroundPolling
          account.backgroundPollingEnabled = backgroundPolling
          PollScheduler.scheduleNext(context)
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
    if (backgroundPolling) {
      item {
        Chip(
          label = { Text(stringResource(R.string.status_interval, intervalMinutes)) },
          onClick = {
            intervalMinutes = when (intervalMinutes) {
              1 -> 5
              5 -> 15
              else -> 1
            }
            account.pollIntervalMinutes = intervalMinutes
            PollScheduler.scheduleNext(context)
          },
          colors = ChipDefaults.secondaryChipColors(),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
    item {
      Chip(
        label = {
          Text(
            stringResource(
              if (readReceipts) R.string.status_read_receipts_on else R.string.status_read_receipts_off
            )
          )
        },
        secondaryLabel = {
          Text(
            stringResource(
              if (readReceipts) R.string.status_read_receipts_on_desc else R.string.status_read_receipts_off_desc
            )
          )
        },
        onClick = {
          readReceipts = !readReceipts
          account.sendReadReceipts = readReceipts
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
    item {
      Chip(
        label = {
          Text(
            stringResource(
              if (lockscreenPrivacy) R.string.status_lockscreen_private else R.string.status_lockscreen_show
            )
          )
        },
        secondaryLabel = {
          Text(
            stringResource(
              if (lockscreenPrivacy) R.string.status_lockscreen_private_desc else R.string.status_lockscreen_show_desc
            )
          )
        },
        onClick = {
          lockscreenPrivacy = !lockscreenPrivacy
          account.lockscreenPrivacyEnabled = lockscreenPrivacy
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
    if (BuildConfig.DEBUG) {
      item {
        Chip(
          label = {
            Text(
              when (override) {
                null -> stringResource(R.string.status_phone_auto)
                true -> stringResource(R.string.status_phone_connected)
                false -> stringResource(R.string.status_phone_away)
              }
            )
          },
          onClick = {
            override = when (override) {
              null -> true
              true -> false
              false -> null
            }
            account.phoneConnectedOverride = override
          },
          colors = ChipDefaults.secondaryChipColors(),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
    item {
      Chip(
        label = {
          Text(
            stringResource(
              if (confirmUnlink) R.string.status_unlink_confirm else R.string.status_unlink
            )
          )
        },
        secondaryLabel = { Text(stringResource(R.string.status_unlink_desc)) },
        onClick = {
          if (!confirmUnlink) {
            confirmUnlink = true
          } else {
            scope.launch {
              withContext(Dispatchers.IO) { AppDeps.clearAllLocalData(context) }
              onUnlinked()
            }
          }
        },
        colors = ChipDefaults.chipColors(
          backgroundColor = if (confirmUnlink) Color(0xFFB71C1C) else Color(0xFF4A1C1C),
          contentColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
      )
    }
    item {
      Text(
        text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}\n${stringResource(R.string.status_about)}",
        style = MaterialTheme.typography.caption3,
        color = Color(0xFF9E9E9E),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
      )
    }
  }
}
