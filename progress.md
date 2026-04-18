# Progress Log

## 2026-03-14
- Read project structure and confirmed current app is still the default Android template.
- Loaded `brainstorming`, `planning-with-files`, and `test-driven-development` skill instructions.
- Created planning files to track requirements, design, testing, and implementation phases.
- Confirmed with the user that first release should include a configuration UI for sender numbers, keywords, and Feishu/WeCom robot URLs.
- Reviewed the public `mobile-next/mobile-mcp` project as a background architecture reference and noted the applicable service-oriented ideas.
- Confirmed the preferred runtime model is a resident foreground service with notification visibility.
- Confirmed the matching model should be sender-specific rather than global rules.
- Confirmed first release needs in-app history and failed retry visibility.
- Confirmed the app should store only configured-number messages and include a debug simulation channel.
- Wrote requirements, design, and test plan drafts into the `docs/` directory for user review.

## 2026-03-15
- Updated the documents so robot endpoints are configured globally and sender rules select reusable robot entries.
- Completed a cross-review for consistency, open questions, and missing tests.
- Added confirmed rules for keyword matching semantics, single-attempt manual retry, unique constraints, disabled-rule handling, missing-robot configuration failure, notification-permission guidance, and first-release no-dedup behavior.
- Recorded the sandbox issue affecting `apply_patch` during large doc updates.
- Reinitialized the local `git` repository for the project workspace.
- Wrote the first TDD test set for domain SMS processing behavior.
- Implemented `ProcessIncomingSmsUseCase` and supporting domain models to satisfy the first test set.
- Verified the new domain tests pass with `testDebugUnitTest`.
- Documented local build environment workarounds: `GRADLE_USER_HOME`, `ANDROID_USER_HOME`, `KOTLIN_USER_HOME`, and local JDK 17 toolchain override.
- Wrote and passed a second TDD test set for in-memory repository behavior covering unique robot names, unique sender numbers, robot associations, and robot updates.
- Added Room dependencies, KSP integration, and AGP compatibility configuration.
- Wrote and passed a third TDD test set for Room entity and aggregate mapping behavior.
- Implemented Room entities, cross-reference model, DAOs, database skeleton, processing entities, keyword codec, and Room-backed repository wrappers.
- Added a configuration summary use case and test coverage for rule-to-robot display logic.
- Replaced the default template activity with a Room-backed Compose configuration workbench for adding robots and sender rules and viewing the current configuration snapshot.
- Wired `AppContainer` and `MySmsCodeApplication` so the app now initializes the Room database and repositories at runtime.
- Added `CreateProcessingOutcomeUseCase` and test coverage for translating processing results into persisted record/attempt drafts.
- Added `MonitoringForegroundService`, `IncomingSmsReceiver`, and processing persistence repository wiring so incoming SMS can enter the foreground-service pipeline and be stored.
- Added `WebhookDispatcher` test coverage and made webhook posting injectable and result-aware.
- Added `FinalizeForwardingOutcomeUseCase` so webhook success or failure is folded into final persisted record status in a single save.
- Extended processing models and Room attempt persistence with response code and response message fields.
- Updated the foreground service to execute real webhook dispatches and persist final `SUCCESS` or `FAILED` outcomes.
- Extended the Compose workbench with monitoring controls and a recent-history card backed by persisted SMS processing records.
- Added `RetryFailedAttemptUseCase` and unit tests for successful and failed single-channel retry semantics.
- Extended Room queries and repositories to surface only the latest retryable failed attempts and to append retry attempts while recomputing record-level status from all latest channel results.
- Added a foreground-service retry action so the app can re-dispatch a single failed channel attempt by id.
- Added a failed retry queue card to the Compose workbench so users can inspect retryable failures and trigger manual retry.
- Added `AutoRetryPolicyUseCase` and unit tests for retry backoff scheduling and retry exhaustion.
- Extended forward attempt models and persistence with `nextRetryAt` so failures can be scheduled rather than only recorded.
- Updated finalize and retry use cases to assign retry windows automatically when dispatches fail.
- Added Room queries for due retry attempts and updated the foreground service to poll and execute automatic retries while running.
- Reworked automatic retry from fixed constants to a persisted `RetryPolicyConfig`, with defaults of 10 / 30 / 60 seconds for verification-code scenarios.
- Added Room-backed settings persistence and a configuration card in the Compose workbench so retry intervals can be edited in-app.
- Updated the foreground service to read the saved retry policy before scheduling or executing retries.
- Extended the failed retry queue UI to show the next automatic retry timestamp for each retryable channel.
- Ran the full `testDebugUnitTest` suite successfully after the configurable retry policy changes.
- Added a retry-attempt presentation helper so the failed retry queue can show readable local timestamps, completed retry counts, and whether automatic retries are scheduled, exhausted, or disabled.
- Added a history-record presentation helper so the recent history card now shows readable local timestamps plus user-facing status and source labels.
- Added presentation-layer filters for recent history and failed retry queues so records can be narrowed by status without changing repository queries.
- Added a simulation injection card to the workbench and routed injected messages through the same foreground-service pipeline with source = Simulation.
- Added a simulation feedback plan so injection now updates the status bar immediately and auto-refreshes the workbench shortly after enqueueing a test SMS.
- Wrote the first layered test report documenting current automated coverage, pending Android/device validation, and next end-to-end verification steps.
- Built a fresh debug APK and wrote a manual walkthrough checklist covering simulation injection, configuration, retry verification, and remaining device-level validation items.
- Added a permission-status presentation model and unit tests covering missing SMS permission, missing notification permission, and pre-Tiramisu behavior.
- Extended the Compose workbench with a runtime permission card, permission-state refresh, and start-monitoring gating so fresh installs cannot silently fail.
- Added targeted debug trace logging around SMS receipt, service startup, processing decisions, persistence, and retry execution.
- Fixed the Android 16 foreground-service startup crash by declaring `android.permission.FOREGROUND_SERVICE_DATA_SYNC` in the manifest.
- Validated on the Medium_Phone_API_36.1 emulator that a fresh install shows the permission prompt flow, transitions to Permissions ready after approval, and enables the monitoring action.
- Validated with a real emulator SMS that the runtime-permission flow now leads into the real receive -> process -> persist pipeline without manual adb permission grants.
- Replaced the emulator test robot configuration with a live Feishu webhook and revalidated the existing `10690001` sender rule with keyword `code`.
- Verified the real Feishu end-to-end path on the emulator and later corrected the transport-only success assumption by parsing Feishu business response bodies.
- Added a webhook dispatcher regression test proving Feishu HTTP `200` plus an error body now returns `FAILED` instead of a false `SUCCESS`.
- Revalidated the live Feishu path with the required group keyword by sending `test verification code is 778899` and confirmed the newest persisted attempt recorded `response_code = 200` and `response_message = success`.

## 2026-03-18
- 引入 `libphonenumber`，将发送号码匹配升级为基于 E.164 的正式国际号码标准化，默认地区为 `CN`。
- 新增 `PhoneNumberNormalizer` 及对应单元测试，覆盖中国号码、本地号与国际号等场景。
- 在模拟器 `Medium_Phone_API_36.1` 上完成国际号码标准化回归：规则保存 `13608083211`，来信发送方使用 `+8613608083211`，系统成功命中并完成双通道转发。
- 重写阶段二测试报告为 UTF-8 中文版本，并补充 2026-03-18 的国际号码标准化验证证据。

## 2026-04-18
- 基于荣耀 200 Pro 实际保活问题，完成通用保活框架 + 荣耀 200 Pro 优先验证的保活优化设计方案。
- 结合当前前台服务实现、30 秒重试轮询现状，以及荣耀官方后台运行建议与 SmsForwarder 的公开保活思路，明确了恢复机制、精确调度、设置向导和诊断页四个优化方向。


- 实现了保活优化第 1 阶段恢复机制：新增监控持久化状态、开机恢复、升级恢复，以及用户主动停止后不自动恢复的边界。
- 实现了保活优化第 2 阶段第一版：移除前台服务 30 秒轮询重试，改为根据下一次最早重试时间进行精确调度，并新增重试闹钟接收器。
- 新增恢复与调度相关测试：MonitoringRecoveryPolicyTest、RetrySchedulingPlanTest；相关定向单测与 ssembleDebug 均通过。
- 实现了保活优化第 3 阶段第一版：在配置中心新增保活设置向导，展示监控启用、服务运行、电池优化豁免、通知权限状态，并提供电池设置、通知设置、应用详情、荣耀引导入口。
- 实现了保活优化第 4 阶段第一版：在配置中心新增保活状态诊断卡，展示最近一次监控启动、停止、恢复启动、恢复原因、最近短信记录与下一次自动重试时间。
- 本轮验证通过：定向单测 `MonitoringRecoveryPolicyTest`、`RetrySchedulingPlanTest` 与 `assembleDebug` 均成功，Kotlin daemon 仍存在权限回退告警但不影响结果。

