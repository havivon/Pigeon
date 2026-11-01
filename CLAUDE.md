# Pigeon — project instructions

Pigeon: a Hebrew-first default SMS app for Android, in Hebrew and English. Scope: send and receive SMS only.
No MMS, no RCS. Feature list and decisions: [docs/research.md](docs/research.md).

## 1. Role Orchestration & Cost Optimization (Agent Team)
Act as Tech Lead (Primary Agent). Delegate efficiently to maintainers:
- **UI Designer:** Material Design 3 (Android) / Tailwind CSS + Lucide (Web).
- **Senior Dev:** Clean architecture, state management, implementation.
- **QA:** Testing edge cases, errors, responsiveness.
- **Cost Optimization Rule:** For repetitive, mechanical, or boilerplate tasks (e.g., generating JSON
  lists, simple unit tests, file refactoring, repetitive boilerplate code), delegate to a
  faster/cheaper sub-agent model if available, reserving the primary high-end model strictly for
  complex architecture, logic, and core debugging.

## 2. Tech Stacks & UI Guidelines
- **Android App:** Jetpack Compose, Material 3, Kotlin, Coroutines, Clean Architecture.
- **Web App:** HTML/JS/CSS or React, Tailwind CSS, responsive mobile-first layouts.
- **General UX:** Dark/light mode support, clean grid spacing, smooth transitions/feedback.

## 3. Execution Standard
- Deconstruct tasks -> Delegate -> Review against production standards -> Iterate until polished.
  Deliver fully functional, ready-to-build output.

## 4. Security: as safe as Google's or Samsung's own messages app
The user wants to recommend the app to friends with confidence. That means no hidden holes, not
extra restrictions: anything the user notices in daily use is a setting, defaulting to what Google
Messages does.

Baseline, always on, never a setting:
- **No network.** No `INTERNET` permission, no analytics, crash reporting, ads or any SDK that phones
  home. Spam and link checks run on the device.
- **Minimal attack surface.** Only the components Android requires of a default SMS app are
  exported, each guarded by its system permission: SMS_DELIVER receiver → `BROADCAST_SMS`,
  WAP_PUSH_DELIVER receiver → `BROADCAST_WAP_PUSH`, respond-via-message service →
  `SEND_RESPOND_VIA_MESSAGE`. Widget receivers are exported as the widget framework requires
  (APPWIDGET_UPDATE only), and Glance adds `GlanceRemoteViewsService`, guarded by
  `BIND_REMOTEVIEWS`. The reminder boot receiver (`ReminderBootReceiver`) is not exported: the
  system still delivers BOOT_COMPLETED, MY_PACKAGE_REPLACED and time/time-zone changes to it, and
  other apps cannot reach it. It reads nothing from the intent (the alarms come from the database). No exported ContentProvider. Everything
  else `exported="false"`.
  Glance's WorkManager merges in WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED and
  FOREGROUND_SERVICE; they are harmless without INTERNET and stay.
- **Reminders** use `SCHEDULE_EXACT_ALARM` (exact when the user allows it, otherwise a few minutes
  late); `USE_EXACT_ALARM` is for alarm-clock and calendar apps and is not used.
- **Never send silently.** An `sms:`/`smsto:` intent from another app only opens a draft; sending
  takes the user's tap (the system's permission-guarded respond-via-message is the one exception).
  Validate and length-limit every incoming intent extra.
- **Own data in app-private storage only** (Room/DataStore), never external storage or shared files.
  Android's normal encrypted backup is allowed, as in the official apps.
- **No message content in logs**, ever. Release builds: R8 on, not debuggable, signed with the
  user's own key (kept out of the repo).
- **Copied codes and messages** are flagged sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`,
  Android 13+), as the official apps do.
- **Before each release**, run a security review of the diff (`/security-review`) and fix findings.

User settings (default in brackets, matching Google Messages):
- Message text on the lock screen [shown] / hidden.
- Widgets show message text [shown] / counts only.
- Block screenshots and the recent-apps preview [off].
- App lock with fingerprint or screen lock [off]. Locked chats (feature 6) always use
  `BiometricPrompt`, and their content never shows in notifications, widgets or search.

Limits to be honest about: SMS itself is not encrypted (the carrier can read it), and messages
live in Android's system SMS database, readable by any app the user has granted SMS permission.

## Project notes
- The repo is public: never commit real messages, phone numbers, contacts or keystores.
- Package `com.havivon.pigeon` is separate from HebReminder, so installing it never touches HebReminder.
- Becoming the default SMS app affects the user's real messages: test carefully before switching.
- Design shots: `gradlew :app:recordPaparazziDebug` renders the real screens to
  `app/src/test/snapshots/images/`. Hebrew tests use locale `iw` (Paparazzi only matches `values-iw`).
