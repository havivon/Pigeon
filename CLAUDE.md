# HebSMS — project instructions

Hebrew-first default SMS app for Android. Scope: send and receive SMS only. No MMS, no RCS.
Feature list and decisions: [docs/research.md](docs/research.md).

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

## 4. Security (the user's explicit requirement: nobody else may read or reach the messages)
Every change is checked against these rules; a feature that needs to break one is raised with the
user first, never just built.
- **No network.** No `INTERNET` permission, no analytics, crash reporting, ads or any SDK that phones
  home. Spam and link checks run on the device only.
- **No backups out.** `allowBackup="false"`, `fullBackupContent="false"` and data extraction rules
  that exclude everything (cloud and device transfer).
- **Minimal attack surface.** Only the components Android requires of a default SMS app are
  exported, each guarded by its system permission: SMS_DELIVER receiver → `BROADCAST_SMS`,
  WAP_PUSH_DELIVER receiver → `BROADCAST_WAP_PUSH`, respond-via-message service →
  `SEND_RESPOND_VIA_MESSAGE`. No exported ContentProvider. Everything else `exported="false"`.
- **Never send silently.** An `sms:`/`smsto:` intent from another app only opens a draft; sending
  always takes the user's tap (except the system's respond-via-message, which is permission-guarded).
  Validate and length-limit every incoming intent extra.
- **Own data stays private.** Rules, favourites, lock settings and reminders live in app-private
  storage only (Room/DataStore), never on external storage or in shared files.
- **Locked chats and app lock** use `BiometricPrompt` (fingerprint or the screen lock). Their content
  never appears in notifications, the widget, search results or recent-apps previews.
- **Screens with message content** set `FLAG_SECURE` when locked chats are shown, and as a user
  option everywhere (blocks screenshots, screen recording and the recent-apps thumbnail).
- **Notifications** default to `VISIBILITY_PRIVATE` (content hidden on the lock screen), with an
  option to hide content entirely.
- **Clipboard.** Copied codes and messages are flagged sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`,
  Android 13+) so the system clipboard preview hides them.
- **Widget** shows counts only by default; content only if the user turns it on, never locked chats.
- **No message content in logs**, ever. Release builds: R8 minify on, debuggable off, signed with the
  user's own key (kept out of the repo).
- **Before each release** run a security review of the diff (`/security-review`) and fix findings.
- Limits to be honest about: SMS itself is not encrypted (the carrier can see it), and the messages
  live in Android's system SMS database, readable by any other app the user has granted SMS
  permission. The app can open the system screen that lists them, but cannot revoke it.

## Project notes
- The repo is public: never commit real messages, phone numbers, contacts or keystores.
- Package `com.hebsms.app` is separate from HebReminder, so installing it never touches HebReminder.
- Becoming the default SMS app affects the user's real messages: test on the emulator first.
