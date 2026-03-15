# SMS Forwarding App Test Report

## Scope
- Date: 2026-03-15
- Environment: Android emulator `Medium_Phone_API_36.1`
- App build: current debug APK on `master`
- Focus: runtime permission flow, real SMS receive pipeline, and live Feishu webhook success validation

## Summary
- Result: partial end-to-end success
- Runtime permission flow is verified on a fresh-install style path.
- Real incoming SMS from the emulator console is verified.
- Live Feishu webhook delivery is verified with a successful HTTP response and persisted success status.
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
- Sent a real emulator SMS:
  - sender: `10690001`
  - body: `Your verification code is 445566`
- Observed trace logs:
  - `incoming_sms sender=10690001 parts=1 length=32`
  - `service_start action=com.example.mysmscode.action.PROCESS_SMS`
  - `processing_result source=REAL_SMS sender=10690001 status=PENDING_FORWARD attempts=1 keyword=code`

### 4. Live Feishu Webhook Success
- Updated the configured Feishu robot endpoint to use the provided live webhook.
- Reused the existing sender rule for `10690001` with keyword `code`.
- Observed trace log:
  - `persistence_result source=REAL_SMS sender=10690001 recordStatus=SUCCESS attempts=1 failure=n/a`
- Exported the post-run database snapshot and confirmed:
  - newest `sms_records` row: `SUCCESS`
  - newest `forward_attempts` row: `channel = FEISHU`, `status = SUCCESS`, `response_code = 200`, `response_message = OK`

## Evidence
- Emulator `logcat` trace confirmed the processing pipeline reached persisted success.
- Post-run database snapshot confirmed:
  - `sms_records.id = 5`, `sender_number = 10690001`, `processing_status = SUCCESS`, `matched_keyword = code`
  - `forward_attempts.id = 17`, `sms_record_id = 5`, `channel = FEISHU`, `attempt_number = 1`, `status = SUCCESS`, `response_code = 200`, `response_message = OK`

## Coverage Update Against Test Plan
- TC-03 Configured Sender Matched to Feishu Only: passed with live webhook
- TC-11 Runtime Permission Guidance: passed on emulator
- Foreground service startup validation: passed on emulator
- Real SMS broadcast reception validation: passed on emulator

## Remaining Gaps
- Live WeCom webhook success and failure behavior
- Multi-channel live forwarding with both Feishu and WeCom selected
- Physical-device SMS validation outside the emulator
- Deployment handbook should still wait until the remaining live-channel and device checks are complete

## Conclusion
- The app now has verified evidence for the most important first-release path:
  - permissions granted
  - monitoring started
  - real SMS received
  - keyword matched
  - live Feishu webhook delivered successfully
  - success recorded durably in local persistence
