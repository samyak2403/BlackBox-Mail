package com.samyak.blackboxmailt.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samyak.blackboxmailt.data.MailAddress
import com.samyak.blackboxmailt.data.MessageSummary
import com.samyak.blackboxmailt.data.SenderLogos
import com.samyak.blackboxmailt.ui.formatListDate
import com.samyak.blackboxmailt.ui.theme.avatarColorFor

/**
 * Looks up the sender's brand icon. Returns null while loading, or when the domain has no icon,
 * which is the signal to fall back to the coloured initial.
 */
@Composable
fun rememberSenderLogo(address: String, enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    var logo by remember(address, enabled) {
        mutableStateOf(
            if (enabled && address.isNotBlank()) {
                SenderLogos.cached(address)?.asImageBitmap()
            } else {
                null
            }
        )
    }
    LaunchedEffect(address, enabled) {
        if (!enabled || address.isBlank() || logo != null) return@LaunchedEffect
        logo = SenderLogos.load(context, address)?.asImageBitmap()
    }
    return logo
}

/**
 * The sender circle. Shows the brand logo when one exists, otherwise Gmail's coloured initial,
 * and a check mark while the row is selected.
 */
@Composable
fun SenderAvatar(
    sender: MailAddress,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 40,
    showLogo: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val logo = rememberSenderLogo(sender.address, showLogo && !selected)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        logo != null -> LogoBackdrop
        else -> avatarColorFor(sender.address)
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(background, CircleShape)
            .then(if (onClick != null) Modifier.combinedClickableCircle(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size((size * 0.55f).dp)
            )
        }
        AnimatedVisibility(
            visible = !selected && logo != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            logo?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .size(size.dp)
                        .clip(CircleShape)
                        .padding((size * 0.15f).dp)
                )
            }
        }
        AnimatedVisibility(
            visible = !selected && logo == null,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Text(
                text = sender.initial,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Favicons are usually drawn for light backgrounds, so they sit on a white circle. */
private val LogoBackdrop = Color(0xFFFFFFFF)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCircle(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick))

/** One inbox row: avatar, sender, subject, snippet, date and star - the Gmail layout. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailListRow(
    message: MessageSummary,
    starred: Boolean,
    selected: Boolean,
    showPreview: Boolean,
    showLogo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onStarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unread = !message.seen
    val colors = MaterialTheme.colorScheme
    val rowBackground = when {
        selected -> colors.primaryContainer
        else -> colors.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(rowBackground, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SenderAvatar(
                sender = message.from,
                selected = selected,
                showLogo = showLogo,
                onClick = onAvatarClick,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.from.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message.displaySubject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showPreview) {
                    Text(
                        text = message.intro.ifBlank { "(no preview)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp)
            ) {
                Text(
                    text = formatListDate(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.hasAttachments) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Has attachments",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onStarClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (starred) "Remove star" else "Add star",
                            tint = if (starred) Color(0xFFF4B400) else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Coloured background revealed while swiping a row, same as Gmail's archive/delete affordance.
 * The icon and label follow whatever action the user bound to that direction in Settings.
 */
@Composable
fun SwipeBackground(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
    towardsStart: Boolean,
    progress: Float
) {
    val scale by animateFloatAsState(if (progress > 0.25f) 1f else 0.75f, label = "swipeIcon")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(container, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = if (towardsStart) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size((24 * scale).dp)
                    .alpha(if (progress > 0.05f) 1f else 0f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Gmail's pill shaped search field that doubles as the top app bar. */
@Composable
fun MailSearchBar(
    query: String,
    placeholder: String,
    accountInitial: String,
    accountSeed: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open navigation drawer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickableCircle(onSearchClick),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = query.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search mail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier.padding(end = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                SenderAvatar(
                    sender = MailAddress(accountInitial, accountSeed),
                    selected = false,
                    size = 32,
                    // Our own throwaway domain has no brand icon; keep the initial.
                    showLogo = false,
                    onClick = onAvatarClick
                )
            }
        }
    }
}

/** Centred illustration + copy used for empty folders and loading states. */
@Composable
fun MailEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
