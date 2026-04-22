package com.example.mysmscode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mysmscode.domain.CountryOption
import com.example.mysmscode.domain.buildCountryPickerOptions
import com.example.mysmscode.domain.findCountryOption
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.RuleSenderInputMode
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.requiresWebhookReentry

@Composable
fun RobotManagementCard(
    robots: List<RobotEndpoint>,
    onAdd: () -> Unit,
    onEdit: (RobotEndpoint) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.configuration_summary_robots),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.action_add))
                }
            }
            if (robots.isEmpty()) {
                Text(stringResource(R.string.robot_management_empty))
            } else {
                robots.forEachIndexed { index, robot ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(robot) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(robot.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (robot.requiresWebhookReentry()) {
                                "${robotTypeLabel(context, robot.type)} | ${stringResource(R.string.robot_webhook_reentry_badge)}"
                            } else {
                                "${robotTypeLabel(context, robot.type)} | ${shortEnabledStateLabel(context, robot.enabled)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (robot.requiresWebhookReentry()) {
                                stringResource(R.string.robot_webhook_reentry_required)
                            } else {
                                robot.webhookUrl
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (robot.requiresWebhookReentry()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (index != robots.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RuleManagementCard(
    rules: List<SenderRule>,
    robots: List<RobotEndpoint>,
    onAdd: () -> Unit,
    onEdit: (SenderRule) -> Unit,
) {
    val context = LocalContext.current
    val robotsById = robots.associateBy(RobotEndpoint::id)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.configuration_summary_sender_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.action_add))
                }
            }
            if (rules.isEmpty()) {
                Text(stringResource(R.string.rule_management_empty))
            } else {
                rules.forEachIndexed { index, rule ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(rule) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(rule.senderNumber, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.configuration_summary_keyword_line,
                                rule.keywords.joinToString(),
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.configuration_summary_robot_line,
                                rule.selectedRobotIds.mapNotNull { robotsById[it]?.name }.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(if (rule.enabled) R.string.rule_enabled else R.string.rule_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != rules.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RobotEditorDialog(
    isEditMode: Boolean,
    robotName: String,
    onRobotNameChange: (String) -> Unit,
    robotWebhook: String,
    onRobotWebhookChange: (String) -> Unit,
    robotEnabled: Boolean,
    onRobotEnabledChange: (Boolean) -> Unit,
    robotType: RobotType,
    onRobotTypeChange: (RobotType) -> Unit,
    webhookWarningMessage: String?,
    disableWarningMessage: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditMode) R.string.robot_dialog_edit_title else R.string.robot_form_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = robotName,
                    onValueChange = onRobotNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.robot_name_label)) },
                )
                OutlinedTextField(
                    value = robotWebhook,
                    onValueChange = onRobotWebhookChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.robot_webhook_label)) },
                    placeholder = { Text("https://...") },
                )
                if (webhookWarningMessage != null) {
                    Text(
                        text = webhookWarningMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
                if (disableWarningMessage != null) {
                    Text(
                        text = disableWarningMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = robotName.isNotBlank() && robotWebhook.isNotBlank()) {
                Text(stringResource(if (isEditMode) R.string.action_update else R.string.robot_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
fun RuleEditorDialog(
    isEditMode: Boolean,
    inputMode: RuleSenderInputMode,
    onInputModeChange: (RuleSenderInputMode) -> Unit,
    countryOptions: List<CountryOption>,
    selectedCountry: CountryOption,
    onCountrySelected: (CountryOption) -> Unit,
    localNumber: String,
    onLocalNumberChange: (String) -> Unit,
    displaySender: String,
    onDisplaySenderChange: (String) -> Unit,
    keywordText: String,
    onKeywordTextChange: (String) -> Unit,
    ruleEnabled: Boolean,
    onRuleEnabledChange: (Boolean) -> Unit,
    robots: List<RobotEndpoint>,
    selectedRobotIds: List<Long>,
    onToggleRobot: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var showCountryPicker by remember { mutableStateOf(false) }

    if (showCountryPicker) {
        CountryPickerDialog(
            options = countryOptions,
            selectedCountry = selectedCountry,
            onSelect = {
                onCountrySelected(it)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    val senderInputValid = when (inputMode) {
        RuleSenderInputMode.DISPLAY_VALUE -> displaySender.isNotBlank()
        RuleSenderInputMode.INTERNATIONAL_NUMBER -> localNumber.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditMode) R.string.rule_dialog_edit_title else R.string.rule_form_title))
        },
        text = {
            val contentScrollState = rememberScrollState()
            val robotScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(contentScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.rule_sender_mode_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = inputMode == RuleSenderInputMode.DISPLAY_VALUE,
                        onClick = { onInputModeChange(RuleSenderInputMode.DISPLAY_VALUE) },
                    )
                    Text(stringResource(R.string.rule_sender_mode_display))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = inputMode == RuleSenderInputMode.INTERNATIONAL_NUMBER,
                        onClick = { onInputModeChange(RuleSenderInputMode.INTERNATIONAL_NUMBER) },
                    )
                    Text(stringResource(R.string.rule_sender_mode_international))
                }
                Text(
                    text = stringResource(
                        if (inputMode == RuleSenderInputMode.DISPLAY_VALUE) {
                            R.string.rule_sender_mode_display_hint
                        } else {
                            R.string.rule_sender_mode_international_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (inputMode == RuleSenderInputMode.DISPLAY_VALUE) {
                    OutlinedTextField(
                        value = displaySender,
                        onValueChange = onDisplaySenderChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rule_sender_display_label)) },
                        supportingText = { Text(stringResource(R.string.rule_sender_display_support)) },
                    )
                } else {
                    TextButton(onClick = { showCountryPicker = true }) {
                        Text(selectedCountry.displayName)
                    }
                    OutlinedTextField(
                        value = localNumber,
                        onValueChange = onLocalNumberChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rule_sender_label)) },
                        supportingText = { Text(stringResource(R.string.rule_sender_international_support)) },
                    )
                }
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = onKeywordTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rule_keyword_label)) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(robotScrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        robots.forEach { robot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = selectedRobotIds.contains(robot.id),
                                        onValueChange = { checked -> onToggleRobot(robot.id, checked) },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selectedRobotIds.contains(robot.id), onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(robot.name)
                                    Text(
                                        text = if (robot.requiresWebhookReentry()) {
                                            "${robotTypeLabel(context, robot.type)} - ${stringResource(R.string.robot_webhook_reentry_badge)}"
                                        } else {
                                            "${robotTypeLabel(context, robot.type)} - ${shortEnabledStateLabel(context, robot.enabled)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (robot.requiresWebhookReentry()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = senderInputValid && keywordText.isNotBlank()) {
                Text(stringResource(if (isEditMode) R.string.action_update else R.string.rule_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun CountryPickerDialog(
    options: List<CountryOption>,
    selectedCountry: CountryOption,
    onSelect: (CountryOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredOptions = remember(options, selectedCountry, query) {
        buildCountryPickerOptions(
            options = options,
            selectedCountry = selectedCountry,
            query = query,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_country_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rule_country_picker_search_label)) },
                    singleLine = true,
                )
                if (filteredOptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.rule_country_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            items = filteredOptions,
                            key = { option -> option.regionCode },
                        ) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(findCountryOption(option.regionCode)) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(
                                    selected = option.regionCode == selectedCountry.regionCode,
                                    onClick = { onSelect(findCountryOption(option.regionCode)) },
                                )
                                Text(
                                    text = option.displayName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = option.callingCode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm))
            }
        },
    )
}
