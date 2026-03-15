# SMS Forwarding App Test Plan

## 1. Document Info
- Project: MySMSCode
- Date: 2026-03-15
- Status: Reviewed and confirmed for implementation baseline

## 2. Test Objectives
- Verify sender-specific matching and forwarding behavior.
- Verify durable history and retry visibility.
- Verify reusable robot endpoint configuration and rule association behavior.
- Verify background execution model based on a foreground service.
- Verify the simulation channel covers the same processing path as real SMS.

## 3. Test Levels
### 3.1 Unit Tests
Scope:
- keyword matching
- sender rule filtering
- sender enabled/disabled handling
- sender rule to robot selection resolution
- forwarding channel selection
- retry timing calculation
- manual single-attempt retry behavior
- processing status transitions
- recoverable vs non-recoverable error classification

### 3.2 Data/Repository Tests
Scope:
- create, update, delete robot endpoint configurations
- enforce unique robot endpoint names
- create, update, delete sender rules
- enforce unique sender numbers
- persist sender rule and robot endpoint associations
- persist configured-number SMS records
- persist forwarding attempts
- query history list
- query failed-attempt list
- update retry counters and next retry times

### 3.3 Android Integration Tests
Scope:
- foreground service lifecycle and notification presence
- foreground service warning state when notification permission is missing on supported Android versions
- receiver intake path for configured sender records
- UI navigation and screen state
- permission-related warning states

### 3.4 Manual End-to-End Tests
Scope:
- real SMS flow on device
- simulated SMS flow in app
- webhook integration to test robot endpoints

## 4. Test Environment
- Android Studio latest stable environment available to the team
- Emulator for UI and simulation validation
- Physical Android device for real SMS verification
- Test Feishu and WeCom robot webhooks

## 5. Core Test Cases
### TC-01 Unconfigured Sender Ignored
- Input: real or simulated SMS from an unconfigured sender
- Expected: no database record, no forwarding attempt, no history entry

### TC-02 Configured Sender Not Matched
- Input: configured enabled sender SMS with no keyword hit
- Expected: `SmsRecord` stored, status `NOT_MATCHED`, no forwarding attempt

### TC-02A Disabled Sender Rule Ignored
- Input: SMS from a configured but disabled sender rule
- Expected: no database record, no forwarding attempt, no history entry

### TC-03 Configured Sender Matched to Feishu Only
- Input: configured enabled sender with matching keyword and a selected Feishu robot endpoint
- Expected: one Feishu attempt, success or failure recorded, no WeCom attempt

### TC-04 Configured Sender Matched to WeCom Only
- Input: configured enabled sender with matching keyword and a selected WeCom robot endpoint
- Expected: one WeCom attempt, success or failure recorded, no Feishu attempt

### TC-05 Configured Sender Matched to Both Channels
- Input: configured enabled sender with matching keyword and multiple selected robot endpoints
- Expected: one attempt per channel, both tracked independently

### TC-05A Disabled Robot Endpoint Not Used
- Input: configured sender references a robot endpoint that is disabled
- Expected: no new forwarding attempt is executed for the disabled robot, and the outcome is visible as a configuration-related condition if needed

### TC-05B Matched Rule Without Selected Enabled Robot
- Input: configured enabled sender with matching keyword and no selected enabled robot endpoint
- Expected: no forwarding attempt is created and the SMS record is marked as a non-recoverable configuration failure

### TC-06 Recoverable Failure Retries
- Input: simulated network timeout during forwarding
- Expected: retry schedule created, attempt count increments, final status reflects max retry outcome

### TC-07 Non-Recoverable Configuration Failure
- Input: empty or malformed webhook configuration
- Expected: failure recorded without retry

### TC-07A Reusable Robot Endpoint Update Propagates
- Input: update a globally configured robot webhook used by multiple sender rules
- Expected: subsequent forwarding attempts for all linked sender rules use the updated robot configuration

### TC-08 Foreground Service Status Visible
- Action: start monitoring
- Expected: persistent notification present and UI reflects active state

### TC-08A Notification Permission Guidance
- Action: start or resume monitoring on Android versions requiring notification permission when permission is denied
- Expected: the app exposes guidance that foreground-service-related notification requirements are not satisfied

### TC-09 History Visibility
- Action: process matched and unmatched configured sender messages
- Expected: history view shows both with correct statuses and timestamps

### TC-10 Failed Attempt Visibility
- Action: exhaust retries for a forwarding attempt
- Expected: failed attempts page shows final failure reason and retry count

### TC-10A Manual Single-Attempt Retry
- Action: manually retry one failed channel attempt
- Expected: only the selected failed channel attempt is retried and a new attempt result is recorded

### TC-11 Simulation Channel Parity
- Input: same sender and body through real SMS path and simulation path
- Expected: matching, forwarding, and persistence behavior are equivalent apart from source type

### TC-12 Keyword Matching Semantics
- Input: varying case combinations and messages containing one of multiple configured keywords
- Expected: matching uses case-insensitive substring logic with OR semantics

### TC-13 No De-Duplication in First Release
- Input: the same SMS event is processed twice
- Expected: both events are processed independently according to first-release rules

## 6. TDD Strategy for Implementation
- Write failing tests for pure domain behavior before implementation code.
- Build domain logic first:
  - rule matching
  - enabled/disabled rule filtering
  - rule-to-robot resolution
  - forwarding plan creation
  - retry policy
  - manual retry behavior
  - status transitions
- Add repository tests before Room-backed implementation details.
- Add Android integration tests after the basic domain flow is passing.

## 7. Entry and Exit Criteria
### Entry Criteria
- Requirements and design documents confirmed.
- Core domain interfaces identified.
- Test robot webhooks available.

### Exit Criteria
- Unit tests pass for matching, status transitions, and retry policy.
- Repository tests pass for robot, rule, and record persistence.
- App builds successfully in debug mode.
- Simulation flow passes end-to-end verification.
- At least one physical-device real SMS forwarding verification is completed or explicitly deferred.

## 8. Risks and Special Attention
- SMS behavior may vary by Android version and OEM.
- Broadcast handling timing must remain lightweight.
- Webhook rate limits or API response differences may affect retry behavior.
- Physical SMS verification may require carrier/device setup outside emulator support.
