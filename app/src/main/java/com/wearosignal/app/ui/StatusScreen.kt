package com.wearosignal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
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
 * Modern Wear OS settings screen: native ToggleChips with interactive checkboxes,
 * circular interval selector buttons (1m / 5m / 15m), and a circular unlink action button.
 */
@Composable
fun StatusScreen(
  onChatsCleared: () -> Unit = {},
  onUnlinked: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val account = AppDeps.account
  val listState = rememberScalingLazyListState()
  var intervalMinutes by remember { mutableIntStateOf(account.pollIntervalMinutes) }
  var backgroundPolling by remember { mutableStateOf(account.backgroundPollingEnabled) }
  var readReceipts by remember { mutableStateOf(account.sendReadReceipts) }
  var lockscreenPrivacy by remember { mutableStateOf(account.lockscreenPrivacyEnabled) }
  var override by remember { mutableStateOf(account.phoneConnectedOverride) }
  var confirmClearChats by remember { mutableStateOf(false) }
  var confirmUnlink by remember { mutableStateOf(false) }

  ScalingLazyColumn(state = listState) {
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
      ) {
        Text(
          text = stringResource(R.string.conversations_settings),
          style = MaterialTheme.typography.title3,
          textAlign = TextAlign.Center
        )
        Text(
          text = account.e164 ?: stringResource(R.string.status_not_linked),
          style = MaterialTheme.typography.caption2,
          color = MaterialTheme.colors.primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 2.dp)
        )
        val last = account.lastPollAt
        Text(
          text = if (last == 0L) {
            stringResource(R.string.status_never_polled)
          } else {
            stringResource(R.string.status_last_poll, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last)))
          },
          style = MaterialTheme.typography.caption3,
          color = Color(0xFF9E9E9E),
          textAlign = TextAlign.Center
        )
      }
    }

    item {
      ToggleChip(
        checked = backgroundPolling,
        onCheckedChange = { checked ->
          backgroundPolling = checked
          account.backgroundPollingEnabled = checked
          PollScheduler.scheduleNext(context)
        },
        label = {
          Text(
            text = stringResource(R.string.status_notifications),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        secondaryLabel = {
          Text(
            text = if (backgroundPolling) {
              stringResource(R.string.status_polls_every, intervalMinutes)
            } else {
              stringResource(R.string.status_phone_forwards)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        toggleControl = {
          Icon(
            imageVector = ToggleChipDefaults.checkboxIcon(checked = backgroundPolling),
            contentDescription = null
          )
        },
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (backgroundPolling) {
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
          listOf(1, 5, 15).forEach { mins ->
            val selected = intervalMinutes == mins
            Button(
              onClick = {
                intervalMinutes = mins
                account.pollIntervalMinutes = mins
                PollScheduler.scheduleNext(context)
              },
              colors = if (selected) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
              modifier = Modifier.size(42.dp)
            ) {
              Text("${mins}m", style = MaterialTheme.typography.caption1)
            }
          }
        }
      }
    }

    item {
      ToggleChip(
        checked = readReceipts,
        onCheckedChange = { checked ->
          readReceipts = checked
          account.sendReadReceipts = checked
        },
        label = {
          Text(
            text = stringResource(R.string.status_read_receipts),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        secondaryLabel = {
          Text(
            text = stringResource(
              if (readReceipts) R.string.status_read_receipts_on_desc else R.string.status_read_receipts_off_desc
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        toggleControl = {
          Icon(
            imageVector = ToggleChipDefaults.checkboxIcon(checked = readReceipts),
            contentDescription = null
          )
        },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      ToggleChip(
        checked = lockscreenPrivacy,
        onCheckedChange = { checked ->
          lockscreenPrivacy = checked
          account.lockscreenPrivacyEnabled = checked
        },
        label = {
          Text(
            text = stringResource(R.string.status_lockscreen),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        secondaryLabel = {
          Text(
            text = stringResource(
              if (lockscreenPrivacy) R.string.status_lockscreen_private_desc else R.string.status_lockscreen_show_desc
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        toggleControl = {
          Icon(
            imageVector = ToggleChipDefaults.checkboxIcon(checked = lockscreenPrivacy),
            contentDescription = null
          )
        },
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

    // Clear all local chats on the watch without unlinking
    item {
      Chip(
        label = {
          Text(
            stringResource(
              if (confirmClearChats) R.string.status_clear_chats_confirm else R.string.status_clear_chats
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        secondaryLabel = {
          Text(
            stringResource(R.string.status_clear_chats_desc),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        onClick = {
          if (!confirmClearChats) {
            confirmClearChats = true
          } else {
            confirmClearChats = false
            scope.launch {
              withContext(Dispatchers.IO) {
                AppDeps.messages.clearAllChats()
                AppDeps.account.pinnedPeers = emptyList()
              }
              onChatsCleared()
            }
          }
        },
        colors = ChipDefaults.chipColors(
          backgroundColor = if (confirmClearChats) Color(0xFFB71C1C) else Color(0xFF2D232E),
          contentColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
      )
    }

    // Circular Unlink Watch action button with tap-again confirmation
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
      ) {
        Button(
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
          colors = ButtonDefaults.buttonColors(
            backgroundColor = if (confirmUnlink) Color(0xFFD32F2F) else Color(0xFF5C1E1E),
            contentColor = Color.White
          ),
          modifier = Modifier.size(50.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_logout),
            contentDescription = stringResource(R.string.status_unlink),
            modifier = Modifier.size(22.dp)
          )
        }
        Text(
          text = stringResource(
            if (confirmUnlink) R.string.status_unlink_confirm else R.string.status_unlink
          ),
          style = MaterialTheme.typography.caption2,
          color = if (confirmUnlink) Color(0xFFFF8A80) else Color(0xFFE0E0E0),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }

    item {
      Text(
        text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}\n${stringResource(R.string.status_about)}",
        style = MaterialTheme.typography.caption3,
        color = Color(0xFF9E9E9E),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp)
      )
    }
  }
}
