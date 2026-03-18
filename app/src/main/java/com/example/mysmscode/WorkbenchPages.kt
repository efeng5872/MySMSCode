package com.example.mysmscode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mysmscode.domain.MonitoringAction
import com.example.mysmscode.domain.MonitoringControlState
import com.example.mysmscode.domain.MonitoringDashboard
import com.example.mysmscode.domain.MonitoringRuntimeState
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.completedRetryCount
import com.example.mysmscode.domain.formatRetryTimestamp
import com.example.mysmscode.domain.receivedAtLabel

enum class WorkbenchPage {
    HOME,
    CONFIG,
}

const val HOME_RECENT_RECORD_LIMIT = 5
private const val HOME_FAILED_ATTEMPT_LIMIT = 3

@Composable
fun WorkbenchHeader(
    currentPage: WorkbenchPage,
    onNavigate: (WorkbenchPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (currentPage == WorkbenchPage.HOME) R.string.page_home_title else R.string.page_config_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = {
                onNavigate(
                    if (currentPage == WorkbenchPage.HOME) WorkbenchPage.CONFIG else WorkbenchPage.HOME,
                )
            },
        ) {
            Text(
                stringResource(
                    if (currentPage == WorkbenchPage.HOME) R.string.action_open_config else R.string.action_back_home,
                ),
            )
        }
    }
}

@Composable
fun MonitoringStatusCard(
    isLoading: Boolean,
    controlState: MonitoringControlState,
    permissionUiState: PermissionUiState,
    onToggleMonitoring: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.monitoring_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(
                    stringResource(
                        R.string.monitoring_runtime_line,
                        stringResource(controlRuntimeLabel(controlState.runtimeState)),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.monitoring_permission_line, permissionUiState.title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(controlRuntimeHint(controlState.runtimeState)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!permissionUiState.canStartMonitoring) {
                    Text(
                        permissionUiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onToggleMonitoring,
                    enabled = controlState.primaryActionEnabled &&
                        (permissionUiState.canStartMonitoring || controlState.primaryAction == MonitoringAction.STOP),
                ) {
                    Text(stringResource(controlActionLabel(controlState.primaryAction)))
                }
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        }
    }
}

@Composable
fun FailureAlertCard(
    isLoading: Boolean,
    dashboard: MonitoringDashboard,
    onRetry: (Long) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.failure_alert_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isLoading) {
                CircularProgressIndicator()
            } else if (!dashboard.shouldExpandFailureCard) {
                Text(
                    stringResource(R.string.failure_alert_collapsed_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.failure_alert_total, dashboard.failureSummary.totalCount))
                Text(stringResource(R.string.failure_alert_scheduled, dashboard.failureSummary.scheduledCount))
                Text(stringResource(R.string.failure_alert_exhausted, dashboard.failureSummary.exhaustedCount))
                Text(stringResource(R.string.failure_alert_non_recoverable, dashboard.failureSummary.nonRecoverableCount))
                dashboard.visibleFailedAttempts.forEach { attempt ->
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(attempt.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(attempt.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(
                                R.string.failed_retry_robot_line,
                                attempt.robotName,
                                robotTypeLabel(context, attempt.robotType),
                                attempt.attemptNumber,
                                attempt.completedRetryCount(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.failed_retry_status, retryStatusLabel(context, attempt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.failed_retry_next_time,
                                attempt.nextRetryAt?.let(::formatRetryTimestamp)
                                    ?: stringResource(R.string.failed_retry_no_schedule),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onRetry(attempt.attemptId) }) {
                            Text(stringResource(R.string.action_retry_channel))
                        }
                    }
                }
                if (dashboard.hiddenFailedAttemptCount > 0) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.failure_alert_more_hint, dashboard.hiddenFailedAttemptCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardRecentHistoryCard(
    isLoading: Boolean,
    records: List<SmsRecordPreview>,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.recent_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.recent_history_home_hint, HOME_RECENT_RECORD_LIMIT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isLoading) {
                CircularProgressIndicator()
            } else if (records.isEmpty()) {
                Text(stringResource(R.string.recent_history_empty))
            } else {
                records.forEachIndexed { index, record ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(record.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(
                                R.string.record_summary_line,
                                smsStatusLabel(context, record),
                                smsSourceLabel(context, record),
                                record.receivedAtLabel(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != records.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

private fun controlRuntimeLabel(runtimeState: MonitoringRuntimeState): Int = when (runtimeState) {
    MonitoringRuntimeState.STOPPED -> R.string.monitoring_runtime_stopped
    MonitoringRuntimeState.STARTING -> R.string.monitoring_runtime_starting
    MonitoringRuntimeState.RUNNING -> R.string.monitoring_runtime_running
    MonitoringRuntimeState.STOPPING -> R.string.monitoring_runtime_stopping
}

private fun controlRuntimeHint(runtimeState: MonitoringRuntimeState): Int = when (runtimeState) {
    MonitoringRuntimeState.STOPPED -> R.string.monitoring_runtime_hint_stopped
    MonitoringRuntimeState.STARTING -> R.string.monitoring_runtime_hint_starting
    MonitoringRuntimeState.RUNNING -> R.string.monitoring_runtime_hint_running
    MonitoringRuntimeState.STOPPING -> R.string.monitoring_runtime_hint_stopping
}

private fun controlActionLabel(action: MonitoringAction): Int = when (action) {
    MonitoringAction.START -> R.string.action_start_monitoring
    MonitoringAction.STARTING -> R.string.action_starting_monitoring
    MonitoringAction.STOP -> R.string.action_stop_monitoring
    MonitoringAction.STOPPING -> R.string.action_stopping_monitoring
}
