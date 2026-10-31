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

## Project notes
- The repo is public: never commit real messages, phone numbers, contacts or keystores.
- Package `com.hebsms.app` is separate from HebReminder, so installing it never touches HebReminder.
- Becoming the default SMS app affects the user's real messages: test on the emulator first.
