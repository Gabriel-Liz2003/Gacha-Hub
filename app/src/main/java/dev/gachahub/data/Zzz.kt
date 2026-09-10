package dev.gachahub.data

import java.text.Normalizer
import java.util.Locale

object Zzz {
    val attributes = setOf("Physical", "Fire", "Ice", "Electric", "Ether", "Wind", "Lumiflux")
    val specialties = setOf("Attack", "Stun", "Anomaly", "Support", "Defense", "Rupture", "Armorer")
    val skillKeys = listOf("basic", "dodge", "assist", "special", "chain")
    val labels = mapOf("level" to "Nível do agente", "ascension" to "Promoção", "core" to "Núcleo (0, A–F = 1–6)",
        "skill:basic" to "Ataque básico", "skill:dodge" to "Esquiva", "skill:assist" to "Assistência",
        "skill:special" to "Ataque especial", "skill:chain" to "Ataque em cadeia / Supremo")
    fun trackLabel(track: String, pack: ContentPack?): String {
        if(track.startsWith("weapon:")) {
            val id = track.removePrefix("weapon:").substringBeforeLast(':')
            return "${pack?.weapons?.find { it.id == id }?.name ?: id} • ${if(track.endsWith(":level")) "Nível" else "Promoção"}"
        }
        return labels[track] ?: track
    }
    fun current(track: String, owned: Owned): Int? = when {
        track == "level" -> owned.level
        track == "ascension" -> owned.ascension
        track == "core" -> owned.skills["core"] ?: 0
        track.startsWith("skill:") -> owned.skills[track.substringAfter(':')] ?: 1
        track.startsWith("weapon:") -> owned.weapon?.takeIf { track.startsWith("weapon:${it.id}:") }?.let {
            if(track.endsWith(":level")) it.level else it.ascension
        }
        else -> null
    }
    fun weaponSteps(weapon: Weapon, characterId: String): List<CostStep> = weapon.upgrades.map {
        CostStep(characterId, "weapon:${weapon.id}:${it.track}", it.from, it.to, it.costs, weapon.sources.first())
    }
}

data class CharacterFilter(val query: String = "", val attribute: String = "", val specialty: String = "",
    val faction: String = "", val rarity: Int = 0, val ownedOnly: Boolean = false, val favoritesOnly: Boolean = false)
fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
fun filterCharacters(characters: List<Character>, game: Game, owned: Map<String, Owned>, filter: CharacterFilter): List<Character> {
    val terms = searchKey(filter.query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return characters.filter { c ->
        val haystack = searchKey(listOf(c.name,c.fullName,c.element,c.attributeVariant,c.specialty,c.faction,c.role).joinToString(" "))
        c.game == game && terms.all { it in haystack } &&
            (filter.attribute.isEmpty() || c.element == filter.attribute || c.attributeVariant == filter.attribute) &&
            (filter.specialty.isEmpty() || c.specialty == filter.specialty) &&
            (filter.faction.isEmpty() || c.faction == filter.faction) &&
            (filter.rarity == 0 || c.rarity == filter.rarity) &&
            (!filter.ownedOnly || c.id in owned) && (!filter.favoritesOnly || owned[c.id]?.favorite == true)
    }.sortedBy { searchKey(it.name) }
}

internal fun ContentPack.validateEquipmentCatalog() {
    val chars = characters.associateBy { it.id }
    val mats = materials.associateBy { it.id }
    val engines = weapons.associateBy { it.id }; val discs = discSets.associateBy { it.id }
    require(engines.size == weapons.size && discs.size == discSets.size) { "IDs duplicados de equipamento" }
    require(weapons.map { it.game to it.providerId }.distinct().size == weapons.size)
    require(discSets.map { it.game to it.providerId }.distinct().size == discSets.size)
    fun validateIdentity(id: String, name: String, image: String, sources: List<Source>) {
        require(id.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) && name.isNotBlank())
        require(image.isBlank() || validHttps(image)); require(sources.isNotEmpty()); sources.forEach(Source::validate)
    }
    weapons.forEach { w ->
        validateIdentity(w.id,w.name,w.image,w.sources)
        require(w.rarity in 3..5 && w.providerId.isNotBlank() && w.specialty.isNotBlank())
        require(w.baseStats.values.all { it.isFinite() && it >= 0 })
        require(w.upgrades.map { it.track to it.from }.distinct().size == w.upgrades.size)
        w.upgrades.forEach { u ->
            require(u.from >= 0 && u.to > u.from && u.costs.isNotEmpty())
            require(u.costs.all { (id,n) -> mats[id]?.game == w.game && n in 1..1_000_000_000 })
        }
    }
    discSets.forEach { d ->
        validateIdentity(d.id,d.name,d.image,d.sources)
        require(d.providerId.isNotBlank() && d.twoPiece.isNotBlank() && d.fourPiece.isNotBlank())
    }
    if(schemaVersion >= 3) characters.filter { it.game == Game.ZZZ }.forEach { c ->
        require(c.element in Zzz.attributes && c.specialty in Zzz.specialties && c.faction.isNotBlank()) { "Taxonomia ZZZ inválida: ${c.name}" }
        require(c.signatureWeaponId in engines && engines[c.signatureWeaponId]?.game == c.game)
        require(c.baseStats.values.all { it.isFinite() && it >= 0 } && c.baseStats.isNotEmpty())
        require(builds.any { it.characterId == c.id }) { "Agente sem build: ${c.name}" }
        require(c.skillNames.keys.containsAll(Zzz.skillKeys) && c.mindscapes.size == 6)
    }
    builds.forEach { b ->
        val game = chars[b.characterId]?.game
        b.weaponOptions.forEach { require(engines[it.weaponId]?.game == game && it.tier in setOf("bis","premium","accessible","alternative")) }
        b.discOptions.forEach { d ->
            require(discs[d.fourPieceId]?.game == game && d.twoPieceIds.isNotEmpty())
            require(d.twoPieceIds.distinct().size == d.twoPieceIds.size)
            require(d.twoPieceIds.all { it != d.fourPieceId && discs[it]?.game == game })
        }
        if(schemaVersion >= 3 && game == Game.ZZZ) require(b.weaponOptions.isNotEmpty() && b.discOptions.isNotEmpty() && b.slots.keys.containsAll(listOf("4","5","6")))
    }
}

/** Resolve public provider IDs while preserving unrecognized equipment and raw imports. */
fun normalizeZzzGear(owned: Owned, pack: ContentPack?): Owned {
    val weapon=owned.weapon?.let { gear ->
        pack?.weapons?.find { it.game==Game.ZZZ && (it.id==gear.id || it.providerId==gear.id) }?.let {
            gear.copy(id=it.id,name=it.name)
        } ?: gear
    }
    val equipment=owned.equipment.map { gear ->
        // Provider disc ID = suit base ID + rank*10 + slot; catalog suit IDs end in 00.
        val suit=gear.id.toIntOrNull()?.let { (it/100*100).toString() }
        pack?.discSets?.find { it.game==Game.ZZZ && (it.id==gear.set || it.providerId==suit) }?.let {
            gear.copy(name=it.name,set=it.id)
        } ?: gear
    }
    return owned.copy(weapon=weapon,equipment=equipment)
}
