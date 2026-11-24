# Pigeon — session handoff (2026-11-11)

Read `CLAUDE.md` first, then this file. Write to the user in **Hebrew** always (even when he writes
English or a subagent reports in English). Code, commits and comments stay English.

## Where things stand (2026-11-14)
- Latest released: **0.7.0** (versionCode 33, commit 86faa93, pushed; apps site synced, content.json updated).
  APK: `dist/Pigeon-0.7.0_20261114-1956.apk` (the debug build, same signing key as 0.6.x).
  0.7.0 = the six service categories (`ServiceCategories.kt`), drag-reorder of every category + reset + backup v3
  (Room 5), the OTP spam guard (`SpamScorer.VERSION = 7`), plus two requests from this session:
  - the "All" side of the All / Unread toggle shows the total messages of the chosen category, incoming and sent,
    read and unread (`ThreadSummary.totalCount` -> `Conversation.totalCount` -> `InboxUiState.totalCount`);
  - **an ad from a known institution's sender id stays in its category** (the user's choice, after the Phoenix
    conversation fell out of Insurance because its latest message was an ad). An ad's *text* still never decides.
    `FolderClassifier.senderFolder`. A conversation's folder is still from its latest message only.
- **0.7.1** (versionCode 34): "Move to category" on the spam screen: a button on each sender card and an icon in the
  selection bar. It releases the senders from spam (allow list, as "Not spam") and files every conversation of
  theirs (and later messages) in the chosen category; Spam is not offered as a target there (`offerSpam = false`).
- **0.7.2** (versionCode 35): drag-reorder fixed in Settings > Categories and in the colour rules
  (`ui/components/ReorderDrag.kt`): one order state for the screen's life (`rememberDraggedOrder`; a state keyed by the
  source was replaced after the first save, and the gestures, made once per item, kept writing the old one, so nothing
  could be dragged again), a swap once the card's leading edge passes the neighbour's middle (the last place is easy to
  reach), and slow auto-scroll near the edges (`ReorderAutoScroll`, 260dp/s at most, by time not by frame).
- **0.7.3** (versionCode 36): dragging upwards with auto-scroll. A swap with the list's first visible item made
  the LazyColumn keep that item in place (by key), so the card jumped a place, swapped back and flickered; now
  `ReorderDragState.pendingScroll` puts the scroll position back (`scrollToItem`) and no swap is decided until then.
  Compose foundation is 1.6.8 (no `requestScrollToItem`). Not tried on the phone yet: the user tests drags by hand.
- **0.7.4** (versionCode 37): under "All categories" every inbox row (and unread row) shows its category tag, as
  search results do ("No category" too; elsewhere only coloured categories show a tag). Message dates are
  dd/MM/yyyy everywhere (the user's request): list rows (today = the time), bubbles ("Today 13:14" or
  "26/10/2026 13:14"), day dividers ("Today" or the date), copied/shared stamps and the favourites widget.
  Reminders keep their own labels. `DateLabels` in `ui/common/Formatting.kt`.
- **0.7.5** (versionCode 38): every delete of messages or conversations asks first (`ui/components/ConfirmDeleteDialog`):
  the inbox sheet, the conversation (selected messages, whole conversation), search (selection, one message) and Spam
  (one sender, the selected ones); when Pigeon may not delete, the "needs default" notice shows at once instead. The
  undo after a delete stays. Inbox multi-select as in search: "Select" in the long-press sheet, a tap toggles, the
  search's selection bar (count says "conversations") and action row (read, move, spam, remind for one, delete).
  It belongs to the chosen category: another category or the Unread list clears it.
- **0.7.6** (versionCode 39): a "Select" button (Checklist icon) in the inbox header next to the bell, as in search
  (`InboxViewModel.selecting`: mode on with nothing selected; "Select" in the sheet stays too). The category menu now
  always has a row of its own, with All / Unread / "Mark all read" under it (full label unless a large font or a narrow
  screen leaves no room). The search opened from a category keeps to it: `Screen.Search(category)`,
  `SearchFilters.category` (narrows, not one of `any`), a chip "In: <category>" whose X searches everywhere.
- **0.7.7** (versionCode 40): unlocking a locked chat (conversation menu, inbox sheet, search sheet) and changing the
  app lock always ask (`Authenticator.authenticateNow`), skipping the 10-second reuse of a recent success that let
  "unlock" through right after the chat was opened with a fingerprint.
- **0.7.8** (versionCode 41): the inbox no longer shows a loading sign on every launch. `ConversationSnapshot`
  (`cacheDir/inbox-snapshot.json`, app-private, not backed up) keeps the last read list; `TelephonyMessageRepository`
  emits it first (`onStart`, only while READ_SMS is granted, else it is deleted) and the full read replaces it.
  Locked chats are saved without their text. Documented in CLAUDE.md (security).
- **0.7.9** (versionCode 42), installed on the phone: the start measured on the phone (temporary timing logs, removed):
  `verdictFor` scanned every spam verdict per conversation (926 x 582, about 1.8 s); now a table by
  `PhoneNumbers.sameKey` (same meaning as `same`, tested on all pairs). The folders are kept between launches in
  `AnalysisCache` (`cacheDir/analysis-cache.json`: thread id, a 64-bit fingerprint and the folder; no text; dropped
  when the versionCode changes). List ready after a cold start: about 1.5 s -> 0.7 s (the first launch after an
  install still classifies everything once). The dist APKs are debug builds (no keystore.properties here): a signed
  release build would be faster still.
- **0.7.10** (versionCode 43), installed on the phone: in a conversation's multi-select a tap on the message text unselected it and the TextView's pending long press selected it again a moment later; the tap now cancels it (`cancelLongPress` + ACTION_CANCEL), and a long press while selecting toggles too. "Remind me" moved from the header to the bottom row (Copy / Share / Remind me) and works on several messages (one reminder each). "Copy" for one message, "Copy N together" for more (`copyLabel`, also in search). The reminder chip already showed on old messages whose date is still ahead (dates are read relative to when the message was written; test in RemindMeTest).
- **0.7.11** (versionCode 44), installed on the phone: a code copied automatically on arrival shows a short toast "Code copied, ready to paste" (`AndroidCodeClipboard.copy`; no code in it, it may show over the lock screen; Samsung shows no confirmation of its own for a background copy). The conversation's selection row is three equal columns, icon above the words (`SelectionAction` in ConversationScreen), so "Remind me" no longer wraps alone. "Mark as spam" / "Not spam" is in the inbox's long-press sheet too (`withSpam = true`), right after "Move to category" there and in search's message sheet.
- **0.7.12** (versionCode 45), NOT installed yet (the phone was disconnected): a star in the inbox header, next to the bell, opens the search on the starred messages (`Screen.Search(starred = true)` -> `SearchViewModel(starred)`, the "Starred" filter on, no keyboard). Open question from the user: show the "Code copied" toast only when Android shows no paste prompt of its own; the app cannot know that (nothing reports it), so it was put to the user.
- **0.8.1** (versionCode 47; 0.8.0 was installed, 0.8.1 NOT yet: the phone disconnected): spam rules v8/v9 (docs/spam-rules.md). Audited on the phone's own messages (export to the scratchpad, a local-only test running the real rules, deleted after; never committed): 21 institution senders left Spam (BituahLeumi was taken for Bank Leumi; surveys, opt-out lines and SMS-platform links of known senders), 15 missed scams now caught (fake emergency alert, stopped/unpaid parcel, courier named in the text, escort, login lure, drugs price list, gambling, politician sender ids). Known senders = `SpamScorer.isTrustedSender` (brands, prefixes like MaccabiSKR, gov, municipalities); `CodeSpamRelease` now also releases the filter's old verdicts on them. The category pill no longer shows the unread number (the menu and the Unread button do).
- **0.8.2** (versionCode 48), NOT installed yet (phone disconnected): "Move to category > New category" from the inbox sheet, the selections, search and spam sometimes did nothing, or made the category without moving the conversation: `MoveWithNotices` launched the work on its own `rememberCoroutineScope`, which the name dialog's `onDismiss` takes away on the next frame. Now `launch(NonCancellable, start = UNDISPATCHED)`. The conversation screen used `appScope` already. Keep only the newest Pigeon APK in the phone's Downloads (the user's wish).
- **0.8.3** (versionCode 49), NOT installed yet (phone disconnected): the composer's first tap. The field was only as big as its text, and while a bubble's selectable TextView held the focus the first tap only moved it back; now the whole pill focuses the field and focus always asks for the keyboard. A quiet scrollbar (`ui/components/ListScrollbar.kt`) on the inbox, a conversation, search, spam and the bin: thin and half-transparent at the end edge, solid and wider under a finger, draggable; only the thumb takes touches. Static Paparazzi shots do not show it (no layout yet on the first frame).
- **0.8.4** (versionCode 50), NOT installed yet (phone disconnected; 0.8.3 neither): while the scrollbar is dragged a date bubble beside the thumb names the top row's date (`ListScrollbar(label = { key -> ... })`, `scrollDate`: the rows' "dd/MM/yyyy", or Today for a time-only row; a conversation uses its day separators).
- **0.8.5** (versionCode 51), installed: the scrollbar thumb could not be dragged on the phone (it sits on the screen edge, which gesture navigation keeps for "back"): `systemGestureExclusion()` on the thumb; thicker (7dp, 12dp pressed), touch area 40dp wide, at least 56dp tall.
- **0.8.6** (versionCode 52), installed: the scrollbar thumb took the press but did not scroll. The drag was measured relative to the thumb, which moves with the finger, so each move was undone; now the finger's place on the track (`thumbTop` from the last layout + position - grab point).
- **0.8.7** (versionCode 53), NOT installed yet (phone disconnected): the reminder button under a message makes no reminder once its date has passed (`ConversationEvent.ReminderPassed`): a dialog says so and offers "Another time" (`ReminderSheet` -> `vm.remindMessage`). The starred list (and any opening of search) no longer waits out the typing debounce: an empty field is not debounced.
- **0.8.8** (versionCode 54), installed: the starred list still flashed the plain search's "start searching" text (the state's initial value had no filters) before its answer; now the initial state carries the filters and `loaded = false`, which shows no placeholder.
- **0.8.9** (versionCode 55), not installed yet (phone disconnected): the code copier took a year ("הראל 2026") over the real code in a long cue sentence ("קוד האימות ... 2026 הוא: 464802"). `CodeExtractor` now treats the first "הוא/היא/is/:" after a cue word (same sentence, within 80 chars) as a cue too, and adds a penalty to year-like numbers, so any other number near the cue wins ("your code is 2026" still gives 2026).
- **0.9.0** (versionCode 56): the launcher badge showed one conversation's count (Samsung sums the numbers of the active notifications). New setting "Number on the app icon" [off]. Off: message notifications go to `messages_nobadge` / `messages_hidden_nobadge` (showBadge false; a channel's badge cannot change after creation, so each setting has its channel, created with the user's sound copied, the unused one deleted once empty). On: the newest notification carries the unread messages without a notification (`unreadTotal` = the counter widget's count, spam out), the rest their own count (`EXTRA_OWN`), reposted with `recoverBuilder` + `setOnlyAlertOnce`. Reminders moved to `reminders_nobadge` channels (never a badge). `AppContainer.keepIconBadge()` refreshes on launch and, while on, on unread changes. Android shows a badge only while a notification exists.
- **0.9.1** (versionCode 57): "Mark as unread" in the long-press sheets (conversation, message result / starred), the inbox and search selections, the conversation menu and its selection. `markMessagesUnread` / `markThreadsUnread` (newest received message of each) set READ=0 only (SEEN stays). An open conversation closes after it (`keepUnread`, since what is on the screen is read). Spam has no read state in the UI, so no option there. Also: the code pointer ignores the colon of a time or a link. Security review of 0.8.9-0.9.1: no findings.
- **0.9.2** (versionCode 58): "Mark as unread" only for messages (the user's choice): the message sheet, the search selection while only messages are selected, and the conversation's selection. Removed from the conversation sheet, the inbox selection and the conversation menu, with `markThreadsUnread`. The icon number's total now comes from the database (`unreadByThread`), not the kept list, which is stale when a message starts the process. Still true: no notification, no number (Android).
- **0.9.3** (versionCode 59): the number stays on the icon until everything is read (setting on). A launcher shows none without a notification, so when no message notification is left a quiet one (`unread_count` channel, IMPORTANCE_LOW, VISIBILITY_SECRET, count only) carries it; `BadgePlan` decides who carries what. Refreshed on swipe (delete intent, `ACTION_DISMISSED`), when the app leaves the screen, after a reboot or update, and on unread changes. Not yet seen on the phone; if One UI badges IMPORTANCE_MIN too, the channel could go lower (no status-bar icon).
- Stale test XMLs: if `testDebugUnitTest` says "Could not write XML test results", stop old Gradle/Java processes
  (`./gradlew --stop`) and delete `app/build/test-results/testDebugUnitTest`; the failures it shows are old.

## Analysis results (spam/categories, 925 conversations)
"No category" 424 → 229 after rules v6; Spam 327 → 490; Deliveries 16 → 41. Remaining uncategorized:
~78 fit the six new categories, ~18 employer (Harel HR/IT), ~15 weddings/invitations, ~50
politics/charity/religion not caught, ~7 pension reports, ~8 private people. Known brands' ads
(Phoenix, Libra, Wobi, Htzone, Pelephone) stay out of Spam on purpose.

## Working rules (the user's)
- **Subagents on Sonnet** by default (usage quota). Opus only for a hard bug Sonnet failed on.
- **Compose crash rule:** never `if (x != null) ({…}) else null` lambdas or smart-cast captures inside
  composable/sheet content — caused `ClassCastException … to java.lang.Void` (fixed 0.6.3). After builds,
  scan compiled UI classes with javap for `checkcast java/lang/Void`, in batches (35 s; one javap per class takes
  minutes, and one javap for all of them exceeds the Windows command line and silently reads nothing):
  `find app/build/tmp/kotlin-classes/debug/com/havivon/pigeon/ui -name "*.class" | xargs -n 150 javap -c -p | grep -c "checkcast.*java/lang/Void"`
  (check that a plain `grep -c checkcast` is in the thousands, so the scan really read the classes).
- Gate: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew --no-daemon
  :app:testDebugUnitTest :app:verifyPaparazziDebug :app:lintDebug :app:assembleDebug` (record shots first
  when UI changes). Never `export MSYS_NO_PATHCONV=1` in a shell that runs gradlew.
- adb: `/c/Users/YANIV/AppData/Local/Android/Sdk/platform-tools/adb.exe`, always wrapped in `timeout`,
  `MSYS_NO_PATHCONV=1` on adb calls only. Launch with `am start -n com.havivon.pigeon/.IconEnvelope`,
  never monkey. No screenshots, no device-setting changes, never read message content.
- APKs to `dist/Pigeon-<ver>_<yyyyMMdd-HHmm>.apk` (unique names).
- **Apps site:** Pigeon is listed. After each version: `cd "F:\My Projects\Havivons-apps" && python
  sync.py --push pigeon`, then set `data/content.json` → `pigeon.version` (and add features if new),
  commit and push.
- "Created by Havivon" stays at the bottom of Settings.
- Crash reports: 0.6.4+ saves crashes to `files/crash/last.txt` and offers "Send report" on next launch.
  Friends reported the search long-press crash on "0.6.3"; unconfirmed whether they had the fixed build.
