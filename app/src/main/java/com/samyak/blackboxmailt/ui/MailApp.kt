package com.samyak.blackboxmailt.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samyak.blackboxmailt.notifications.MailNotifier
import com.samyak.blackboxmailt.BuildConfig
import com.samyak.blackboxmailt.data.MailAttachment
import com.samyak.blackboxmailt.data.MailFolder
import com.samyak.blackboxmailt.ui.components.MailDrawerSheet
import com.samyak.blackboxmailt.ui.components.MailEmptyState
import com.samyak.blackboxmailt.ui.components.SenderAvatar
import com.samyak.blackboxmailt.ui.screens.InboxScreen
import com.samyak.blackboxmailt.ui.screens.MessageScreen
import com.samyak.blackboxmailt.ui.screens.SettingsScreen
import com.samyak.blackboxmailt.ui.screens.openAttachment
import kotlinx.coroutines.launch
import java.io.File

private enum class MailRoute { INBOX, MESSAGE, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailApp(
    openMessageId: String? = null,
    onOpenMessageHandled: () -> Unit = {},
    viewModel: MailViewModel = viewModel(factory = MailViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val settings by viewModel.settings.collectAsState()

    var showAccountSheet by remember { mutableStateOf(false) }
    var showCustomAddressDialog by remember { mutableStateOf(false) }
    var showDeleteMailboxDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var passwordRevealed by remember { mutableStateOf(false) }
    var pendingMessageDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val copyAddress: () -> Unit = {
        val address = state.account?.address
        if (address.isNullOrBlank()) {
            viewModel.notify("No address yet")
        } else {
            copyToClipboard(context, address, "Temporary email address")
            viewModel.notify("Address copied")
        }
    }

    val copyPassword: () -> Unit = {
        val password = state.password
        if (password.isNullOrBlank()) {
            viewModel.notify("No password stored for this mailbox")
        } else {
            copyToClipboard(context, password, "Mailbox password", sensitive = true)
            viewModel.notify("Password copied")
        }
    }

    var notificationsPermitted by remember { mutableStateOf(MailNotifier.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsPermitted = granted
        viewModel.onNotificationPermissionResult(granted)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsPermitted = true
            viewModel.onNotificationPermissionResult(true)
        }
    }

    // Ask once on first launch, since new mail alerts are on by default.
    LaunchedEffect(Unit) {
        if (!notificationsPermitted && settings.newMailNotifications) {
            requestNotificationPermission()
        }
    }

    // Suppress alerts while the inbox is on screen; resume them once it is not.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setAppInForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.setAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Opening the app from a notification jumps straight to that message.
    LaunchedEffect(openMessageId, state.initializing) {
        val id = openMessageId
        if (!id.isNullOrBlank() && !state.initializing) {
            viewModel.dismissNotification(id)
            viewModel.openMessage(id)
            onOpenMessageHandled()
        }
    }

    // Never leave the password on screen once the sheet is dismissed.
    LaunchedEffect(showAccountSheet) {
        if (!showAccountSheet) passwordRevealed = false
    }

    // "Copy new addresses automatically" from Settings.
    LaunchedEffect(state.justCreatedAddress) {
        val created = state.justCreatedAddress ?: return@LaunchedEffect
        if (settings.copyNewAddress) {
            copyToClipboard(context, created, "Temporary email address")
            viewModel.notify("New address copied")
        }
        viewModel.consumeCreatedAddress()
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice
        if (!notice.isNullOrBlank()) {
            snackbarHostState.showSnackbar(notice)
            viewModel.consumeNotice()
        }
    }

    // Back navigation priority: drawer, settings, selection, search, open message.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && showSettings) { showSettings = false }
    BackHandler(
        enabled = !drawerState.isOpen && !showSettings && state.inSelectionMode
    ) { viewModel.clearSelection() }
    BackHandler(
        enabled = !drawerState.isOpen && !showSettings && !state.inSelectionMode &&
            state.searchActive
    ) { viewModel.setSearchActive(false) }
    BackHandler(
        enabled = !drawerState.isOpen && !showSettings && !state.inSelectionMode &&
            !state.searchActive && state.openId != null
    ) { viewModel.closeMessage() }

    if (state.fatalError != null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MailEmptyState(
                icon = Icons.Filled.CloudOff,
                title = "Could not reach mail.tm",
                subtitle = state.fatalError ?: "",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = viewModel::retryBootstrap,
                modifier = Modifier.padding(bottom = 48.dp)
            ) { Text("Try again") }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = state.openId == null && !state.inSelectionMode && !showSettings,
        drawerContent = {
            MailDrawerSheet(
                state = state,
                onFolderSelected = {
                    viewModel.setFolder(it)
                    viewModel.closeMessage()
                    scope.launch { drawerState.close() }
                },
                onCopyAddress = copyAddress,
                onNewAddress = {
                    scope.launch { drawerState.close() }
                    viewModel.createNewAddress()
                },
                onSwitchAccount = {
                    scope.launch { drawerState.close() }
                    viewModel.switchAccount(it)
                },
                onDeleteMailbox = {
                    scope.launch { drawerState.close() }
                    showDeleteMailboxDialog = true
                },
                onEmptyTrash = {
                    scope.launch { drawerState.close() }
                    viewModel.emptyTrash()
                },
                onMarkAllRead = {
                    scope.launch { drawerState.close() }
                    viewModel.markAllRead()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    showSettings = true
                },
                onOpenMailTm = { openUrl(context, "https://mail.tm") }
            )
        }
    ) {
        val route = when {
            showSettings -> MailRoute.SETTINGS
            state.openId != null -> MailRoute.MESSAGE
            else -> MailRoute.INBOX
        }
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState == MailRoute.INBOX) {
                    (fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (fadeOut(tween(160)))
                }
            },
            label = "mailNavigation"
        ) { current ->
            when (current) {
                MailRoute.SETTINGS -> SettingsScreen(
                    settings = settings,
                    address = state.account?.address ?: "",
                    password = state.password,
                    mailboxCount = state.savedAccounts.size,
                    appVersion = BuildConfig.VERSION_NAME,
                    notificationsPermitted = notificationsPermitted,
                    snackbarHostState = snackbarHostState,
                    onBack = { showSettings = false },
                    onThemeMode = viewModel::setThemeMode,
                    onRefreshInterval = viewModel::setRefreshInterval,
                    onBlockRemoteImages = viewModel::setBlockRemoteImages,
                    onShowSenderLogos = viewModel::setShowSenderLogos,
                    onShowPreviewText = viewModel::setShowPreviewText,
                    onSwipeRight = viewModel::setSwipeRight,
                    onSwipeLeft = viewModel::setSwipeLeft,
                    onConfirmBeforeDelete = viewModel::setConfirmBeforeDelete,
                    onCopyNewAddress = viewModel::setCopyNewAddress,
                    onNewMailNotifications = { wanted ->
                        if (wanted && !notificationsPermitted) {
                            requestNotificationPermission()
                        } else {
                            viewModel.setNewMailNotifications(wanted)
                        }
                    },
                    onBackgroundChecks = viewModel::setBackgroundChecks,
                    onOpenSystemNotificationSettings = { openNotificationSettings(context) },
                    onCopyAddress = copyAddress,
                    onCopyPassword = copyPassword,
                    onNewAddress = viewModel::createNewAddress,
                    onDeleteMailbox = { showDeleteMailboxDialog = true },
                    onResetSettings = viewModel::resetSettings,
                    onOpenUrl = { openUrl(context, it) }
                )

                MailRoute.MESSAGE -> {
                    val openId = state.openId
                    val inTrash = state.folder == MailFolder.TRASH
                    MessageScreen(
                        detail = state.openMessage,
                        loading = state.openLoading,
                        starred = openId != null && openId in state.starred,
                        blockRemoteImages = settings.blockRemoteImages,
                        showSenderLogos = settings.showSenderLogos,
                        recipientAddress = state.account?.address ?: "",
                        snackbarHostState = snackbarHostState,
                        onBack = viewModel::closeMessage,
                        onToggleStar = { openId?.let(viewModel::toggleStar) },
                        onArchive = {
                            openId?.let { viewModel.archive(setOf(it)) }
                            viewModel.closeMessage()
                        },
                        onDelete = {
                            openId?.let { id ->
                                if (settings.confirmBeforeDelete) {
                                    pendingMessageDelete = id to inTrash
                                } else {
                                    if (inTrash) {
                                        viewModel.deleteForever(setOf(id))
                                    } else {
                                        viewModel.moveToTrash(setOf(id))
                                    }
                                    viewModel.closeMessage()
                                }
                            }
                        },
                        onMarkUnread = {
                            openId?.let { viewModel.markSeen(setOf(it), seen = false) }
                            viewModel.closeMessage()
                        },
                        onAttachmentClick = { attachment ->
                            scope.launch { saveAndOpen(context, viewModel, attachment) }
                        },
                        onReplyUnsupported = {
                            viewModel.notify("mail.tm mailboxes can receive mail only")
                        }
                    )
                }

                MailRoute.INBOX -> InboxScreen(
                    state = state,
                    settings = settings,
                    snackbarHostState = snackbarHostState,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAvatarClick = { showAccountSheet = true },
                    onSearchActiveChange = viewModel::setSearchActive,
                    onQueryChange = viewModel::setQuery,
                    onRefresh = viewModel::refresh,
                    onOpenMessage = viewModel::openMessage,
                    onToggleSelection = viewModel::toggleSelection,
                    onClearSelection = viewModel::clearSelection,
                    onToggleStar = viewModel::toggleStar,
                    onArchive = viewModel::archive,
                    onUnarchive = viewModel::unarchive,
                    onTrash = viewModel::moveToTrash,
                    onRestore = viewModel::restoreFromTrash,
                    onDeleteForever = viewModel::deleteForever,
                    onMarkSeen = viewModel::markSeen,
                    onCopyAddress = copyAddress,
                    onNewAddress = { showAccountSheet = true }
                )
            }
        }
    }

    pendingMessageDelete?.let { (id, permanent) ->
        AlertDialog(
            onDismissRequest = { pendingMessageDelete = null },
            title = { Text(if (permanent) "Delete forever?" else "Move to Trash?") },
            text = {
                Text(
                    if (permanent) {
                        "This removes the message from mail.tm and cannot be undone."
                    } else {
                        "You can restore it from Trash later."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (permanent) {
                        viewModel.deleteForever(setOf(id))
                    } else {
                        viewModel.moveToTrash(setOf(id))
                    }
                    pendingMessageDelete = null
                    viewModel.closeMessage()
                }) { Text(if (permanent) "Delete" else "Move") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMessageDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            AccountSheetContent(
                state = state,
                passwordRevealed = passwordRevealed,
                onTogglePasswordReveal = { passwordRevealed = !passwordRevealed },
                onCopyPassword = copyPassword,
                onCopy = {
                    copyAddress()
                    showAccountSheet = false
                },
                onRandomAddress = {
                    showAccountSheet = false
                    viewModel.createNewAddress()
                },
                onCustomAddress = {
                    showAccountSheet = false
                    showCustomAddressDialog = true
                },
                onSettings = {
                    showAccountSheet = false
                    showSettings = true
                },
                onSwitch = {
                    showAccountSheet = false
                    viewModel.switchAccount(it)
                },
                onDelete = {
                    showAccountSheet = false
                    showDeleteMailboxDialog = true
                }
            )
        }
    }

    if (showCustomAddressDialog) {
        CustomAddressDialog(
            domains = state.domains,
            onDismiss = { showCustomAddressDialog = false },
            onConfirm = { localPart, domain ->
                showCustomAddressDialog = false
                viewModel.createNewAddress(localPart, domain)
            }
        )
    }

    if (showDeleteMailboxDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMailboxDialog = false },
            title = { Text("Delete this mailbox?") },
            text = {
                Text(
                    "${state.account?.address ?: "This address"} and every message in it will be " +
                        "removed from mail.tm permanently. A fresh address will be created for you."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteMailboxDialog = false
                    viewModel.deleteCurrentMailbox()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMailboxDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AccountSheetContent(
    state: MailUiState,
    passwordRevealed: Boolean,
    onTogglePasswordReveal: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopy: () -> Unit,
    onRandomAddress: () -> Unit,
    onCustomAddress: () -> Unit,
    onSettings: () -> Unit,
    onSwitch: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SenderAvatar(
                sender = com.samyak.blackboxmailt.data.MailAddress(
                    "",
                    state.account?.address ?: ""
                ),
                selected = false,
                size = 44,
                showLogo = false
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.account?.address ?: "No address",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Temporary mailbox on mail.tm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CredentialsRow(
            password = state.password,
            revealed = passwordRevealed,
            onToggleReveal = onTogglePasswordReveal,
            onCopy = onCopyPassword
        )

        SheetAction(Icons.Filled.ContentCopy, "Copy address", onCopy)
        SheetAction(Icons.Filled.Autorenew, "Create random address", onRandomAddress)
        SheetAction(Icons.Filled.Edit, "Create custom address", onCustomAddress)
        SheetAction(Icons.Filled.Settings, "Settings", onSettings)
        SheetAction(Icons.Filled.Delete, "Delete this mailbox", onDelete)

        if (state.savedAccounts.size > 1) {
            Text(
                text = "Switch mailbox",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.savedAccounts
                    .filter { it.address != state.account?.address }
                    .forEach { saved ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SenderAvatar(
                                sender = com.samyak.blackboxmailt.data.MailAddress(
                                    "",
                                    saved.address
                                ),
                                selected = false,
                                size = 32,
                                showLogo = false,
                                onClick = { onSwitch(saved.address) }
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = saved.address,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onSwitch(saved.address) }) { Text("Use") }
                        }
                    }
            }
        }
    }
}

/**
 * mail.tm mailboxes are plain accounts, so the generated password is what lets the user sign in
 * on mail.tm itself or restore the same inbox on another device. It stays masked until asked for.
 */
@Composable
private fun CredentialsRow(
    password: String?,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Mailbox password",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        password.isNullOrBlank() -> "Not available"
                        revealed -> password
                        else -> "•".repeat(password.length.coerceAtMost(18))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleReveal, enabled = !password.isNullOrBlank()) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCopy, enabled = !password.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy password",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CustomAddressDialog(
    domains: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var localPart by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(domains.firstOrNull() ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val valid = localPart.length >= 3 && localPart.all { it.isLetterOrDigit() || it in "._-" } &&
        domain.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom address") },
        text = {
            Column {
                OutlinedTextField(
                    value = localPart,
                    onValueChange = { localPart = it.lowercase().filter { ch -> ch != ' ' } },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("At least 3 characters, letters and digits") }
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { expanded = true }) {
                    Text(if (domain.isBlank()) "Choose a domain" else "@$domain")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    domains.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("@$option") },
                            onClick = {
                                domain = option
                                expanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(localPart, domain) }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ platform glue

private fun copyToClipboard(
    context: Context,
    text: String,
    label: String,
    sensitive: Boolean = false
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Keeps the password out of the clipboard preview toast/overlay.
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard?.setPrimaryClip(clip)
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private suspend fun saveAndOpen(
    context: Context,
    viewModel: MailViewModel,
    attachment: MailAttachment
) {
    val bytes = viewModel.attachmentBytes(attachment)
    if (bytes == null) {
        viewModel.notify("Could not download ${attachment.filename}")
        return
    }
    val result = runCatching {
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val safeName = attachment.filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, safeName.ifBlank { "attachment" })
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    result.onSuccess { uri ->
        openAttachment(context, uri, attachment.contentType)
    }.onFailure {
        viewModel.notify("Could not open ${attachment.filename}")
    }
}
