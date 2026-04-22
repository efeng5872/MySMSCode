package com.example.mysmscode

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleConflictUiIntegrationTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = installTestHarness(targetContext)

    init {
        grantPermission(Manifest.permission.RECEIVE_SMS)
        grantPermission(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            val result = harness.container.senderRuleRepository.save(
                SenderRule(
                    senderNumber = "+8613608083211",
                    senderMatchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = emptyList(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            check(result is RepositorySaveResult.Success) {
                "预置冲突规则失败：$result"
            }
        }
    }

    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun creatingConflictingRule_showsConflictMessageAndDoesNotPersistNewRule() = runBlocking {
        composeRule.onNodeWithTag(UiTestTags.WORKBENCH_NAV_BUTTON).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.RULE_ADD_BUTTON).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(UiTestTags.RULE_EDITOR_DIALOG, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(UiTestTags.RULE_DISPLAY_SENDER_INPUT, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.RULE_DISPLAY_SENDER_INPUT, useUnmergedTree = true)
            .performTextInput("13608083211")
        composeRule.onNodeWithTag(UiTestTags.RULE_KEYWORD_INPUT, useUnmergedTree = true)
            .performTextInput("otp")
        composeRule.onNodeWithTag(UiTestTags.RULE_SAVE_BUTTON, useUnmergedTree = true)
            .performClick()

        val expectedMessage = targetContext.getString(
            R.string.status_rule_conflict,
            "+8613608083211",
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(UiTestTags.STATUS_MESSAGE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.STATUS_MESSAGE)
            .assertIsDisplayed()
            .assertTextEquals(expectedMessage)

        assertEquals(1, harness.container.senderRuleRepository.getAll().size)
    }

    private fun grantPermission(permission: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${targetContext.packageName} $permission")
            .close()
    }
}
