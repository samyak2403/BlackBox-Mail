package com.samyak.blackboxmailt.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.samyak.blackboxmailt.data.ApiException
import com.samyak.blackboxmailt.data.MailAccount
import com.samyak.blackboxmailt.data.MailAttachment
import com.samyak.blackboxmailt.data.MailFolder
import com.samyak.blackboxmailt.data.MailRepository
import com.samyak.blackboxmailt.data.MailSettings
import com.samyak.blackboxmailt.data.MessageDetail
import com.samyak.blackboxmailt.data.MessageSummary
import com.samyak.blackboxmailt.data.RefreshInterval
import com.samyak.blackboxmailt.data.SettingsStore
import com.samyak.blackboxmailt.data.StoredAccount
import com.samyak.blackboxmailt.data.SwipeAction
import com.samyak.blackboxmailt.data.ThemeMode
import com.samyak.blackboxmailt.notifications.MailNotifier
import com.samyak.blackboxmailt.notifications.NewMailWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MailUiState(
    val initializing: Boolean = true,
    val refreshing: Boolean = false,
    val creatingAddress: Boolean = false,
    val account: MailAccount? = null,
    val password: String? = null,
    val savedAccounts: List<StoredAccount> = emptyList(),
    val messages: List<MessageSummary> = emptyList(),
    val folder: MailFolder = MailFolder.INBOX,
    val query: String = "",
    val searchActive: Boolean = false,
    val selected: Set<String> = emptySet(),
    val starred: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    val trashed: Set<String> = emptySet(),
    val openId: String? = null,
    val openMessage: MessageDetail? = null,
    val openLoading: Boolean = false,
    val domains: List<String> = emptyList(),
    val notice: String? = null,
    val fatalError: String? = null,
    /** Set right after a mailbox is provisioned so the UI can auto-copy the address. */
    val justCreatedAddress: String? = null
) {
    val inSelectionMode: Boolean get() = selected.isNotEmpty()

    private fun inFolder(folder: MailFolder): List<MessageSummary> = when (folder) {
        MailFolder.INBOX -> messages.filter { it.id !in archived && it.id !in trashed }
        MailFolder.STARRED -> messages.filter { it.id in starred && it.id !in trashed }
        MailFolder.ALL -> messages.filter { it.id !in trashed }
        MailFolder.TRASH -> messages.filter { it.id in trashed }
    }

    val visibleMessages: List<MessageSummary>
        get() {
            val base = inFolder(folder)
            val needle = query.trim()
            if (needle.isEmpty()) return base
            return base.filter { message ->
                message.subject.contains(needle, ignoreCase = true) ||
                    message.intro.contains(needle, ignoreCase = true) ||
                    message.from.address.contains(needle, ignoreCase = true) ||
                    message.from.name.contains(needle, ignoreCase = true)
            }
        }

    fun unreadCount(folder: MailFolder): Int = inFolder(folder).count { !it.seen }

    fun folderCount(folder: MailFolder): Int = inFolder(folder).size
}

class MailViewModel(application: Application) : AndroidViewModel(application) {

    /** A getter, not a stored field: holding a Context in a ViewModel trips StaticFieldLeak. */
    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val repository = MailRepository(application)
    private val settingsStore = SettingsStore.getInstance(application)

    /** Notifications are pointless while the user is already looking at the inbox. */
    private var appInForeground = true

    private val _state = MutableStateFlow(MailUiState())
    val state: StateFlow<MailUiState> = _state.asStateFlow()

    val settings: StateFlow<MailSettings> = settingsStore.settings

    private var pollJob: Job? = null

    init {
        MailNotifier.ensureChannel(context)
        NewMailWorker.sync(context)
        bootstrap()
        // Restart the poll loop whenever the refresh interval changes in Settings.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.refreshInterval }
                .distinctUntilChanged()
                .collect { startPolling(it) }
        }
    }

    // ---------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    fun setRefreshInterval(interval: RefreshInterval) = settingsStore.setRefreshInterval(interval)
    fun setBlockRemoteImages(value: Boolean) = settingsStore.setBlockRemoteImages(value)
    fun setShowSenderLogos(value: Boolean) = settingsStore.setShowSenderLogos(value)
    fun setShowPreviewText(value: Boolean) = settingsStore.setShowPreviewText(value)
    fun setSwipeRight(action: SwipeAction) = settingsStore.setSwipeRight(action)
    fun setSwipeLeft(action: SwipeAction) = settingsStore.setSwipeLeft(action)
    fun setConfirmBeforeDelete(value: Boolean) = settingsStore.setConfirmBeforeDelete(value)
    fun setCopyNewAddress(value: Boolean) = settingsStore.setCopyNewAddress(value)

    fun resetSettings() {
        settingsStore.resetToDefaults()
        notify("Settings restored to defaults")
    }

    fun consumeCreatedAddress() = _state.update { it.copy(justCreatedAddress = null) }

    // ----------------------------------------------------------- notifications

    fun setNewMailNotifications(value: Boolean) {
        settingsStore.setNewMailNotifications(value)
        if (!value) MailNotifier.cancelAll(context)
        NewMailWorker.sync(context)
    }

    fun setBackgroundChecks(value: Boolean) {
        settingsStore.setBackgroundChecks(value)
        NewMailWorker.sync(context)
    }

    /** Called once the notification permission result is known. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            MailNotifier.ensureChannel(context)
            NewMailWorker.sync(context)
        } else {
            settingsStore.setNewMailNotifications(false)
            NewMailWorker.cancel(context)
            notify("Notifications are off. Enable them in system settings to get new mail alerts.")
        }
    }

    /**
     * Posts a notification for messages that have not been announced yet. The first look at a
     * mailbox only records a baseline, so opening the app never dumps a pile of notifications
     * for mail that was already there.
     */
    private fun announceNewMail(messages: List<MessageSummary>) {
        val address = _state.value.account?.address ?: return
        val store = repository.store
        val allIds = messages.map { it.id }.toSet()

        if (!store.hasNotificationBaseline(address)) {
            store.setNotifiedIds(address, allIds)
            return
        }

        val known = store.notifiedIds(address)
        val fresh = messages.filter { it.id !in known && !it.seen }
        store.setNotifiedIds(address, known + allIds)

        if (fresh.isEmpty()) return
        if (!settingsStore.current.newMailNotifications) return
        // Foreground alerts would be noise while the user is looking at the list.
        if (appInForeground) return
        MailNotifier.notifyNewMail(context, address, fresh)
    }

    /** Set by the UI so notifications are suppressed while the inbox is on screen. */
    fun setAppInForeground(inForeground: Boolean) {
        appInForeground = inForeground
        if (inForeground) MailNotifier.cancelAll(context)
    }

    fun dismissNotification(messageId: String) = MailNotifier.cancel(context, messageId)

    // ------------------------------------------------------------- lifecycle

    private fun bootstrap() {
        viewModelScope.launch {
            _state.update { it.copy(initializing = true, fatalError = null) }
            try {
                val session = repository.session()
                applyAccount(session.account)
                _state.update { it.copy(initializing = false) }
                loadMessages(silent = false)
                startPolling(settingsStore.current.refreshInterval)
                loadDomains()
            } catch (e: ApiException) {
                _state.update {
                    it.copy(initializing = false, fatalError = e.message ?: "Something went wrong.")
                }
            }
        }
    }

    fun retryBootstrap() = bootstrap()

    private fun startPolling(interval: RefreshInterval) {
        pollJob?.cancel()
        if (interval.seconds <= 0) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(interval.seconds * 1000L)
                if (!_state.value.initializing && _state.value.fatalError == null) {
                    loadMessages(silent = true)
                }
            }
        }
    }

    private fun loadDomains() {
        viewModelScope.launch {
            val names = runCatching { repository.domains().map { it.domain } }.getOrDefault(emptyList())
            if (names.isNotEmpty()) _state.update { it.copy(domains = names) }
        }
    }

    private fun applyAccount(account: MailAccount) {
        val store = repository.store
        val saved = repository.savedAccounts()
        _state.update {
            it.copy(
                account = account,
                password = saved.firstOrNull { entry -> entry.address == account.address }?.password,
                savedAccounts = saved,
                starred = store.starred(account.address),
                archived = store.archived(account.address),
                trashed = store.trashed(account.address),
                selected = emptySet(),
                openId = null,
                openMessage = null
            )
        }
    }

    // -------------------------------------------------------------- messages

    fun refresh() = loadMessages(silent = false)

    private fun loadMessages(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(refreshing = true) }
            try {
                val messages = repository.messages()
                _state.update { it.copy(messages = messages, refreshing = false) }
                announceNewMail(messages)
            } catch (e: ApiException) {
                _state.update {
                    it.copy(refreshing = false, notice = if (silent) it.notice else e.message)
                }
            }
        }
    }

    fun openMessage(id: String) {
        _state.update { it.copy(openId = id, openLoading = true, openMessage = null) }
        viewModelScope.launch {
            try {
                val detail = repository.message(id)
                _state.update { it.copy(openMessage = detail, openLoading = false) }
                if (!detail.seen) markSeen(setOf(id), seen = true)
            } catch (e: ApiException) {
                _state.update { it.copy(openLoading = false, openId = null, notice = e.message) }
            }
        }
    }

    fun closeMessage() {
        _state.update { it.copy(openId = null, openMessage = null, openLoading = false) }
    }

    fun markSeen(ids: Set<String>, seen: Boolean) {
        if (ids.isEmpty()) return
        _state.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id in ids) it.copy(seen = seen) else it
                },
                selected = emptySet()
            )
        }
        viewModelScope.launch {
            val failures = runBatched(ids) { repository.markSeen(it, seen) }
            if (failures > 0) {
                notify("Could not update $failures ${plural(failures, "message", "messages")}")
            }
            // Reconcile with the server so a partial failure cannot leave a stale local flag.
            loadMessages(silent = true)
        }
    }

    /** Drawer action: every message in the mailbox, regardless of folder or active search. */
    fun markAllRead() {
        val current = _state.value
        val ids = current.messages
            .filter { !it.seen && it.id !in current.trashed }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) {
            notify("No unread messages")
            return
        }
        markSeen(ids, seen = true)
        notify("Marked ${ids.size} as read")
    }

    // ------------------------------------------------------- gmail-like flags

    fun toggleStar(id: String) {
        val address = _state.value.account?.address ?: return
        val updated = _state.value.starred.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        repository.store.setStarred(address, updated)
        _state.update { it.copy(starred = updated) }
    }

    fun archive(ids: Set<String>) {
        val address = _state.value.account?.address ?: return
        if (ids.isEmpty()) return
        val updated = _state.value.archived + ids
        repository.store.setArchived(address, updated)
        _state.update { it.copy(archived = updated, selected = emptySet()) }
        notify(if (ids.size == 1) "Archived" else "${ids.size} archived")
    }

    fun unarchive(ids: Set<String>) {
        val address = _state.value.account?.address ?: return
        val updated = _state.value.archived - ids
        repository.store.setArchived(address, updated)
        _state.update { it.copy(archived = updated, selected = emptySet()) }
        notify("Moved to Inbox")
    }

    fun moveToTrash(ids: Set<String>) {
        val address = _state.value.account?.address ?: return
        if (ids.isEmpty()) return
        val updated = _state.value.trashed + ids
        repository.store.setTrashed(address, updated)
        _state.update { it.copy(trashed = updated, selected = emptySet()) }
        notify(if (ids.size == 1) "Moved to Trash" else "${ids.size} moved to Trash")
    }

    fun restoreFromTrash(ids: Set<String>) {
        val address = _state.value.account?.address ?: return
        val updated = _state.value.trashed - ids
        repository.store.setTrashed(address, updated)
        _state.update { it.copy(trashed = updated, selected = emptySet()) }
        notify("Restored")
    }

    /** Removes the message from mail.tm for good. */
    fun deleteForever(ids: Set<String>) {
        if (ids.isEmpty()) return
        val address = _state.value.account?.address ?: return
        _state.update { current ->
            current.copy(
                messages = current.messages.filterNot { it.id in ids },
                trashed = current.trashed - ids,
                starred = current.starred - ids,
                archived = current.archived - ids,
                selected = emptySet(),
                openId = current.openId?.takeIf { it !in ids },
                openMessage = current.openMessage?.takeIf { it.id !in ids }
            )
        }
        repository.store.setTrashed(address, _state.value.trashed)
        repository.store.setStarred(address, _state.value.starred)
        repository.store.setArchived(address, _state.value.archived)
        viewModelScope.launch {
            val failures = runBatched(ids) { repository.deleteMessage(it) }
            val deleted = ids.size - failures
            when {
                failures == 0 ->
                    notify(if (deleted == 1) "Deleted forever" else "$deleted deleted forever")

                deleted == 0 ->
                    notify("Could not delete from mail.tm. Check your connection.")

                else ->
                    notify("$deleted deleted, $failures failed")
            }
            // Anything the server refused to delete comes back on the next load.
            loadMessages(silent = true)
        }
    }

    fun emptyTrash() {
        val ids = _state.value.trashed
        if (ids.isEmpty()) {
            notify("Trash is already empty")
            return
        }
        deleteForever(ids)
    }

    /**
     * mail.tm allows 8 requests per second per IP. Firing a whole batch at once earns 429s that
     * used to be swallowed, so requests are spaced out and failures are counted and reported.
     */
    private suspend fun runBatched(
        ids: Set<String>,
        action: suspend (String) -> Unit
    ): Int {
        var failures = 0
        ids.forEachIndexed { index, id ->
            if (index > 0) delay(BATCH_SPACING_MS)
            runCatching { action(id) }.onFailure { failures++ }
        }
        return failures
    }

    private fun plural(count: Int, singular: String, plural: String) =
        if (count == 1) singular else plural

    // ------------------------------------------------------------- selection

    fun toggleSelection(id: String) {
        _state.update { current ->
            val selected = current.selected.toMutableSet().apply { if (!add(id)) remove(id) }
            current.copy(selected = selected)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectAllVisible() {
        _state.update { it.copy(selected = it.visibleMessages.map { m -> m.id }.toSet()) }
    }

    // ----------------------------------------------------------------- chrome

    fun setFolder(folder: MailFolder) {
        _state.update {
            it.copy(folder = folder, selected = emptySet(), query = "", searchActive = false)
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setSearchActive(active: Boolean) {
        _state.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    }

    fun notify(text: String) = _state.update { it.copy(notice = text) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    // ---------------------------------------------------------------- account

    fun createNewAddress(localPart: String? = null, domain: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(creatingAddress = true) }
            try {
                val session = repository.newAddress(localPart, domain)
                applyAccount(session.account)
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        creatingAddress = false,
                        justCreatedAddress = session.account.address
                    )
                }
                notify("New address ready")
                loadMessages(silent = false)
            } catch (e: ApiException) {
                _state.update { it.copy(creatingAddress = false, notice = e.message) }
            }
        }
    }

    fun switchAccount(address: String) {
        if (address == _state.value.account?.address) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val session = repository.switchTo(address)
                applyAccount(session.account)
                _state.update { it.copy(messages = emptyList()) }
                loadMessages(silent = false)
            } catch (e: ApiException) {
                _state.update { it.copy(refreshing = false, notice = e.message) }
            }
        }
    }

    fun deleteCurrentMailbox() {
        viewModelScope.launch {
            _state.update { it.copy(creatingAddress = true) }
            try {
                val session = repository.deleteCurrentAccount()
                applyAccount(session.account)
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        creatingAddress = false,
                        justCreatedAddress = session.account.address
                    )
                }
                notify("Mailbox deleted")
                loadMessages(silent = false)
            } catch (e: ApiException) {
                _state.update { it.copy(creatingAddress = false, notice = e.message) }
            }
        }
    }

    fun forgetAccount(address: String) {
        viewModelScope.launch {
            repository.forgetAccount(address)
            _state.update { it.copy(savedAccounts = repository.savedAccounts()) }
            if (address == _state.value.account?.address) bootstrap()
        }
    }

    suspend fun attachmentBytes(attachment: MailAttachment): ByteArray? =
        runCatching { repository.downloadAttachment(attachment) }.getOrNull()

    companion object {
        /** ~6.6 requests per second, comfortably under mail.tm's limit of 8. */
        private const val BATCH_SPACING_MS = 150L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MailViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
            }
        }
    }
}
