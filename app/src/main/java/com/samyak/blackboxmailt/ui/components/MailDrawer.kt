package com.samyak.blackboxmailt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samyak.blackboxmailt.data.MailFolder
import com.samyak.blackboxmailt.data.StoredAccount
import com.samyak.blackboxmailt.ui.MailUiState

@Composable
fun MailDrawerSheet(
    state: MailUiState,
    onFolderSelected: (MailFolder) -> Unit,
    onCopyAddress: () -> Unit,
    onNewAddress: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteMailbox: () -> Unit,
    onEmptyTrash: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMailTm: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BlackBox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mail",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(4.dp))

            // ------------------------------------------------------ active address
            state.account?.let { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your temporary address",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = account.address,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onCopyAddress) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // ------------------------------------------------------------- folders
            MailFolder.entries.forEach { folder ->
                val unread = state.unreadCount(folder)
                val total = state.folderCount(folder)
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = folder.label,
                            fontWeight = if (state.folder == folder) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = when (folder) {
                                MailFolder.INBOX -> Icons.Filled.Inbox
                                MailFolder.STARRED -> Icons.Filled.Star
                                MailFolder.ALL -> Icons.Filled.AllInbox
                                MailFolder.TRASH -> Icons.Filled.Delete
                            },
                            contentDescription = null
                        )
                    },
                    badge = {
                        val label = when {
                            unread > 0 -> unread.toString()
                            total > 0 -> total.toString()
                            else -> null
                        }
                        if (label != null) {
                            if (unread > 0) Badge { Text(label) } else Text(label)
                        }
                    },
                    selected = state.folder == folder,
                    onClick = { onFolderSelected(folder) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SectionLabel("Mailboxes")
            state.savedAccounts.forEach { saved: StoredAccount ->
                val isActive = saved.address == state.account?.address
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = saved.address,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        SenderAvatar(
                            sender = com.samyak.blackboxmailt.data.MailAddress("", saved.address),
                            selected = false,
                            size = 28,
                            showLogo = false
                        )
                    },
                    selected = isActive,
                    onClick = { onSwitchAccount(saved.address) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            NavigationDrawerItem(
                label = { Text("New address") },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                selected = false,
                onClick = onNewAddress,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SectionLabel("Actions")
            NavigationDrawerItem(
                label = { Text("Mark all as read") },
                icon = { Icon(Icons.Filled.MarkEmailRead, contentDescription = null) },
                selected = false,
                onClick = onMarkAllRead,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Empty trash") },
                icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                selected = false,
                onClick = onEmptyTrash,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Delete this mailbox") },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                selected = false,
                onClick = onDeleteMailbox,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            Spacer(Modifier.height(12.dp))
            // mail.tm's terms of use ask for a visible link back to the service.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenMailTm) {
                    Text("Powered by mail.tm")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
    )
}
