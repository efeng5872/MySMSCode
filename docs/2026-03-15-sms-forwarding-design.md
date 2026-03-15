# SMS Forwarding App Design

## 1. Document Info
- Project: MySMSCode
- Date: 2026-03-15
- Status: Reviewed and confirmed for implementation baseline

## 2. Design Summary
The application will use a three-stage processing pipeline:
- `BroadcastReceiver` for lightweight SMS intake
- persisted local task state for durable processing and history
- foreground service for steady background execution, forwarding, and retry orchestration

This structure separates time-sensitive system callbacks from network and retry work, improving reliability and debuggability.

## 3. Architecture
### 3.1 Runtime Flow
1. Foreground service starts and exposes monitoring status through a persistent notification.
2. Incoming SMS triggers `BroadcastReceiver`.
3. Receiver extracts sender number and body, then checks whether the sender is configured and enabled.
4. Unconfigured or disabled senders are ignored immediately.
5. Configured enabled sender messages are stored as `SmsRecord`.
6. Matching logic evaluates sender-specific keywords using case-insensitive substring matching.
7. Non-matching records are marked `NOT_MATCHED`.
8. Matching records create robot-specific forwarding attempts and become ready for forwarding.
9. If no enabled robot endpoint is selected, the record is marked as a configuration failure.
10. Foreground service processes pending attempts.
11. Success and failure details are stored for history and retry views.
12. Recoverable failures are rescheduled according to retry policy.
13. Failed channel attempts can be retried manually from the UI.

### 3.2 Layering
- `receiver`: SMS intake entry point.
- `service`: foreground service lifecycle, notification state, task consumption, retry scheduling.
- `data`: Room entities, DAO interfaces, repositories.
- `domain`: matching, processing, forwarding orchestration, retry decision logic.
- `network`: Feishu and WeCom clients behind a shared interface.
- `ui`: Compose screens for robot management, sender rule management, service status, history, failed attempts, and debug simulation.

## 4. Data Model
### 4.1 SenderRule
- `id: Long`
- `senderNumber: String`
- `enabled: Boolean`
- `keywords: List<String>` or normalized child table if needed later
- `createdAt: Long`
- `updatedAt: Long`

`senderNumber` is unique.

### 4.2 RobotEndpoint
- `id: Long`
- `name: String`
- `type: String`
  - `FEISHU`
  - `WECOM`
- `enabled: Boolean`
- `webhookUrl: String`
- `createdAt: Long`
- `updatedAt: Long`

`name` is unique.

### 4.3 SenderRuleRobotCrossRef
- `senderRuleId: Long`
- `robotEndpointId: Long`

### 4.4 SmsRecord
- `id: Long`
- `senderNumber: String`
- `messageBody: String`
- `receivedAt: Long`
- `matched: Boolean`
- `matchedKeyword: String?`
- `processingStatus: String`
- `failureReason: String?`
- `source: String`
  - `REAL_SMS`
  - `SIMULATION`

### 4.5 ForwardAttempt
- `id: Long`
- `smsRecordId: Long`
- `robotEndpointId: Long`
- `channel: String`
  - `FEISHU`
  - `WECOM`
- `attemptNumber: Int`
- `status: String`
  - `PENDING`
  - `SUCCESS`
  - `FAILED`
- `responseCode: String?`
- `responseMessage: String?`
- `attemptedAt: Long`
- `nextRetryAt: Long?`
- `recoverable: Boolean`

## 5. State Model
### 5.1 SMS Record Status
- `RECEIVED`
- `NOT_MATCHED`
- `PENDING_FORWARD`
- `FORWARDING`
- `SUCCESS`
- `FAILED`
- `CONFIGURATION_FAILED`

### 5.2 Forward Attempt Status
- `PENDING`
- `SUCCESS`
- `FAILED`

## 6. Core Components
### 6.1 SmsReceiver
- Parses SMS PDUs.
- Resolves sender number and message text.
- Performs minimal sender rule existence and enabled check.
- Persists relevant message records quickly.
- Triggers service processing.

### 6.2 ForegroundMonitorService
- Runs as a resident foreground service with visible notification.
- Exposes active/inactive state to UI.
- Consumes pending forwarding attempts.
- Applies retry policy and updates records.
- Provides a central point for service health visibility.
- Exposes notification/permission problems so the UI can guide the user.

### 6.3 Repositories
- `RobotEndpointRepository`
- `SenderRuleRepository`
- `SmsRecordRepository`
- `ForwardAttemptRepository`

Repositories isolate Room details from domain logic and make testing simpler.

### 6.4 Domain Use Cases
- `ProcessIncomingSmsUseCase`
- `MatchSmsUseCase`
- `CreateForwardAttemptsUseCase`
- `ExecuteForwardAttemptUseCase`
- `RetryFailedForwardUseCase`
- `RetrySingleFailedAttemptUseCase`
- `SimulateIncomingSmsUseCase`

### 6.5 Forwarding Clients
- `RobotForwarder`
- `FeishuRobotForwarder`
- `WeComRobotForwarder`

Each client converts a shared forwarding model into the target webhook payload format.

## 7. Retry Strategy
- Retry per channel attempt.
- Default retry limit: 3 attempts.
- Fixed retry schedule: 1 minute, 5 minutes, 15 minutes.
- Retry only recoverable failures such as network timeout or server error.
- Do not retry missing permission or invalid configuration failures.
- Manual retry is supported per failed single channel attempt.

## 8. Error Handling
### 8.1 Permission Errors
- Mark as non-recoverable.
- Surface user-facing message in UI and service status.

### 8.2 Configuration Errors
- Mark as non-recoverable.
- Keep failure reason visible in history and failed list.
- Use `CONFIGURATION_FAILED` when a matched rule has no enabled selected robot endpoints.

### 8.3 Network Errors
- Mark recoverable.
- Schedule retry until the retry limit is reached.

### 8.4 Remote API Business Errors
- Decide recoverability by response class.
- Invalid request or webhook configuration is non-recoverable.

## 9. UI Design Scope
- Robot list page
- Robot edit/create page
- Rule list page
- Rule edit/create page
- Service status page
- History page
- Failed attempts page
- Debug simulation page or debug-only entry

The UI will prioritize clear operational status over decorative complexity.

## 10. Simulation Channel Design
- Available at least in debug builds.
- Accepts sender number and SMS body input.
- Inserts a simulated `SmsRecord` with source `SIMULATION`.
- Reuses the exact same processing path after record creation.
- Supports validation of matching, forwarding, history, and retry without waiting for real SMS traffic.

## 11. Security and Privacy Considerations
- Store only SMS from configured sender numbers.
- Avoid logging webhook secrets in plaintext debug output.
- Use HTTPS-only webhook requests.
- Keep simulation separate from production entry points where appropriate.

## 12. Rule and Robot Relationship
- Robot endpoints are reusable global configurations.
- Sender rules reference one or more robot endpoints through selection UI instead of duplicating webhook details.
- Disabling a robot endpoint removes it from active forwarding without requiring each sender rule to be edited.

## 13. Implementation Notes
- Prefer Room for durable state.
- Prefer DataStore for lightweight service/UI preferences if needed.
- Use WorkManager only if later needed for deferred recovery outside service lifetime; first release will rely on the foreground service and persisted attempt state.
- The first release does not implement SMS de-duplication.
- Keep Android framework code thin and push behavior into testable domain classes.
