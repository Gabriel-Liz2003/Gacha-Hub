package dev.gachahub

import dev.gachahub.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream

class DomainTests {
    private val source=Source("Test fixture","https://example.org/fixture","2026-09-08")
    private val character=Character("hsr:test",Game.HSR,"Test",providerId="1202")
    private fun pack()=ContentPack(version=1,publishedAt="2026-09-08",coverage="test fixture",characters=listOf(character),
        materials=listOf(Material("hsr:coin",Game.HSR,"Coin","Moeda")),
        costs=listOf(CostStep(character.id,"level",40,41,mapOf("hsr:coin" to 100L),source),CostStep(character.id,"level",41,42,mapOf("hsr:coin" to 300L),source)))
    @Test fun exactEdgesSum() { assertEquals(mapOf("hsr:coin" to 400L),Planner.calculate(pack(),character.id,listOf(Target("level",40,42)))) }
    @Test fun noUpgradeHasNoCost() { assertEquals(emptyMap<String,Long>(),Planner.calculate(pack(),character.id,listOf(Target("level",40,40)))) }
    @Test(expected=IllegalStateException::class) fun missingEdgeCannotBeProrated() { Planner.calculate(pack(),character.id,listOf(Target("level",40,43))) }
    @Test(expected=IllegalArgumentException::class) fun downgradeRejected() { Planner.calculate(pack(),character.id,listOf(Target("level",41,40))) }
    @Test(expected=IllegalArgumentException::class) fun duplicateTracksRejected() { Planner.calculate(pack(),character.id,listOf(Target("level",40,41),Target("level",40,41))) }
    @Test fun validPack() { pack().validate() }
    @Test(expected=IllegalArgumentException::class) fun crossGameCostsRejected() { pack().copy(materials=listOf(Material("hsr:coin",Game.WUWA,"Coin","Moeda"))).validate() }
    @Test(expected=IllegalArgumentException::class) fun ambiguousEdgesRejected() { val p=pack();p.copy(costs=p.costs+p.costs.first()).validate() }
    @Test(expected=IllegalArgumentException::class) fun futureSchemaRejected() { pack().copy(schemaVersion=99).validate() }
    @Test(expected=IllegalArgumentException::class) fun buildWithoutSourceRejected() { pack().copy(builds=listOf(Build("b",character.id,"t","r",sources=emptyList()))).validate() }
    @Test fun hsrImport() {
        val input="""{"detailInfo":{"avatarDetailList":[{"avatarId":1202,"level":40,"rank":2,"promotion":2,"skillTreeList":[{"pointId":120201,"level":4}]}]},"ttl":60}"""
        val result=ShowcaseParser.parse(Game.HSR,input,listOf(character),1)
        assertEquals(40,result.owned.single().level);assertEquals(2,result.owned.single().copies)
        assertEquals(4,result.owned.single().skills["120201"]);assertTrue(result.owned.single().stats.isEmpty())
    }
    @Test fun zzzImport() {
        val input="""{"PlayerInfo":{"ShowcaseDetail":{"AvatarList":[{"Id":1191,"Level":60,"PromotionLevel":6,"TalentLevel":3,"Weapon":{"Id":14119,"Level":60,"UpgradeLevel":1},"SkillLevelList":[{"Index":0,"Level":10}],"EquippedList":[{"Slot":4,"Equipment":{"Id":123,"Level":15}}]}]}},"ttl":60}"""
        val result=ShowcaseParser.parse(Game.ZZZ,input,emptyList(),1)
        assertEquals("zzz:1191",result.owned.single().characterId);assertEquals(10,result.owned.single().skills["0"])
        assertEquals("4",result.owned.single().equipment.single().slot)
        assertEquals(1,result.owned.single().weapon?.refinement)
    }
    @Test fun genshinPercentAndRefinement() {
        val input="""{"playerInfo":{},"avatarInfoList":[{"avatarId":10000032,"propMap":{"4001":{"val":"40"}},"fightPropMap":{"20":0.7,"23":1.8,"2001":2500},"equipList":[{"itemId":111,"weapon":{"level":40,"affixMap":{"1":4}}}]}]}"""
        val o=ShowcaseParser.parse(Game.GENSHIN,input,emptyList()).owned.single()
        assertEquals(70.0,o.stats["CRIT Rate"]!!,0.00001);assertEquals(180.0,o.stats["Energy Recharge"]!!,0.00001)
        assertEquals(5,o.weapon?.refinement)
    }
    @Test fun privateShowcaseIsEmptyNotFakeRoster() { assertTrue(ShowcaseParser.parse(Game.GENSHIN,"""{"playerInfo":{}}""",emptyList()).owned.isEmpty()) }
    @Test(expected=IllegalArgumentException::class) fun unexpectedResponseRejected() { ShowcaseParser.parse(Game.HSR,"{}",emptyList()) }
    @Test(expected=IllegalArgumentException::class) fun wuwaDoesNotGuessEndpoint() { ShowcaseParser.parse(Game.WUWA,"{}",emptyList()) }
    @Test(expected=IllegalArgumentException::class) fun importRejectsBadLevel() { ShowcaseParser.parse(Game.HSR,"""{"detailInfo":{"avatarDetailList":[{"avatarId":1202,"level":900}]}}""",emptyList()) }
    @Test fun teamSearchAvoidsRepeatedCharacter() {
        val t=TeamGuide("t",Game.WUWA,"t",listOf(listOf("a","b"),listOf("a"),listOf("c")),"fixture",source)
        assertEquals(listOf("b","a","c"),Planner.availableTeam(t,setOf("a","b","c")))
        assertNull(Planner.availableTeam(t,setOf("a","c")))
    }
    @Test fun boundedRead() { assertArrayEquals(byteArrayOf(1,2),ByteArrayInputStream(byteArrayOf(1,2)).readBounded(2)) }
    @Test(expected=IllegalArgumentException::class) fun oversizedReadRejected() { ByteArrayInputStream(ByteArray(101)).readBounded(100) }
    @Test fun ownedRoundTrip() {
        val a=Owned(character.id,40,stats=mapOf("ATK" to 1234.0),favorite=true)
        val text=codec.encodeToString(Owned.serializer(),a)
        assertEquals(a,codec.decodeFromString<Owned>(text))
    }
}
