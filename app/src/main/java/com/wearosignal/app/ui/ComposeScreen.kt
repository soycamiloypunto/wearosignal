package com.wearosignal.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wearosignal.app.R
import com.wearosignal.app.contacts.ContactDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pick a Signal contact to start a new conversation with. The list is the cached CDSI directory;
 * "Refresh contacts" re-reads the watch's address book and re-runs discovery (rate-limited, so
 * it's manual). Tapping a contact hands back the recipient to open the Wear text input.
 */
@Composable
fun ComposeScreen(onPick: (ContactDirectory.Entry) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val listState = rememberScalingLazyListState()
  var entries by remember { mutableStateOf(emptyList<ContactDirectory.Entry>()) }
  var status by remember { mutableStateOf<String?>(null) }
  var refreshing by remember { mutableStateOf(false) }

  fun runRefresh() {
    if (refreshing) return
    refreshing = true
    status = context.getString(R.string.compose_looking_up)
    scope.launch {
      val result = withContext(Dispatchers.IO) { ContactDirectory.refresh(context) }
      entries = withContext(Dispatchers.IO) { ContactDirectory.all() }
      status = when (result) {
        is ContactDirectory.RefreshResult.Success -> when {
          result.lookedUp == 0 -> context.getString(R.string.compose_no_contacts)
          result.registered == 0 -> context.getString(R.string.compose_none_on_signal, result.lookedUp)
          else -> context.getString(R.string.compose_found_on_signal, result.registered, result.lookedUp)
        }
        is ContactDirectory.RefreshResult.RateLimited ->
          context.getString(R.string.compose_rate_limited, result.retryAfterSeconds)
        is ContactDirectory.RefreshResult.Failure -> result.message
      }
      refreshing = false
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) runRefresh() else status = context.getString(R.string.compose_permission_needed)
  }

  fun refreshOrRequestPermission() {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
      runRefresh()
    } else {
      permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
  }

  LaunchedEffect(Unit) {
    entries = withContext(Dispatchers.IO) { ContactDirectory.all() }
    if (entries.isEmpty()) refreshOrRequestPermission()
  }

  ScalingLazyColumn(state = listState) {
    item {
      Text(
        text = stringResource(R.string.compose_title),
        style = MaterialTheme.typography.title3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
      )
    }
    item {
      Chip(
        label = {
          Text(
            stringResource(if (refreshing) R.string.compose_refreshing else R.string.compose_refresh)
          )
        },
        onClick = { refreshOrRequestPermission() },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
    status?.let { message ->
      item {
        Text(
          text = message,
          style = MaterialTheme.typography.caption2,
          color = MaterialTheme.colors.secondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
      }
    }
    items(entries.size) { i ->
      val entry = entries[i]
      Chip(
        label = { Text(entry.name) },
        secondaryLabel = { Text(entry.e164) },
        onClick = { onPick(entry) },
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
