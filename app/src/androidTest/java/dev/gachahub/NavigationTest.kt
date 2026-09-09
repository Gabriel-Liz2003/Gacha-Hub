package dev.gachahub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
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
