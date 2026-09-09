package dev.gachahub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gachahub.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val database = HubDatabase.open(app)
    val repository = Repository(database)
    private val mutableError = MutableStateFlow<String?>(null)
    val message = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val lock = Mutex()
    val state = repository.state.catch { mutableError.value = "Falha ao ler banco: ${it.message}. Os dados foram preservados." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HubState())
    private val importer = EnkaImporter(repository.dao)
    init { perform { repository.seed(app) } }
    fun dismiss() { mutableError.value = null }
    fun report(text: String) { mutableError.value = text }
    fun perform(success: String? = null, action: suspend ()->Unit) {
        viewModelScope.launch { lock.withLock {
            mutableBusy.value = true
            try { action(); success?.let { mutableError.value = it } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutableError.value = e.message ?: "Não foi possível concluir a operação" }
            finally { mutableBusy.value = false }
        } }
    }
    fun importUid(account: Account, uid: String) = perform {
        require(uid.isNotBlank()) { "Informe o UID" }
        val file = importer.import(account.game,uid,state.value.characters)
        repository.import(account.id,file)
        val latest = repository.snapshot().accounts.first { it.id == account.id }
        repository.saveAccount(latest.copy(uid=uid))
        report("${file.owned.size} personagens importados da vitrine. Isso não representa a conta completa. IDs desconhecidos e atributos não calculados podem ser editados.")
    }
    fun readFile(text: String, accountId: String?, kind: String) = perform("Arquivo importado") {
        require(text.length <= 8*1024*1024) { "Arquivo excede 8 MB" }
        when(kind) {
            "content" -> repository.content(codec.decodeFromString<ContentPack>(text))
            "backup" -> repository.restore(codec.decodeFromString<Backup>(text))
            else -> repository.import(requireNotNull(accountId),codec.decodeFromString<ImportFile>(text))
        }
    }
    fun sync(url: String = DEFAULT_CONTENT_URL) = perform {
        val updated = repository.refreshContent(codec.decodeFromString<ContentPack>(PublicHttp().get(url)))
        report(if(updated) "Conteúdo atualizado; disponível offline" else "Conteúdo já atualizado")
    }
    override fun onCleared() { super.onCleared(); database.close() }
}
