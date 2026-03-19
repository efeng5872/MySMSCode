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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.AppPermissionSnapshot
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
import com.example.mysmscode.domain.buildDebugSimulationQuickAction
import com.example.mysmscode.domain.findInjectedSimulationRecord
import com.example.mysmscode.domain.findMatchingSimulationRule
import com.example.mysmscode.domain.shouldAutoRequestPermissions
import com.example.mysmscode.domain.buildRuleSenderNumber
import com.example.mysmscode.domain.defaultCountryOption
import com.example.mysmscode.domain.findCountryOption
import com.example.mysmscode.domain.splitSenderNumberForEditing
import com.example.mysmscode.domain.supportedCountryOptions
import com.example.mysmscode.domain.formatRetryTimestamp
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
    var statusMessage by remember { mutableStateOf("") }
    var currentPage by rememberSaveable { mutableStateOf(WorkbenchPage.HOME.name) }
    var hasAutoRequestedPermissions by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val debugSimulationQuickAction = remember {
        buildDebugSimulationQuickAction(
            isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )
    }

    var robotName by rememberSaveable { mutableStateOf("") }
    var robotWebhook by rememberSaveable { mutableStateOf("") }
    var robotEnabled by rememberSaveable { mutableStateOf(true) }
    var robotType by rememberSaveable { mutableStateOf(RobotType.FEISHU) }

    var senderNumber by rememberSaveable { mutableStateOf("") }
    var selectedCountryRegion by rememberSaveable { mutableStateOf(defaultCountryOption().regionCode) }
    var simulationSenderNumber by rememberSaveable { mutableStateOf("") }
    var simulationMessageBody by rememberSaveable { mutableStateOf("") }
    var keywordText by rememberSaveable { mutableStateOf("") }
    var ruleEnabled by rememberSaveable { mutableStateOf(true) }
    val selectedRobotIds = remember { mutableStateListOf<Long>() }

    var firstRetryDelayText by rememberSaveable { mutableStateOf("10") }
    var secondRetryDelayText by rememberSaveable { mutableStateOf("30") }
    var thirdRetryDelayText by rememberSaveable { mutableStateOf("60") }
    var retryPolicyExpanded by rememberSaveable { mutableStateOf(false) }
    var simulationExpanded by rememberSaveable { mutableStateOf(false) }
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

    suspend fun performSimulationInjection(request: SimulationInjectionRequest) {
        val matchedRule = findMatchingSimulationRule(
            senderNumber = request.senderNumber,
            rules = rules,
        )
        if (matchedRule == null) {
            statusMessage = buildSimulationRuleMismatchMessage()
            return
        }
        val feedbackPlan = buildSimulationFeedbackPlan(request.senderNumber)
        val submittedAt = System.currentTimeMillis()
        statusMessage = feedbackPlan.submittedStatusMessage
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
        statusMessage = feedbackPlan.matchedRuleStatusMessage
        var recordWritten = false
        for (delayMillis in feedbackPlan.refreshDelaysMillis) {
            delay(delayMillis)
            reloadData()
            val injectedRecord = findInjectedSimulationRecord(
                records = recentRecords,
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                statusMessage = feedbackPlan.completedStatusMessage
                recordWritten = true
                break
            }
        }
        if (!recordWritten && feedbackPlan.finalRefreshBeforeTimeout) {
            reloadData()
            val injectedRecord = findInjectedSimulationRecord(
                records = recentRecords,
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                statusMessage = feedbackPlan.completedStatusMessage
                recordWritten = true
            }
        }
        if (!recordWritten) {
            statusMessage = feedbackPlan.timeoutStatusMessage
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
        robotWebhook = robot.webhookUrl
        robotEnabled = robot.enabled
        robotType = robot.type
        showRobotDialog = true
    }

    fun resetRuleEditor() {
        editingRuleId = null
        editingRuleCreatedAt = 0L
        senderNumber = ""
        selectedCountryRegion = defaultCountryOption().regionCode
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
        val numberDraft = splitSenderNumberForEditing(rule.senderNumber)
        editingRuleId = rule.id
        editingRuleCreatedAt = rule.createdAt
        selectedCountryRegion = numberDraft.countryOption.regionCode
        senderNumber = numberDraft.localNumber
        keywordText = rule.keywords.joinToString()
        ruleEnabled = rule.enabled
        selectedRobotIds.clear()
        selectedRobotIds.addAll(rule.selectedRobotIds)
        showRuleDialog = true
    }
    LaunchedEffect(Unit) {
        reloadData()
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

    val availableCountryOptions = remember { supportedCountryOptions() }
    val selectedCountryOption = remember(selectedCountryRegion) { findCountryOption(selectedCountryRegion) }

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
                            launchPermissionRequest()
                        },
                    )
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
                                    reloadData()
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
                            onRobotWebhookChange = { robotWebhook = it },
                            robotEnabled = robotEnabled,
                            onRobotEnabledChange = { robotEnabled = it },
                            robotType = robotType,
                            onRobotTypeChange = { robotType = it },
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
                            countryOptions = availableCountryOptions,
                            selectedCountry = selectedCountryOption,
                            onCountrySelected = { selectedCountryRegion = it.regionCode },
                            localNumber = senderNumber,
                            onLocalNumberChange = { senderNumber = it },
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
                                            senderNumber = buildRuleSenderNumber(countryOption = selectedCountryOption, localNumber = senderNumber),
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
                                    reloadData()
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
                                    reloadData()
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
                            debugQuickAction = debugSimulationQuickAction,
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
                            onInjectSample = {
                                scope.launch {
                                    debugSimulationQuickAction?.let { quickAction ->
                                        simulationSenderNumber = quickAction.request.senderNumber
                                        simulationMessageBody = quickAction.request.messageBody
                                        performSimulationInjection(quickAction.request)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
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
    debugQuickAction: com.example.mysmscode.domain.DebugSimulationQuickAction? = null,
    showHeader: Boolean = true,
    onInject: () -> Unit,
    onInjectSample: () -> Unit = {},
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (debugQuickAction != null) {
                    TextButton(onClick = onInjectSample) {
                        Text(debugQuickAction.actionLabel)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Button(
                    onClick = onInject,
                    enabled = senderNumber.isNotBlank() && messageBody.isNotBlank(),
                ) {
                    Text(stringResource(R.string.simulation_inject))
                }
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






















