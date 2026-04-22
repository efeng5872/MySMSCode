package com.example.mysmscode

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.ConfigurationRuleSummary
import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import com.example.mysmscode.domain.MonitoringControlTransition
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.MonitoringRecoveryTrigger
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.RuleSenderInputMode
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionRequest
import com.example.mysmscode.domain.SimulationInjectionValidation
import com.example.mysmscode.domain.buildSimulationFeedbackPlan
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.buildMonitoringControlState
import com.example.mysmscode.domain.buildMonitoringDashboard
import com.example.mysmscode.domain.completedRetryCount
import com.example.mysmscode.domain.resolveMonitoringStatusMessage
import com.example.mysmscode.domain.canDeleteRobot
import com.example.mysmscode.domain.buildSimulationRuleMismatchMessage
import com.example.mysmscode.domain.findInjectedSimulationRecord
import com.example.mysmscode.domain.findMatchingSimulationRule
import com.example.mysmscode.domain.shouldAutoRequestPermissions
import com.example.mysmscode.domain.buildRuleSenderNumber
import com.example.mysmscode.domain.defaultRuleNumberDraft
import com.example.mysmscode.domain.defaultCountryOption
import com.example.mysmscode.domain.findCountryOption
import com.example.mysmscode.domain.splitSenderNumberForEditing
import com.example.mysmscode.domain.supportedCountryOptions
import com.example.mysmscode.domain.formatRetryTimestamp
import com.example.mysmscode.domain.preloadCountryOptions
import com.example.mysmscode.domain.receivedAtLabel
import com.example.mysmscode.domain.requiresWebhookReentry
import com.example.mysmscode.domain.statusLabel
import com.example.mysmscode.domain.transformRuleNumberDraft
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
    val context = LocalContext.current
    val robotsFlow = remember(container) { container.robotRepository.observeAll() }
    val rulesFlow = remember(container) { container.senderRuleRepository.observeAll() }
    val recentRecordsFlow = remember(container) { container.processingRepository.observeRecentRecords(limit = 10) }
    val failedAttemptsFlow = remember(container) { container.processingRepository.observeRetryableFailedAttempts(limit = 20) }

    val robots by robotsFlow.collectAsState(initial = emptyList())
    val rules by rulesFlow.collectAsState(initial = emptyList())
    val recentRecords by recentRecordsFlow.collectAsState(initial = emptyList())
    val failedAttempts by failedAttemptsFlow.collectAsState(initial = emptyList())
    var retryPolicyConfig by remember { mutableStateOf(RetryPolicyConfig.default()) }
    var permissionSnapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    var monitoringServiceRunning by remember { mutableStateOf(readMonitoringServiceRunning(context)) }
    var monitoringPersistenceState by remember { mutableStateOf(MonitoringPersistenceState()) }
    var monitoringTransition by remember { mutableStateOf(MonitoringControlTransition.IDLE) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    var currentPage by rememberSaveable { mutableStateOf(WorkbenchPage.HOME.name) }
    var hasAutoRequestedPermissions by rememberSaveable { mutableStateOf(false) }
    var countryOptionsPreloaded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var robotName by rememberSaveable { mutableStateOf("") }
    var robotWebhook by rememberSaveable { mutableStateOf("") }
    var robotEnabled by rememberSaveable { mutableStateOf(true) }
    var robotType by rememberSaveable { mutableStateOf(RobotType.FEISHU) }
    var robotWebhookResetWarningVisible by rememberSaveable { mutableStateOf(false) }

    var ruleSenderInputMode by rememberSaveable { mutableStateOf(defaultRuleNumberDraft().inputMode.name) }
    var senderNumber by rememberSaveable { mutableStateOf("") }
    var rawSenderDisplay by rememberSaveable { mutableStateOf("") }
    var selectedCountryRegion by rememberSaveable { mutableStateOf(defaultCountryOption().regionCode) }
    var simulationSenderNumber by rememberSaveable { mutableStateOf("10654321") }
    var simulationMessageBody by rememberSaveable { mutableStateOf("test verification code is 223344") }
    var simulationStatusMessage by rememberSaveable { mutableStateOf("") }
    var keywordText by rememberSaveable { mutableStateOf("") }
    var ruleEnabled by rememberSaveable { mutableStateOf(true) }
    val selectedRobotIds = remember { mutableStateListOf<Long>() }

    var firstRetryDelayText by rememberSaveable { mutableStateOf("10") }
    var secondRetryDelayText by rememberSaveable { mutableStateOf("30") }
    var thirdRetryDelayText by rememberSaveable { mutableStateOf("60") }
    var retryPolicyExpanded by rememberSaveable { mutableStateOf(false) }
    var simulationExpanded by rememberSaveable { mutableStateOf(false) }
    var keepaliveGuideExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var retryPolicySectionOffset by remember { mutableStateOf(0) }
    var simulationSectionOffset by remember { mutableStateOf(0) }

    var editingRobotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingRobotCreatedAt by rememberSaveable { mutableStateOf(0L) }
    var showRobotDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRobotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var blockedRobotName by rememberSaveable { mutableStateOf<String?>(null) }

    var editingRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingRuleCreatedAt by rememberSaveable { mutableStateOf(0L) }
    var showRuleDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHonorKeepaliveGuide by rememberSaveable { mutableStateOf(false) }
    var keepaliveDialogMessage by rememberSaveable { mutableStateOf<String?>(null) }

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

    fun applyRetryPolicyDraft(config: RetryPolicyConfig) {
        retryPolicyConfig = config
        firstRetryDelayText = config.firstRetryDelaySeconds.toString()
        secondRetryDelayText = config.secondRetryDelaySeconds.toString()
        thirdRetryDelayText = config.thirdRetryDelaySeconds.toString()
    }

    suspend fun refreshRetryPolicyState() {
        val reloadedRetryPolicyConfig = withContext(Dispatchers.IO) { container.settingsRepository.getRetryPolicyConfig() }
        applyRetryPolicyDraft(reloadedRetryPolicyConfig)
    }

    suspend fun refreshMonitoringRuntimeState() {
        monitoringPersistenceState = withContext(Dispatchers.IO) { container.settingsRepository.getMonitoringState() }
        monitoringServiceRunning = readMonitoringServiceRunning(context)
    }

    fun refreshPermissionState() {
        permissionSnapshot = readPermissionSnapshot(context)
    }

    fun refreshPassiveStatusMessage() {
        statusMessage = reconcilePassiveMonitoringStatusMessage(
            context = context,
            currentMessage = statusMessage,
            isServiceRunning = monitoringServiceRunning,
            monitoringState = monitoringPersistenceState,
            transition = monitoringTransition,
        )
    }

    suspend fun refreshRuntimeState() {
        isLoading = true
        refreshRetryPolicyState()
        refreshMonitoringRuntimeState()
        refreshPermissionState()
        refreshPassiveStatusMessage()
        isLoading = false
    }

    suspend fun syncMonitoringState(expectedRunning: Boolean): MonitoringPersistenceState {
        var latestState = monitoringPersistenceState
        repeat(8) {
            val isRunning = readMonitoringServiceRunning(context)
            latestState = withContext(Dispatchers.IO) { container.settingsRepository.getMonitoringState() }
            monitoringServiceRunning = isRunning
            monitoringPersistenceState = latestState
            val persistenceMatches = if (expectedRunning) {
                latestState.monitoringEnabled && !latestState.stoppedByUser
            } else {
                !latestState.monitoringEnabled && latestState.stoppedByUser
            }
            if (isRunning == expectedRunning && persistenceMatches) {
                return latestState
            }
            delay(250L)
        }
        monitoringServiceRunning = readMonitoringServiceRunning(context)
        latestState = withContext(Dispatchers.IO) { container.settingsRepository.getMonitoringState() }
        monitoringPersistenceState = latestState
        return latestState
    }

    suspend fun performSimulationInjection(request: SimulationInjectionRequest) {
        val matchedRule = findMatchingSimulationRule(
            senderNumber = request.senderNumber,
            rules = rules,
        )
        if (matchedRule == null) {
            simulationStatusMessage = buildSimulationRuleMismatchMessage()
            return
        }
        val feedbackPlan = buildSimulationFeedbackPlan(request.senderNumber)
        val submittedAt = System.currentTimeMillis()
        simulationStatusMessage = feedbackPlan.submittedStatusMessage
        MonitoringForegroundService.enqueueSimulation(
            context = context,
            senderNumber = request.senderNumber,
            messageBody = request.messageBody,
        )
        if (feedbackPlan.navigateToHomeRecentRecords) {
            currentPage = WorkbenchPage.HOME.name
            scrollState.animateScrollTo(0)
        }
        delay(feedbackPlan.submittedStatusVisibleDelayMillis)
        simulationStatusMessage = feedbackPlan.matchedRuleStatusMessage
        var recordWritten = false
        for (delayMillis in feedbackPlan.refreshDelaysMillis) {
            delay(delayMillis)
            val injectedRecord = findInjectedSimulationRecord(
                records = recentRecords,
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                simulationStatusMessage = feedbackPlan.completedStatusMessage
                recordWritten = true
                break
            }
        }
        if (!recordWritten && feedbackPlan.finalRefreshBeforeTimeout) {
            val injectedRecord = findInjectedSimulationRecord(
                records = recentRecords,
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                simulationStatusMessage = feedbackPlan.completedStatusMessage
                recordWritten = true
            }
        }
        if (!recordWritten) {
            simulationStatusMessage = feedbackPlan.timeoutStatusMessage
        }
    }

    fun launchPermissionRequest() {
        val missingPermissions = requiredPermissions(permissionSnapshot)
        if (missingPermissions.isNotEmpty()) {
            hasAutoRequestedPermissions = true
            permissionLauncher.launch(missingPermissions)
        }
    }

    fun resetRobotEditor() {
        editingRobotId = null
        editingRobotCreatedAt = 0L
        robotName = ""
        robotWebhook = ""
        robotEnabled = true
        robotType = RobotType.FEISHU
        robotWebhookResetWarningVisible = false
        showRobotDialog = false
    }

    fun openRobotCreateDialog() {
        resetRobotEditor()
        showRobotDialog = true
    }

    fun openRobotEditDialog(robot: RobotEndpoint) {
        editingRobotId = robot.id
        editingRobotCreatedAt = robot.createdAt
        robotName = robot.name
        robotWebhook = if (robot.requiresWebhookReentry()) "" else robot.webhookUrl
        robotEnabled = robot.enabled
        robotType = robot.type
        robotWebhookResetWarningVisible = robot.requiresWebhookReentry()
        showRobotDialog = true
    }

    fun resetRuleEditor() {
        val defaultDraft = defaultRuleNumberDraft()
        editingRuleId = null
        editingRuleCreatedAt = 0L
        ruleSenderInputMode = defaultDraft.inputMode.name
        senderNumber = defaultDraft.localNumber
        rawSenderDisplay = defaultDraft.displaySender
        selectedCountryRegion = defaultDraft.countryOption.regionCode
        keywordText = ""
        ruleEnabled = true
        selectedRobotIds.clear()
        showRuleDialog = false
    }

    fun openRuleCreateDialog() {
        resetRuleEditor()
        showRuleDialog = true
    }

    fun openRuleEditDialog(rule: SenderRule) {
        val numberDraft = splitSenderNumberForEditing(rule.senderNumber, rule.senderMatchMode)
        editingRuleId = rule.id
        editingRuleCreatedAt = rule.createdAt
        ruleSenderInputMode = numberDraft.inputMode.name
        selectedCountryRegion = numberDraft.countryOption.regionCode
        senderNumber = numberDraft.localNumber
        rawSenderDisplay = numberDraft.displaySender
        keywordText = rule.keywords.joinToString()
        ruleEnabled = rule.enabled
        selectedRobotIds.clear()
        selectedRobotIds.addAll(rule.selectedRobotIds)
        showRuleDialog = true
    }
    LaunchedEffect(Unit) {
        refreshRuntimeState()
    }

    LaunchedEffect(permissionSnapshot, hasAutoRequestedPermissions) {
        if (permissionSnapshot.canStartMonitoring) {
            hasAutoRequestedPermissions = false
        } else if (shouldAutoRequestPermissions(
                snapshot = permissionSnapshot,
                hasRequestedAutomatically = hasAutoRequestedPermissions,
            )
        ) {
            launchPermissionRequest()
        }
    }

    LaunchedEffect(currentPage, countryOptionsPreloaded) {
        if (currentPage == WorkbenchPage.CONFIG.name && !countryOptionsPreloaded) {
            withContext(Dispatchers.Default) {
                preloadCountryOptions()
            }
            countryOptionsPreloaded = true
        }
    }

    val availableCountryOptions = remember { supportedCountryOptions() }
    val selectedCountryOption = remember(selectedCountryRegion) { findCountryOption(selectedCountryRegion) }
    val selectedRuleSenderInputMode = remember(ruleSenderInputMode) { RuleSenderInputMode.valueOf(ruleSenderInputMode) }

    val permissionUiState = remember(context, permissionSnapshot) {
        buildPermissionUiState(context, permissionSnapshot)
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WorkbenchHeader(
                currentPage = WorkbenchPage.valueOf(currentPage),
                onNavigate = { page -> currentPage = page.name },
            )
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                                        val requestedAt = System.currentTimeMillis()
                                        monitoringTransition = MonitoringControlTransition.STOPPING
                                        statusMessage = context.getString(R.string.status_monitoring_stopping)
                                        MonitoringForegroundService.stopMonitoring(context)
                                        syncMonitoringState(expectedRunning = false)
                                        if (!monitoringServiceRunning) {
                                            monitoringPersistenceState = monitoringPersistenceState.copy(
                                                monitoringEnabled = false,
                                                stoppedByUser = true,
                                                lastMonitoringStoppedAt = monitoringPersistenceState.lastMonitoringStoppedAt ?: requestedAt,
                                            )
                                        }
                                        statusMessage = resolveMonitoringStatusMessage(
                                            transition = MonitoringControlTransition.STOPPING,
                                            isServiceRunning = monitoringServiceRunning,
                                            requestMessage = context.getString(R.string.status_monitoring_stopping),
                                            completedMessage = context.getString(R.string.status_monitoring_stopped),
                                            fallbackMessage = context.getString(R.string.status_monitoring_stop_failed),
                                        )
                                        monitoringTransition = MonitoringControlTransition.IDLE
                                        refreshRuntimeState()
                                    }

                                    !permissionUiState.canStartMonitoring -> {
                                        statusMessage = permissionUiState.message
                                    }

                                    else -> {
                                        val requestedAt = System.currentTimeMillis()
                                        monitoringTransition = MonitoringControlTransition.STARTING
                                        statusMessage = context.getString(R.string.status_monitoring_requested)
                                        MonitoringForegroundService.startMonitoring(context)
                                        syncMonitoringState(expectedRunning = true)
                                        if (monitoringServiceRunning) {
                                            monitoringPersistenceState = monitoringPersistenceState.copy(
                                                monitoringEnabled = true,
                                                stoppedByUser = false,
                                                lastMonitoringStartedAt = monitoringPersistenceState.lastMonitoringStartedAt ?: requestedAt,
                                            )
                                        }
                                        statusMessage = resolveMonitoringStatusMessage(
                                            transition = MonitoringControlTransition.STARTING,
                                            isServiceRunning = monitoringServiceRunning,
                                            requestMessage = context.getString(R.string.status_monitoring_requested),
                                            completedMessage = context.getString(R.string.status_monitoring_started),
                                            fallbackMessage = context.getString(R.string.status_monitoring_start_failed),
                                        )
                                        monitoringTransition = MonitoringControlTransition.IDLE
                                        refreshRuntimeState()
                                    }
                                }
                            }
                        },
                        onRefresh = {
                            scope.launch { refreshRuntimeState() }
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
                    ConfigSectionLabel(title = stringResource(R.string.config_section_status))
                    PermissionCard(
                        uiState = permissionUiState,
                        onRequestPermissions = {
                            launchPermissionRequest()
                        },
                    )
                    CollapsibleSectionCard(
                        title = stringResource(R.string.keepalive_title),
                        expanded = keepaliveGuideExpanded,
                        onToggle = { keepaliveGuideExpanded = !keepaliveGuideExpanded },
                    ) {
                        KeepaliveGuideCard(
                            monitoringState = monitoringPersistenceState,
                            isServiceRunning = monitoringServiceRunning,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
                            notificationsReady = permissionSnapshot.postNotificationsGranted,
                            showHeader = false,
                            onOpenBatterySettings = {
                                if (isIgnoringBatteryOptimizations(context)) {
                                    keepaliveDialogMessage = context.getString(R.string.keepalive_battery_already_optimized)
                                    return@KeepaliveGuideCard
                                }
                                val opened = openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                ) || openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                ) || openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                )
                                if (!opened) {
                                    keepaliveDialogMessage = context.getString(R.string.keepalive_open_battery_settings_failed)
                                }
                            },
                            onOpenNotificationSettings = {
                                openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    },
                                ) || openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                )
                            },
                            onOpenAppDetails = {
                                openIntentSafely(
                                    context,
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                )
                            },
                            onOpenHonorGuide = { showHonorKeepaliveGuide = true },
                        )
                    }
                    CollapsibleSectionCard(
                        title = stringResource(R.string.diagnostics_title),
                        expanded = diagnosticsExpanded,
                        onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                    ) {
                        DiagnosticsCard(
                            monitoringState = monitoringPersistenceState,
                            latestRecord = recentRecords.firstOrNull(),
                            nextRetryAt = failedAttempts.mapNotNull { it.nextRetryAt }.minOrNull(),
                            showHeader = false,
                        )
                    }
                    ConfigSectionLabel(title = stringResource(R.string.config_section_core))
                    RuleManagementCard(
                        rules = rules,
                        robots = robots,
                        onAdd = { openRuleCreateDialog() },
                        onEdit = { rule -> openRuleEditDialog(rule) },
                    )
                    RobotManagementCard(
                        robots = robots,
                        onAdd = { openRobotCreateDialog() },
                        onEdit = { robot -> openRobotEditDialog(robot) },
                    )
                    ConfigSectionLabel(title = stringResource(R.string.config_section_advanced))
                    CollapsibleSectionCard(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            retryPolicySectionOffset = coordinates.positionInParent().y.toInt()
                        },
                        title = stringResource(R.string.retry_policy_title),
                        expanded = retryPolicyExpanded,
                        onToggle = {
                            val willExpand = !retryPolicyExpanded
                            retryPolicyExpanded = willExpand
                            if (willExpand) {
                                scope.launch {
                                    delay(120L)
                                    scrollState.animateScrollTo((retryPolicySectionOffset - 120).coerceAtLeast(0))
                                }
                            }
                        },
                    ) {
                        RetryPolicyCard(
                            firstRetryDelayText = firstRetryDelayText,
                            onFirstRetryDelayChange = { firstRetryDelayText = it },
                            secondRetryDelayText = secondRetryDelayText,
                            onSecondRetryDelayChange = { secondRetryDelayText = it },
                            thirdRetryDelayText = thirdRetryDelayText,
                            onThirdRetryDelayChange = { thirdRetryDelayText = it },
                            showHeader = false,
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
                                    refreshRuntimeState()
                                }
                            },
                        )
                    }
                    if (showRobotDialog) {
                        val referencedRuleCount = editingRobotId?.let { robotId ->
                            rules.count { it.selectedRobotIds.contains(robotId) }
                        } ?: 0
                        RobotEditorDialog(
                            isEditMode = editingRobotId != null,
                            robotName = robotName,
                            onRobotNameChange = { robotName = it },
                            robotWebhook = robotWebhook,
                            onRobotWebhookChange = {
                                robotWebhook = it
                                if (it.isNotBlank()) {
                                    robotWebhookResetWarningVisible = false
                                }
                            },
                            robotEnabled = robotEnabled,
                            onRobotEnabledChange = { robotEnabled = it },
                            robotType = robotType,
                            onRobotTypeChange = { robotType = it },
                            webhookWarningMessage = if (robotWebhookResetWarningVisible) {
                                context.getString(R.string.robot_webhook_reentry_dialog_message)
                            } else {
                                null
                            },
                            disableWarningMessage = if (editingRobotId != null && !robotEnabled && referencedRuleCount > 0) {
                                context.getString(R.string.robot_disable_in_use_warning, referencedRuleCount)
                            } else {
                                null
                            },
                            onDismiss = { resetRobotEditor() },
                            onSave = {
                                scope.launch {
                                    val isEditMode = editingRobotId != null
                                    val now = System.currentTimeMillis()
                                    val result = withContext(Dispatchers.IO) {
                                        val robot = RobotEndpoint(
                                            id = editingRobotId ?: 0L,
                                            name = robotName.trim(),
                                            type = robotType,
                                            enabled = robotEnabled,
                                            webhookUrl = robotWebhook.trim(),
                                            createdAt = if (editingRobotId == null) now else editingRobotCreatedAt,
                                            updatedAt = now,
                                        )
                                        if (editingRobotId == null) {
                                            container.robotRepository.save(robot)
                                        } else {
                                            container.robotRepository.update(robot)
                                        }
                                    }
                                    when (result) {
                                        is RepositorySaveResult.Success -> {
                                            val savedName = result.value.name
                                            resetRobotEditor()
                                            statusMessage = context.getString(
                                                if (isEditMode) R.string.status_robot_updated else R.string.status_robot_saved,
                                                savedName,
                                            )
                                        }
                                        RepositorySaveResult.DuplicateName -> {
                                            statusMessage = context.getString(R.string.status_robot_duplicate)
                                        }
                                        RepositorySaveResult.Failed -> {
                                            statusMessage = context.getString(R.string.status_robot_save_failed)
                                        }
                                        else -> {
                                            statusMessage = context.getString(R.string.status_robot_save_failed)
                                        }
                                    }
                                }
                            },
                            onDelete = editingRobotId?.let {
                                {
                                    val currentRobot = robots.firstOrNull { robot -> robot.id == it }
                                    if (currentRobot != null) {
                                        if (canDeleteRobot(currentRobot.id, rules)) {
                                            pendingDeleteRobotId = currentRobot.id
                                        } else {
                                            blockedRobotName = currentRobot.name
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (showRuleDialog) {
                        RuleEditorDialog(
                            isEditMode = editingRuleId != null,
                            inputMode = selectedRuleSenderInputMode,
                            onInputModeChange = { targetMode ->
                                val transformedDraft = transformRuleNumberDraft(
                                    currentInputMode = selectedRuleSenderInputMode,
                                    targetInputMode = targetMode,
                                    currentCountryOption = selectedCountryOption,
                                    currentLocalNumber = senderNumber,
                                    currentDisplaySender = rawSenderDisplay,
                                )
                                ruleSenderInputMode = transformedDraft.inputMode.name
                                selectedCountryRegion = transformedDraft.countryOption.regionCode
                                senderNumber = transformedDraft.localNumber
                                rawSenderDisplay = transformedDraft.displaySender
                            },
                            countryOptions = availableCountryOptions,
                            selectedCountry = selectedCountryOption,
                            onCountrySelected = { selectedCountryRegion = it.regionCode },
                            localNumber = senderNumber,
                            onLocalNumberChange = { senderNumber = it },
                            displaySender = rawSenderDisplay,
                            onDisplaySenderChange = { rawSenderDisplay = it },
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
                            onDismiss = { resetRuleEditor() },
                            onSave = {
                                scope.launch {
                                    val isEditMode = editingRuleId != null
                                    val now = System.currentTimeMillis()
                                    val keywords = keywordText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                                    val result = withContext(Dispatchers.IO) {
                                        val rule = SenderRule(
                                            id = editingRuleId ?: 0L,
                                            senderNumber = buildRuleSenderNumber(inputMode = selectedRuleSenderInputMode, countryOption = selectedCountryOption, localNumber = senderNumber, displaySender = rawSenderDisplay),
                                            senderMatchMode = when (selectedRuleSenderInputMode) {
                                                RuleSenderInputMode.DISPLAY_VALUE -> SenderMatchMode.DISPLAY_VALUE
                                                RuleSenderInputMode.INTERNATIONAL_NUMBER -> SenderMatchMode.INTERNATIONAL_NUMBER
                                            },
                                            enabled = ruleEnabled,
                                            keywords = keywords,
                                            selectedRobotIds = selectedRobotIds.toList(),
                                            createdAt = if (editingRuleId == null) now else editingRuleCreatedAt,
                                            updatedAt = now,
                                        )
                                        if (editingRuleId == null) {
                                            container.senderRuleRepository.save(rule)
                                        } else {
                                            container.senderRuleRepository.update(rule)
                                        }
                                    }
                                    when (result) {
                                        is RepositorySaveResult.Success -> {
                                            val savedSender = result.value.senderNumber
                                            resetRuleEditor()
                                            statusMessage = context.getString(
                                                if (isEditMode) R.string.status_rule_updated else R.string.status_rule_saved,
                                                savedSender,
                                            )
                                        }
                                        RepositorySaveResult.DuplicateSenderNumber -> {
                                            statusMessage = context.getString(R.string.status_rule_duplicate)
                                        }
                                        is RepositorySaveResult.ConflictingSenderRule -> {
                                            statusMessage = context.getString(
                                                R.string.status_rule_conflict,
                                                result.existingSenderNumber,
                                            )
                                        }
                                        RepositorySaveResult.Failed -> {
                                            statusMessage = context.getString(R.string.status_rule_save_failed)
                                        }
                                        else -> {
                                            statusMessage = context.getString(R.string.status_rule_save_failed)
                                        }
                                    }
                                }
                            },
                            onDelete = editingRuleId?.let { id -> { pendingDeleteRuleId = id } },
                        )
                    }
                    pendingDeleteRobotId?.let { robotId ->
                        val robotNameToDelete = robots.firstOrNull { robot -> robot.id == robotId }?.name.orEmpty()
                        ConfirmDeleteDialog(
                            title = context.getString(R.string.delete_confirm_title),
                            message = context.getString(R.string.delete_robot_confirm_message, robotNameToDelete),
                            onConfirm = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { container.robotRepository.deleteById(robotId) }
                                    pendingDeleteRobotId = null
                                    resetRobotEditor()
                                    statusMessage = context.getString(R.string.status_robot_deleted, robotNameToDelete)
                                }
                            },
                            onDismiss = { pendingDeleteRobotId = null },
                        )
                    }
                    pendingDeleteRuleId?.let { ruleId ->
                        val senderNumberToDelete = rules.firstOrNull { rule -> rule.id == ruleId }?.senderNumber.orEmpty()
                        ConfirmDeleteDialog(
                            title = context.getString(R.string.delete_confirm_title),
                            message = context.getString(R.string.delete_rule_confirm_message, senderNumberToDelete),
                            onConfirm = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { container.senderRuleRepository.deleteById(ruleId) }
                                    pendingDeleteRuleId = null
                                    resetRuleEditor()
                                    statusMessage = context.getString(R.string.status_rule_deleted, senderNumberToDelete)
                                }
                            },
                            onDismiss = { pendingDeleteRuleId = null },
                        )
                    }
                    blockedRobotName?.let {
                        InfoDialog(
                            title = context.getString(R.string.delete_blocked_title),
                            message = context.getString(R.string.delete_robot_blocked_message),
                            onDismiss = { blockedRobotName = null },
                        )
                    }
                    CollapsibleSectionCard(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            simulationSectionOffset = coordinates.positionInParent().y.toInt()
                        },
                        title = stringResource(R.string.simulation_title),
                        expanded = simulationExpanded,
                        onToggle = {
                            val willExpand = !simulationExpanded
                            simulationExpanded = willExpand
                            if (willExpand) {
                                scope.launch {
                                    delay(120L)
                                    scrollState.animateScrollTo((simulationSectionOffset - 120).coerceAtLeast(0))
                                }
                            }
                        },
                    ) {
                        SimulationInjectionCard(
                            senderNumber = simulationSenderNumber,
                            onSenderNumberChange = { simulationSenderNumber = it },
                            messageBody = simulationMessageBody,
                            onMessageBodyChange = { simulationMessageBody = it },
                            statusMessage = simulationStatusMessage,
                            showHeader = false,
                            onInject = {
                                scope.launch {
                                    when (val validation = validateSimulationInjectionInput(
                                        context = context,
                                        senderNumber = simulationSenderNumber,
                                        messageBody = simulationMessageBody,
                                    )) {
                                        is SimulationInjectionValidation.Invalid -> {
                                            statusMessage = validation.reason
                                        }

                                        is SimulationInjectionValidation.Valid -> {
                                            performSimulationInjection(validation.request)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (showHonorKeepaliveGuide) {
                        InfoDialog(
                            title = context.getString(R.string.keepalive_honor_dialog_title),
                            message = context.getString(R.string.keepalive_honor_dialog_message),
                            onDismiss = { showHonorKeepaliveGuide = false },
                        )
                    }
                    keepaliveDialogMessage?.let { message ->
                        InfoDialog(
                            title = context.getString(R.string.keepalive_battery_dialog_title),
                            message = message,
                            onDismiss = { keepaliveDialogMessage = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CollapsibleSectionCard(
    modifier: Modifier = Modifier,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (expanded) R.string.action_collapse else R.string.action_expand,
                        ),
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

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

private fun reconcilePassiveMonitoringStatusMessage(
    context: Context,
    currentMessage: String,
    isServiceRunning: Boolean,
    monitoringState: MonitoringPersistenceState,
    transition: MonitoringControlTransition,
): String {
    if (transition != MonitoringControlTransition.IDLE) {
        return currentMessage
    }
    val startedMessage = context.getString(R.string.status_monitoring_started)
    return when {
        currentMessage.isBlank() && isServiceRunning && monitoringState.monitoringEnabled -> startedMessage
        currentMessage == startedMessage && (!isServiceRunning || !monitoringState.monitoringEnabled) -> ""
        else -> currentMessage
    }
}

@Composable
private fun KeepaliveGuideCard(
    monitoringState: MonitoringPersistenceState,
    isServiceRunning: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    notificationsReady: Boolean,
    showHeader: Boolean = true,
    onOpenBatterySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenHonorGuide: () -> Unit,
) {
    val monitoringEnabled = monitoringState.monitoringEnabled || isServiceRunning
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader) {
                Text(
                    text = stringResource(R.string.keepalive_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.keepalive_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.keepalive_monitoring_state,
                    stringResource(
                        if (monitoringEnabled) R.string.keepalive_state_enabled
                        else R.string.keepalive_state_disabled
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.keepalive_service_state,
                    stringResource(
                        if (isServiceRunning) R.string.keepalive_state_running
                        else R.string.keepalive_state_not_running
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.keepalive_battery_state,
                    stringResource(
                        if (isIgnoringBatteryOptimizations) R.string.keepalive_state_completed
                        else R.string.keepalive_state_pending
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.keepalive_notification_state,
                    stringResource(
                        if (notificationsReady) R.string.keepalive_state_completed
                        else R.string.keepalive_state_pending
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.keepalive_honor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    onClick = onOpenBatterySettings,
                ) {
                    Text(
                        text = stringResource(R.string.keepalive_open_battery_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    onClick = onOpenNotificationSettings,
                ) {
                    Text(
                        text = stringResource(R.string.keepalive_open_notification_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    onClick = onOpenAppDetails,
                ) {
                    Text(
                        text = stringResource(R.string.keepalive_open_app_details),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    onClick = onOpenHonorGuide,
                ) {
                    Text(
                        text = stringResource(R.string.keepalive_open_honor_guide),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    monitoringState: MonitoringPersistenceState,
    latestRecord: SmsRecordPreview?,
    nextRetryAt: Long?,
    showHeader: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader) {
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.diagnostics_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_monitoring_started,
                    monitoringState.lastMonitoringStartedAt?.let(::formatRetryTimestamp)
                        ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_monitoring_stopped,
                    monitoringState.lastMonitoringStoppedAt?.let(::formatRetryTimestamp)
                        ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_recovery_started,
                    monitoringState.lastRecoveryStartedAt?.let(::formatRetryTimestamp)
                        ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_recovery_trigger,
                    monitoringState.lastRecoveryTrigger?.let(::monitoringRecoveryTriggerLabel)
                        ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_sms_received,
                    latestRecord?.receivedAtLabel() ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_sms_summary,
                    latestRecord?.let { "${it.senderNumber} / ${it.statusLabel()}" }
                        ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_next_retry,
                    nextRetryAt?.let(::formatRetryTimestamp) ?: stringResource(R.string.diagnostics_not_available),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
    showHeader: Boolean = true,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader) {
                Text(
                    stringResource(R.string.retry_policy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
    statusMessage: String,
    showHeader: Boolean = true,
    onInject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader) {
                Text(
                    stringResource(R.string.simulation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.simulation_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                    Text(
                        "- ${robot.name} (${robotTypeLabel(context, robot.type)}) - ${
                            if (robot.requiresWebhookReentry()) {
                                stringResource(R.string.robot_webhook_reentry_badge)
                            } else {
                                shortEnabledStateLabel(context, robot.enabled)
                            }
                        }"
                    )
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

private fun readPermissionSnapshot(context: Context): AppPermissionSnapshot {
    return AppPermissionSnapshot(
        receiveSmsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED,
        readSmsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED,
        postNotificationsGranted = !requiresNotificationPermission() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        notificationPermissionRequired = requiresNotificationPermission(),
    )
}

private fun readMonitoringServiceRunning(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
        service.service.className == MonitoringForegroundService::class.java.name
    }
}

private fun requiredPermissions(snapshot: AppPermissionSnapshot): Array<String> = buildList {
    if (!snapshot.receiveSmsGranted) add(Manifest.permission.RECEIVE_SMS)
    if (!snapshot.readSmsGranted) add(Manifest.permission.READ_SMS)
    if (snapshot.notificationPermissionRequired && !snapshot.postNotificationsGranted) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun requiresNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openIntentSafely(context: Context, intent: Intent): Boolean {
    val packageManager = context.packageManager
    val targetIntent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (targetIntent.resolveActivity(packageManager) != null) {
        context.startActivity(targetIntent)
        true
    } else {
        false
    }
}

private fun monitoringRecoveryTriggerLabel(trigger: String): String = when (trigger) {
    MonitoringRecoveryTrigger.BOOT_COMPLETED.name -> "开机恢复"
    MonitoringRecoveryTrigger.PACKAGE_REPLACED.name -> "应用升级恢复"
    MonitoringRecoveryTrigger.SERVICE_RECOVERY.name -> "服务异常恢复"
    else -> trigger
}






















