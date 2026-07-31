package com.example.mysmscode

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleNameUiIntegrationTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = installTestHarness(targetContext)

    init {
        grantPermission(Manifest.permission.RECEIVE_SMS)
        grantPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun creatingRule_showsRuleNameInManagementList() {
        val ruleName = "招商银行验证码"

        composeRule.onNodeWithTag(UiTestTags.WORKBENCH_NAV_BUTTON).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.RULE_ADD_BUTTON).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(UiTestTags.RULE_EDITOR_DIALOG, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(UiTestTags.RULE_NAME_INPUT, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(UiTestTags.RULE_NAME_INPUT, useUnmergedTree = true)
            .performTextInput(ruleName)
        composeRule.onNodeWithTag(UiTestTags.RULE_DISPLAY_SENDER_INPUT, useUnmergedTree = true)
            .performTextInput("95555")
        composeRule.onNodeWithTag(UiTestTags.RULE_KEYWORD_INPUT, useUnmergedTree = true)
            .performTextInput("验证码")
        composeRule.onNodeWithTag(UiTestTags.RULE_SAVE_BUTTON, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(ruleName, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(ruleName).assertIsDisplayed()
    }

    private fun grantPermission(permission: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${targetContext.packageName} $permission")
            .close()
    }
}
