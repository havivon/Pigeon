# Pigeon — QA report (stage D)

Scope: the whole app after stages A, B, C1, C2, read file by file, against [research.md](../research.md)
(requirements 1–7 and the chosen list) and the security baseline in [CLAUDE.md](../../CLAUDE.md) §4.
Nothing could run on a phone here: the evidence is code review, JVM tests, Paparazzi renders, lint,
the merged manifest and an R8 release build. What only a phone can show is in
[device-checklist.he.md](device-checklist.he.md); the traceability is in [test-plan.md](test-plan.md).

Severity: **blocker** (loses or leaks messages, or the app cannot be the default) · **major** (a
chosen feature or a visible control does not work, a security rule is broken, a likely crash) ·
**minor** (rare, cosmetic, or a limitation).

No blocker was found. Every major was fixed, each with a JVM test where it can be tested there.

## Fixed

| # | Sev. | Finding | Fix | Test |
|---|---|---|---|---|
| F1 | major | **Double send when not the default app.** When the system database refused the Outbox row, the message was still handed to the radio (the system then wrote it to Sent itself), while the screen said "failed" and kept the draft: a second tap sent it again. | `sendEach`: only a written message is transmitted. | `SendingTest` |
| F2 | major | **A refused incoming SMS could vanish.** The provider answers a refused insert with row `…/0`, which was taken as stored: the thread read back as 0 and no notification was shown. A stored message whose thread could not be read back was never notified either. | `ProviderSmsStore` treats row 0 as refused; both cases now notify with the text (`notifyUnsaved`). Store, hook and notifier are seams. | `IncomingSmsHandlerTest` (9) |
| F3 | major | **"Not spam" could do nothing.** Verdicts and the allow-list matched the exact normalised key, but one number is written differently on the SMS (`+972…`) and in the conversation it joined (`05…`). | `sameSender` (`PhoneNumbers.same`) for marking, checking and removing. | `SameSenderTest` |
| F4 | major | **Auto-delete of codes (on by default) deleted personal messages**, e.g. a friend's "the gate code is 4590". | Messages from contacts are never deleted; the setting's note says so. | `CodeCleanupTest` |
| F5 | major | **Friends' messages could go to spam silently** when the contacts permission is off (every sender is "unknown"): "click here" plus a short link is enough. | A sender the user has written to is treated like a contact. | `AnalysisSeamsTest` |
| F6 | major | **Search chips did nothing without typed text** ("Has a code", "Has a link", dates, and the sender chip with an empty list). | Filters alone browse the newest messages (`MessageRepository.browse`); the sender chip offers the newest conversations (never a locked one); a "No messages match these filters" empty state. | `ViewModelsTest` (2) |
| F7 | major | **Favourites had no place in the app** (feature 22: "and a screen that gathers them"); only the widget showed them. | "Starred" chip in search: the favourites' copies, filterable by text, never from a locked chat. | `ViewModelsTest` |
| F8 | major | **Release build without R8**, against CLAUDE.md. | `isMinifyEnabled`, `isShrinkResources`, not debuggable, keep rules (`app/proguard-rules.pro`), signing from the git-ignored `keystore.properties` when present. `:app:assembleRelease` builds (unsigned without the file). | gate |
| F9 | major | **Exported `PreviewActivity` in debug builds** (Compose tooling), which the user installs. | Removed with `tools:node="remove"` in `app/src/debug/AndroidManifest.xml`. | merged manifest |
| F10 | major | **Copy from the bubble's own toolbar was not flagged sensitive** (CLAUDE.md: copied messages are). | The selection toolbar's Copy goes through `SensitiveClipboard`. | device (DC 10) |
| F11 | major | **App lock showed the messages in the recent-apps preview.** | `setRecentsScreenshotEnabled(false)` while app lock is on (Android 13+). | device (DC 29) |
| F12 | major | **Possible crash in the new-message list:** a person saved twice (phone and SIM) gave two rows with one key. | Suggestions de-duplicated by name and `PhoneNumbers.same`; the key uses the normalised number. | `ViewModelsTest` |
| F13 | major | **Main-thread work on every change:** the inbox mapped every conversation (about 20 regexes for the code, plus rules and folders) and the conversation every message, on the main thread, on each provider change. | Inbox, conversation, spam and search build their state on `Dispatchers.Default` (injected; tests pass their own). | existing ViewModel tests |
| F14 | major | **`sms:` links tapped in a browser** arrive as VIEW, which the manifest did not accept. | VIEW added to the draft filter and to `LaunchRequests` (still only a draft). | `LaunchRequestsTest` (parsing) |
| F15 | minor | A multipart message whose parts reported at once could end Sent after a part failed (read-then-write race). | `sentResultGuard`: Sent is written only where the row is not Failed. | `SendingTest` |
| F16 | minor | A delete that threw anything but SecurityException left the rows hidden until restart. | `DelayedDeletion` releases in `finally`. | `DelayedDeletionTest` (4) |
| F17 | minor | Widget redraw inside the SMS receiver had no time bound (goAsync allows about 10 s). | `withTimeoutOrNull(4 s)`, after storing and notifying. | review |
| F18 | minor | `getOrCreateThreadId` throwing left "sending" stuck; a send with no thread opened an empty screen. | Returns 0; the screen says "sent" and goes back. | `ViewModelsTest` |
| F19 | minor | Mark-read caught only SecurityException; vendor providers throw others. | Catches RuntimeException. | review |
| F20 | minor | Two fingerprint requests at once (cover and locked chat): the second never got an answer. | One prompt, every waiter gets the result. | device (DC 26, 29) |
| F21 | minor | Avatars were blank for numbers (the LTR isolate was the "initial") and split emoji. | `avatarInitial`; a person icon for numbers. | `FormattingTest` |
| F22 | minor | Manifest and class comments called the boot receiver exported. | Corrected. | — |
| F23 | minor | No check that every string exists in both languages with the same placeholders. | `StringResourcesTest` (5); it passes, Hebrew plurals have one/two/other. | `StringResourcesTest` |

Snapshots changed on purpose: search (31, 40, 42) for the "Starred" chip; settings (46, 47, 48) for the
auto-delete note. No other PNG changed.

## Audit results with nothing to fix

- **Dead controls.** Every `clickable`, `IconButton`/`HeaderIcon`, `PillButton`, `Switch`, sheet action,
  menu item, widget action and notification action was traced to a handler; each Route passes every
  callback its Screen declares. The only do-nothing cases were the search chips (F6).
- **Manifest (merged, debug).** No `INTERNET`. Exported: `MainActivity` and its three launcher aliases
  (drafts only, extras validated and capped); `SmsDeliverReceiver` (`BROADCAST_SMS`),
  `WapPushDeliverReceiver` (`BROADCAST_WAP_PUSH`), `RespondViaMessageService`
  (`SEND_RESPOND_VIA_MESSAGE`); five widget receivers (`APPWIDGET_UPDATE`);
  `GlanceRemoteViewsService` (`BIND_REMOTEVIEWS`). Library extras, all system-guarded: WorkManager
  `SystemJobService` (`BIND_JOB_SERVICE`), WorkManager `DiagnosticsReceiver` and `ProfileInstallReceiver`
  (`DUMP`). These last two are not listed in CLAUDE.md; they are harmless (only the shell and the
  system hold DUMP). Everything else is not exported, the boot receiver and every own receiver included.
- **PendingIntents.** All `FLAG_IMMUTABLE` and explicit, except the inline reply (`FLAG_MUTABLE`, explicit,
  not-exported target, `setAuthenticationRequired`). Each is distinct by its data.
- **Logs.** Five `Log.w` calls, exception class name only; none quotes a message.
- **Locked chats.** Every way in (inbox, spam, search, new message, notification tap, reminder tap, widget
  tap) goes through `openThread`, which asks first; the cover returns after 10 s away; `FLAG_SECURE` over
  a locked chat; nothing of it in notifications, reminders, widgets or search.
- **Strings and RTL.** No hard-coded visible text in Kotlin; directional icons are `AutoMirrored`; numbers
  are isolated LTR and the isolates are removed when copied; dark mode has its own palette.
- **Lists.** Every lazy list has stable keys (ids; day separators are unique per date).
- **Lint** and the **R8 release build** pass.

## Left as they are (and why)

| Sev. | Item | Why |
|---|---|---|
| minor | Deleting and then killing the app inside the 5 s undo window keeps the messages (they come back). | Deliberate: it errs on the side of keeping messages. |
| minor | A conversation's unsent draft is lost on process death and on a language switch. | Needs drafts in the system database; small risk, larger change. |
| minor | The composer stays active for sender IDs ("Leumi") that cannot be replied to; such a send shows as failed. | Visible and recoverable; hiding it changes the approved design. |
| minor | Replies go out on the phone's default SMS SIM, not on the SIM the message came on; with "ask every time" the system decides. | SIM choice was taken out of scope by the user; DC 33 checks it. |
| minor | A notification reply in a group conversation goes to the last sender only. | Group SMS is rare (no MMS in scope). |
| minor | Pills and chips are 36–40 dp tall (Material asks 48 dp); rule reordering has no alternative to dragging. | Approved design; changing it alters every screen. |
| minor | A favourite keyed by a message id the provider later reuses (its ids are not AUTOINCREMENT) can show a star on a new message. | Rare (only after deleting the newest message); the favourite's own copy stays right. |
| minor | Any app can start MainActivity with a thread id and bring an unlocked conversation to the screen (locked ones still ask). It cannot read anything. | Same exposure as deep links in the official apps; a signed token would add complexity. |
| minor | On Android 8–12, app lock does not hide the recent-apps preview unless "Block screenshots" is on. | There it needs FLAG_SECURE, which also blocks screenshots: a visible restriction. |
| minor | If the phone's screen lock is removed later, locked chats open without a prompt. | Nothing is left to check the owner against, and removing the screen lock needs it first. |
| minor | Manual folders, bubble colours, chat wallpaper, font size, a limit per conversation (parts of research items 1, 2, 4). | Scoped out in the plan (automatic folders, theme, icon, rules). |

## Device-only risks (see the checklist)

1. Becoming the default and switching back to Google Messages without losing anything (DC 1–8).
2. Real radio results: long messages, airplane mode, retry (DC 6, 9); two SIMs (DC 33).
3. Receiver timing on a cold process, and the redraw of placed widgets (DC 4, 30).
4. BiometricPrompt on the user's phone: PIN fallback, the cover after 10 s, a notification tap on a locked
   chat with app lock on (DC 26, 29).
5. Text selection inside the bubble and its toolbar copy; drag-to-reorder of rules (DC 10, 20).
6. Exact alarms (Android 14 needs the special access) and reminders after a reboot (DC 21).
7. Respond-via-message from the phone app (DC 34).
8. The R8 release build was built but not run: install it once, signed, before giving it to friends.
