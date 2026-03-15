package com.example.mysmscode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.BuildConfigurationSummaryUseCase
import com.example.mysmscode.domain.ConfigurationRuleSummary
import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.autoRetryStatusLabel
import com.example.mysmscode.domain.completedRetryCount
import com.example.mysmscode.domain.formatRetryTimestamp
import com.example.mysmscode.domain.receivedAtLabel
import com.example.mysmscode.domain.sourceLabel
import com.example.mysmscode.domain.statusLabel
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionValidation
import com.example.mysmscode.domain.validateSimulationInjection
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.ui.theme.MySMSCodeTheme
import kotlinx.coroutines.Dispatchers
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
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Room-backed configuration workbench ready.") }

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
    var historyFilter by rememberSaveable { mutableStateOf(HistoryFilterOption.ALL) }
    var failedRetryFilter by rememberSaveable { mutableStateOf(FailedRetryFilterOption.ALL) }
    var secondRetryDelayText by rememberSaveable { mutableStateOf("30") }
    var thirdRetryDelayText by rememberSaveable { mutableStateOf("60") }

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
        firstRetryDelayText = reloadedRetryPolicyConfig.firstRetryDelaySeconds.toString()
        secondRetryDelayText = reloadedRetryPolicyConfig.secondRetryDelaySeconds.toString()
        thirdRetryDelayText = reloadedRetryPolicyConfig.thirdRetryDelaySeconds.toString()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    val summaries = remember(rules, robots) {
        summaryUseCase.build(rules = rules, robots = robots)
    }
    val filteredRecentRecords = remember(recentRecords, historyFilter) { historyFilter.apply(recentRecords) }
    val filteredFailedAttempts = remember(failedAttempts, failedRetryFilter) { failedRetryFilter.apply(failedAttempts) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SMS Forwarding Workbench",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            StatusCard(
                isLoading = isLoading,
                robotCount = robots.size,
                ruleCount = rules.size,
                recentRecordCount = filteredRecentRecords.size,
                failedRetryCount = filteredFailedAttempts.size,
                retryPolicyConfig = retryPolicyConfig,
                onStartMonitoring = {
                    MonitoringForegroundService.startMonitoring(context)
                    statusMessage = "Monitoring service start requested."
                },
                onRefresh = {
                    scope.launch { reloadData() }
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
                            statusMessage = "Retry delays must be positive integers in seconds."
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
                        statusMessage = "Retry policy saved: ${firstDelay}s / ${secondDelay}s / ${thirdDelay}s"
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
                                statusMessage = "Robot saved: ${result.value.name}"
                                reloadData()
                            }

                            RepositorySaveResult.DuplicateName -> {
                                statusMessage = "Robot name already exists. Choose a unique name."
                            }

                            else -> {
                                statusMessage = "Robot could not be saved."
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
                                statusMessage = "Sender rule saved: ${result.value.senderNumber}"
                                reloadData()
                            }

                            RepositorySaveResult.DuplicateSenderNumber -> {
                                statusMessage = "Sender number already has a rule."
                            }

                            else -> {
                                statusMessage = "Sender rule could not be saved."
                            }
                        }
                    }
                }
            )
            SimulationInjectionCard(
                senderNumber = simulationSenderNumber,
                onSenderNumberChange = { simulationSenderNumber = it },
                messageBody = simulationMessageBody,
                onMessageBodyChange = { simulationMessageBody = it },
                onInject = {
                    when (val validation = validateSimulationInjection(simulationSenderNumber, simulationMessageBody)) {
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
                            statusMessage = "Simulation enqueued for ${validation.request.senderNumber}."
                        }
                    }
                },
            )
            ConfigurationSummaryCard(robots = robots, summaries = summaries)
            FailedRetryCard(
                attempts = filteredFailedAttempts,
                selectedFilter = failedRetryFilter,
                onFilterSelected = { failedRetryFilter = it },
                onRetry = { attemptId ->
                    MonitoringForegroundService.retryFailedAttempt(context, attemptId)
                    statusMessage = "Retry requested for failed attempt #$attemptId."
                },
            )
            RecentHistoryCard(
                records = filteredRecentRecords,
                selectedFilter = historyFilter,
                onFilterSelected = { historyFilter = it },
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
    onStartMonitoring: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Current Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading persisted configuration...")
                }
            } else {
                Text("Configured robots: $robotCount")
                Text("Configured sender rules: $ruleCount")
                Text("Recent processed records: $recentRecordCount")
                Text("Retryable failed attempts: $failedRetryCount")
                Text(
                    "Automatic retry policy: ${retryPolicyConfig.firstRetryDelaySeconds}s / ${retryPolicyConfig.secondRetryDelaySeconds}s / ${retryPolicyConfig.thirdRetryDelaySeconds}s"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartMonitoring) {
                    Text("Start Monitoring")
                }
                Button(onClick = onRefresh) {
                    Text("Refresh")
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
            Text("Automatic Retry Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Verification-code friendly defaults are 10 / 30 / 60 seconds. The app will stop automatic retries after the third retry window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = firstRetryDelayText,
                onValueChange = onFirstRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("First retry delay (seconds)") },
            )
            OutlinedTextField(
                value = secondRetryDelayText,
                onValueChange = onSecondRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Second retry delay (seconds)") },
            )
            OutlinedTextField(
                value = thirdRetryDelayText,
                onValueChange = onThirdRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Third retry delay (seconds)") },
            )
            Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
                Text("Save Retry Policy")
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
            Text("Add Robot Endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = robotName,
                onValueChange = onRobotNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Robot name") },
                placeholder = { Text("Ops Feishu") },
            )
            OutlinedTextField(
                value = robotWebhook,
                onValueChange = onRobotWebhookChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Webhook URL") },
                placeholder = { Text("https://...") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.FEISHU, onClick = { onRobotTypeChange(RobotType.FEISHU) })
                    Text("Feishu")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.WECOM, onClick = { onRobotTypeChange(RobotType.WECOM) })
                    Text("WeCom")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = robotEnabled, onCheckedChange = onRobotEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (robotEnabled) "Robot enabled" else "Robot disabled")
            }
            Button(
                onClick = onSave,
                enabled = robotName.isNotBlank() && robotWebhook.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Robot")
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add Sender Rule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sender number") },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = keywordText,
                onValueChange = onKeywordTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Keywords") },
                placeholder = { Text("code, otp, verification") },
                supportingText = { Text("Comma-separated. Matching uses case-insensitive substring rules.") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ruleEnabled, onCheckedChange = onRuleEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (ruleEnabled) "Rule enabled" else "Rule disabled")
            }
            Text("Select target robots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (robots.isEmpty()) {
                Text("Create at least one robot endpoint before adding a sender rule.")
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
                                text = "${robot.type.name} - ${if (robot.enabled) "enabled" else "disabled"}",
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
                Text("Save Rule")
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
            Text("Simulation Injection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Inject a test SMS into the same foreground-service pipeline. The record will be stored with source = Simulation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Simulation sender number") },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = messageBody,
                onValueChange = onMessageBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Simulation message body") },
                placeholder = { Text("Your verification code is 123456") },
            )
            Button(
                onClick = onInject,
                enabled = senderNumber.isNotBlank() && messageBody.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Inject Simulation")
            }
        }
    }
}

@Composable
private fun ConfigurationSummaryCard(
    robots: List<RobotEndpoint>,
    summaries: List<ConfigurationRuleSummary>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Configuration Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Robots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (robots.isEmpty()) {
                Text("No robot endpoints saved yet.")
            } else {
                robots.forEach { robot ->
                    Text("- ${robot.name} (${robot.type.name}) - ${if (robot.enabled) "enabled" else "disabled"}")
                }
            }
            HorizontalDivider()
            Text("Sender Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (summaries.isEmpty()) {
                Text("No sender rules saved yet.")
            } else {
                summaries.forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text("Keywords: ${summary.keywordPreview}")
                        Text("Robots: ${summary.robotNames.joinToString()}")
                        Text(if (summary.enabled) "Rule enabled" else "Rule disabled", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Failed Retry Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                labels = FailedRetryFilterOption.entries.map { it.label },
                selectedIndex = FailedRetryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(FailedRetryFilterOption.entries[it]) },
            )
            if (attempts.isEmpty()) {
                Text("No retryable failed attempts right now.")
            } else {
                attempts.forEachIndexed { index, attempt ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(attempt.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(attempt.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Robot: ${attempt.robotName} (${attempt.robotType.name}) | Attempt: ${attempt.attemptNumber} | Retries completed: ${attempt.completedRetryCount()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Last error: ${attempt.lastErrorMessage.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Retry status: ${attempt.autoRetryStatusLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Next auto retry at: ${attempt.nextRetryAt?.let(::formatRetryTimestamp) ?: "no automatic retry scheduled"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onRetry(attempt.attemptId) }) {
                            Text("Retry This Channel")
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                labels = HistoryFilterOption.entries.map { it.label },
                selectedIndex = HistoryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(HistoryFilterOption.entries[it]) },
            )
            if (records.isEmpty()) {
                Text("No processed SMS records yet.")
            } else {
                records.forEachIndexed { index, record ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(record.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Status: ${record.statusLabel()} | Source: ${record.sourceLabel()} | Received at: ${record.receivedAtLabel()}",
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
