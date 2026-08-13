package com.samyak.blackboxmailt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samyak.blackboxmailt.data.MailSettings
import com.samyak.blackboxmailt.data.RefreshInterval
import com.samyak.blackboxmailt.data.SwipeAction
import com.samyak.blackboxmailt.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: MailSettings,
    address: String,
    password: String?,
    mailboxCount: Int,
    appVersion: String,
    notificationsPermitted: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onRefreshInterval: (RefreshInterval) -> Unit,
    onBlockRemoteImages: (Boolean) -> Unit,
    onShowSenderLogos: (Boolean) -> Unit,
    onShowPreviewText: (Boolean) -> Unit,
    onSwipeRight: (SwipeAction) -> Unit,
    onSwipeLeft: (SwipeAction) -> Unit,
    onConfirmBeforeDelete: (Boolean) -> Unit,
    onCopyNewAddress: (Boolean) -> Unit,
    onNewMailNotifications: (Boolean) -> Unit,
    onBackgroundChecks: (Boolean) -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onCopyAddress: () -> Unit,
    onCopyPassword: () -> Unit,
    onNewAddress: () -> Unit,
    onDeleteMailbox: () -> Unit,
    onResetSettings: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var refreshDialog by remember { mutableStateOf(false) }
    var swipeRightDialog by remember { mutableStateOf(false) }
    var swipeLeftDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ------------------------------------------------------------ mailbox
            SectionHeader("Mailbox")
            SettingRow(
                title = "Email address",
                subtitle = address.ifBlank { "Creating..." },
                trailing = {
                    IconButton(onClick = onCopyAddress) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy address")
                    }
                },
                onClick = onCopyAddress
            )
            SettingRow(
                title = "Password",
                subtitleContent = {
                    Text(
                        text = when {
                            password.isNullOrBlank() -> "Not available"
                            passwordVisible -> password
                            else -> "•".repeat(password.length.coerceAtMost(18))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (passwordVisible) FontFamily.Monospace else FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailing = {
                    Row {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !password.isNullOrBlank()
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password"
                                else "Show password"
                            )
                        }
                        IconButton(onClick = onCopyPassword, enabled = !password.isNullOrBlank()) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                        }
                    }
                }
            )
            SwitchRow(
                title = "Copy new addresses automatically",
                subtitle = "Put a freshly created address on the clipboard",
                checked = settings.copyNewAddress,
                onCheckedChange = onCopyNewAddress
            )
            SettingRow(
                title = "Create a new address",
                subtitle = if (mailboxCount > 1) "$mailboxCount mailboxes saved on this device"
                else "The current one keeps working",
                onClick = onNewAddress
            )
            SettingRow(
                title = "Delete this mailbox",
                subtitle = "Removes the address and all its mail from mail.tm",
                titleColor = MaterialTheme.colorScheme.error,
                trailing = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = onDeleteMailbox
            )

            SectionDivider()

            // -------------------------------------------------------------- general
            SectionHeader("General")
            SettingRow(
                title = "Theme",
                subtitle = settings.themeMode.label,
                onClick = { themeDialog = true }
            )
            SettingRow(
                title = "Auto refresh",
                subtitle = settings.refreshInterval.label,
                trailing = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = { refreshDialog = true }
            )
            SwitchRow(
                title = "Preview text",
                subtitle = "Show a snippet of the message under the subject",
                checked = settings.showPreviewText,
                onCheckedChange = onShowPreviewText
            )
            SwitchRow(
                title = "Sender logos",
                subtitle = "Show brand icons instead of letters. Looks up the sender's domain " +
                    "with Google's public favicon service.",
                checked = settings.showSenderLogos,
                onCheckedChange = onShowSenderLogos
            )

            SectionDivider()

            // --------------------------------------------------------- swipe actions
            SectionHeader("Swipe actions")
            SettingRow(
                title = "Swipe right",
                subtitle = settings.swipeRight.label,
                trailing = { Icon(Icons.Filled.SwipeRight, contentDescription = null) },
                onClick = { swipeRightDialog = true }
            )
            SettingRow(
                title = "Swipe left",
                subtitle = settings.swipeLeft.label,
                trailing = { Icon(Icons.Filled.SwipeLeft, contentDescription = null) },
                onClick = { swipeLeftDialog = true }
            )
            SwitchRow(
                title = "Confirm before deleting",
                subtitle = "Ask before a message leaves for good",
                checked = settings.confirmBeforeDelete,
                onCheckedChange = onConfirmBeforeDelete
            )

            SectionDivider()

            // -------------------------------------------------------- notifications
            SectionHeader("Notifications")
            SwitchRow(
                title = "New mail notifications",
                subtitle = if (notificationsPermitted) {
                    "Alerts when a message arrives while the app is closed"
                } else {
                    "Permission denied. Tap to allow notifications."
                },
                checked = settings.newMailNotifications && notificationsPermitted,
                onCheckedChange = onNewMailNotifications
            )
            SwitchRow(
                title = "Background checks",
                subtitle = "Android limits background checks to once every 15 minutes",
                checked = settings.backgroundChecks && settings.newMailNotifications,
                enabled = settings.newMailNotifications && notificationsPermitted,
                onCheckedChange = onBackgroundChecks
            )
            SettingRow(
                title = "System notification settings",
                subtitle = "Sound, importance and badges",
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                onClick = onOpenSystemNotificationSettings
            )

            SectionDivider()

            // -------------------------------------------------------------- privacy
            SectionHeader("Privacy")
            SwitchRow(
                title = "Block remote images",
                subtitle = "Stops senders from seeing that you opened their mail",
                checked = settings.blockRemoteImages,
                onCheckedChange = onBlockRemoteImages
            )
            SettingRow(
                title = "JavaScript in messages",
                subtitle = "Always disabled - message bodies are untrusted content",
                enabled = false
            )

            SectionDivider()

            // ---------------------------------------------------------------- about
            SectionHeader("About")
            SettingRow(
                title = "Powered by mail.tm",
                subtitle = "Free temporary email API - tap to open mail.tm",
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                onClick = { onOpenUrl("https://mail.tm") }
            )
            SettingRow(
                title = "API documentation",
                subtitle = "docs.mail.tm",
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                onClick = { onOpenUrl("https://docs.mail.tm") }
            )
            SettingRow(title = "Version", subtitle = appVersion, enabled = false)
            SettingRow(
                title = "Reset settings",
                subtitle = "Restore every option on this screen to its default",
                trailing = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                onClick = { resetDialog = true }
            )
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.toList(),
            selected = settings.themeMode,
            labelOf = { it.label },
            onDismiss = { themeDialog = false },
            onSelect = {
                onThemeMode(it)
                themeDialog = false
            }
        )
    }
    if (refreshDialog) {
        ChoiceDialog(
            title = "Auto refresh",
            options = RefreshInterval.entries.toList(),
            selected = settings.refreshInterval,
            labelOf = { it.label },
            onDismiss = { refreshDialog = false },
            onSelect = {
                onRefreshInterval(it)
                refreshDialog = false
            }
        )
    }
    if (swipeRightDialog) {
        ChoiceDialog(
            title = "Swipe right",
            options = SwipeAction.entries.toList(),
            selected = settings.swipeRight,
            labelOf = { it.label },
            onDismiss = { swipeRightDialog = false },
            onSelect = {
                onSwipeRight(it)
                swipeRightDialog = false
            }
        )
    }
    if (swipeLeftDialog) {
        ChoiceDialog(
            title = "Swipe left",
            options = SwipeAction.entries.toList(),
            selected = settings.swipeLeft,
            labelOf = { it.label },
            onDismiss = { swipeLeftDialog = false },
            onSelect = {
                onSwipeLeft(it)
                swipeLeftDialog = false
            }
        )
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset settings?") },
            text = { Text("Your mailboxes stay untouched. Only the options on this screen change.") },
            confirmButton = {
                TextButton(onClick = {
                    resetDialog = false
                    onResetSettings()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                subtitleContent != null -> subtitleContent()
                subtitle != null -> Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(labelOf(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
