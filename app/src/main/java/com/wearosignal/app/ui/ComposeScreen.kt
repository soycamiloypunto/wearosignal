package com.wearosignal.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.wearosignal.app.contacts.ContactReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pick a Signal contact to start a new conversation with.
 * Shows Signal-registered contacts first ("On Signal"), followed by a dimmed "Not on Signal"
 * section so the user clearly sees which watch contacts are reachable on Signal.
 */
@Composable
fun ComposeScreen(onPick: (ContactDirectory.Entry) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val listState = rememberScalingLazyListState()
  var entries by remember { mutableStateOf(emptyList<ContactDirectory.Entry>()) }
  var nonSignal by remember { mutableStateOf(emptyList<ContactReader.DeviceContact>()) }
  var status by remember { mutableStateOf<String?>(null) }
  var refreshing by remember { mutableStateOf(false) }

  suspend fun reloadLists() {
    val signalEntries = withContext(Dispatchers.IO) { ContactDirectory.all() }
    val signalNumbers = signalEntries.map { it.e164 }.toSet()
    val others = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
      withContext(Dispatchers.IO) {
        runCatching { ContactReader.read(context) }
          .getOrDefault(emptyList())
          .filter { it.e164 !in signalNumbers }
          .sortedBy { it.name.lowercase() }
      }
    } else {
      emptyList()
    }
    entries = signalEntries
    nonSignal = others
  }

  fun runRefresh() {
    if (refreshing) return
    refreshing = true
    status = context.getString(R.string.compose_looking_up)
    scope.launch {
      val result = withContext(Dispatchers.IO) { ContactDirectory.refresh(context) }
      reloadLists()
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
    reloadLists()
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

    if (entries.isNotEmpty()) {
      item {
        Text(
          text = stringResource(R.string.compose_on_signal),
          style = MaterialTheme.typography.caption2,
          color = MaterialTheme.colors.primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
        )
      }
      items(entries.size) { i ->
        val entry = entries[i]
        Chip(
          label = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          secondaryLabel = { Text(entry.e164, maxLines = 1) },
          onClick = { onPick(entry) },
          colors = ChipDefaults.primaryChipColors(),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    if (nonSignal.isNotEmpty()) {
      item {
        Text(
          text = stringResource(R.string.compose_without_signal),
          style = MaterialTheme.typography.caption2,
          color = Color(0xFF9E9E9E),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
        )
      }
      items(nonSignal.size) { i ->
        val contact = nonSignal[i]
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Text(
            text = contact.name,
            style = MaterialTheme.typography.caption1,
            color = Color(0xFF9E9E9E),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = contact.e164,
            style = MaterialTheme.typography.caption3,
            color = Color(0xFF6E6E6E),
            maxLines = 1
          )
        }
      }
    }
  }
}
