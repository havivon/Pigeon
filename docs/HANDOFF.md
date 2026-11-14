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
- **Not done:** installing 0.7.1 on the phone (it was not connected). When it is: `adb install -r` the dist APK,
  push it to `/sdcard/Download/`, launch with `am start -n com.havivon.pigeon/.IconEnvelope`.
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
  scan compiled UI classes with javap for `checkcast java/lang/Void`.
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
