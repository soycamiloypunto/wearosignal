package com.wearosignal.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.BuildConfig
import com.wearosignal.app.R
import com.wearosignal.app.messages.ConversationRow

/**
 * Circular Wear OS launcher-style home screen: 3-column grid of circular chat avatars
 * plus circular quick actions (Sync, New message, Settings).
 */
@Composable
fun ConversationsScreen(
  conversations: List<ConversationRow>,
  hasMore: Boolean,
  polling: Boolean,
  pollProgress: String?,
  pollStatus: String?,
  onPoll: () -> Unit,
  onLoadMore: () -> Unit,
  onOpen: (ConversationRow) -> Unit,
  onNewMessage: () -> Unit,
  onOpenSettings: () -> Unit
) {
  val listState = rememberScalingLazyListState()
  ScalingLazyColumn(state = listState) {
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.title3,
          textAlign = TextAlign.Center
        )
        Text(
          text = "v${BuildConfig.VERSION_NAME}",
          style = MaterialTheme.typography.caption3,
          color = Color(0xFF9E9E9E),
          textAlign = TextAlign.Center
        )
      }
    }

    if (polling) {
      item {
        Text(
          text = pollProgress ?: stringResource(R.string.conversations_checking),
          style = MaterialTheme.typography.caption3,
          color = Color(0xFF9E9E9E),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
      }
    } else if (pollStatus != null) {
      item {
        Text(
          text = "⚠ $pollStatus",
          style = MaterialTheme.typography.caption3,
          color = Color(0xFFFFAB91),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
      }
    }

    if (conversations.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.conversations_empty),
          style = MaterialTheme.typography.caption1,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
      }
    } else {
      val rows = conversations.chunked(3)
      items(rows.size) { rowIdx ->
        val rowItems = rows[rowIdx]
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
          rowItems.forEach { conversation ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(52.dp)
                .clickable { onOpen(conversation) }
            ) {
              Avatar(
                name = conversation.title,
                colorKey = conversation.peer,
                avatarKey = conversation.peer,
                size = 46.dp
              )
              Text(
                text = conversation.title.substringBefore(" "),
                style = MaterialTheme.typography.caption3,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
          // Balance incomplete last row
          repeat(3 - rowItems.size) {
            Spacer(modifier = Modifier.width(52.dp))
          }
        }
      }
    }

    if (hasMore) {
      item {
        Chip(
          label = { Text(stringResource(R.string.conversations_load_more)) },
          onClick = onLoadMore,
          colors = ChipDefaults.secondaryChipColors(),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
    }

    // Circular action buttons row: Sync, New message, Settings
    item {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
      ) {
        Button(
          onClick = onPoll,
          enabled = !polling,
          colors = ButtonDefaults.primaryButtonColors(),
          modifier = Modifier.size(46.dp)
        ) {
          if (polling) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              painter = painterResource(R.drawable.ic_refresh),
              contentDescription = stringResource(R.string.conversations_check),
              modifier = Modifier.size(22.dp)
            )
          }
        }
        Button(
          onClick = onNewMessage,
          colors = ButtonDefaults.secondaryButtonColors(),
          modifier = Modifier.size(46.dp)
        ) {
          Text("＋", style = MaterialTheme.typography.title3)
        }
        Button(
          onClick = onOpenSettings,
          colors = ButtonDefaults.secondaryButtonColors(),
          modifier = Modifier.size(46.dp)
        ) {
          Text("⚙", style = MaterialTheme.typography.title3)
        }
      }
    }
  }
}
