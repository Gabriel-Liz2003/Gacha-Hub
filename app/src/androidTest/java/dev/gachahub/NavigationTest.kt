package dev.gachahub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun opensGameAndAccountFlow() {
        compose.onNodeWithText("Gacha Hub").assertExists()
        compose.onNodeWithText("Zenless Zone Zero").performClick()
        compose.onNodeWithText("Conta",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nome da conta").performTextInput("Minha conta de teste")
        compose.onNodeWithText("Criar conta").performClick()
        compose.waitUntil(timeoutMillis=10000){compose.onAllNodesWithText("Conta criada").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Entendi").performClick()
        compose.onNodeWithText("UID público").assertExists()
    }
}
