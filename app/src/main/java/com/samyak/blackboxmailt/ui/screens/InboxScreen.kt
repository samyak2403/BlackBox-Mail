package com.samyak.blackboxmailt.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samyak.blackboxmailt.data.MailFolder
import com.samyak.blackboxmailt.data.MailSettings
import com.samyak.blackboxmailt.data.SwipeAction
import com.samyak.blackboxmailt.ui.MailUiState
import com.samyak.blackboxmailt.ui.components.MailEmptyState
import com.samyak.blackboxmailt.ui.components.MailListRow
import com.samyak.blackboxmailt.ui.components.MailSearchBar
import com.samyak.blackboxmailt.ui.components.SwipeBackground

/** What a swipe or toolbar button ends up doing, once the folder is taken into account. */
private enum class RowAction { NONE, ARCHIVE, TRASH, MARK_READ, STAR, RESTORE, DELETE_FOREVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: MailUiState,
    settings: MailSettings,
    snackbarHostState: SnackbarHostState,
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onToggleStar: (String) -> Unit,
    onArchive: (Set<String>) -> Unit,
    onUnarchive: (Set<String>) -> Unit,
    onTrash: (Set<String>) -> Unit,
    onRestore: (Set<String>) -> Unit,
    onDeleteForever: (Set<String>) -> Unit,
    onMarkSeen: (Set<String>, Boolean) -> Unit,
    onCopyAddress: () -> Unit,
    onNewAddress: () -> Unit
) {
    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48
        }
    }
    val messages = state.visibleMessages

    // Pending delete for the "Confirm before deleting" setting.
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    fun requestDelete(ids: Set<String>, permanent: Boolean) {
        if (ids.isEmpty()) return
        if (settings.confirmBeforeDelete) {
            pendingDelete = PendingDelete(ids, permanent)
        } else if (permanent) {
            onDeleteForever(ids)
        } else {
            onTrash(ids)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    when {
                        state.inSelectionMode -> SelectionTopBar(
                            count = state.selected.size,
                            folder = state.folder,
                            anyUnread = messages.any { it.id in state.selected && !it.seen },
                            onClose = onClearSelection,
                            onArchive = { onArchive(state.selected) },
                            onUnarchive = { onUnarchive(state.selected) },
                            onTrash = { requestDelete(state.selected, permanent = false) },
                            onRestore = { onRestore(state.selected) },
                            onDeleteForever = { requestDelete(state.selected, permanent = true) },
                            onToggleRead = { unread -> onMarkSeen(state.selected, !unread) }
                        )

                        state.searchActive -> SearchTopBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClose = { onSearchActiveChange(false) }
                        )

                        else -> MailSearchBar(
                            query = "",
                            placeholder = "Search in ${state.folder.label.lowercase()}",
                            accountInitial = state.account?.address?.take(1)?.uppercase() ?: "?",
                            accountSeed = state.account?.address ?: "",
                            onMenuClick = onMenuClick,
                            onSearchClick = { onSearchActiveChange(true) },
                            onAvatarClick = onAvatarClick
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode && !state.searchActive) {
                ExtendedFloatingActionButton(
                    onClick = onNewAddress,
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        if (state.creatingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Filled.Edit, contentDescription = "Create a new address")
                        }
                    },
                    text = { Text("New address") }
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.initializing -> LoadingState()

                messages.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    if (state.folder == MailFolder.INBOX && !state.searchActive) {
                        AddressCard(
                            address = state.account?.address ?: "",
                            onCopy = onCopyAddress,
                            onRefresh = onRefresh
                        )
                    }
                    MailEmptyState(
                        icon = when {
                            state.query.isNotBlank() -> Icons.Filled.Inbox
                            state.folder == MailFolder.TRASH -> Icons.Filled.Delete
                            state.folder == MailFolder.STARRED -> Icons.Filled.Star
                            else -> Icons.Filled.Drafts
                        },
                        title = when {
                            state.query.isNotBlank() -> "No results"
                            state.folder == MailFolder.TRASH -> "Trash is empty"
                            state.folder == MailFolder.STARRED -> "No starred mail"
                            else -> "Waiting for mail"
                        },
                        subtitle = when {
                            state.query.isNotBlank() -> "Try a different search term."
                            state.folder == MailFolder.TRASH -> "Deleted mail shows up here."
                            state.folder == MailFolder.STARRED -> "Star a message to find it fast."
                            settings.refreshInterval.seconds <= 0 ->
                                "Auto refresh is off. Pull down to check for mail."
                            else -> "Messages arrive within seconds of being sent."
                        }
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp)
                ) {
                    if (state.folder == MailFolder.INBOX && !state.searchActive) {
                        item(key = "address-card") {
                            AddressCard(
                                address = state.account?.address ?: "",
                                onCopy = onCopyAddress,
                                onRefresh = onRefresh
                            )
                        }
                    }
                    items(items = messages, key = { it.id }) { message ->
                        val rightAction = resolveAction(settings.swipeRight, state.folder, false)
                        val leftAction = resolveAction(settings.swipeLeft, state.folder, true)
                        val starred = message.id in state.starred

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                val ids = setOf(message.id)
                                val action = when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> rightAction
                                    SwipeToDismissBoxValue.EndToStart -> leftAction
                                    SwipeToDismissBoxValue.Settled -> RowAction.NONE
                                }
                                when (action) {
                                    RowAction.NONE -> false

                                    RowAction.ARCHIVE -> {
                                        onArchive(ids)
                                        state.folder == MailFolder.INBOX
                                    }

                                    RowAction.TRASH -> {
                                        // A confirmation dialog needs the row to stay put.
                                        if (settings.confirmBeforeDelete) {
                                            pendingDelete = PendingDelete(ids, false)
                                            false
                                        } else {
                                            onTrash(ids)
                                            true
                                        }
                                    }

                                    RowAction.DELETE_FOREVER -> {
                                        if (settings.confirmBeforeDelete) {
                                            pendingDelete = PendingDelete(ids, true)
                                            false
                                        } else {
                                            onDeleteForever(ids)
                                            true
                                        }
                                    }

                                    RowAction.RESTORE -> {
                                        onRestore(ids)
                                        true
                                    }

                                    RowAction.MARK_READ -> {
                                        onMarkSeen(ids, !message.seen)
                                        false
                                    }

                                    RowAction.STAR -> {
                                        onToggleStar(message.id)
                                        false
                                    }
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !state.inSelectionMode &&
                                rightAction != RowAction.NONE,
                            enableDismissFromEndToStart = !state.inSelectionMode &&
                                leftAction != RowAction.NONE,
                            backgroundContent = {
                                val towardsStart =
                                    dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val action = if (towardsStart) leftAction else rightAction
                                if (action != RowAction.NONE) {
                                    val visual = swipeVisual(action, message.seen, starred)
                                    SwipeBackground(
                                        label = visual.label,
                                        icon = visual.icon,
                                        container = visual.container,
                                        content = visual.content,
                                        towardsStart = towardsStart,
                                        progress = dismissState.progress
                                    )
                                }
                            }
                        ) {
                            MailListRow(
                                message = message,
                                starred = starred,
                                selected = message.id in state.selected,
                                showPreview = settings.showPreviewText,
                                showLogo = settings.showSenderLogos,
                                onClick = {
                                    if (state.inSelectionMode) {
                                        onToggleSelection(message.id)
                                    } else {
                                        onOpenMessage(message.id)
                                    }
                                },
                                onLongClick = { onToggleSelection(message.id) },
                                onAvatarClick = { onToggleSelection(message.id) },
                                onStarClick = { onToggleStar(message.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    if (pending.permanent) "Delete forever?"
                    else if (pending.ids.size == 1) "Move to Trash?"
                    else "Move ${pending.ids.size} to Trash?"
                )
            },
            text = {
                Text(
                    if (pending.permanent) {
                        "This removes the message from mail.tm and cannot be undone."
                    } else {
                        "You can restore it from Trash later."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pending.permanent) onDeleteForever(pending.ids) else onTrash(pending.ids)
                    pendingDelete = null
                }) { Text(if (pending.permanent) "Delete" else "Move") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private data class PendingDelete(val ids: Set<String>, val permanent: Boolean)

private data class SwipeVisual(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

/** Trash overrides the configured swipes with restore / delete forever. */
private fun resolveAction(
    configured: SwipeAction,
    folder: MailFolder,
    towardsStart: Boolean
): RowAction = when (folder) {
    MailFolder.TRASH -> if (towardsStart) RowAction.DELETE_FOREVER else RowAction.RESTORE
    else -> when (configured) {
        SwipeAction.NONE -> RowAction.NONE
        SwipeAction.ARCHIVE -> RowAction.ARCHIVE
        SwipeAction.DELETE -> RowAction.TRASH
        SwipeAction.MARK_READ -> RowAction.MARK_READ
        SwipeAction.STAR -> RowAction.STAR
    }
}

@Composable
private fun swipeVisual(action: RowAction, seen: Boolean, starred: Boolean): SwipeVisual {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        RowAction.ARCHIVE -> SwipeVisual(
            "Archive", Icons.Filled.Archive, scheme.primaryContainer, scheme.onPrimaryContainer
        )

        RowAction.TRASH -> SwipeVisual(
            "Delete", Icons.Filled.Delete, scheme.errorContainer, scheme.onErrorContainer
        )

        RowAction.DELETE_FOREVER -> SwipeVisual(
            "Delete forever", Icons.Filled.DeleteForever, scheme.errorContainer,
            scheme.onErrorContainer
        )

        RowAction.RESTORE -> SwipeVisual(
            "Restore", Icons.Filled.Unarchive, scheme.primaryContainer, scheme.onPrimaryContainer
        )

        RowAction.MARK_READ -> SwipeVisual(
            if (seen) "Mark unread" else "Mark read",
            if (seen) Icons.Filled.MarkEmailUnread else Icons.Filled.Drafts,
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer
        )

        RowAction.STAR -> SwipeVisual(
            if (starred) "Remove star" else "Star",
            if (starred) Icons.Filled.StarBorder else Icons.Filled.Star,
            scheme.secondaryContainer,
            scheme.onSecondaryContainer
        )

        RowAction.NONE -> SwipeVisual(
            "", Icons.Filled.Archive, Color.Transparent, Color.Transparent
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Setting up your temporary mailbox",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The card at the top of the inbox showing the disposable address, with a one-tap copy. */
@Composable
private fun AddressCard(
    address: String,
    onCopy: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your address",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = address.ifBlank { "Creating..." },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy address",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Check for new mail",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Contextual action bar shown while rows are selected, mirroring Gmail's behaviour. */
@Composable
private fun SelectionTopBar(
    count: Int,
    folder: MailFolder,
    anyUnread: Boolean,
    onClose: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onToggleRead: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear selection",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.width(32.dp)
            )
            Spacer(Modifier.weight(1f))
            if (folder == MailFolder.TRASH) {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Filled.Unarchive,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = "Delete forever",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                IconButton(onClick = if (folder == MailFolder.ALL) onUnarchive else onArchive) {
                    Icon(
                        if (folder == MailFolder.ALL) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = if (folder == MailFolder.ALL) "Move to inbox" else "Archive",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = onTrash) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = { onToggleRead(anyUnread) }) {
                    Icon(
                        if (anyUnread) Icons.Filled.Drafts else Icons.Filled.MarkEmailUnread,
                        contentDescription = if (anyUnread) "Mark as read" else "Mark as unread",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Full-width search field, styled like Gmail's expanded search. */
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search in mail",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
