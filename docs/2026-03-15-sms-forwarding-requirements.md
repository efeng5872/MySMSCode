# SMS Forwarding App Requirements

## 1. Document Info
- Project: MySMSCode
- Date: 2026-03-15
- Status: Reviewed and confirmed for implementation baseline

## 2. Project Goal
Build an Android application that monitors incoming SMS messages from configured sender numbers, matches sender-specific keywords, and forwards matched messages to Feishu and WeCom group robots. The app must provide configuration UI, persistent foreground-service-based runtime, processing history, failure retry visibility, and a debug simulation channel.

## 3. Scope
### 3.1 In Scope
- Configure multiple monitored sender numbers in the app.
- Configure reusable Feishu and WeCom robot endpoints in the app.
- Configure sender-specific keyword lists.
- Enable or disable each sender rule independently.
- Run a foreground service with visible notification for stable background operation.
- Process only SMS messages from configured sender numbers.
- Record configured-number messages in local history.
- Mark non-matching configured-number messages as not matched.
- Forward matching messages to one or more selected robot endpoints.
- Record forwarding success, failure, retry count, and failure reason.
- Provide history and failed-retry records in the app.
- Provide manual retry for failed forwarding attempts.
- Provide a debug simulation channel for end-to-end testing without real SMS.

### 3.2 Out of Scope
- Recording SMS from unconfigured sender numbers.
- Acting as the default SMS app.
- Vendor-specific aggressive keep-alive hacks.
- Cloud sync, multi-device sync, or remote management.
- Encryption key management beyond standard HTTPS webhook transmission.
- SMS de-duplication logic in the first release.

## 4. User Roles
- Operator: configures robot endpoints and monitoring rules, starts service, reviews forwarding outcomes, and retries failed operations.
- Tester/Developer: uses the debug simulation channel to validate matching and forwarding flows.

## 5. Functional Requirements
### FR-01 Robot Management
- The app shall allow the user to create, edit, enable, disable, and delete robot endpoint configurations.
- Each robot endpoint shall include:
  - robot name
  - robot type
  - enabled status
  - webhook URL
- Robot endpoint names shall be unique within the app.

### FR-02 Rule Management
- The app shall allow the user to create, edit, enable, disable, and delete sender rules.
- Each sender rule shall include:
  - sender phone number
  - enabled status
  - keyword list
  - selected target robots from the configured robot list
- Sender rules shall be unique by sender phone number.

### FR-03 Foreground Service
- The app shall provide a foreground service that can be started by the user.
- The foreground service shall display a persistent notification describing monitoring status.
- The app shall show whether monitoring is active.
- The app shall provide user-visible guidance when foreground service notification requirements are not satisfied.

### FR-04 SMS Intake
- The app shall listen for incoming SMS messages.
- The app shall ignore SMS messages from sender numbers that are not configured.
- The app shall ignore SMS messages from sender rules that are disabled.
- The app shall persist SMS messages from configured and enabled sender numbers for history and processing.

### FR-05 Matching
- The app shall evaluate keywords using the sender rule associated with the sender number.
- The first release shall treat keyword matching as case-insensitive substring matching.
- If any configured keyword matches, the SMS shall be treated as matched.
- If none of the keywords match, the SMS record shall be saved with status `NOT_MATCHED`.
- If one or more keywords match, the app shall create forwarding work for the selected enabled robot endpoints.
- If a matched sender rule has no selected enabled robot endpoints, the app shall record a non-recoverable configuration failure.

### FR-06 Forwarding
- The app shall support forwarding to Feishu group robots.
- The app shall support forwarding to WeCom group robots.
- The app shall support associating multiple configured robots to the same sender rule.
- The forwarded payload shall contain enough information to identify sender, receive time, and SMS content.

### FR-07 Retry and Failure Handling
- The app shall retry recoverable forwarding failures.
- Each forwarding channel attempt shall track attempt count, timestamp, and result.
- After the retry limit is reached, the app shall mark the forwarding as failed.
- The user shall be able to review failed attempts in the app.
- The user shall be able to manually retry a single failed channel attempt.

### FR-08 History and Visibility
- The app shall provide an in-app history view for processed configured-number SMS messages.
- The app shall show per-record status including not matched, forwarding, success, failed, and retry information.
- The app shall show failure reasons when available.

### FR-09 Debug Simulation Channel
- The app shall provide a debug simulation entry point.
- The simulation channel shall allow injecting sender number and SMS body.
- The simulation channel shall reuse the same matching, forwarding, history, and retry pipeline as real SMS processing.
- The simulation channel may be limited to debug builds.

## 6. Non-Functional Requirements
### NFR-01 Maintainability
- Business logic shall be separated from Android framework code.
- Forwarding channels shall be abstracted to allow future expansion.

### NFR-02 Reliability
- SMS intake shall stay lightweight and avoid long-running network work in the broadcast handler.
- Forwarding work shall be recoverable through persisted state and retry.

### NFR-03 Privacy
- The app shall only store messages from configured sender numbers.
- The app shall avoid collecting unrelated SMS content.

### NFR-04 Observability
- The app shall expose service status and processing results in UI.
- Failures shall be diagnosable through stored error information.

## 7. Business Rules
- BR-01: Unconfigured sender numbers are ignored and not stored.
- BR-02: Disabled sender rules are treated as inactive and their messages are ignored and not stored.
- BR-03: Configured enabled sender numbers are stored whether or not the message matches keywords.
- BR-04: Matching is sender-specific rather than global.
- BR-05: Robot endpoints are configured globally and sender rules select from the configured robot list.
- BR-06: Retry applies per forwarding channel attempt.
- BR-07: The first release does not perform SMS de-duplication.

## 8. Assumptions
- Webhook endpoints are reachable from the device network.
- Robot webhook security requirements remain compatible with simple HTTPS POST integration in the first release.
- Runtime SMS permissions are granted by the operator.

## 9. Acceptance Criteria
- The user can maintain sender rules fully from the app UI.
- The user can maintain reusable Feishu and WeCom robot endpoints from the app UI.
- The foreground service can be started and shows active monitoring status.
- SMS from an unconfigured sender is ignored and not stored.
- SMS from a configured but disabled sender rule is ignored and not stored.
- SMS from a configured enabled sender with no keyword hit is stored as `NOT_MATCHED`.
- SMS from a configured enabled sender with a keyword hit creates forwarding attempts for the robots selected by that sender rule.
- A matched rule with no enabled selected robot is recorded as a configuration failure.
- Forwarding success and failure are visible in history.
- Recoverable failures retry up to the configured limit.
- A failed single channel attempt can be manually retried from the app.
- The debug simulation channel can execute the full processing flow without real SMS input.
