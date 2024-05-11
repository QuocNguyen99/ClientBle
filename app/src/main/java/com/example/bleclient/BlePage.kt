package com.example.bleclient

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import java.nio.ByteOrder
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun BlePage(viewModel: MainViewModel, hasRequiredBluetoothPermissions: () -> Boolean, requestRelevantRuntimePermissions: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier
                .weight(1f),
                content = {
                    items(uiState.deviceScan) {
                        ListItem(
                            headlineContent = { Text(text = it.name ?: " unknown") },
                            supportingContent = { Text(text = it.address) },
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.connectToDevice(it) },
                                onLongClick = {
                                    viewModel.writeCharacteristic()
                                })
                        )
                    }
                })

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.fillMaxWidth(0.5f), onClick = {
                    if (!hasRequiredBluetoothPermissions()) {
                        requestRelevantRuntimePermissions()
                    } else {
                        viewModel.scanBle()
                    }

                }) {
                    Text(text = "Scan")
                }
                Button(modifier = Modifier.fillMaxWidth(1f), onClick = { viewModel.stopScanBle() }) {
                    Text(text = "Stop")
                }
            }
        }

        if (uiState.isConnected)
            AlertDialog(onDismissRequest = {},
                confirmButton = { Button(onClick = { viewModel.writeCharacteristic() }) {
                    Text(text = "Connected")
                }})
    }
}