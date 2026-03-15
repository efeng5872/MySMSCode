package com.example.mysmscode

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.BuildConfigurationSummaryUseCase
import com.example.mysmscode.domain.ConfigurationRuleSummary
import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionValidation
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.autoRetryStatusLabel
import com.example.mysmscode.domain.buildPermissionUiState
import com.example.mysmscode.domain.buildSimulationFeedbackPlan
import com.example.mysmscode.domain.completedRetryCount
import com.example.mysmscode.domain.formatRetryTimestamp
import com.example.mysmscode.domain.receivedAtLabel
import com.example.mysmscode.domain.sourceLabel
import com.example.mysmscode.domain.statusLabel
import com.example.mysmscode.domain.validateSimulationInjection
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
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("基于 Room 的配置工作台已就绪。") }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionSnapshot = readPermissionSnapshot(context)
        statusMessage = if (permissionSnapshot.canStartMonitoring) {
            "所需权限已全部授予。"
        } else {
            "仍有权限未授予：${permissionSnapshot.missingPermissions.joinToString()}"
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
        firstRetryDelayText = reloadedRetryPolicyConfig.firstRetryDelaySeconds.toString()
        secondRetryDelayText = reloadedRetryPolicyConfig.secondRetryDelaySeconds.toString()
        thirdRetryDelayText = reloadedRetryPolicyConfig.thirdRetryDelaySeconds.toString()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    val permissionUiState = remember(permissionSnapshot) { buildPermissionUiState(permissionSnapshot) }
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
                text = "短信转发工作台",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            PermissionCard(
                uiState = permissionUiState,
                onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions(permissionSnapshot))
                },
            )
            StatusCard(
                isLoading = isLoading,
                robotCount = robots.size,
                ruleCount = rules.size,
                recentRecordCount = filteredRecentRecords.size,
                failedRetryCount = filteredFailedAttempts.size,
                retryPolicyConfig = retryPolicyConfig,
                canStartMonitoring = permissionUiState.canStartMonitoring,
                onStartMonitoring = {
                    if (!permissionUiState.canStartMonitoring) {
                        statusMessage = permissionUiState.message
                    } else {
                        MonitoringForegroundService.startMonitoring(context)
                        statusMessage = "已请求启动监控服务。"
                    }
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
                            statusMessage = "重试间隔必须是大于 0 的秒数整数。"
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
                        statusMessage = "自动重试策略已保存：${firstDelay}s / ${secondDelay}s / ${thirdDelay}s"
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
                                statusMessage = "目标机器人已保存：${result.value.name}"
                                reloadData()
                            }

                            RepositorySaveResult.DuplicateName -> {
                                statusMessage = "目标机器人名称已存在，请使用唯一名称。"
                            }

                            else -> {
                                statusMessage = "目标机器人保存失败。"
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
                                statusMessage = "号码规则已保存：${result.value.senderNumber}"
                                reloadData()
                            }

                            RepositorySaveResult.DuplicateSenderNumber -> {
                                statusMessage = "该发送号码已经存在规则。"
                            }

                            else -> {
                                statusMessage = "号码规则保存失败。"
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
                            val feedbackPlan = buildSimulationFeedbackPlan(validation.request.senderNumber)
                            MonitoringForegroundService.enqueueSimulation(
                                context = context,
                                senderNumber = validation.request.senderNumber,
                                messageBody = validation.request.messageBody,
                            )
                            simulationSenderNumber = ""
                            simulationMessageBody = ""
                            statusMessage = feedbackPlan.initialStatusMessage
                            scope.launch {
                                feedbackPlan.refreshDelaysMillis.forEach { refreshDelay ->
                                    delay(refreshDelay)
                                    reloadData()
                                }
                            }
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
                    statusMessage = "已请求重试失败记录 #$attemptId。"
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

private fun readPermissionSnapshot(context: android.content.Context): AppPermissionSnapshot {
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
            Text("权限状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text("当前概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("已配置目标机器人：$robotCount")
                Text("已配置号码规则：$ruleCount")
                Text("最近处理记录：$recentRecordCount")
                Text("可重试失败记录：$failedRetryCount")
                Text("自动重试策略：${retryPolicyConfig.firstRetryDelaySeconds}s / ${retryPolicyConfig.secondRetryDelaySeconds}s / ${retryPolicyConfig.thirdRetryDelaySeconds}s")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartMonitoring, enabled = canStartMonitoring) {
                    Text("启动监控")
                }
                Button(onClick = onRefresh) {
                    Text("刷新")
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
            Text("自动重试策略", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "验证码场景推荐默认值 10 / 30 / 60 秒。第三次自动重试窗口结束后，系统会停止自动重试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = firstRetryDelayText,
                onValueChange = onFirstRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("第一次重试延迟（秒）") },
            )
            OutlinedTextField(
                value = secondRetryDelayText,
                onValueChange = onSecondRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("第二次重试延迟（秒）") },
            )
            OutlinedTextField(
                value = thirdRetryDelayText,
                onValueChange = onThirdRetryDelayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("第三次重试延迟（秒）") },
            )
            Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
                Text("保存重试策略")
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
            Text("新增目标机器人", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = robotName,
                onValueChange = onRobotNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("目标机器人名称") },
                placeholder = { Text("运维飞书群") },
            )
            OutlinedTextField(
                value = robotWebhook,
                onValueChange = onRobotWebhookChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Webhook 地址") },
                placeholder = { Text("https://...") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("类型")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.FEISHU, onClick = { onRobotTypeChange(RobotType.FEISHU) })
                    Text("飞书")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = robotType == RobotType.WECOM, onClick = { onRobotTypeChange(RobotType.WECOM) })
                    Text("企业微信")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = robotEnabled, onCheckedChange = onRobotEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (robotEnabled) "目标机器人已启用" else "目标机器人已停用")
            }
            Button(
                onClick = onSave,
                enabled = robotName.isNotBlank() && robotWebhook.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存目标机器人")
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
            Text("新增号码规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("发送号码") },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = keywordText,
                onValueChange = onKeywordTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("关键字") },
                placeholder = { Text("code, otp, verification") },
                supportingText = { Text("使用逗号分隔。系统会按不区分大小写的包含匹配规则处理。") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ruleEnabled, onCheckedChange = onRuleEnabledChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (ruleEnabled) "规则已启用" else "规则已停用")
            }
            Text("选择目标机器人", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (robots.isEmpty()) {
                Text("请先创建至少一个目标机器人，再新增号码规则。")
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
                                text = "${robot.type.name} - ${if (robot.enabled) "已启用" else "已停用"}",
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
                Text("保存规则")
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
            Text("模拟短信注入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "把一条测试短信注入到与真实短信相同的前台服务处理链路中。该记录会以来源 Simulation 保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = senderNumber,
                onValueChange = onSenderNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模拟发送号码") },
                placeholder = { Text("10690001") },
            )
            OutlinedTextField(
                value = messageBody,
                onValueChange = onMessageBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模拟短信内容") },
                placeholder = { Text("Your verification code is 123456") },
            )
            Button(
                onClick = onInject,
                enabled = senderNumber.isNotBlank() && messageBody.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("执行模拟注入")
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
            Text("配置摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("目标机器人", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (robots.isEmpty()) {
                Text("还没有保存任何目标机器人。")
            } else {
                robots.forEach { robot ->
                    Text("- ${robot.name} (${robot.type.name}) - ${if (robot.enabled) "已启用" else "已停用"}")
                }
            }
            HorizontalDivider()
            Text("号码规则", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (summaries.isEmpty()) {
                Text("还没有保存任何号码规则。")
            } else {
                summaries.forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text("关键字：${summary.keywordPreview}")
                        Text("目标机器人：${summary.robotNames.joinToString()}")
                        Text(if (summary.enabled) "规则已启用" else "规则已停用", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("失败重试队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                labels = FailedRetryFilterOption.entries.map { it.label },
                selectedIndex = FailedRetryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(FailedRetryFilterOption.entries[it]) },
            )
            if (attempts.isEmpty()) {
                Text("当前没有可重试的失败记录。")
            } else {
                attempts.forEachIndexed { index, attempt ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(attempt.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(attempt.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "目标机器人：${attempt.robotName} (${attempt.robotType.name}) | 尝试次数：${attempt.attemptNumber} | 已完成重试：${attempt.completedRetryCount()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "最近错误：${attempt.lastErrorMessage.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "重试状态：${attempt.autoRetryStatusLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "下次自动重试时间：${attempt.nextRetryAt?.let(::formatRetryTimestamp) ?: "未安排自动重试"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onRetry(attempt.attemptId) }) {
                            Text("重试此渠道")
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
            Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                labels = HistoryFilterOption.entries.map { it.label },
                selectedIndex = HistoryFilterOption.entries.indexOf(selectedFilter),
                onSelected = { onFilterSelected(HistoryFilterOption.entries[it]) },
            )
            if (records.isEmpty()) {
                Text("当前还没有处理过的短信记录。")
            } else {
                records.forEachIndexed { index, record ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(record.messageBody, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "状态：${record.statusLabel()} | 来源：${record.sourceLabel()} | 接收时间：${record.receivedAtLabel()}",
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

