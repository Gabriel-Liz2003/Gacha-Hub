package dev.gachahub.data

import dev.gachahub.core.ResourceMath

object Planner {
    fun calculate(pack: ContentPack, characterId: String, targets: List<Target>): Map<String, Long> {
        require(targets.isNotEmpty() && targets.map { it.track }.distinct().size == targets.size) { "Selecione trilhas sem repetição" }
        require(pack.characters.any { it.id == characterId }) { "Personagem desconhecido" }
        val character = pack.characters.first { it.id == characterId }
        val available = pack.costs.filter { it.characterId == characterId } + targets.filter { it.track.startsWith("weapon:") }
            .map { it.track.removePrefix("weapon:").substringBeforeLast(':') }.distinct().flatMap { id ->
                val weapon = requireNotNull(pack.weapons.find { it.id == id && it.game == character.game }) { "W-Engine desconhecido" }
                Zzz.weaponSteps(weapon,characterId)
            }
        val byTrack = available.groupBy { it.track }.mapValues { (_, v) -> v.associateBy { it.from } }
        if(character.game == Game.ZZZ) {
            require(targets.filter { it.track.startsWith("weapon:") }.map { it.track.substringBeforeLast(':') }.distinct().size <= 1) { "Escolha um W-Engine por projeto" }
            targets.find { it.track == "ascension" }?.let { promotion ->
                targets.find { it.track == "level" }?.let { level ->
                    require(level.to <= 10 + promotion.to * 10) { "O nível objetivo exige mais promoções" }
                }
            }
        }
        val steps = mutableListOf<Map<String, Long>>()
        targets.forEach { t ->
            require(t.from >= 0 && t.to >= t.from) { "Objetivo deve ser maior ou igual ao atual" }
            var current = t.from
            while (current < t.to) {
                val step = byTrack[t.track]?.get(current)?.takeIf { it.to <= t.to }
                    ?: error("Sem custo verificado: ${t.track} $current → ${t.to}. Importe uma tabela completa ou use checklist manual.")
                steps += step.costs; current = step.to
            }
        }
        return ResourceMath.sum(steps)
    }
    fun allocations(state: HubState, accountId: String): Map<String, Map<String, Long>> {
        val projects = state.projects.filter { it.accountId == accountId && !it.completed }.sortedWith(compareBy<Project> { it.priority }.thenBy { it.id })
        val inventory = state.accounts.find { it.id == accountId }?.inventory ?: emptyMap()
        return projects.map { it.id }.zip(ResourceMath.allocate(projects.map { it.costs }, inventory)).toMap()
    }
    // Select the best distinct combination from ranked source slots; no invented synergy score.
    fun availableTeam(guide: TeamGuide, owned: Set<String>): List<String>? {
        fun visit(i: Int, chosen: List<String>): List<String>? {
            if (i == guide.slots.size) return chosen
            for (c in guide.slots[i]) if (c in owned && c !in chosen) visit(i+1, chosen+c)?.let { return it }
            return null
        }
        return visit(0, emptyList())
    }
}
