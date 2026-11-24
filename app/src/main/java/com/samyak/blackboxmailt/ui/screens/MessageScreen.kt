package com.samyak.blackboxmailt.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.samyak.blackboxmailt.data.MailAttachment
import com.samyak.blackboxmailt.data.MessageDetail
import com.samyak.blackboxmailt.ui.buildMessageDocument
import com.samyak.blackboxmailt.ui.components.SenderAvatar
import com.samyak.blackboxmailt.ui.formatFullDate
import com.samyak.blackboxmailt.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    detail: MessageDetail?,
    loading: Boolean,
    starred: Boolean,
    blockRemoteImages: Boolean,
    showSenderLogos: Boolean,
    recipientAddress: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMarkUnread: () -> Unit,
    onAttachmentClick: (MailAttachment) -> Unit,
    onReplyUnsupported: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archive")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = onMarkUnread) {
                        Icon(Icons.Filled.MarkEmailUnread, contentDescription = "Mark as unread")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading || detail == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    MessageHeader(
                        detail = detail,
                        starred = starred,
                        recipientAddress = recipientAddress,
                        showSenderLogo = showSenderLogos,
                        onToggleStar = onToggleStar
                    )
                    if (detail.attachments.isNotEmpty()) {
                        AttachmentRow(detail.attachments, onAttachmentClick)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MessageBody(
                        detail = detail,
                        blockRemoteImages = blockRemoteImages,
                        modifier = Modifier.weight(1f)
                    )
                    ReplyActions(onReplyUnsupported)
                }
            }
        }
    }
}

@Composable
private fun MessageHeader(
    detail: MessageDetail,
    starred: Boolean,
    recipientAddress: String,
    showSenderLogo: Boolean,
    onToggleStar: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = detail.displaySubject,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (starred) "Remove star" else "Add star",
                    tint = if (starred) Color(0xFFF4B400) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SenderAvatar(
                sender = detail.from,
                selected = false,
                size = 40,
                showLogo = showSenderLogo
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = detail.from.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatFullDate(detail.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = detail.from.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "to ${detail.to.firstOrNull()?.address ?: recipientAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AttachmentRow(
    attachments: List<MailAttachment>,
    onAttachmentClick: (MailAttachment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AssistChip(
                onClick = { onAttachmentClick(attachment) },
                label = {
                    Column {
                        Text(
                            text = attachment.filename,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatSize(attachment.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

@Composable
private fun MessageBody(
    detail: MessageDetail,
    blockRemoteImages: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val context = LocalContext.current
    val html = detail.htmlBody
    val document = remember(detail.id, dark) {
        buildMessageDocument(
            body = html ?: detail.text.ifBlank { "(This message has no content.)" },
            isHtml = html != null,
            dark = dark
        )
    }
    val hasRemoteImages = remember(detail.id) {
        html?.contains("<img", ignoreCase = true) == true
    }
    var showImages by remember(detail.id) { mutableStateOf(!blockRemoteImages) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!showImages && hasRemoteImages) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Images are blocked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showImages = true }) { Text("Show") }
                }
            }
        }
        MessageWebView(
            document = document,
            loadImages = showImages,
            context = context,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MessageWebView(
    document: String,
    loadImages: Boolean,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // JavaScript stays off: message bodies are untrusted content.
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                isVerticalScrollBarEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url ?: return false
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            )
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.settings.blockNetworkImage = !loadImages
            webView.settings.loadsImagesAutomatically = loadImages
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
            }
        }
    )
}

/** mail.tm is receive-only, so the reply row explains itself instead of opening a composer. */
@Composable
private fun ReplyActions(onReplyUnsupported: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Reply", Icons.AutoMirrored.Filled.Reply, 0),
                Triple("Reply all", Icons.AutoMirrored.Filled.ReplyAll, 1),
                Triple("Forward", Icons.AutoMirrored.Filled.Forward, 2)
            ).forEach { (label, icon, _) ->
                AssistChip(
                    onClick = onReplyUnsupported,
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        }
    }
}

/** Opens a downloaded attachment with whatever app can handle it. */
fun openAttachment(context: android.content.Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
