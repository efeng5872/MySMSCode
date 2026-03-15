# SMS Forwarding App Manual Walkthrough Checklist

## 1. Scope
This checklist is for the current debug build and focuses on the app-level walkthrough that can be executed before real SMS carrier testing.

## 2. Build Artifact
- APK path: `D:\Android\project\MySMSCode\app\build\outputs\apk\debug\app-debug.apk`
- Build time: 2026-03-15 16:32:20 +08:00
- Build verification: `assembleDebug` passed successfully in the current workspace

## 3. Preconditions
- Install the debug APK on an emulator or physical Android device.
- Grant the app notification permission if prompted on Android 13+.
- Prepare at least one test Feishu or WeCom webhook if webhook dispatch needs to be verified.
- Keep network connectivity available if webhook dispatch is part of the run.

## 4. Walkthrough Steps
### Step 1: Launch And Snapshot
- Open the app.
- Expected:
  - workbench page loads successfully
  - current snapshot card is visible
  - robot, sender rule, history, and failed retry sections are visible

### Step 2: Add A Robot Endpoint
- Create one test robot endpoint.
- Recommended sample:
  - name: `QA Feishu`
  - type: `Feishu`
  - webhook: test webhook URL
- Expected:
  - save succeeds
  - configuration summary shows the new robot

### Step 3: Add A Sender Rule
- Create one sender rule that references the robot created above.
- Recommended sample:
  - sender number: `10690001`
  - keywords: `code,otp,verification`
- Expected:
  - save succeeds
  - sender rule appears in configuration summary

### Step 4: Start Monitoring
- Tap `Start Monitoring`.
- Expected:
  - status text updates
  - persistent notification appears

### Step 5: Simulation Injection - Positive Match
- In `Simulation Injection`, submit:
  - sender number: `10690001`
  - message body: `Your verification code is 123456`
- Expected:
  - status bar immediately reports simulation enqueued
  - workbench auto-refreshes shortly after submission
  - recent history shows a new `Simulation` record
  - if webhook is valid and reachable, record should end in `Forwarded successfully`
  - if webhook fails, failure should be visible in history and failed retry queue according to error type

### Step 6: Simulation Injection - Keyword Miss
- Submit:
  - sender number: `10690001`
  - message body: `Monthly account statement ready`
- Expected:
  - recent history shows a new record with `Keyword not matched`
  - no failed retry entry created

### Step 7: Simulation Injection - Unconfigured Sender
- Submit:
  - sender number: `95555`
  - message body: `Your verification code is 123456`
- Expected:
  - no history record created
  - no failed retry entry created

### Step 8: Failed Retry Queue Verification
- Cause a recoverable webhook failure if possible, for example by using a temporary unreachable webhook.
- Expected:
  - failed retry queue shows the channel entry
  - retry status, retry count, and next retry time are visible
  - filter chips can narrow the queue by `Scheduled`, `Exhausted`, or `Non-Recoverable`

### Step 9: Manual Retry Verification
- Tap `Retry This Channel` on a failed entry.
- Expected:
  - status bar updates
  - failed attempt history changes after refresh
  - record status recomputes based on latest channel results

### Step 10: History And Queue Filters
- Toggle history and failed-retry filters.
- Expected:
  - displayed items change without app restart
  - filter results remain consistent with visible statuses

## 5. Items Still Requiring Separate Validation
- real SMS broadcast receive on a physical device
- OEM/background restriction behavior over longer uptime
- Android 13+ permission edge cases across device variants
- real Feishu and WeCom webhook success/failure semantics against production-like endpoints

## 6. Output Recording Template
Record each run with:
- device model / emulator image
- Android version
- webhook type used
- step number
- actual result
- screenshots if behavior deviates
- logcat snippet if service or receiver behavior is unexpected