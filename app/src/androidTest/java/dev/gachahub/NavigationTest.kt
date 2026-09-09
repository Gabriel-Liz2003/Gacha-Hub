package dev.gachahub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import dev.gachahub.data.*
import dev.gachahub.ui.HubViewModel
import kotlinx.coroutines.runBlocking
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
    @Test fun createEditAndDeleteProjectAndTeam() {
        val vm=ViewModelProvider(compose.activity)[HubViewModel::class.java]
        compose.waitUntil(timeoutMillis=10000){vm.state.value.pack!=null}
        runBlocking {
            val character=vm.repository.snapshot().characters.first{it.providerId=="1202" && it.game==Game.HSR}
            vm.repository.saveAccount(Account("ui-hsr",Game.HSR,"UI account",characters=listOf(Owned(character.id))))
        }
        compose.waitUntil(timeoutMillis=10000){vm.state.value.accounts.any{it.id=="ui-hsr"}}
        compose.onNodeWithText("Honkai: Star Rail").performScrollTo().performClick()
        compose.onNodeWithText("Planejamento",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Novo planejamento").performClick()
        compose.onNodeWithText("Nome do projeto").performTextInput("UI ascension")
        compose.onNodeWithText("Ascensão total (sem EXP)").performScrollTo().performClick()
        compose.onNodeWithText("Atual • Ascensão total (sem EXP)").assertExists()
        compose.onNodeWithText("Salvar projeto").performClick()
        dismissMessage("Planejamento salvo")
        compose.onNodeWithText("Nome e prioridade").performScrollTo().performClick()
        compose.onNodeWithText("Nome do projeto").performTextReplacement("UI renamed")
        compose.onNodeWithText("Salvar alterações").performClick()
        dismissMessage("Projeto atualizado")
        compose.onNodeWithText("Excluir projeto").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar exclusão").performClick()
        dismissMessage("Projeto excluído")
        compose.onNodeWithText("Times",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Nome do time").performTextInput("UI team")
        compose.onNodeWithText("Tingyun •",substring=true).performScrollTo().performClick()
        compose.onNodeWithText("Salvar time").performScrollTo().performClick()
        dismissMessage("Time salvo")
        compose.onNodeWithText("Editar time").performScrollTo().performClick()
        compose.onNodeWithText("Nome do time").performScrollTo().performTextReplacement("UI team edited")
        compose.onNodeWithText("Salvar alterações do time").performScrollTo().performClick()
        dismissMessage("Time salvo")
        compose.onNodeWithText("Excluir time").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar exclusão").performClick()
        dismissMessage("Time excluído")
    }
    private fun dismissMessage(text:String) {
        compose.waitUntil(timeoutMillis=10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Entendi").performClick()
    }
}
