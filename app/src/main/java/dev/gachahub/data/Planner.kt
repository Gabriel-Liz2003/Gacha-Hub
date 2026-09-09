package dev.gachahub.data

import dev.gachahub.core.ResourceMath

object Planner {
    fun calculate(pack: ContentPack, characterId: String, targets: List<Target>): Map<String, Long> {
        require(targets.isNotEmpty() && targets.map { it.track }.distinct().size == targets.size) { "Selecione trilhas sem repetição" }
        val steps = mutableListOf<Map<String, Long>>()
        targets.forEach { t ->
            require(t.from >= 0 && t.to >= t.from) { "Objetivo deve ser maior ou igual ao atual" }
            var current = t.from
            while (current < t.to) {
                val step = pack.costs.singleOrNull { it.characterId == characterId && it.track == t.track && it.from == current && it.to <= t.to }
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
