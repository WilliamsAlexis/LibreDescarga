package com.libredescarga.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.libredescarga.core.designsystem.theme.LibreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val next = context.getString(R.string.onboarding_next)
    private val start = context.getString(R.string.onboarding_start)
    private val accept = context.getString(R.string.onboarding_terms_accept)

    @Test
    fun empezarSoloSeHabilitaTrasAceptarLosTerminos() {
        var state by mutableStateOf(OnboardingUiState())
        var finishCalls = 0

        composeRule.setContent {
            LibreTheme {
                OnboardingContent(
                    uiState = state,
                    onTermsAcceptedChange = { state = state.copy(termsAccepted = it) },
                    onFinish = { finishCalls++ },
                )
            }
        }

        // Paso 1 → Paso 2 → Paso 3
        composeRule.onNodeWithText(next).performClick()
        composeRule.onNodeWithText(next).performClick()

        composeRule.onNodeWithText(start).assertIsDisplayed().assertIsNotEnabled()

        composeRule.onNodeWithText(accept).performClick()
        composeRule.onNodeWithText(start).assertIsEnabled().performClick()

        assertEquals(1, finishCalls)
    }
}
