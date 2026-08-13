package com.samyak.blackboxmailt.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.samyak.blackboxmailt.MainActivity
import com.samyak.blackboxmailt.R
import com.samyak.blackboxmailt.data.MessageSummary

/** Posts "new mail" notifications, grouped under a summary the way Gmail does. */
object MailNotifier {

    const val EXTRA_MESSAGE_ID = "com.samyak.blackboxmailt.OPEN_MESSAGE_ID"

    private const val CHANNEL_ID = "new_mail"
    private const val GROUP_KEY = "com.samyak.blackboxmailt.NEW_MAIL"
    private const val SUMMARY_ID = 1
    private const val MAX_VISIBLE = 5

    /** Safe to call repeatedly; creating an existing channel is a no-op. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New mail",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when your temporary mailbox receives a message"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Shows one notification per message plus a group summary. Silently does nothing when the
     * user has not granted the notification permission.
     *
     * MissingPermission is suppressed deliberately: POST_NOTIFICATIONS is checked on the line
     * below (lint cannot follow the check through [hasPermission]), the manager's own
     * areNotificationsEnabled gate is honoured, and every notify call is wrapped in runCatching
     * so a revoked permission surfaces as a no-op rather than a crash.
     */
    @SuppressLint("MissingPermission")
    fun notifyNewMail(context: Context, mailbox: String, messages: List<MessageSummary>) {
        if (messages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val shown = messages.sortedByDescending { it.createdAt }.take(MAX_VISIBLE)

        shown.forEach { message ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setContentTitle(message.from.displayName)
                .setContentText(message.displaySubject)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildString {
                            append(message.displaySubject)
                            if (message.intro.isNotBlank()) {
                                append('\n')
                                append(message.intro)
                            }
                        }
                    )
                )
                .setSubText(mailbox)
                .setWhen(message.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openMessageIntent(context, message.id))
                .build()
            runCatching { manager.notify(message.id.stableNotificationId(), notification) }
        }

        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle(
                if (messages.size == 1) "1 new message" else "${messages.size} new messages"
            )
            .setContentText(mailbox)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(mailbox)
                    .also { style ->
                        shown.forEach { style.addLine("${it.from.displayName}: ${it.displaySubject}") }
                    }
            )
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setContentIntent(openMessageIntent(context, null))
            .build()
        runCatching { manager.notify(SUMMARY_ID, summary) }
    }

    fun cancelAll(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    fun cancel(context: Context, messageId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(messageId.stableNotificationId())
        }
    }

    private fun openMessageIntent(context: Context, messageId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (messageId != null) putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getActivity(
            context,
            messageId?.stableNotificationId() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Notification ids must be Int, message ids are hex strings. */
    private fun String.stableNotificationId(): Int {
        // Offset keeps every per-message id clear of SUMMARY_ID.
        return (hashCode() and 0x7FFFFFFF).let { if (it == SUMMARY_ID) it + 1 else it }
    }
}
