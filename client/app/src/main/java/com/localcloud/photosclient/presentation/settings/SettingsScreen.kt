package com.localcloud.photosclient.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcloud.photosclient.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFreeUpSpace: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_sync)) {
                        PremiumSwitchPreference(
                            title = stringResource(id = R.string.settings_auto_sync),
                            subtitle = "Keep your media in sync automatically",
                            icon = Icons.Default.CloudSync,
                            isChecked = state.syncSettings.autoSyncEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleAutoSync(it)) }
                        )
                        PremiumSwitchPreference(
                            title = stringResource(id = R.string.settings_wifi_only),
                            subtitle = "Save mobile data by using Wi-Fi only",
                            icon = Icons.Default.Wifi,
                            isChecked = state.syncSettings.wifiOnly,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleWifiOnly(it)) }
                        )
                        PremiumSwitchPreference(
                            title = stringResource(id = R.string.settings_charging_only),
                            subtitle = "Sync only when your device is charging",
                            icon = Icons.Default.BatteryChargingFull,
                            isChecked = state.syncSettings.chargingOnly,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleChargingOnly(it)) }
                        )
                        PremiumSwitchPreference(
                            title = "Background Sync",
                            subtitle = "Allow syncing when the app is in background",
                            icon = Icons.Default.Sync,
                            isChecked = state.syncSettings.backgroundSyncEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleBackgroundSync(it)) }
                        )
                        PremiumSwitchPreference(
                            title = "Allow Local Removal",
                            subtitle = "Enable removing local files after backup",
                            icon = Icons.Default.CloudOff,
                            isChecked = state.allowLocalRemoval,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleAllowLocalRemoval(it)) }
                        )
                        if (state.allowLocalRemoval) {
                            PremiumActionPreference(
                                title = "Free Up Space",
                                subtitle = "Remove local copies of backed-up media",
                                icon = Icons.Default.DeleteSweep,
                                actionLabel = "Review",
                                onAction = onNavigateToFreeUpSpace
                            )
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_device)) {
                        PremiumActionPreference(
                            title = stringResource(id = R.string.settings_device_id),
                            subtitle = state.deviceId,
                            icon = Icons.Default.Smartphone,
                            actionLabel = stringResource(id = R.string.settings_copy),
                            onAction = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Device ID", state.deviceId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.settings_device_id_copied), Toast.LENGTH_SHORT).show()
                            }
                        )
                        PremiumInfoPreference(
                            title = "Last Synced",
                            value = state.lastSyncTime,
                            icon = Icons.Default.History
                        )
                        PremiumInfoPreference(
                            title = "Total Synced",
                            value = "${state.totalSyncedCount} items",
                            icon = Icons.Default.DoneAll
                        )
                    }
                }

                item {
                    SettingsSection(title = "Debug & Stats") {
                        PremiumInfoPreference(
                            title = "Total Media",
                            value = "${state.totalMediaCount} files",
                            icon = Icons.Default.Collections
                        )
                        PremiumInfoPreference(
                            title = "Database Size",
                            value = state.databaseSize,
                            icon = Icons.Default.Storage
                        )
                        PremiumSwitchPreference(
                            title = "Debug Logs",
                            subtitle = "Enable detailed logging for troubleshooting",
                            icon = Icons.Default.BugReport,
                            isChecked = state.debugEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleDebugInfo(it)) }
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_app)) {
                        PremiumInfoPreference(
                            title = "Version",
                            value = state.appVersion,
                            icon = Icons.Default.Info
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun PremiumSwitchPreference(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (isChecked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null
        )
    }
}

@Composable
fun PremiumInfoPreference(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PremiumActionPreference(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
