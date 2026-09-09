package dev.gachahub.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class Repository(val db: HubDatabase) {
    val dao = db.dao()
    fun decode(records: List<Record>): HubState {
        fun <T> rows(kind: String, parse: (String)->T) = records.filter { it.kind == kind }.map { parse(it.payload) }
        return HubState(
            rows("content") { codec.decodeFromString<ContentPack>(it) }.singleOrNull(),
            rows("character") { codec.decodeFromString<Character>(it) },
            rows("account") { codec.decodeFromString<Account>(it) },
            rows("project") { codec.decodeFromString<Project>(it) },
            rows("team") { codec.decodeFromString<Team>(it) },
            rows("material") { codec.decodeFromString<Material>(it) }
        )
    }
    val state = dao.observe().map(::decode)
    suspend fun snapshot() = decode(dao.all())
    suspend fun seed(context: Context) = withContext(Dispatchers.IO) {
        if (dao.get("content", "current") == null) {
            val pack = codec.decodeFromString<ContentPack>(context.assets.open("starter.json").bufferedReader().use { it.readText() })
            pack.validate(); dao.put(Record("content", "current", codec.encodeToString(pack)))
        }
    }
    suspend fun content(pack: ContentPack) = db.withTransaction {
        pack.validate()
        val old = snapshot()
        require(pack.characters.all { c -> old.characters.find { it.id == c.id }?.let { it.game == c.game } != false }) { "Atualização altera jogo de personagem existente" }
        require(pack.materials.all { m -> old.materials.find { it.id == m.id }?.let { it.game == m.game } != false }) { "Atualização altera jogo de material existente" }
        require(pack.version > (old.pack?.version ?: 0)) { "Pacote não é mais recente" }
        // Preserve catalog records referenced by user data when publisher removes entries.
        val ids = pack.characters.map { it.id }.toSet()
        old.pack?.materials?.filter { previous -> pack.materials.none { it.id == previous.id } }?.forEach { dao.put(Record("material", it.id, codec.encodeToString(it))) }
        old.pack?.characters?.filter { it.id !in ids }?.forEach { dao.put(Record("character", it.id, codec.encodeToString(it))) }
        dao.put(Record("content", "current", codec.encodeToString(pack)))
    }
    suspend fun saveAccount(account: Account) = db.withTransaction {
        val s = snapshot()
        require(account.id.isNotBlank() && account.name.isNotBlank())
        require(account.serverOffset in -12..14 && account.resetHour in 0..23)
        val old = s.accounts.find { it.id == account.id }
        require(old == null || old.game == account.game)
        val valid = s.characters.filter { it.game == account.game }.map { it.id }.toSet()
        require(account.characters.map { it.characterId }.distinct().size == account.characters.size)
        account.characters.forEach { require(it.characterId in valid); it.validate(account.game) }
        require(account.inventory.values.all { it in 0..1_000_000_000 })
        require(account.inventory.keys.all { id -> s.materials.any { it.id == id && it.game == account.game } }) { "Inventário contém material de outro jogo ou desconhecido" }
        dao.put(Record("account", account.id, codec.encodeToString(account)))
    }
    suspend fun editOwned(accountId: String, owned: Owned) = db.withTransaction {
        val a = requireNotNull(snapshot().accounts.find { it.id == accountId })
        saveAccount(a.copy(characters = a.characters.filterNot { it.characterId == owned.characterId } + owned))
    }
    suspend fun inventory(accountId: String, materialId: String, quantity: Long) = db.withTransaction {
        val s = snapshot(); val a = s.accounts.first { it.id == accountId }
        require(quantity in 0..1_000_000_000)
        require(s.materials.any { it.id == materialId && it.game == a.game })
        saveAccount(a.copy(inventory = a.inventory + (materialId to quantity)))
    }
    suspend fun saveMaterial(material: Material) {
        require(material.id.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) && material.name.isNotBlank())
        require(material.days.all { it in 1..7 })
        require(material.energyPerRun == null || material.energyPerRun > 0)
        require(material.estimatedYield == null || material.estimatedYield.isFinite() && material.estimatedYield > 0)
        material.sources.forEach(Source::validate)
        val existing = snapshot().materials.find { it.id == material.id }
        require(existing == null || existing.game == material.game)
        dao.put(Record("material",material.id,codec.encodeToString(material)))
    }
    suspend fun saveCharacter(character: Character) {
        character.validate()
        val existing = snapshot().characters.find { it.id == character.id }
        require(existing == null || existing.game == character.game)
        dao.put(Record("character", character.id, codec.encodeToString(character)))
    }
    suspend fun import(accountId: String, file: ImportFile) = db.withTransaction {
        require(file.schemaVersion == 1 && file.owned.size <= 5000)
        val a = snapshot().accounts.first { it.id == accountId }
        require(file.game == a.game) { "O arquivo pertence a outro jogo" }
        require(file.owned.map { it.characterId }.distinct().size == file.owned.size)
        file.characters.forEach { require(it.game == a.game); saveCharacter(it) }
        val merged = a.characters.associateBy { it.characterId }.toMutableMap()
        file.owned.forEach { o ->
            o.validate(a.game)
            val previous = merged[o.characterId]
            merged[o.characterId] = o.copy(stats = o.stats.ifEmpty { previous?.stats ?: emptyMap() }, favorite = previous?.favorite ?: o.favorite,
                buildId = previous?.buildId ?: o.buildId, notes = previous?.notes ?: o.notes)
        }
        saveAccount(a.copy(characters = merged.values.toList()))
    }
    suspend fun saveProject(project: Project) = db.withTransaction {
        val s = snapshot(); val a = s.accounts.first { it.id == project.accountId }
        val previous = s.projects.find { it.id == project.id }
        require(project.id.isNotBlank())
        require(previous == null || previous.accountId == project.accountId) { "Projeto pertence a outra conta" }
        require(!project.completed && previous?.completed != true) { "Projeto concluído não pode ser recalculado" }
        require(project.priority in 0..1000000)
        require(project.title.isNotBlank() && project.characterId in a.characters.map { it.characterId })
        require(project.costs.isNotEmpty() && project.costs.values.all { it in 1..1_000_000_000 })
        require(project.costs.keys.all { id -> s.materials.any { it.id == id && it.game == a.game } })
        if (!project.manual) require(project.costs == Planner.calculate(requireNotNull(s.pack), project.characterId, project.targets))
        dao.put(Record("project", project.id, codec.encodeToString(project)))
    }
    suspend fun renameProject(accountId: String, id: String, title: String, priority: Int) = db.withTransaction {
        val p = snapshot().projects.first { it.id == id && it.accountId == accountId }
        require(title.isNotBlank() && priority in 0..1000000)
        // Keep the historical cost snapshot even after a content update.
        dao.put(Record("project", id, codec.encodeToString(p.copy(title=title.trim(), priority=priority))))
    }
    suspend fun deleteProject(accountId: String, id: String) = db.withTransaction {
        require(snapshot().projects.any { it.id == id && it.accountId == accountId }) { "Projeto não pertence à conta" }
        // Reservations are derived. Deleting never spends or refunds inventory.
        dao.delete("project", id)
    }
    suspend fun completeProject(id: String) = db.withTransaction {
        val s = snapshot(); val p = s.projects.first { it.id == id }; require(!p.completed)
        val a = s.accounts.first { it.id == p.accountId }
        val allocated = Planner.allocations(s, a.id)[p.id].orEmpty()
        require(p.costs.all { (id,n) -> (allocated[id] ?: 0) >= n }) { "Ainda faltam materiais alocados a este projeto" }
        saveAccount(a.copy(inventory = a.inventory.mapValues { (id,n) -> n - (p.costs[id] ?: 0) }))
        dao.put(Record("project", p.id, codec.encodeToString(p.copy(completed = true))))
    }
    suspend fun saveTeam(team: Team) = db.withTransaction {
        val s = snapshot()
        val a = s.accounts.first { it.id == team.accountId }
        require(team.id.isNotBlank())
        require(s.teams.find { it.id == team.id }?.accountId?.let { it == team.accountId } != false) { "Time pertence a outra conta" }
        require(team.name.isNotBlank() && team.members.size in 1..a.game.teamSize && team.members.distinct().size == team.members.size)
        require(team.members.all { id -> a.characters.any { it.characterId == id } }) { "Time contém personagem não possuído" }
        dao.put(Record("team", team.id, codec.encodeToString(team)))
    }
    suspend fun deleteTeam(accountId: String, id: String) = db.withTransaction {
        require(snapshot().teams.any { it.id == id && it.accountId == accountId }) { "Time não pertence à conta" }
        dao.delete("team", id)
    }
    suspend fun export(): String {
        val s = snapshot()
        return codec.encodeToString(Backup(accounts=s.accounts, projects=s.projects, teams=s.teams, customCharacters=s.custom, customMaterials=s.customMaterials, content=s.pack))
    }
    suspend fun restore(backup: Backup) = db.withTransaction {
        require(backup.schemaVersion == 1 && backup.accounts.size <= 500)
        require(backup.accounts.map { it.id }.distinct().size == backup.accounts.size)
        val current = snapshot().pack
        val incoming = backup.content
        incoming?.validate()
        // Imported backup is merged; accounts with the same ID are replaced, unrelated accounts survive.
        if (incoming != null && (current == null || incoming.version > current.version)) content(incoming)
        backup.customCharacters.forEach { saveCharacter(it) }
        backup.customMaterials.forEach { saveMaterial(it) }
        backup.accounts.forEach { saveAccount(it) }
        backup.projects.forEach { p ->
            // Preserve cost snapshot rather than recomputing historical projects against new data.
            val a = snapshot().accounts.first { it.id == p.accountId }
            require(p.characterId in a.characters.map { it.characterId } && p.costs.values.all { it in 1..1_000_000_000 })
            require(p.costs.keys.all { id -> snapshotMaterialGame(id, incoming) == a.game })
            dao.put(Record("project", p.id, codec.encodeToString(p)))
        }
        backup.teams.forEach { saveTeam(it) }
    }
    private suspend fun snapshotMaterialGame(id: String, fallback: ContentPack?) =
        snapshot().materials.find { it.id == id }?.game ?: fallback?.materials?.find { it.id == id }?.game
}
