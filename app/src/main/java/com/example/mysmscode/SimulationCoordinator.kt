package com.example.mysmscode

import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionRequest
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.buildSimulationFeedbackPlan
import com.example.mysmscode.domain.buildSimulationRuleMismatchMessage
import com.example.mysmscode.domain.findInjectedSimulationRecord
import com.example.mysmscode.domain.findMatchingSimulationRule
import kotlinx.coroutines.delay

class SimulationCoordinator(
    private val enqueueSimulation: (SimulationInjectionRequest) -> Unit,
    private val currentRecordsProvider: () -> List<SmsRecordPreview>,
    private val updateSimulationStatus: (String) -> Unit,
    private val navigateToHomeRecentRecords: suspend () -> Unit,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun execute(
        request: SimulationInjectionRequest,
        rules: List<SenderRule>,
    ) {
        val matchedRule = findMatchingSimulationRule(
            senderNumber = request.senderNumber,
            rules = rules,
        )
        if (matchedRule == null) {
            updateSimulationStatus(buildSimulationRuleMismatchMessage())
            return
        }

        val feedbackPlan = buildSimulationFeedbackPlan(request.senderNumber)
        val submittedAt = nowProvider()
        updateSimulationStatus(feedbackPlan.submittedStatusMessage)
        enqueueSimulation(request)

        if (feedbackPlan.navigateToHomeRecentRecords) {
            navigateToHomeRecentRecords()
        }

        delayMillis(feedbackPlan.submittedStatusVisibleDelayMillis)
        updateSimulationStatus(feedbackPlan.matchedRuleStatusMessage)

        var recordWritten = false
        for (delayValue in feedbackPlan.refreshDelaysMillis) {
            delayMillis(delayValue)
            val injectedRecord = findInjectedSimulationRecord(
                records = currentRecordsProvider(),
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                updateSimulationStatus(feedbackPlan.completedStatusMessage)
                recordWritten = true
                break
            }
        }

        if (!recordWritten && feedbackPlan.finalRefreshBeforeTimeout) {
            val injectedRecord = findInjectedSimulationRecord(
                records = currentRecordsProvider(),
                senderNumber = request.senderNumber,
                messageBody = request.messageBody,
                submittedAt = submittedAt,
            )
            if (injectedRecord != null) {
                updateSimulationStatus(feedbackPlan.completedStatusMessage)
                recordWritten = true
            }
        }

        if (!recordWritten) {
            updateSimulationStatus(feedbackPlan.timeoutStatusMessage)
        }
    }
}
