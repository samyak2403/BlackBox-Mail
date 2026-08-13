# BlackBox Mail

A disposable email client for Android, built with Jetpack Compose and modelled closely on the
Gmail app's look and behaviour. It creates throwaway mailboxes on
[mail.tm](https://mail.tm) so you can receive signup confirmations and verification codes without
handing over your real address.

Free, no account, no API key.

## Features

**Mailbox**
- Creates a random mailbox on first launch and restores it on every later start
- Custom addresses: pick your own name and domain
- Keeps multiple mailboxes and switches between them
- Shows the mailbox password so you can sign in on mail.tm itself or restore the inbox elsewhere
- One-tap copy for the address, optional auto-copy whenever a new one is created
- Delete a mailbox and its mail from mail.tm for good

**Reading mail**
- Gmail-style list: sender avatar, bold unread rows, snippet, relative date, star
- Real brand logos in the avatar circle, falling back to a coloured initial
- HTML bodies rendered in a WebView with JavaScript permanently off
- Attachments open in whichever app handles the file type
- Local search across sender, subject and preview text

**Organising**
- Inbox, Starred, All mail and Trash, with unread counts in the drawer
- Configurable swipe actions in both directions
- Long-press or tap an avatar for multi-select, with a contextual action bar
- Pull to refresh, plus polling on an interval you choose

**Notifications**
- Alerts for new mail, grouped under a summary, tapping opens the message
- Background checks via WorkManager for when the app is closed
- Suppressed while you are already looking at the inbox

**Settings**
- Theme: system, light or dark
- Auto-refresh interval, or manual only
- Swipe action bindings and delete confirmation
- Sender logos and remote image blocking
- Preview text on or off

## How it works

mail.tm needs no API key. The flow is:

1. `GET /domains` for an available domain
2. `POST /accounts` with a random local part and password
3. `POST /token` to exchange those credentials for a bearer token
4. `GET /messages` on an interval, `GET /messages/{id}` for a full body

Credentials are stored on the device so the same inbox comes back after a restart. When a token
expires the app re-authenticates with the stored password; if the mailbox itself has expired, it
provisions a fresh one.

Note that `Accept: application/json` makes the API answer with bare JSON arrays rather than Hydra
collections, so the parser handles both shapes.

## Requirements

| | |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 37 |
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3 |
| Build | AGP 9.3 |

## Building

```bash
git clone <your-repo-url>
cd BlackBoxMail
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. No API keys or local properties to configure.

To check everything before a release:

```bash
./gradlew assembleDebug lintDebug
```

## Project layout

```
app/src/main/java/com/samyak/blackboxmailt/
├── MainActivity.kt              Theme host, notification deep links
├── data/
│   ├── MailTmApi.kt             mail.tm REST client (OkHttp + org.json)
│   ├── MailRepository.kt        Session lifecycle, re-auth, message operations
│   ├── Models.kt                Domain types
│   ├── SessionStore.kt          Credentials, local folder flags
│   ├── SettingsStore.kt         Preferences exposed as a StateFlow
│   └── SenderLogos.kt           Brand icon lookup and caching
├── notifications/
│   ├── MailNotifier.kt          Channel and notification building
│   └── NewMailWorker.kt         Periodic background check
└── ui/
    ├── MailApp.kt               Navigation, drawer, dialogs, platform glue
    ├── MailViewModel.kt         UI state and actions
    ├── components/              Avatar, list row, drawer, search bar
    ├── screens/                 Inbox, message, settings
    └── theme/                   Gmail-like Material 3 palette
```

Single-module app, single ViewModel, no DI framework. State flows one way: `MailViewModel` owns a
`StateFlow<MailUiState>`, screens are passed the state plus callbacks.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talking to api.mail.tm |
| `ACCESS_NETWORK_STATE` | Letting WorkManager wait for connectivity |
| `POST_NOTIFICATIONS` | New mail alerts. Optional, and the app works fully without it |

## Privacy

- Mailbox credentials never leave the device except to authenticate with mail.tm.
- JavaScript is disabled in message bodies, always. Message HTML is untrusted input.
- Remote images can be blocked so senders cannot confirm you opened their mail. Off by default,
  toggleable in Settings.
- Sender logos send the sender's domain to Google's public favicon service the first time that
  sender is seen. Turn off "Sender logos" in Settings to stop this entirely.
- Copied passwords are flagged sensitive on Android 13+ so they stay out of the clipboard preview.

## Limitations

**Receiving only.** mail.tm has no send endpoint, so there is no composer. The Reply, Reply all and
Forward controls in the reading view explain this rather than opening one. Sending would need a
separate provider and a domain you control, since nothing can legitimately send as a mail.tm
address.

**Folders are local.** mail.tm stores a single inbox. Starred, All mail and Trash are tracked on the
device, so they do not follow you to another device or to the mail.tm website. Trash is a soft
delete; "Delete forever" is what actually removes a message from the server.

**Background checks are throttled.** Android's minimum periodic work interval is 15 minutes, so with
the app fully closed a notification can lag by that much. While the app is running the poll
interval you set in Settings applies.

**Mailboxes expire.** They are disposable by design. If mail.tm drops one, the app notices on the
next launch and creates a replacement.

## Packaging for F-Droid and IzzyOnDroid

Store listing text lives in `fastlane/metadata/android/en-US/`, which both repositories read
directly from this repo. See `fastlane/README.md` for the layout, character limits and the
screenshots that still need adding.

The build is already set up for their toolchains:

- No proprietary dependencies. Everything is AndroidX, OkHttp and WorkManager.
- `dependenciesInfo` is disabled, so the signed, non-reproducible dependency metadata block that
  Google normally embeds is left out of the APK.
- No API keys, no `local.properties` requirements, no Google Play Services.

Two things to be aware of before submitting:

- **Tag your releases.** The metadata at `metadata/com.samyak.blackboxmailt.yml` uses
  `UpdateCheckMode: Tags` with a `v%v` binary URL, so each release needs a `vX.Y.Z` tag and an
  `app-release.apk` asset attached to it.
- **Expect the NonFreeNet anti-feature.** The app depends on mail.tm, a network service that is not
  free software, and sender logos call Google's favicon endpoint. Both are core to what the app
  does; the logo lookup at least can be turned off in Settings.

## Credits

Email infrastructure by [mail.tm](https://mail.tm), used under their
[terms of use](https://docs.mail.tm), which ask for a visible link back to the service. That link
lives in the app's navigation drawer and in Settings.

API documentation: [docs.mail.tm](https://docs.mail.tm)

## License

MIT. See [LICENSE](LICENSE).
