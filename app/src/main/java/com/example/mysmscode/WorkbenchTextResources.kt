package com.example.mysmscode

import android.content.Context
import androidx.annotation.StringRes
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SimulationInjectionValidation
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.validateSimulationInjection

fun buildPermissionUiState(
    context: Context,
    snapshot: AppPermissionSnapshot,
): PermissionUiState {
    return if (snapshot.canStartMonitoring) {
        PermissionUiState(
            title = context.getString(R.string.permission_title_ready),
            message = context.getString(R.string.permission_message_ready),
            actionLabel = context.getString(R.string.permission_action_ready),
            canStartMonitoring = true,
        )
    } else {
        PermissionUiState(
            title = context.getString(R.string.permission_title_required),
            message = context.getString(
                R.string.permission_message_required,
                missingPermissionLabels(context, snapshot).joinToString(),
            ),
            actionLabel = context.getString(R.string.permission_action_request),
            canStartMonitoring = false,
        )
    }
}

fun missingPermissionLabels(context: Context, snapshot: AppPermissionSnapshot): List<String> = buildList {
    if (!snapshot.receiveSmsGranted) add(context.getString(R.string.permission_receive_sms))
    if (snapshot.notificationPermissionRequired && !snapshot.postNotificationsGranted) {
        add(context.getString(R.string.permission_notifications))
    }
}

fun validateSimulationInjectionInput(
    context: Context,
    senderNumber: String,
    messageBody: String,
): SimulationInjectionValidation {
    return validateSimulationInjection(
        senderNumber = senderNumber,
        messageBody = messageBody,
    )
}

@StringRes
fun historyFilterLabelRes(option: HistoryFilterOption): Int = when (option) {
    HistoryFilterOption.ALL -> R.string.history_filter_all
    HistoryFilterOption.SUCCESS -> R.string.history_filter_success
    HistoryFilterOption.FAILED -> R.string.history_filter_failed
    HistoryFilterOption.NOT_MATCHED -> R.string.history_filter_not_matched
    HistoryFilterOption.CONFIGURATION_FAILED -> R.string.history_filter_configuration_failed
}

@StringRes
fun failedRetryFilterLabelRes(option: FailedRetryFilterOption): Int = when (option) {
    FailedRetryFilterOption.ALL -> R.string.failed_filter_all
    FailedRetryFilterOption.SCHEDULED -> R.string.failed_filter_scheduled
    FailedRetryFilterOption.EXHAUSTED -> R.string.failed_filter_exhausted
    FailedRetryFilterOption.NON_RECOVERABLE -> R.string.failed_filter_non_recoverable
}

@StringRes
fun smsStatusLabelRes(status: String): Int = when (status) {
    "NOT_MATCHED" -> R.string.record_status_not_matched
    "PENDING_FORWARD" -> R.string.record_status_pending_forward
    "CONFIGURATION_FAILED" -> R.string.record_status_configuration_failed
    "SUCCESS" -> R.string.record_status_success
    "FAILED" -> R.string.record_status_failed
    else -> error("Unknown status: $status")
}

@StringRes
fun smsSourceLabelRes(source: String): Int = when (source) {
    "REAL_SMS" -> R.string.record_source_real_sms
    "SIMULATION" -> R.string.record_source_simulation
    else -> error("Unknown source: $source")
}

fun historyFilterLabel(context: Context, option: HistoryFilterOption): String =
    context.getString(historyFilterLabelRes(option))

fun failedRetryFilterLabel(context: Context, option: FailedRetryFilterOption): String =
    context.getString(failedRetryFilterLabelRes(option))

fun smsStatusLabel(context: Context, record: SmsRecordPreview): String =
    smsStatusLabelResOrNull(record.status)?.let(context::getString) ?: record.status

fun smsSourceLabel(context: Context, record: SmsRecordPreview): String =
    smsSourceLabelResOrNull(record.source)?.let(context::getString) ?: record.source

fun retryStatusLabel(context: Context, attempt: RetryableAttempt): String = when {
    attempt.nextRetryAt != null -> context.getString(R.string.retry_status_scheduled)
    !attempt.recoverable -> context.getString(R.string.retry_status_non_recoverable)
    else -> context.getString(R.string.retry_status_exhausted)
}

fun robotTypeLabel(context: Context, type: RobotType): String = when (type) {
    RobotType.FEISHU -> context.getString(R.string.robot_type_feishu)
    RobotType.WECOM -> context.getString(R.string.robot_type_wecom)
}

fun shortEnabledStateLabel(context: Context, enabled: Boolean): String =
    context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)

private fun smsStatusLabelResOrNull(status: String): Int? = when (status) {
    "NOT_MATCHED" -> R.string.record_status_not_matched
    "PENDING_FORWARD" -> R.string.record_status_pending_forward
    "CONFIGURATION_FAILED" -> R.string.record_status_configuration_failed
    "SUCCESS" -> R.string.record_status_success
    "FAILED" -> R.string.record_status_failed
    else -> null
}

private fun smsSourceLabelResOrNull(source: String): Int? = when (source) {
    "REAL_SMS" -> R.string.record_source_real_sms
    "SIMULATION" -> R.string.record_source_simulation
    else -> null
}
