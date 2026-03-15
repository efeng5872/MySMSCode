# Task Plan

## Goal
Build an Android app that monitors incoming SMS messages from configured sender numbers, matches configured keywords, and forwards matched content to Feishu and WeCom group robots. Follow a software engineering workflow: requirements, design, testing, implementation, and verification.

## Phases
| Phase | Status | Notes |
|---|---|---|
| 1. Inspect current project state | completed | Android Studio template project confirmed |
| 2. Confirm scope and constraints | completed | UI scope, service model, history, simulation, robot reuse, and edge rules confirmed |
| 3. Write requirements/design/test docs | completed | Documents reviewed and finalized under `docs/` |
| 4. Implement core feature with TDD | in_progress | Domain, repositories, Room persistence, configuration workbench, service/receiver pipeline, webhook dispatch, recent-history view, manual retry chain, configurable automatic retry scheduling, retry settings UI, failed-retry observability improvements, readable recent-history presentation, presentation-layer status filters, simulation injection, and automatic post-injection refresh feedback implemented |
| 5. Verify and summarize | in_progress | Automated unit tests complete, layered test report written, fresh-install permission flow and emulator SMS receive path validated; real Feishu/WeCom webhook validation still pending |

## Decisions
- Use existing single-module Android app as the starting point.
- Treat this as a phased delivery: documents first, implementation after approval.
- Assume first release prioritizes stability and maintainability over aggressive background keep-alive behavior.
- First release includes a configuration UI instead of hard-coded sender and keyword rules.
- Background runtime model will use a resident foreground service with visible notification.
- Matching and forwarding rules are sender-specific: each monitored sender can define its own keywords and destination robots.
- Robot webhook endpoints are reusable global configurations; sender rules select linked robots instead of duplicating webhook settings.
- Keyword matching is case-insensitive substring matching with OR semantics.
- Manual retry operates on a single failed channel attempt.
- Automatic retry now reads a persisted retry policy, with defaults of 10 seconds, 30 seconds, and 60 seconds, and no further automatic retries after the third retry window is exhausted.
- Disabled sender rules are ignored and not stored.
- The first release does not implement SMS de-duplication.
- Domain baseline now includes ignore, not-matched, pending-forward, configuration-failed, success, and failed outcomes.
- In-memory repositories now enforce unique robot names, unique sender numbers, and multi-robot rule associations.
- Room is enabled with KSP and a compatibility flag for AGP built-in Kotlin in this environment.
- The current app UI provides a Room-backed configuration workbench, recent-history preview, failed retry queue, retry-policy editor, and monitoring controls.
- The failed retry queue now shows readable next-retry timestamps, completed retry counts, and whether automatic retries are scheduled, exhausted, or disabled.
- The recent-history card and failed retry queue now support in-app status filters without requiring new database queries.
- The workbench now includes a simulation injection entry point that feeds sender number and message body into the same service pipeline with source = `SIMULATION`.
- A phase-one layered test report now records automated coverage, current testing gaps, and the next device-level verification steps.
- A manual walkthrough checklist now documents the debug APK path and the recommended emulator/device validation sequence.
- Simulation injection now triggers short follow-up refreshes so recent history and retry state update without a manual refresh tap.
- The Android shell now includes a foreground service and SMS receiver pipeline that route incoming SMS through the tested domain logic, real webhook dispatching, manual retry execution, and automatic retry polling.
- The workbench now includes a runtime permission card that requests SMS and notification permissions and blocks monitoring until they are granted.
- Emulator validation on March 15, 2026 confirmed the fresh-install permission flow and a real SMS reaching the processing pipeline without manual adb permission grants.

## Risks
- SMS broadcast behavior differs by Android version and OEM restrictions.
- Reading SMS requires runtime permission and likely default SMS app considerations on some devices.
- Feishu and WeCom robot APIs may have rate limits, security signatures, or IP restrictions depending on configuration.
- Local command-line builds currently fall back when the Kotlin daemon cannot access the default user profile temp path.
- Automatic retry polling currently depends on the foreground service remaining alive; there is not yet a separate scheduler or reboot recovery path.
- Real Feishu/WeCom webhook delivery has not yet been validated against live endpoints; current emulator checks still use a placeholder failing URL for negative-path verification.

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| `git log` failed because repository is not initialized | 1 | Continue without commit history for now |
| `apply_patch` hit Windows sandbox refresh failures during doc updates | 1 | Used direct PowerShell file rewrite as a workaround |
| Gradle toolchain download failed for JDK 21 | 1 | Switched local daemon toolchain file to 17 for current CLI environment |
| KSP conflicted with AGP built-in Kotlin source-set restriction | 1 | Added `android.disallowKotlinSourceSets=false` in `gradle.properties` |
| Kotlin daemon could not access the default temp marker path | multiple | Gradle fallback compilation still completed successfully with local workspace user-home overrides |
| Foreground service startup initially crashed on Android 16 because the data-sync foreground permission was missing | 1 | Added `android.permission.FOREGROUND_SERVICE_DATA_SYNC` to the manifest and revalidated on emulator |

