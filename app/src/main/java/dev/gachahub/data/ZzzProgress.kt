package dev.gachahub.data

/** Enka base skill levels; Mindscape bonuses never become paid planner levels. */
object ZzzProgress {
    val tracks = listOf("Basic", "Dodge", "Assist", "Special", "Chain")
    private val indices = mapOf("0" to "Basic", "1" to "Special", "2" to "Dodge", "3" to "Chain", "6" to "Assist")
    fun skills(values: Map<String, Int>): Map<String, Int> {
        val result = values.filterKeys { it !in indices }.toMutableMap()
        indices.forEach { (index, name) -> values[index]?.let { result.putIfAbsent(name, it) } }
        return result
    }
    fun coreLabel(value: Int) = if(value in 1..6) ('A' + value - 1).toString() else "0"
}
