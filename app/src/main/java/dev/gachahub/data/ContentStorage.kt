package dev.gachahub.data

import kotlinx.serialization.encodeToString

/** Catalog rows stay small enough for Android's CursorWindow. Order is preserved. */
object ContentStorage {
    fun records(pack: ContentPack): List<Record> {
        val result=mutableListOf(Record("content","current",codec.encodeToString(pack.copy(
            characters=emptyList(),builds=emptyList(),materials=emptyList(),costs=emptyList(),teams=emptyList(),banners=emptyList(),weapons=emptyList(),discSets=emptyList()))))
        fun add(kind:String, values:List<String>) {
            values.forEachIndexed { i,payload ->
                require(payload.toByteArray(Charsets.UTF_8).size <= 512*1024) { "Registro de catálogo excede 512 KB" }
                result += Record(kind,i.toString().padStart(8,'0'),payload)
            }
        }
        add("catalog_character",pack.characters.map{codec.encodeToString(it)})
        add("catalog_build",pack.builds.map{codec.encodeToString(it)})
        add("catalog_material",pack.materials.map{codec.encodeToString(it)})
        add("catalog_cost",pack.costs.map{codec.encodeToString(it)})
        add("catalog_team",pack.teams.map{codec.encodeToString(it)})
        add("catalog_weapon",pack.weapons.map{codec.encodeToString(it)})
        add("catalog_disc",pack.discSets.map{codec.encodeToString(it)})
        add("catalog_banner",pack.banners.map{codec.encodeToString(it)})
        require(result.first().payload.toByteArray(Charsets.UTF_8).size <= 512*1024) { "Metadados de catálogo excedem 512 KB" }
        return result
    }
    fun decode(records:List<Record>): ContentPack? {
        val metadata=records.singleOrNull{it.kind=="content" && it.id=="current"} ?: return null
        val pack=codec.decodeFromString<ContentPack>(metadata.payload)
        if(records.none{it.kind.startsWith("catalog_")}) return pack // Legacy or empty catalog.
        fun values(kind:String)=records.filter{it.kind==kind}.sortedBy{it.id}.map{it.payload}
        return pack.copy(
            characters=values("catalog_character").map{codec.decodeFromString<Character>(it)},
            builds=values("catalog_build").map{codec.decodeFromString<Build>(it)},
            materials=values("catalog_material").map{codec.decodeFromString<Material>(it)},
            costs=values("catalog_cost").map{codec.decodeFromString<CostStep>(it)},
            teams=values("catalog_team").map{codec.decodeFromString<TeamGuide>(it)},
            weapons=values("catalog_weapon").map{codec.decodeFromString<Weapon>(it)},
            discSets=values("catalog_disc").map{codec.decodeFromString<DiscSet>(it)},
            banners=values("catalog_banner").map{codec.decodeFromString<Banner>(it)}
        )
    }
}
