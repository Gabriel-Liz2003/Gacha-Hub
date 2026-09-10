package dev.gachahub

import dev.gachahub.data.*
import dev.gachahub.data.Target
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ZzzTests {
    private val pack by lazy { codec.decodeFromString<ContentPack>(File("src/main/assets/starter.json").readText()) }
    @Test fun fullCatalogParsesValidatesAndUsesSmallRows() {
        pack.validate()
        assertEquals(59,pack.characters.count { it.game==Game.ZZZ })
        val rows=ContentStorage.records(pack)
        assertTrue(rows.all { it.payload.toByteArray().size<512*1024 })
        assertEquals(pack,ContentStorage.decode(rows.reversed()))
    }
    @Test fun allAgentsHaveUsablePlannerAndBuild() {
        pack.characters.filter { it.game==Game.ZZZ }.forEach { c ->
            val targets=listOf(Target("level",1,60),Target("ascension",0,5),Target("core",0,6))+Zzz.skillKeys.map { Target("skill:$it",1,12) }
            val costs=Planner.calculate(pack,c.id,targets)
            assertEquals(900000L,costs["zzz:agent-exp"])
            assertEquals(3705000L,costs["zzz:denny"])
            assertTrue(pack.builds.any{it.characterId==c.id && it.weaponOptions.isNotEmpty() && it.discOptions.isNotEmpty()})
            assertTrue(Planner.calculate(pack,c.id,listOf(Target("weapon:${c.signatureWeaponId}:level",40,60),Target("weapon:${c.signatureWeaponId}:ascension",3,5))).isNotEmpty())
        }
    }
    @Test fun publicProviderIdsResolveEveryLiveAgent() {
        pack.characters.filter { it.game==Game.ZZZ }.forEach { c ->
            val json="""{"PlayerInfo":{"ShowcaseDetail":{"AvatarList":[{"Id":${c.providerId},"Level":60,"PromotionLevel":5,"TalentLevel":2,"CoreSkillEnhancement":6,"SkillLevelList":[{"Index":0,"Level":12},{"Index":6,"Level":9}]}]}}}"""
            val result=ShowcaseParser.parse(Game.ZZZ,json,pack.characters)
            val owned=result.owned.single()
            assertEquals(c.id,owned.characterId);assertEquals(6,owned.skills["core"]);assertEquals(9,owned.skills["assist"])
        }
    }
    @Test fun searchAndCombinedFilters() {
        val claret=pack.characters.first{it.name=="Claret"}
        val owned=listOf(Owned(claret.id,favorite=true))
        val f=CharacterFilter(query="CLARET",attribute=claret.element,specialty=claret.specialty,faction=claret.faction,rarity=claret.rarity,ownedOnly=true,favoritesOnly=true)
        assertEquals(listOf(claret),filterCharacters(pack.characters,Game.ZZZ,owned.associateBy{it.characterId},f))
        assertTrue(filterCharacters(pack.characters,Game.ZZZ,owned.associateBy{it.characterId},f.copy(attribute="Ice")).isEmpty())
        pack.characters.filter{it.game==Game.ZZZ}.forEach { c ->assertTrue(filterCharacters(pack.characters,Game.ZZZ,emptyMap(),CharacterFilter(query=c.name)).contains(c)) }
    }
    @Test fun gearAndProgressRoundTrip() {
        val c=pack.characters.first{it.name=="Claret"};val w=pack.weapons.first{it.id==c.signatureWeaponId}
        val o=normalizeZzzGear(Owned(c.id,level=60,ascension=5,copies=1,skills=mapOf("core" to 6,"basic" to 12),weapon=Gear(id=w.providerId,level=60,ascension=5),favorite=true),pack)
        o.validate(Game.ZZZ);assertEquals(w.id,o.weapon?.id)
        assertEquals(o,codec.decodeFromString<Owned>(codec.encodeToString(Owned.serializer(),o)))
    }
}
