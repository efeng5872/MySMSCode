# SMS Forwarding App Test Report

## Scope
- Date: 2026-03-15
- Environment: Android emulator `Medium_Phone_API_36.1`
- App build: current debug APK on `master`
- Focus: runtime permission flow, real SMS receive pipeline, and live Feishu webhook validation with business-response parsing

## Summary
- Result: partial end-to-end success
- Runtime permission flow is verified on a fresh-install style path.
- Real incoming SMS from the emulator console is verified.
- The Feishu integration now parses the response body instead of trusting HTTP `200` alone.
- Live Feishu webhook delivery was revalidated with a message body containing the required `test` keyword and persisted as success.
- Live WeCom webhook validation is still pending because no WeCom test endpoint has been provided yet.

## Executed Checks

### 1. Fresh-Install Permission Flow
- Reset `RECEIVE_SMS`, `READ_SMS`, and `POST_NOTIFICATIONS` on the emulator app instance.
- Launched the app and confirmed the workbench showed `Permissions required`.
- Confirmed `Start Monitoring` was disabled before permission grant.
- Granted notifications permission, then SMS permissions, through the system dialogs.
- Confirmed the workbench updated to `Permissions ready` and allowed monitoring startup.

### 2. Foreground Service Startup
- Started monitoring from the workbench.
- Verified `MonitoringForegroundService` was active through `dumpsys activity services`.
- Confirmed the earlier Android 16 foreground-service crash did not recur after adding `android.permission.FOREGROUND_SERVICE_DATA_SYNC`.

### 3. Real SMS Receive Path
- Sent a real emulator SMS from sender `10690001`.
- Observed trace logs showing `incoming_sms`, `service_start`, and `processing_result` for the real broadcast path.

### 4. Feishu Business-Response Validation Fix
- Reproduced the product risk reported during review: HTTP success alone is not enough to prove the Feishu group accepted the message.
- Updated the webhook dispatcher so Feishu uses the response body business code (`code`) and message (`msg`) for final success/failure judgment.
- Added a unit test proving that HTTP `200` plus a Feishu error body now returns `FAILED`.

### 5. Live Feishu Webhook Success Revalidation
- Reused sender rule `10690001` with keyword `code`.
- Sent a real emulator SMS whose body contained the required Feishu group keyword:
  - `test verification code is 778899`
- Observed trace logs:
  - `incoming_sms sender=10690001 parts=1 length=32`
  - `processing_result source=REAL_SMS sender=10690001 status=PENDING_FORWARD attempts=1 keyword=code`
  - `persistence_result source=REAL_SMS sender=10690001 recordStatus=SUCCESS attempts=1 failure=n/a`
- Exported the post-run database snapshot and confirmed:
  - newest `sms_records` row: `SUCCESS`
  - newest `forward_attempts` row: `channel = FEISHU`, `status = SUCCESS`, `response_code = 200`, `response_message = success`

## Evidence
- Emulator `logcat` trace confirmed the processing pipeline reached persisted success after the `test`-containing SMS.
- Post-run database snapshot confirmed:
  - `sms_records.id = 6`, `sender_number = 10690001`, `message_body = test verification code is 778899`, `processing_status = SUCCESS`, `matched_keyword = code`
  - `forward_attempts.id = 18`, `sms_record_id = 6`, `channel = FEISHU`, `attempt_number = 1`, `status = SUCCESS`, `response_code = 200`, `response_message = success`

## Coverage Update Against Test Plan
- TC-03 Configured Sender Matched to Feishu Only: passed with live webhook and required group keyword in message body
- TC-11 Runtime Permission Guidance: passed on emulator
- Foreground service startup validation: passed on emulator
- Real SMS broadcast reception validation: passed on emulator
- Feishu business-response parsing regression: covered by updated unit test

## Remaining Gaps
- Live WeCom webhook success and failure behavior
- Multi-channel live forwarding with both Feishu and WeCom selected
- Physical-device SMS validation outside the emulator
- Deployment handbook should still wait until the remaining live-channel and device checks are complete

## Conclusion
- The app now has stronger evidence for the first-release happy path:
  - permissions granted
  - monitoring started
  - real SMS received
  - keyword matched
  - Feishu business response parsed correctly
  - live Feishu webhook accepted the `test`-containing message
  - success recorded durably in local persistence
