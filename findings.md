# Findings

## Project State
- Project root contains a standard Android Studio app template.
- `app/src/main/java/com/example/mysmscode/MainActivity.kt` still renders the default "Hello Android!" Compose screen.
- `app/src/main/AndroidManifest.xml` only declares the launcher activity and no SMS/network/background permissions yet.
- `app/build.gradle.kts` uses Compose, minSdk 29, targetSdk 36, Java 11.

## Initial Architectural Implications
- SMS listening will likely require a `BroadcastReceiver` plus app-side filtering logic.
- Keyword and sender configuration imply a local persistence layer, likely DataStore for simple structured settings.
- Message forwarding should be abstracted behind gateway clients so Feishu and WeCom can be enabled independently.
- Test strategy should separate pure matching logic from Android platform integration logic.

## External Reference: mobile-mcp
- Reviewed the public repository page and wiki for `mobile-next/mobile-mcp` on March 14, 2026.
- The project is an MCP-based mobile automation server that interacts with Android through ADB and UI Automator, and can run against emulators or physical devices.
- It is not a direct template for consumer Android app keep-alive behavior, but it does reinforce a service-oriented architecture with explicit device integration boundaries.
- Inference: for this project, the closest applicable pattern is a transparent, supportable background model such as `BroadcastReceiver` + optional foreground service + clear runtime status, instead of vendor-specific keep-alive hacks.

## 2026-04-22 集成测试稽核
- 当前 `testDebugUnitTest` 重新执行为绿灯，Claude 提到的“9 个单测失败”不适用于最新代码。
- 当前 `app/src/androidTest/java/com/example/mysmscode/ExampleInstrumentedTest.kt` 仍只有包名断言，Android 侧关键链路缺少自动化保护，这个问题属实。
- 现有 `androidTestImplementation` 只包含 `androidx.junit`、`espresso-core` 和 Compose UI test，能够支撑基础仪器测试，但要稳定验证 Receiver / Service / Room 链路，最好补一个最小测试注入入口，避免直接依赖真实数据库、Keystore 和网络。
- 当前 `MySmsCodeApplication` 与 `AppContainer` 紧耦合，`MonitoringForegroundService` 直接 new `WebhookDispatcher()`，后续若不加测试 seam，端到端集成测试会偏脆弱且难以隔离网络副作用。
- 已实施的最小测试 seam：
  - `MySmsCodeApplication.containerOverride`
  - `AppContainer` 支持注入测试数据库、`WebhookCipher`、`WebhookDispatching`
  - `MonitoringForegroundService` 改为走 `container.webhookDispatcher`
- 已新增的最小 Android 集成测试：
  - `MonitoringForegroundServiceIntegrationTest`
  - `MonitoringRecoveryIntegrationTest`
  - `RoomWebhookIntegrationTest`
  - `RuleConflictUiIntegrationTest`
- 当前验证结果：
  - `testDebugUnitTest` 成功
  - `:app:assembleDebug` 成功
  - `:app:assembleDebugAndroidTest` 成功
  - `:app:connectedDebugAndroidTest` 成功，当前 9 条 Android 集成测试均通过

## 2026-04-22 MainActivity 重构稽核
- Claude 关于 `MainActivity.kt` 模块边界开始变模糊的 review 属实。最新代码中，页面状态、权限处理、监控启停编排、规则/机器人编辑态与模拟注入反馈确实长期集中在单一大文件中。
- 当前最合适的优化方式不是立刻全面引入 ViewModel，而是先做“最小必要重构”：优先抽离规则编辑态、机器人编辑态、模拟注入编排，再视后续功能增长情况决定是否继续抽监控协调层或升级为 ViewModel。
- 已完成第一阶段落地：`RuleEditorState` 与 `RobotEditorState` 已从 `MainActivity.kt` 抽出，并通过新增单元测试锁定默认值、编辑回填、状态重置、号码模式切换与 webhook 重新录入提示等关键行为。
- 已继续完成第二阶段第一步：`SimulationCoordinator` 已从 `MainActivity.kt` 抽出，模拟注入编排现在通过独立协调层处理，页面只负责触发与展示结果。
- 已继续完成第二阶段第二步：`MonitoringCoordinator` 已从 `MainActivity.kt` 抽出，监控启停、运行态刷新与权限请求触发编排已集中到协调层，页面侧主要保留状态回填与 UI 触发。
