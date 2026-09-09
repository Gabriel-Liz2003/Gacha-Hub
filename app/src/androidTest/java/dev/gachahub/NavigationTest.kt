package dev.gachahub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Shell-owned output survives Gradle uninstalling the test application.
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/gacha-$name")
        ).use { it.readBytes() }
    }
    @Test fun opensGameAndAccountFlow() {
        compose.onNodeWithText("Gacha Hub").assertExists()
        screenshot("01-home.png")
        compose.onNodeWithText("Zenless Zone Zero").performClick()
        compose.onNodeWithText("Conta",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nome da conta").performTextInput("Minha conta de teste")
        compose.onNodeWithText("Criar conta").performClick()
        compose.waitUntil(timeoutMillis=10000){compose.onAllNodesWithText("Conta criada").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Entendi").performClick()
        compose.onNodeWithText("UID público").assertExists()
        screenshot("02-account.png")
    }
}
