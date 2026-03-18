package com.example.mysmscode

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.BuildConfigurationSummaryUseCase
import com.example.mysmscode.domain.ConfigurationRuleSummary
import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import com.example.mysmscode.domain.MonitoringControlTransition
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionValidation
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.buildMonitoringControlState
import com.example.mysmscode.domain.buildMonitoringDashboard
import com.example.mysmscode.domain.completedRetryCount
import com.example.mysmscode.domain.formatRetryTimestamp
import com.example.mysmscode.domain.resolveMonitoringStatusMessage
import com.example.mysmscode.domain.receivedAtLabel
import com.example.mysmscode.ui.theme.MySMSCodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MySMSCodeTheme {
                ConfigurationWorkbench((application as MySmsCodeApplication).container)
            }
        }
    }
}

@Composable
private fun ConfigurationWorkbench(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val summaryUseCase = remember { BuildConfigurationSummaryUseCase() }
    val context = LocalContext.current

    var robots by remember { mutableStateOf(emptyList<RobotEndpoint>()) }
    var rules by remember { mutableStateOf(emptyList<SenderRule>()) }
    var recentRecords by remember { mutableStateOf(emptyList<SmsRecordPreview>()) }
    var failedAttempts by remember { mutableStateOf(emptyList<RetryableAttempt>()) }
    var retryPolicyConfig by remember { mutableStateOf(RetryPolicyConfig.default()) }
    var permissionSnapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    var monitoringServiceRunning by remember { mutableStateOf(readMonitoringServiceRunning(context)) }
    var monitoringTransition by remember { mutableStateOf(MonitoringControlTransition.IDLE) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.status_ready)) }
    var currentPage by rememberSaveable { mutableStateOf(WorkbenchPage.HOME.name) }

    var robotName by rememberSaveable { mutableStateOf("") }
    var robotWebhook by rememberSaveable { mutableStateOf("") }
    var robotEnabled by rememberSaveable { mutableStateOf(true) }
    var robotType by rememberSaveable { mutableStateOf(RobotType.FEISHU) }

    var senderNumber by rememberSaveable { mutableStateOf("") }
    var simulationSenderNumber by rememberSaveable { mutableStateOf("") }
    var simulationMessageBody by rememberSaveable { mutableStateOf("") }
    var keywordText by rememberSaveable { mutableStateOf("") }
    var ruleEnabled by rememberSaveable { mutableStateOf(true) }
    val selectedRobotIds = remember { mutableStateListOf<Long>() }

    var firstRetryDelayText by rememberSaveable { mutableStateOf("10") }
    var secondRetryDelayText by rememberSaveable { mutableStateOf("30") }
    var thirdRetryDelayText by rememberSaveable { mutableStateOf("60") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionSnapshot = readPermissionSnapshot(context)
        statusMessage = if (permissionSnapshot.canStartMonitoring) {
            context.getString(R.string.status_permissions_granted)
        } else {
            context.getString(
                R.string.status_permissions_missing,
                missingPermissionLabels(context, permissionSnapshot).joinToString(),
            )
        }
    }

    suspend fun reloadData() {
        isLoading = true
        val reloadedRobots = withContext(Dispatchers.IO) { container.robotRepository.getAll() }
        val reloadedRules = withContext(Dispatchers.IO) { container.senderRuleRepository.getAll() }
        val reloadedRecentRecords = withContext(Dispatchers.IO) { container.processingRepository.getRecentRecords(limit = 10) }
        val reloadedFailedAttempts = withContext(Dispatchers.IO) { container.processingRepository.getRetryableFailedAttempts(limit = 20) }
        val reloadedRetryPolicyConfig = withContext(Dispatchers.IO) { container.settingsRepository.getRetryPolicyConfig() }
        robots = reloadedRobots
        rules = reloadedRules
        recentRecords = reloadedRecentRecords
        failedAttempts = reloadedFailedAttempts
        retryPolicyConfig = reloadedRetryPolicyConfig
        permissionSnapshot = readPermissionSnapshot(context)
        monitoringServiceRunning = readMonitoringServiceRunning(context)
        firstRetryDelayText = reloadedRetryPolicyConfig.firstRetryDelaySeconds.toString()
        secondRetryDelayText = reloadedRetryPolicyConfig.secondRetryDelaySeconds.toString()
        thirdRetryDelayText = reloadedRetryPolicyConfig.thirdRetryDelaySeconds.toString()
        isLoading = false
    }

    suspend fun syncMonitoringState(expectedRunning: Boolean) {
        repeat(8) {
            val isRunning = readMonitoringServiceRunning(context)
            monitoringServiceRunning = isRunning
            if (isRunning == expectedRunning) {
                return
            }
            delay(250L)
        }
        monitoringServiceRunning = readMonitoringServiceRunning(context)
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    val permissionUiState = remember(context, permissionSnapshot) {
        buildPermissionUiState(context, permissionSnapshot)
    }
    val summaries = remember(rules, robots) {
        summaryUseCase.build(rules = rules, robots = robots)
    }
    val monitoringControlState = remember(monitoringServiceRunning, monitoringTransition) {
        buildMonitoringControlState(
            isServiceRunning = monitoringServiceRunning,
            transition = monitoringTransition,
        )
    }
    val dashboard = remember(recentRecords, failedAttempts) {
        buildMonitoringDashboard(
            records = recentRecords,
            failedAttempts = failedAttempts,
            recentRecordLimit = HOME_RECENT_RECORD_LIMIT,
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WorkbenchHeader(
                currentPage = WorkbenchPage.valueOf(currentPage),
                onNavigate = { page -> currentPage = page.name },
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            when (WorkbenchPage.valueOf(currentPage)) {
                WorkbenchPage.HOME -> {
                    MonitoringStatusCard(
                        isLoading = isLoading,
                        controlState = monitoringControlState,
                        permissionUiState = permissionUiState,
                        onToggleMonitoring = {
                            scope.launch {
                                when {
                                    monitoringServiceRunning -> {
                                        monitoringTransition = MonitoringControlTransition.STOPPING
                                        statusMessage = context.getString(R.string.status_monitoring_stopping)
                                        MonitoringForegroundService.stopMonitoring(context)
                                        syncMonitoringState(expectedRunning = false)
                                        statusMessage = resolveMonitoringStatusMessage(
                                            transition = MonitoringControlTransition.STOPPING,
                                            isServiceRunning = monitoringServiceRunning,
                                            requestMessage = context.getString(R.string.status_monitoring_stopping),
                                            completedMessage = context.getString(R.string.status_monitoring_stopped),
                                            fallbackMessage = context.getString(R.string.status_monitoring_stop_failed),
                                        )
                                        monitoringTransition = MonitoringControlTransition.IDLE
                                        reloadData()
                                    }

                                    !permissionUiState.canStartMonitoring -> {
                                        statusMessage = permissionUiState.message
                                    }

                                    else -> {
                                        monitoringTransition = MonitoringControlTransition.STARTING
                                        statusMessage = context.getString(R.string.status_monitoring_requested)
                                        MonitoringForegroundService.startMonitoring(context)
                                        syncMonitoringState(expectedRunning = true)
                                        statusMessage = resolveMonitoringStatusMessage(
                                            transition = MonitoringControlTransition.STARTING,
                                            isServiceRunning = monitoringServiceRunning,
                                            requestMessage = context.getString(R.string.status_monitoring_requested),
                                            completedMessage = context.getString(R.string.status_monitoring_started),
                                            fallbackMessage = context.getString(R.string.status_monitoring_start_failed),
                                        )
                                        monitoringTransition = MonitoringControlTransition.IDLE
                                        reloadData()
                                    }
                                }
                            }
                        },
                        onRefresh = {
                            scope.launch { reloadData() }
                        },
                    )
                    FailureAlertCard(
                        isLoading = isLoading,
                        dashboard = dashboard,
                        onRetry = { attemptId ->
                            MonitoringForegroundService.retryFailedAttempt(context, attemptId)
                            statusMessage = context.getString(R.string.status_retry_requested, attemptId)
                        },
                    )
                    DashboardRecentHistoryCard(
                        isLoading = isLoading,
                        records = dashboard.recentRecords,
                    )
                }

                WorkbenchPage.CONFIG -> {
                    PermissionCard(
                        uiState = permissionUiState,
                        onRequestPermissions = {
                            permissionLauncher.launch(requiredPermissions(permissionSnapshot))
                        },
                    )
                    RetryPolicyCard(
                        firstRetryDelayText = firstRetryDelayText,
                        onFirstRetryDelayChange = { firstRetryDelayText = it },
                        secondRetryDelayText = secondRetryDelayText,
                        onSecondRetryDelayChange = { secondRetryDelayText = it },
                        thirdRetryDelayText = thirdRetryDelayText,
                        onThirdRetryDelayChange = { thirdRetryDelayText = it },
                        onSave = {
                            scope.launch {
                                val firstDelay = firstRetryDelayText.toIntOrNull()
                                val secondDelay = secondRetryDelayText.toIntOrNull()
                                val thirdDelay = thirdRetryDelayText.toIntOrNull()
                                if (firstDelay == null || secondDelay == null || thirdDelay == null || firstDelay <= 0 || secondDelay <= 0 || thirdDelay <= 0) {
                                    statusMessage = context.getString(R.string.status_retry_policy_invalid)
                                    return@launch
                                }
                                val config = RetryPolicyConfig(
                                    firstRetryDelaySeconds = firstDelay,
                                    secondRetryDelaySeconds = secondDelay,
                                    thirdRetryDelaySeconds = thirdDelay,
                                )
                                withContext(Dispatchers.IO) {
                                    container.settingsRepository.saveRetryPolicyConfig(config)
                                }
                                statusMessage = context.getString(
                                    R.string.status_retry_policy_saved,
                                    firstDelay,
                                    secondDelay,
                                    thirdDelay,
                                )
                                reloadData()
                            }
                        },
                    )
                    RobotFormCard(
                        robotName = robotName,
                        onRobotNameChange = { robotName = it },
                        robotWebhook = robotWebhook,
                        onRobotWebhookChange = { robotWebhook = it },
                        robotEnabled = robotEnabled,
                        onRobotEnabledChange = { robotEnabled = it },
                        robotType = robotType,
                        onRobotTypeChange = { robotType = it },
                        onSave = {
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val result = withContext(Dispatchers.IO) {
                                    container.robotRepository.save(
                                        RobotEndpoint(
                                            name = robotName.trim(),
                                            type = robotType,
                                            enabled = robotEnabled,
                                            webhookUrl = robotWebhook.trim(),
                                            createdAt = now,
                                            updatedAt = now,
                                        )
                                    )
                                }
                                when (result) {
                                    is RepositorySaveResult.Success -> {
                                        robotName = ""
                                        robotWebhook = ""
                                        robotEnabled = true
                                        robotType = RobotType.FEISHU
                                        statusMessage = context.getString(R.string.status_robot_saved, result.value.name)
                                        reloadData()
                                    }

                                    RepositorySaveResult.DuplicateName -> {
                                        statusMessage = context.getString(R.string.status_robot_duplicate)
                                    }

                                    else -> {
                                        statusMessage = context.getString(R.string.status_robot_save_failed)
                                    }
                                }
                            }
                        }
                    )
                    RuleFormCard(
                        senderNumber = senderNumber,
                        onSenderNumberChange = { senderNumber = it },
                        keywordText = keywordText,
                        onKeywordTextChange = { keywordText = it },
                        ruleEnabled = ruleEnabled,
                        onRuleEnabledChange = { ruleEnabled = it },
                        robots = robots,
                        selectedRobotIds = selectedRobotIds,
                        onToggleRobot = { robotId, checked ->
                            if (checked) {
                                if (!selectedRobotIds.contains(robotId)) {
                                    selectedRobotIds.add(robotId)
                                }
                            } else {
                                selectedRobotIds.remove(robotId)
                            }
                        },
                        onSave = {
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val keywords = keywordText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                                val result = withContext(Dispatchers.IO) {
                                    container.senderRuleRepository.save(
                                        SenderRule(
                                            senderNumber = senderNumber.trim(),
                                            enabled = ruleEnabled,
                                            keywords = keywords,
                                            selectedRobotIds = selectedRobotIds.toList(),
                                            createdAt = now,
                                            updatedAt = now,
                                        )
                                    )
                                }
                                when (result) {
                                    is RepositorySaveResult.Success -> {
                                        senderNumber = ""
                                        keywordText = ""
                                        ruleEnabled = true
                                        selectedRobotIds.clear()
                                        statusMessage = context.getString(R.string.status_rule_saved, result.value.senderNumber)
                                        reloadData()
                                    }

                                    RepositorySaveResult.DuplicateSenderNumber -> {
                                        statusMessage = context.getString(R.string.status_rule_duplicate)
                                    }

                                    else -> {
                                        statusMessage = context.getString(R.string.status_rule_save_failed)
                                    }
                                }
                            }
                        }
                    )
                    ConfigurationSummaryCard(robots = robots, summaries = summaries)
                    SimulationInjectionCard(
                        senderNumber = simulationSenderNumber,
                        onSenderNumberChange = { simulationSenderNumber = it },
                        messageBody = simulationMessageBody,
                        onMessageBodyChange = { simulationMessageBody = it },
                        onInject = {
                            when (val validation = validateSimulationInjectionInput(context, simulationSenderNumber, simulationMessageBody)) {
                                is SimulationInjectionValidation.Invalid -> {
                                    statusMessage = validation.reason
                                }
                                is SimulationInjectionValidation.Valid -> {
                                    MonitoringForegroundService.enqueueSimulation(
                                        context = context,
                                        senderNumber = validation.request.senderNumber,
                                        messageBody = validation.request.messageBody,
                                    )
                                    simulationSenderNumber = ""
                                    simulationMessageBody = ""
                                    statusMessage = context.getString(
                                        R.string.status_simulation_enqueued,
                                        validation.request.senderNumber,
                                    )
                                    scope.launch {
                                        listOf(250L, 1500L).forEach { refreshDelay ->
                                            delay(refreshDelay)
                                            reloadData()
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun readMonitoringServiceRunning(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return activityManager.getRunningServices(Int.MAX_VALUE).any { serviceInfo ->
        serviceInfo.service.className == MonitoringForegroundService::class.java.name && serviceInfo.foreground
    }
}

private fun readPermissionSnapshot(context: Context): AppPermissionSnapshot {
    val notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    return AppPermissionSnapshot(
        receiveSmsGranted = isGranted(Manifest.permission.RECEIVE_SMS),
        readSmsGranted = isGranted(Manifest.permission.READ_SMS),
        postNotificationsGranted = if (notificationRequired) isGranted(Manifest.permission.POST_NOTIFICATIONS) else true,
        notificationPermissionRequired = notificationRequired,
    )
}

private fun requiredPermissions(snapshot: AppPermissionSnapshot): Array<String> = buildList {
    if (!snapshot.receiveSmsGranted) add(Manifest.permission.RECEIVE_SMS)
    if (!snapshot.readSmsGranted) add(Manifest.permission.READ_SMS)
    if (snapshot.notificationPermissionRequired && !snapshot.postNotificationsGranted) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
@Composable
private fun PermissionCard(
    uiState: PermissionUiState,
    onRequestPermissions: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.permission_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(uiState.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(uiState.message, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRequestPermissions,
                enabled = !uiState.canStartMonitoring,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(uiState.actionLabel)
            }
        }
    }
}

@Composable
private fun StatusCard(
    isLoading: Boolean,
    robotCount: Int,
    ruleCount: Int,
    recentRecordCount: Int,
    failedRetryCount: Int,
    retryPolicyConfig: RetryPolicyConfig,
    canStartMonitoring: Boolean,
    onStartMonitoring: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.snapshot_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.snapshot_robots, robotCount))
                Text(stringResource(R.string.snapshot_rules, ruleCount))
                Text(stringResource(R.string.snapshot_records, recentRecordCount))
                Text(stringResource(R.string.snapshot_failed, failedRetryCount))
                Text(
                    stringResource(
                        R.string.snapshot_retry_policy,
                        retryPolicyConfig.firstRetryDelaySeconds,
                        retryPolicyConfig.secondRetryDelaySeconds,
                        retryPolicyConfig.thirdRetryDelaySeconds,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartMonitoring, enabled = canStartMonitoring) {
                    Text(stringResource(R.string.action_start_monitoring))
                }
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        }
    }
}

@Composable
private fun RetryPolicyCard(
    firstRetryDelayText: String,
    onFirstRetryDelayChange: (String) -> Unit,
    secondRetryDelayText: String,
    onSecondRetryDelayChange: (String) -> Unit,
    thirdRetryDelayText: String,
    onThirdRetryDelayChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.retry_policy_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.retry_policy_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = firstRetryDelayText,
                onValueChange = onFirstRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.retry_policy_first_delay)) },
            )
            OutlinedTextField(
                value = secondRetryDelayText,
                onValueChange = onSecondRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.retry_policy_second_delay)) },
            )
            OutlinedTextField(
                value = thirdRetryDelayText,
                onValueChange = onThirdRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.retry_policy_third_delay)) },
            )
            Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.retry_policy_save))
            }
        }
    }
}

@Composable
private fun RobotFormCard(
    robotName: String,
    onRobotNameChange: (String) -> Unit,
    robotWebhook: String,
    onRobotWebhookChange: (String) -> Unit,
    robotEnabled: Boolean,
    onRobotEnabledChange: (Boolean) -> Unit,
    robotType: RobotType,
    onRobotTypeChange: (RobotType) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.robot_form_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = robotName,
                onValueChange = onRobotNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.robot_name_label)) },
                placeholder = { Text(stringResource(R.string.robot_name_placeholder)) },
            )
            OutlinedTextField(
                value = robotWebhook,
                onValueChange = onRobotWebhookChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.robot_webhook_label)) },
                placeholder = { Text("https://...") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.robot_type_label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.FEISHU, onClick = { onRobotTypeChange(RobotType.FEISHU) })
                    Text(stringResource(R.string.robot_type_feishu))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.WECOM, onClick = { onRobotTypeChange(RobotType.WECOM) })
                    Text(stringResource(R.string.robot_type_wecom))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = robotEnabled, onCheckedChange = onRobotEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(if (robotEnabled) R.string.robot_enabled else R.string.robot_disabled))
            }
            Button(
                onClick = onSave,
                enabled = robotName.isNotBlank() && robotWebhook.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.robot_save))
            }
        }
    }
}

@Composable
private fun RuleFormCard(
    senderNumber: String,
    onSenderNumberChange: (String) -> Unit,
    keywordText: String,
    onKeywordTextChange: (String) -> Unit,
    ruleEnabled: Boolean,
    onRuleEnabledChange: (Boolean) -> Unit,
    robots: List<RobotEndpoint>,
    selectedRobotIds: List<Long>,
    onToggleRobot: (Long, Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.rule_form_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_sender_label)) },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = keywordText,
                onValueChange = onKeywordTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_keyword_label)) },
                placeholder = { Text("code, otp, verification") },
                supportingText = { Text(stringResource(R.string.rule_keyword_support)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ruleEnabled, onCheckedChange = onRuleEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(if (ruleEnabled) R.string.rule_enabled else R.string.rule_disabled))
            }
            Text(
                stringResource(R.string.rule_select_robot),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (robots.isEmpty()) {
                Text(stringResource(R.string.rule_no_robot))
            } else {
                robots.forEach { robot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selectedRobotIds.contains(robot.id),
                                onValueChange = { checked -> onToggleRobot(robot.id, checked) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedRobotIds.contains(robot.id),
                            onCheckedChange = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(robot.name)
                            Text(
                                text = "${robotTypeLabel(context, robot.type)} - ${shortEnabledStateLabel(context, robot.enabled)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = senderNumber.isNotBlank() && keywordText.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.rule_save))
            }
        }
    }
}

@Composable
private fun SimulationInjectionCard(
    senderNumber: String,
    onSenderNumberChange: (String) -> Unit,
    messageBody: String,
    onMessageBodyChange: (String) -> Unit,
    onInject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.simulation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.simulation_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.simulation_sender_label)) },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = messageBody,
                onValueChange = onMessageBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.simulation_body_label)) },
                placeholder = { Text("Your verification code is 123456") },
            )
            Button(
                onClick = onInject,
                enabled = senderNumber.isNotBlank() && messageBody.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.simulation_inject))
            }
        }
    }
}

@Composable
private fun ConfigurationSummaryCard(
    robots: List<RobotEndpoint>,
    summaries: List<ConfigurationRuleSummary>,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.configuration_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.configuration_summary_robots),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (robots.isEmpty()) {
                Text(stringResource(R.string.configuration_summary_no_robots))
            } else {
                robots.forEach { robot ->
                    Text("- ${robot.name} (${robotTypeLabel(context, robot.type)}) - ${shortEnabledStateLabel(context, robot.enabled)}")
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.configuration_summary_sender_rules),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (summaries.isEmpty()) {
                Text(stringResource(R.string.configuration_summary_no_rules))
            } else {
                summaries.forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.configuration_summary_keyword_line, summary.keywordPreview))
                        Text(stringResource(R.string.configuration_summary_robot_line, summary.robotNames.joinToString()))
                        Text(
                            stringResource(if (summary.enabled) R.string.rule_enabled else R.string.rule_disabled),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedRetryCard(
    attempts: List<RetryableAttempt>,
    selectedFilter: FailedRetryFilterOption,
    onFilterSelected: (FailedRetryFilterOption) -> Unit,
    onRetry: (Long) -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.failed_retry_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FilterChipRow(
                labels = FailedRetryFilterOption.entries.map { failedRetryFilterLabel(context, it) },
                selectedIndex = FailedRetryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(FailedRetryFilterOption.entries[it]) },
            )
            if (attempts.isEmpty()) {
                Text(stringResource(R.string.failed_retry_empty))
            } else {
                attempts.forEachIndexed { index, attempt ->
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
                            text = stringResource(R.string.failed_retry_error, attempt.lastErrorMessage.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
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
                    if (index != attempts.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentHistoryCard(
    records: List<SmsRecordPreview>,
    selectedFilter: HistoryFilterOption,
    onFilterSelected: (HistoryFilterOption) -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.recent_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FilterChipRow(
                labels = HistoryFilterOption.entries.map { historyFilterLabel(context, it) },
                selectedIndex = HistoryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(HistoryFilterOption.entries[it]) },
            )
            if (records.isEmpty()) {
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

@Composable
private fun FilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                label = { Text(label) },
            )
        }
    }
}






