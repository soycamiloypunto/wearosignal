package com.wearosignal.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.R
import com.wearosignal.app.messages.ConversationRow

/**
 * Home screen: poll button, the most recent conversations (paged by "Load more"),
 * then the secondary actions.
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
      Chip(
        label = {
          Text(
            if (polling) {
              pollProgress ?: stringResource(R.string.conversations_checking)
            } else {
              stringResource(R.string.conversations_check)
            }
          )
        },
        onClick = onPoll,
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (pollStatus != null && !polling) {
      item {
        Text(
          text = "⚠ $pollStatus",
          style = MaterialTheme.typography.caption2,
          color = Color(0xFFFFAB91),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
      }
    }

    if (conversations.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.conversations_empty),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
      }
    }

    items(conversations.size) { i ->
      val conversation = conversations[i]
      Card(onClick = { onOpen(conversation) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Avatar(
            name = conversation.title,
            colorKey = conversation.peer,
            avatarKey = conversation.peer,
            size = 32.dp
          )
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
              text = if (conversation.isGroup) "${conversation.title} 👥" else conversation.title,
              style = MaterialTheme.typography.caption1,
              color = MaterialTheme.colors.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = if (conversation.isGroup || conversation.lastFromSelf) {
                "${conversation.lastSender}: ${conversation.lastBody}"
              } else {
                conversation.lastBody
              },
              style = MaterialTheme.typography.body2,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
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
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    item {
      Chip(
        label = { Text(stringResource(R.string.conversations_new_message)) },
        onClick = onNewMessage,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
      )
    }
    item {
      Chip(
        label = { Text(stringResource(R.string.conversations_settings)) },
        onClick = onOpenSettings,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
