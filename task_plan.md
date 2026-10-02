# Task Plan

> 接续说明（2026-10-02）：本文件保留历史计划，早期状态可能已过时。当前任务与下一步统一见 [PROJECT_STATUS.md](PROJECT_STATUS.md)，资料入口见 [知识库](docs/README.md)。

## Goal
Build an Android app that monitors incoming SMS messages from configured sender numbers, matches configured keywords, and forwards matched content to Feishu and WeCom group robots. Follow a software engineering workflow: requirements, design, testing, implementation, and verification.

## Phases
| Phase | Status | Notes |
|---|---|---|
| 1. Inspect current project state | completed | Android Studio template project confirmed |
| 2. Confirm scope and constraints | completed | UI scope, service model, history, simulation, robot reuse, and edge rules confirmed |
| 3. Write requirements/design/test docs | completed | Documents reviewed and finalized under `docs/` |
| 4. Implement core feature with TDD | in_progress | Domain, repositories, Room persistence, configuration workbench, service/receiver pipeline, webhook dispatch, recent-history view, manual retry chain, configurable automatic retry scheduling, retry settings UI, failed-retry observability improvements, readable recent-history presentation, presentation-layer status filters, simulation injection, and automatic post-injection refresh feedback implemented |
| 5. Verify and summarize | in_progress | Automated unit tests complete, layered test reports written, fresh-install permission flow, emulator SMS receive path, live Feishu/WeCom dual-channel delivery, and emulator E.164 normalization matching validated; simulator UI optimization pending |
| 6. Add minimum Android integration coverage | completed | Test seam, minimum `androidTest` cases, and rule-conflict UI coverage added; `connectedDebugAndroidTest` now passes end-to-end |
| 7. Reduce MainActivity orchestration coupling incrementally | in_progress | Editor state, simulation orchestration, and monitoring orchestration extracted; remaining work is optional further slimming and ViewModel evaluation |

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
- Emulator validation on March 15, 2026 also confirmed a live Feishu webhook returned HTTP 200 and persisted a `SUCCESS` forwarding record.
- International sender matching now uses `libphonenumber` with default region `CN`, standardizes parsed numbers to E.164, and falls back to a compacted raw form only when parsing fails.
- Android integration coverage now includes monitoring service processing, recovery, Room webhook容错, and rule-conflict UI interception, with `connectedDebugAndroidTest` passing on the emulator.
- MainActivity necessary refactor phase one is complete: rule and robot editor state have been extracted into dedicated state holders with unit-test coverage.
- MainActivity necessary refactor phase two has started: simulation orchestration is now handled by `SimulationCoordinator` with dedicated unit tests.
- MainActivity necessary refactor phase two is now largely complete: monitoring orchestration is also handled by `MonitoringCoordinator`, with unit tests covering refresh, permission request, and start/stop flows.

## Risks
- SMS broadcast behavior differs by Android version and OEM restrictions.
- Reading SMS requires runtime permission and likely default SMS app considerations on some devices.
- Feishu and WeCom robot APIs may have rate limits, security signatures, or IP restrictions depending on configuration.
- Local command-line builds currently fall back when the Kotlin daemon cannot access the default user profile temp path.
- Automatic retry polling currently depends on the foreground service remaining alive; there is not yet a separate scheduler or reboot recovery path.
- Live WeCom webhook delivery has been validated against a real endpoint.
- International number normalization has been validated on the emulator, but additional real-device samples from non-CN regions are still worth regression testing.

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| `git log` failed because repository is not initialized | 1 | Continue without commit history for now |
| `apply_patch` hit Windows sandbox refresh failures during doc updates | 1 | Used direct PowerShell file rewrite as a workaround |
| Gradle toolchain download failed for JDK 21 | 1 | Switched local daemon toolchain file to 17 for current CLI environment |
| KSP conflicted with AGP built-in Kotlin source-set restriction | 1 | Added `android.disallowKotlinSourceSets=false` in `gradle.properties` |
| Kotlin daemon could not access the default temp marker path | multiple | Gradle fallback compilation still completed successfully with local workspace user-home overrides |
| Foreground service startup initially crashed on Android 16 because the data-sync foreground permission was missing | 1 | Added `android.permission.FOREGROUND_SERVICE_DATA_SYNC` to the manifest and revalidated on emulator |

## 2026-04-18 Keepalive Design Decisions
- 新增保活优化方向：通用保活框架 + 荣耀 200 Pro 优先验证。
- 明确保活实现顺序：恢复机制 -> 重试调度改造 -> 保活设置向导 -> 诊断页。
- 明确用户手动停止监控后不得自动恢复。
- 明确现有 30 秒重试轮询将被精确调度替代。


- 已完成恢复机制第一版：开机恢复、升级恢复、用户手动停止边界。
- 已完成重试调度改造第一版：以最早下一次重试时间驱动 AlarmManager 精确调度，替代服务内固定 30 秒轮询。
- 已完成保活设置向导第一版：配置中心可直接检查电池优化、通知、监控状态，并提供荣耀 200 Pro 专项引导。
- 已完成保活状态诊断第一版：可查看最近启动/停止/恢复/短信/重试时间，用于排查保活失效原因。
- 下一步建议进入真机验证，重点检查荣耀 200 Pro 上的保活向导可达性、恢复链路行为和诊断信息可读性。

