# Pigeon — session handoff (2026-11-11)

Read `CLAUDE.md` first, then this file. Write to the user in **Hebrew** always (even when he writes
English or a subagent reports in English). Code, commits and comments stay English.

## Where things stand
- Latest released: **0.6.5** (versionCode 32, commit 4a844c0, sender search). Installed on the phone:
  a build of the `categories-tuning` branch with versionCode 31 (installed with `-d`).
- **In progress when this session ended:** a Sonnet subagent building **0.7.0** (versionCode 33):
  1. merge branch `categories-tuning` (worktree `F:\My Projects\Pigeon-cats`; commits dde28a0
     existing-folder fixes, 3f1e3b2 spam rules v6 + the SpamOnRead "+972/05 = group" bug fix) into `main`,
     then remove the worktree and branch;
  2. six new built-in categories with icons: Telecom/תקשורת (CellTower), Health/בריאות, Bills &
     services/חשבונות ושירותים (ReceiptLong), Transport/תחבורה (DirectionsBus), Travel/תיירות (Flight),
     Shopping/קניות (ShoppingCart) — rules from the user's real senders (HOT, Partner, Pelephone 4458,
     Bezeq 199, CLALIT, Maccabi, MADA, Sheba, TIPATHALAV, PAZGAS, TAMI4, RamatGan, Pango, ITURAN, ELAL,
     ACE, Payngo, SuperPharm, PaisPlus/ManuiPais, …); ads (opt-out / "פרסומת") never go in them;
  3. drag-reorder of ALL categories (built-in + custom) in Settings → Categories, "reset to default
     order", saved in backup;
  4. OTP guard: a message with a one-time code is never spam by score alone (`SpamScorer.VERSION = 7`).
  **Check first:** `git log --oneline -8` in `F:\My Projects\Pigeon`, `git worktree list`, `git status`.
  If 0.7.0 is not committed, finish it from the state on disk (same brief as above).
- After 0.7.0: install on the phone (versionCode > 31), push the APK to `/sdcard/Download/`, launch,
  run the apps-site sync (below), send the APK to the user, summarise in Hebrew.

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
