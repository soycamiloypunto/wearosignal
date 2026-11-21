package com.wearosignal.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val CHAT_ROW_BG = Color(0xFF1F222A)
private val PINNED_ROW_BG = Color(0xFF23293A)

/**
 * Home screen: scrollable list of chat cards (pinned chats first with 📌, long-press to pin/unpin
 * or delete chat) clearly differentiated from the 3 circular action buttons (Sync, New, Settings).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
  conversations: List<ConversationRow>,
  pinnedPeers: List<String>,
  hasMore: Boolean,
  polling: Boolean,
  pollProgress: String?,
  pollStatus: String?,
  onPoll: () -> Unit,
  onLoadMore: () -> Unit,
  onOpen: (ConversationRow) -> Unit,
  onTogglePin: (ConversationRow) -> Boolean,
  onDeleteConversation: (ConversationRow) -> Unit,
  onNewMessage: () -> Unit,
  onOpenSettings: () -> Unit
) {
  val listState = rememberScalingLazyListState()
  var selectedForMenu by remember { mutableStateOf<ConversationRow?>(null) }
  var confirmDelete by remember { mutableStateOf(false) }
  var pinLimitNotice by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
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
        items(conversations.size) { i ->
          val conversation = conversations[i]
          val isPinned = conversation.peer in pinnedPeers
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(if (isPinned) PINNED_ROW_BG else CHAT_ROW_BG)
              .combinedClickable(
                onClick = { onOpen(conversation) },
                onLongClick = {
                  selectedForMenu = conversation
                  confirmDelete = false
                  pinLimitNotice = false
                }
              )
              .padding(horizontal = 10.dp, vertical = 8.dp)
          ) {
            Avatar(
              name = conversation.title,
              colorKey = conversation.peer,
              avatarKey = conversation.peer,
              size = 34.dp
            )
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
              val prefix = if (isPinned) "📌 " else ""
              val suffix = if (conversation.isGroup) " 👥" else ""
              Text(
                text = "$prefix${conversation.title}$suffix",
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
                color = Color.White,
                maxLines = 1,
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
          )
        }
      }

      // 3 circular action buttons: Sync, New message, Settings
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

    selectedForMenu?.let { target ->
      val isPinned = target.peer in pinnedPeers
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xEE000000))
          .clickable { selectedForMenu = null },
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
          Text(
            text = target.title,
            style = MaterialTheme.typography.title3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          if (pinLimitNotice) {
            Text(
              text = stringResource(R.string.conversations_pin_limit),
              style = MaterialTheme.typography.caption3,
              color = Color(0xFFFFAB91),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(bottom = 6.dp)
            )
          }
          Chip(
            label = {
              Text(
                if (isPinned) "📌 ${stringResource(R.string.conversations_unpin)}"
                else "📌 ${stringResource(R.string.conversations_pin)}"
              )
            },
            onClick = {
              val ok = onTogglePin(target)
              if (ok) {
                selectedForMenu = null
              } else {
                pinLimitNotice = true
              }
            },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
          )
          Chip(
            label = {
              Text(
                if (confirmDelete) stringResource(R.string.conversations_delete_confirm)
                else "🗑 ${stringResource(R.string.conversations_delete)}"
              )
            },
            onClick = {
              if (!confirmDelete) {
                confirmDelete = true
              } else {
                selectedForMenu = null
                onDeleteConversation(target)
              }
            },
            colors = ChipDefaults.chipColors(
              backgroundColor = if (confirmDelete) Color(0xFFD32F2F) else Color(0xFF5C1E1E),
              contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  }
}
