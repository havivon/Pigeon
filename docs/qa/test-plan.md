# Pigeon — QA test plan (stage D)

Traceability from what the user chose ([research.md](../research.md): requirements 1–7 and the
"גרסה ראשונה" list) and the security baseline ([CLAUDE.md](../../CLAUDE.md) §4) to the code and to how
each is verified. Findings and fixes are in [report.md](report.md); the on-phone run is
[device-checklist.he.md](device-checklist.he.md).

How things are verified here:
- **Unit** — JVM tests (`:app:testDebugUnitTest`), named by class.
- **Shot** — Paparazzi renders of the real screens (`:app:verifyPaparazziDebug`, 57 approved PNGs in
  `app/src/test/snapshots/images/`), in Hebrew and English, light and dark.
- **Lint / manifest / R8** — `:app:lintDebug`, the merged manifest, `:app:assembleRelease`.
- **Device** — only on a phone; covered by the numbered line of the device checklist (DC n).

Status: ✅ verified here · 🔧 fixed in stage D · 📱 needs the device · ⚠️ known limitation (see report).

## Platform: default SMS app and data safety

| What | Where | Verified by | Status |
|---|---|---|---|
| Incoming SMS stored first, then judged, then notified; never lost when the hook, the notifier or the provider fails; refused insert (row 0) and unknown thread still notified with the text | `data/sms/IncomingSmsHandler.kt`, `SmsReceivers.kt` (`SmsDeliverReceiver`, goAsync) | Unit `IncomingSmsHandlerTest` (9) | ✅🔧 |
| Long messages from several PDUs joined per sender; Hebrew and emoji intact | `SmsMapping.joinParts` | Unit `SmsMappingTest`, `IncomingSmsHandlerTest` | ✅ |
| Cold process: receiver builds the container, works off the main thread, widget redraw bounded to 4 s | `SmsReceivers.kt`, `AsyncReceiver.kt` | Code review; DC 4 | 📱 |
| Two SIMs: incoming SIM recorded (`SUBSCRIPTION_INDEX`/`subscription`); sending uses the phone's default SMS SIM | `SmsReceivers.kt`, `TelephonySmsSender.kt` | Code review; DC 33 | 📱⚠️ |
| Send: Outbox first, then radio; refused write is never transmitted (no double send); dedup of one number written twice | `TelephonySmsSender.kt` (`sendEach`) | Unit `SendingTest` | ✅🔧 |
| Send results: any failed part fails; Sent never overwrites Failed (atomic guard) | `SmsMapping.typeAfterSendResult`, `sentResultGuard`, `SmsSentReceiver` | Unit `SmsMappingTest`, `SendingTest` | ✅🔧 |
| Airplane mode / no service → Failed, tap to retry | `TelephonySmsSender.retry`, `ConversationRoute` | Unit `ViewModelsTest` (failed status, retry); DC 9 | ✅📱 |
| Never send without the user: `sms:`/`smsto:`/`mms:` SENDTO, SEND and (new) VIEW only open a draft; respond-via-message is the one system exception | `LaunchRequests.kt`, `AndroidManifest.xml`, `RespondViaMessageService.kt` | Unit `LaunchRequestsTest`, `ViewModelsTest` (draft from a link is only prefilled); DC 25, 34 | ✅🔧 |
| Delete with undo: hidden at once, deleted after 5 s, undo once, failure brings rows back; process death keeps the messages | `data/sms/DelayedDeletion.kt`, `TelephonyMessageRepository` | Unit `DelayedDeletionTest` (4), `ViewModelsTest`; DC 13–14 | ✅🔧 |
| Conversations and messages as Flows on provider changes; observers unregistered on close | `ProviderAccess.changes`, `TelephonyMessageRepository` | Code review (callbackFlow + awaitClose, WhileSubscribed) | ✅ |
| Provider reads off the main thread; screen state built on `Dispatchers.Default` | repositories (`flowOn(IO)`), Inbox/Conversation/Spam/Search ViewModels (`flowOn(work)`) | Code review; tests run with an injected dispatcher | ✅🔧 |
| Thread lookup / mark read survive vendor providers that throw | `TelephonyMessageRepository` | Code review | ✅🔧 |
| Default-app welcome, reminder card, "not now", settings fallback when the system dialog is suppressed | `PigeonShell.DefaultAppGate`, `DefaultSmsRole` | Shots 26–29, 35; DC 1–2, 7–8 | ✅📱 |
| Back to Google Messages keeps every message (system database only) | design (no private copy of messages) | DC 7 | 📱 |

## Requirements 1–7 (the user's own)

| # | Requirement | Where | Verified by | Status |
|---|---|---|---|---|
| 1 | One tap selects text inside the bubble (native handles) | `ConversationScreen.SelectableMessageText` | Shots 3, 9; DC 10 | ✅📱 |
| 2 | Multi-select, copy together, oldest first | `ConversationViewModel.selectedText`, `Mapping.formatForCopy` | Unit `MappingTest`, `ViewModelsTest`; shot 4; DC 11 | ✅ |
| 3 | Multi-select and share as one text | `ConversationRoute` (ACTION_SEND chooser) | DC 11 | 📱 |
| 4 | Colourful design, gradients, light/dark | `ui/theme/Theme.kt`, components | 57 shots (light, dark, he, en) | ✅ |
| 5 | Spam to its folder without a notification, red frame and warning in list and bubble, "not spam" | `SpamScorer`, `SpamFilterHook`, `SpamScreen`, `ConversationScreen` | Unit `SpamScorerTest` (21), `AnalysisSeamsTest`, `ViewModelsTest`, `SameSenderTest`; shots 5, 10, 36; DC 18 | ✅🔧 |
| 6 | Colour by sender or keyword, first rule wins, drag to reorder | `RuleMatcher`, `RulesScreen`, `RulesViewModel`, `RoomRulesRepository` | Unit `RuleMatcherTest` (13), `RulesAndSettingsViewModelsTest`, `CodecsTest`; shots 6, 11, 49–51; DC 20 | ✅📱 (drag) |
| 7 | Hebrew and English, RTL/LTR, system per-app language | `values/`, `values-iw/`, `locales_config.xml`, `LanguageSync`, `MainActivity` | Unit `StringResourcesTest` (new, 5), `LanguageSyncTest`; shots in both; DC 31 | ✅🔧 |

## The chosen features

| # | Feature | Where | Verified by | Status |
|---|---|---|---|---|
| 1 | Folders (automatic: personal, banks, codes, deliveries, spam) | `FolderClassifier`, `InboxViewModel`, `Mapping.inFolder` | Unit `FolderClassifierTest` (9), `ViewModelsTest`, `MappingTest`; shots 1, 8 | ✅ (manual folders ⚠️) |
| 2 | Personalisation: theme, launcher icon, colour rules | `SettingsScreen`, `AppIconSwitcher`, rules | Unit `RulesAndSettingsViewModelsTest`; shots 23–24, 46–48 | ✅ (bubble colour, wallpaper, font ⚠️) |
| 3 | Blocked keywords → spam | `BlockedKeywordsScreen/ViewModel`, `SpamScorer` | Unit `RulesAndSettingsViewModelsTest`, `AnalysisSeamsTest`; shots 52–54, 57; DC 19 | ✅ |
| 4 | Auto-delete codes after 24 h (counting from first run, starred and contacts' messages kept) | `CodeCleanupWorker` | Unit `CodeCleanupTest` (5) | ✅🔧 |
| 5 | Multi-select | as requirement 2 | as above | ✅ |
| 6 | Locked chats: BiometricPrompt, nothing in notifications/widgets/search, covered after 10 s away, FLAG_SECURE | `PigeonShell.LockGate`, `BiometricAuthenticator`, `MessageNotifier`, `WidgetDataBuilder`, repository search | Unit `MappingTest`, `WidgetDataTest`, `ReminderTimesTest` (locked texts); shot 38; DC 26 | ✅📱 |
| 8 | Undo delete | `DelayedDeletion`, `Notices.undo` | Unit `DelayedDeletionTest`, `ViewModelsTest`; DC 13 | ✅🔧 |
| 10 | Remind me about a message (alarm, Done, Snooze, restore after reboot) | `ReminderSheet`, `RemindersRepository`, `AlarmReminderScheduler`, `ReminderNotifier`, `ReminderReceiver` | Unit `RemindMeTest` (8), `ReminderTimesTest` (19); shots 55, 58; DC 21 | ✅📱 |
| 13 | Pinned conversations, separated | `InboxScreen` (pinned row), `PinsRepository` | Unit `ViewModelsTest`; shots 1, 8; DC 15 | ✅ |
| 19 | Copy code from the notification (and list, widget) | `MessageNotifier.copyCodeAction`, `NotificationActionReceiver`, `CodeExtractor` | Unit `CodeExtractorTest` (10), `WidgetDataTest`; DC 17 | ✅📱 |
| 20 | Real link address and look-alike warning | `LinkAnalyzer`, `LinkSheet` | Unit `LinkAnalyzerTest` (14); shots 33, 44; DC 16 | ✅ |
| 21 | Date in a message → reminder chip | `DateTimeExtractor`, `Mapping.findReminder` | Unit `DateTimeExtractorTest` (17), `MappingTest`, `ViewModelsTest`; DC 22 | ✅ |
| 22 | Favourites (star) and where to see them | `ConversationViewModel.starSelected`, `FavoritesRepository`, favourites widget, search "Starred" chip | Unit `ViewModelsTest`, `WidgetDataTest`; shot 18; DC 12, 23 | ✅🔧 |
| 27 | Search with filters (sender, link, code, 7/30 days), now also without typed text | `SearchViewModel`, `SearchScreen`, `MessageRepository.browse` | Unit `ViewModelsTest` (4 search tests); shots 31, 40, 42; DC 23 | ✅🔧 |
| 29 | Widgets (unread, latest code, contacts, counter, favourites), counts-only mode | `widget/` | Unit `WidgetDataTest` (20); shots 13–22; DC 30 | ✅📱 |
| 30 | Hebrew/RTL, Hebrew date, dark and light | `inboxDateLine`, `DateLabels`, theme | Unit `FormattingTest`; shots | ✅ |

## Security baseline (CLAUDE.md §4)

| Rule | Where | Verified by | Status |
|---|---|---|---|
| No INTERNET, no SDK that phones home | merged manifest | Merged manifest (no `INTERNET`); dependencies reviewed | ✅ |
| Only role components exported, each guarded | `AndroidManifest.xml`; debug manifest removes Compose's `PreviewActivity` | Merged manifest review (see report) | ✅🔧 |
| PendingIntents immutable except the reply (explicit, not exported, auth required) | `MessageNotifier`, `ReminderReceiver`, `SmsSentReceiver`, widgets | Code review | ✅ |
| Intent extras validated and length-limited | `LaunchRequests`, `RespondViaMessageService`, `NotificationActionReceiver` | Unit `LaunchRequestsTest` | ✅ |
| No message content in logs | 5 `Log.w` calls, exception type only | grep; Unit `IncomingSmsHandlerTest` (log names the failure only) | ✅ |
| Locked chat unreachable without auth (notification, reminder, widget, search, back stack after timeout) | `Screens.openThread`, `LockGate`, mappers | Code review; DC 26 | ✅📱 |
| App lock hides the recent-apps preview (Android 13+) | `PigeonShell` (`setRecentsScreenshotEnabled`) | Code review; DC 29 | 🔧📱 |
| Copied codes and messages flagged sensitive, including copy from the bubble's own toolbar | `SensitiveClipboard`, `ConversationScreen` | Code review; DC 10, 17 | ✅🔧 |
| Release: R8 on, resources shrunk, not debuggable, signed with the user's key (kept out of the repo) | `app/build.gradle.kts`, `app/proguard-rules.pro`, `keystore.properties` (git-ignored) | `:app:assembleRelease` | ✅🔧 |
| Own data in app-private storage only | Room, DataStore | Code review | ✅ |

## i18n, RTL, accessibility

| What | Verified by | Status |
|---|---|---|
| Every string in both languages, same placeholders, numbered when several, Hebrew plurals one/two/other | Unit `StringResourcesTest` | ✅🔧 |
| No hard-coded user-visible strings in Kotlin | grep of `Text("`, `contentDescription = "`, notification builders | ✅ |
| Directional icons mirrored (`AutoMirrored` back, send, open, chevron) | grep of icon imports | ✅ |
| Numbers kept LTR inside Hebrew (isolates), stripped when copied | Unit `FormattingTest`, `MappingTest` | ✅ |
| Avatars never blank for a number or an emoji name | Unit `FormattingTest` (avatar initial) | ✅🔧 |
| Icon-only buttons have content descriptions | grep of `HeaderIcon`, `IconButton`, widget icons | ✅ |
| Touch targets ≥ 48 dp | pills and chips are 36–40 dp tall | ⚠️ |

## Edge cases

| Case | Verified by | Status |
|---|---|---|
| Empty states (inbox, folder, not default, spam, search, keywords) | Shots 34–36, 54; search empty titles | ✅ |
| Long messages and names (ellipsis, multipart) | Unit `SmsMappingTest`; shots | ✅📱 |
| Emoji, mixed RTL/LTR | Unit `SmsMappingTest`, `LinkAnalyzerTest`, `FormattingTest` | ✅ |
| +972 / 05x / sender IDs / short codes | Unit `PhoneNumbersTest` (11), `SameSenderTest` | ✅ |
| No contacts permission (numbers instead of names; spam judged on content) | Code review | ✅⚠️ |
| Notifications denied (nothing posted, no crash) | Code review (`areNotificationsEnabled`, SecurityException) | ✅ |
| Not the default app (empty state, sends refused, deletes refused) | Shot 35; Unit `SendingTest` | ✅🔧 |
| Many conversations (lazy lists keyed by id; unique keys in suggestions) | Code review; Unit `ViewModelsTest` (saved twice) | ✅🔧 |
| Rotation (Navigator and ViewModels survive), process death (back to inbox, nothing lost) | Code review | ✅📱 |
| Locale switch on a sub-screen (stack rebuilt in the new language) | Code review (`Navigator.ensureLanguage`) | ✅📱 |
| App-lock grace (10 s), prompt shared between cover and locked chat | Code review | ✅🔧📱 |
