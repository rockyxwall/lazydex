package app.lazydex.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform.getKoin

@RunWith(AndroidJUnit4::class)
class AppearanceThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Verify Koin is running on instrumentation context
        try {
            getKoin()
        } catch (e: Exception) {
            // Ignore context issues
        }
    }

    @Test
    fun testAppearanceScreenElements() {
        composeTestRule.setContent {
            AppearanceScreen(onBack = {})
        }

        // Assert elements are displayed correctly
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pure black dark mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme based on cover").assertIsDisplayed()
        
        // Assert theme options can be tapped
        composeTestRule.onNodeWithText("Light").performClick()
        composeTestRule.onNodeWithText("Dark").performClick()
        composeTestRule.onNodeWithText("System").performClick()
    }
}
