# Pigeon: from design preview to a working app

Tech lead: the primary agent. Every stage ends with a green build, its tests passing, and a review
by the tech lead before the next stage starts. Rules from CLAUDE.md apply throughout (security
baseline, bilingual, light/dark, no INTERNET).

## Stage A — Senior Dev: SMS platform and data (core logic)
- Permissions: RECEIVE_SMS, READ_SMS, SEND_SMS, READ_CONTACTS, POST_NOTIFICATIONS, USE_BIOMETRIC.
- Default-SMS components, each permission-guarded: SMS_DELIVER receiver (stores every incoming SMS
  in the system provider, then notifies), WAP_PUSH_DELIVER receiver (required by the role; MMS is out
  of scope), respond-via-message service, and `sms:`/`smsto:`/`mms:`/`mmsto:` SENDTO filters on
  MainActivity (open a draft, never send by themselves).
- Reading conversations and messages from `Telephony` (threads, inbox, sent, failed), contact names
  from Contacts, as Flows that update on provider changes.
- Sending via SmsManager (multipart), writing to the Sent box, sent-status callbacks, failure marking.
- App's own data in Room: colour rules (ordered), favourites, reminders, locked threads, spam marks
  and "not spam" senders, blocked keywords, settings in DataStore.
- Notifications: per conversation, reply and mark-read actions, copy-code action, lock-screen
  visibility from settings, no content for locked chats; channels for messages and reminders.
- Repository interfaces in `domain/repository`, implementations in `data/`.
- `DefaultSmsRole.SMS_HANDLING_READY = true` only when all of the above works.

## Stage B — Senior Dev: message analysis (pure Kotlin, unit-tested)
Spam scoring with reasons, link extraction and look-alike domain warning, one-time-code extraction,
date/time phrases (Hebrew and English) for reminders, colour-rule matching (first match wins),
folder classification (personal, banks, codes, deliveries, spam). No Android dependencies.

## Stage C — Senior Dev + UI Designer: every screen works on real data
ViewModels per screen over the repositories (sample data stays for the design snapshots only):
inbox with folders, search with filters, pinned; conversation with send, delete + undo, favourites,
remind-me, multi-select copy/share; new-message screen with contact picker; spam folder actions;
rules editing with reorder; locked chats with BiometricPrompt; a real Settings screen (language,
icon, rules, lock-screen text, widget text, screenshot blocking, app lock, blocked keywords,
auto-delete codes); reminders firing as notifications; widgets on real data, updated on change;
default-app welcome and reminder switched on.

## Stage D — QA
Test plan against docs/research.md, unit tests for every analyzer and ViewModel, snapshot updates,
edge cases (long and multipart messages, RTL/LTR mix, emoji, empty states, no contacts permission,
permission denied, dual SIM, airplane mode send failure), and a security review of the whole app
against CLAUDE.md. Findings are fixed by the dev stage that owns them, then QA re-checks.
