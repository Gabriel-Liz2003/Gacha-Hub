package dev.gachahub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

@Serializable
enum class Game(val title: String, val copyTerm: String, val weaponTerm: String, val gearTerm: String,
                val maxLevel: Int, val teamSize: Int, val accent: Long) {
    ZZZ("Zenless Zone Zero", "Mindscape Cinema", "W-Engine", "Drive Discs", 60, 3, 0xFFB6EF66),
    HSR("Honkai: Star Rail", "Eidolon", "Light Cone", "Relics", 80, 4, 0xFFBEABFF),
    GENSHIN("Genshin Impact", "Constellation", "Weapon", "Artifacts", 90, 4, 0xFF70D7F5),
    WUWA("Wuthering Waves", "Resonance Chain", "Weapon", "Echoes", 90, 3, 0xFFEACA83)
}
@Serializable data class Source(val name: String, val url: String, val checkedAt: String,
    val updatedAt: String? = null, val patch: String = "Não informado", val note: String = "")
@Serializable data class Character(val id: String, val game: Game, val name: String,
    val rarity: Int = 5, val element: String = "", val specialty: String = "", val role: String = "",
    val image: String = "", val providerId: String = "", val sources: List<Source> = emptyList())
@Serializable data class Gear(val id: String = "", val name: String = "", val level: Int = 1,
    val refinement: Int = 1, val slot: String = "", val set: String = "", val main: String = "",
    val stats: Map<String, Double> = emptyMap(), val raw: String = "")
@Serializable data class Owned(val characterId: String, val level: Int = 1, val copies: Int = 0,
    val ascension: Int = 0, val skills: Map<String, Int> = emptyMap(), val weapon: Gear? = null,
    val equipment: List<Gear> = emptyList(), val stats: Map<String, Double> = emptyMap(),
    val favorite: Boolean = false, val buildId: String = "", val notes: String = "",
    val importedAt: Long = 0, val raw: String = "")
@Serializable data class Account(val id: String, val game: Game, val name: String,
    val uid: String = "", val serverOffset: Int = -5, val resetHour: Int = 4,
    val characters: List<Owned> = emptyList(), val inventory: Map<String, Long> = emptyMap())
@Serializable data class Benchmark(val stat: String, val adequate: Double, val excellent: Double,
    val low: Double, val unit: String = "", val context: String, val cap: Double? = null)
@Serializable data class Build(val id: String, val characterId: String, val title: String,
    val role: String, val bis: List<String> = emptyList(), val premium: List<String> = emptyList(),
    val accessible: List<String> = emptyList(), val sets: List<String> = emptyList(),
    val slots: Map<String, String> = emptyMap(), val substats: List<String> = emptyList(),
    val benchmarks: List<Benchmark> = emptyList(), val skillPriority: List<String> = emptyList(),
    val rotation: String = "", val notes: String = "", val sources: List<Source>)
@Serializable data class Material(val id: String, val game: Game, val name: String, val category: String,
    val days: List<Int> = emptyList(), val location: String = "", val energyPerRun: Int? = null,
    val estimatedYield: Double? = null, val sources: List<Source> = emptyList())
// Every edge is a documented exact cost, never a linear fraction of a total.
@Serializable data class CostStep(val characterId: String, val track: String, val from: Int,
    val to: Int, val costs: Map<String, Long>, val source: Source)
@Serializable data class Target(val track: String, val from: Int, val to: Int)
@Serializable data class Project(val id: String, val accountId: String, val characterId: String,
    val title: String, val priority: Int = 0, val targets: List<Target> = emptyList(),
    val costs: Map<String, Long> = emptyMap(), val manual: Boolean = false,
    val dataVersion: Int = 0, val completed: Boolean = false)
@Serializable data class Team(val id: String, val accountId: String, val name: String,
    val members: List<String>, val notes: String = "")
@Serializable data class TeamGuide(val id: String, val game: Game, val name: String,
    val slots: List<List<String>>, val explanation: String, val source: Source)
@Serializable data class Banner(val id: String, val game: Game, val name: String,
    val startsAt: String, val endsAt: String, val source: Source)
@Serializable data class ContentPack(val schemaVersion: Int = 1, val version: Int,
    val publishedAt: String, val coverage: String, val characters: List<Character>,
    val builds: List<Build> = emptyList(), val materials: List<Material> = emptyList(),
    val costs: List<CostStep> = emptyList(), val teams: List<TeamGuide> = emptyList(),
    val banners: List<Banner> = emptyList())
@Serializable data class Backup(val schemaVersion: Int = 1, val accounts: List<Account>,
    val projects: List<Project> = emptyList(), val teams: List<Team> = emptyList(),
    val customCharacters: List<Character> = emptyList(), val customMaterials: List<Material> = emptyList(), val content: ContentPack? = null)
@Serializable data class ImportFile(val schemaVersion: Int = 1, val game: Game,
    val characters: List<Character> = emptyList(), val owned: List<Owned>)

data class HubState(val pack: ContentPack? = null, val custom: List<Character> = emptyList(),
    val accounts: List<Account> = emptyList(), val projects: List<Project> = emptyList(),
    val teams: List<Team> = emptyList(), val customMaterials: List<Material> = emptyList()) {
    val materials: List<Material> get() = ((pack?.materials ?: emptyList()) + customMaterials).distinctBy { it.id }
    val characters: List<Character> get() = ((pack?.characters ?: emptyList()) + custom).distinctBy { it.id }
}
fun Source.validate() {
    require(name.isNotBlank() && java.net.URI(url).scheme == "https") { "Fonte precisa de nome e URL HTTPS" }
    LocalDate.parse(checkedAt)
    updatedAt?.let { LocalDate.parse(it) }
}
fun Character.validate() {
    require(id.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) && name.isNotBlank()) { "Personagem inválido" }
    require(rarity in 4..5) { "Raridade deve ser 4 ou 5 (A=4, S=5)" }
    require(image.isEmpty() || java.net.URI(image).scheme == "https")
}
fun Owned.validate(game: Game) {
    require(level in 1..game.maxLevel && copies in 0..6 && ascension in 0..6) { "Nível, ascensão ou duplicatas inválidos" }
    require(skills.values.all { it in 0..20 })
    require(stats.values.all { it.isFinite() && it >= 0 }) { "Atributos inválidos" }
    require(equipment.size <= 6 && equipment.map { it.slot }.distinct().size == equipment.size) { "Slots duplicados" }
    (equipment + listOfNotNull(weapon)).forEach {
        require(it.level in 0..game.maxLevel && it.refinement in 1..5)
        require(it.stats.values.all { n -> n.isFinite() && n >= 0 })
    }
}
fun ContentPack.validate() {
    require(schemaVersion in 1..2 && version > 0 && coverage.isNotBlank()) { "Versão de conteúdo incompatível; atualize o aplicativo" }
    LocalDate.parse(publishedAt)
    require(characters.size <= 5000 && materials.size <= 20000 && costs.size <= 200000)
    fun <T> unique(items: List<T>, key: (T) -> String) = require(items.map(key).distinct().size == items.size) { "IDs duplicados" }
    unique(characters) { it.id }; unique(materials) { it.id }; unique(builds) { it.id }; unique(teams) { it.id }
    characters.forEach { it.validate(); it.sources.forEach(Source::validate) }
    val chars = characters.associateBy { it.id }; val mats = materials.associateBy { it.id }
    materials.forEach {
        require(it.days.all { d -> d in 1..7 } && (it.energyPerRun == null || it.energyPerRun > 0))
        require(it.estimatedYield == null || it.estimatedYield.isFinite() && it.estimatedYield > 0)
        it.sources.forEach(Source::validate)
    }
    builds.forEach { b ->
        require(b.characterId in chars && b.sources.isNotEmpty()) { "Build sem personagem ou fonte" }
        b.sources.forEach(Source::validate)
        b.benchmarks.forEach { x ->
            require(listOf(x.low, x.adequate, x.excellent).all { it.isFinite() && it > 0 })
            require(x.low <= x.adequate && x.adequate <= x.excellent && x.context.isNotBlank())
            require(x.cap == null || x.cap.isFinite() && x.cap >= x.excellent)
        }
    }
    require(costs.map { "${it.characterId}/${it.track}/${it.from}" }.distinct().size == costs.size) { "Custos ambíguos" }
    costs.forEach { c ->
        val game = requireNotNull(chars[c.characterId]).game
        require(c.from >= 0 && c.to > c.from && c.track.isNotBlank() && c.costs.isNotEmpty())
        require(c.costs.all { (id, n) -> mats[id]?.game == game && n in 1..1_000_000_000 })
        c.source.validate()
    }
    teams.forEach { t ->
        require(t.slots.size == t.game.teamSize && t.slots.all { it.isNotEmpty() })
        require(t.slots.flatten().all { chars[it]?.game == t.game }); t.source.validate()
    }
    banners.forEach { require(java.time.Instant.parse(it.endsAt) > java.time.Instant.parse(it.startsAt)); it.source.validate() }
}
