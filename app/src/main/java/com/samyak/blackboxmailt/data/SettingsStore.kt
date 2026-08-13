package com.samyak.blackboxmailt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Actions that can be bound to a list swipe, mirroring Gmail's "Swipe actions" setting. */
enum class SwipeAction(val label: String) {
    NONE("None"),
    ARCHIVE("Archive"),
    DELETE("Delete"),
    MARK_READ("Mark read or unread"),
    STAR("Star")
}

/** How often the inbox polls mail.tm. mail.tm allows 8 requests per second per IP. */
enum class RefreshInterval(val seconds: Int, val label: String) {
    OFF(0, "Manual only"),
    TEN(10, "Every 10 seconds"),
    THIRTY(30, "Every 30 seconds"),
    MINUTE(60, "Every minute"),
    FIVE_MINUTES(300, "Every 5 minutes");

    companion object {
        fun fromSeconds(seconds: Int): RefreshInterval =
            entries.firstOrNull { it.seconds == seconds } ?: TEN
    }
}

data class MailSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val refreshInterval: RefreshInterval = RefreshInterval.TEN,
    val blockRemoteImages: Boolean = false,
    val showSenderLogos: Boolean = true,
    val showPreviewText: Boolean = true,
    val swipeRight: SwipeAction = SwipeAction.ARCHIVE,
    val swipeLeft: SwipeAction = SwipeAction.DELETE,
    val confirmBeforeDelete: Boolean = false,
    val copyNewAddress: Boolean = true,
    val newMailNotifications: Boolean = true,
    val backgroundChecks: Boolean = true
)

/**
 * App preferences, backed by SharedPreferences and exposed as a [StateFlow] so the theme,
 * the poll loop and the list rendering all react the moment a switch is flipped.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<MailSettings> = _settings.asStateFlow()

    val current: MailSettings get() = _settings.value

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setRefreshInterval(interval: RefreshInterval) = update { it.copy(refreshInterval = interval) }
    fun setBlockRemoteImages(value: Boolean) = update { it.copy(blockRemoteImages = value) }
    fun setShowSenderLogos(value: Boolean) = update { it.copy(showSenderLogos = value) }
    fun setShowPreviewText(value: Boolean) = update { it.copy(showPreviewText = value) }
    fun setSwipeRight(action: SwipeAction) = update { it.copy(swipeRight = action) }
    fun setSwipeLeft(action: SwipeAction) = update { it.copy(swipeLeft = action) }
    fun setConfirmBeforeDelete(value: Boolean) = update { it.copy(confirmBeforeDelete = value) }
    fun setCopyNewAddress(value: Boolean) = update { it.copy(copyNewAddress = value) }
    fun setNewMailNotifications(value: Boolean) = update { it.copy(newMailNotifications = value) }
    fun setBackgroundChecks(value: Boolean) = update { it.copy(backgroundChecks = value) }

    fun resetToDefaults() {
        val defaults = MailSettings()
        write(defaults)
        _settings.value = defaults
    }

    private fun update(transform: (MailSettings) -> MailSettings) {
        val updated = transform(_settings.value)
        write(updated)
        _settings.value = updated
    }

    private fun read() = MailSettings(
        themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        refreshInterval = RefreshInterval.fromSeconds(prefs.getInt(KEY_REFRESH, 10)),
        blockRemoteImages = prefs.getBoolean(KEY_BLOCK_IMAGES, false),
        showSenderLogos = prefs.getBoolean(KEY_SENDER_LOGOS, true),
        showPreviewText = prefs.getBoolean(KEY_PREVIEW, true),
        swipeRight = enumOrDefault(prefs.getString(KEY_SWIPE_RIGHT, null), SwipeAction.ARCHIVE),
        swipeLeft = enumOrDefault(prefs.getString(KEY_SWIPE_LEFT, null), SwipeAction.DELETE),
        confirmBeforeDelete = prefs.getBoolean(KEY_CONFIRM_DELETE, false),
        copyNewAddress = prefs.getBoolean(KEY_COPY_NEW, true),
        newMailNotifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        backgroundChecks = prefs.getBoolean(KEY_BACKGROUND, true)
    )

    private fun write(settings: MailSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.themeMode.name)
            .putInt(KEY_REFRESH, settings.refreshInterval.seconds)
            .putBoolean(KEY_BLOCK_IMAGES, settings.blockRemoteImages)
            .putBoolean(KEY_SENDER_LOGOS, settings.showSenderLogos)
            .putBoolean(KEY_PREVIEW, settings.showPreviewText)
            .putString(KEY_SWIPE_RIGHT, settings.swipeRight.name)
            .putString(KEY_SWIPE_LEFT, settings.swipeLeft.name)
            .putBoolean(KEY_CONFIRM_DELETE, settings.confirmBeforeDelete)
            .putBoolean(KEY_COPY_NEW, settings.copyNewAddress)
            .putBoolean(KEY_NOTIFICATIONS, settings.newMailNotifications)
            .putBoolean(KEY_BACKGROUND, settings.backgroundChecks)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    companion object {
        private const val PREFS = "blackbox_mail_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_REFRESH = "refresh_interval_seconds"
        private const val KEY_BLOCK_IMAGES = "block_remote_images"
        private const val KEY_SENDER_LOGOS = "show_sender_logos"
        private const val KEY_PREVIEW = "show_preview_text"
        private const val KEY_SWIPE_RIGHT = "swipe_right"
        private const val KEY_SWIPE_LEFT = "swipe_left"
        private const val KEY_CONFIRM_DELETE = "confirm_before_delete"
        private const val KEY_COPY_NEW = "copy_new_address"
        private const val KEY_NOTIFICATIONS = "new_mail_notifications"
        private const val KEY_BACKGROUND = "background_checks"

        @Volatile
        private var instance: SettingsStore? = null

        fun getInstance(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
