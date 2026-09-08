package dev.gachahub.data

import dev.gachahub.core.ResourceMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

interface AccountImporter {
    fun supports(game: Game): Boolean
    suspend fun import(game: Game, uid: String, catalog: List<Character>): ImportFile
}
class HttpFailure(val status: Int, val retryAfterSeconds: Long = 60) : IllegalStateException(when(status) {
    400 -> "UID inválido."; 404 -> "Perfil não encontrado."; 424 -> "Jogo em manutenção; tente mais tarde."
    429 -> "Limite de consultas. Aguarde antes de tentar novamente."
    else -> "Serviço indisponível (HTTP $status). Os dados locais foram preservados."
})
class PublicHttp {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val uri = java.net.URI(url)
        require(uri.scheme == "https" && uri.userInfo == null && uri.host != null) { "Use URL pública HTTPS" }
        val request = Request.Builder().url(url).header("User-Agent", "GachaHub/0.1 (Android; public-showcase)")
            .header("Accept", "application/json").build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw HttpFailure(r.code, r.header("Retry-After")?.toLongOrNull()?.coerceIn(1,86400) ?: 60)
            val body = requireNotNull(r.body)
            require(body.contentLength() <= 8*1024*1024) { "Resposta excede 8 MB" }
            val bytes = body.byteStream().use { it.readBounded() }
            require(bytes.size <= 8*1024*1024) { "Resposta excede 8 MB" }
            bytes.toString(Charsets.UTF_8)
        }
    }
}
object ShowcaseParser {
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.str(key: String, fallback: String = "") = (this[key] as? JsonPrimitive)?.contentOrNull ?: fallback
    private fun JsonObject.num(key: String, fallback: Int = 0) = (this[key] as? JsonPrimitive)?.intOrNull ?: fallback
    fun parse(game: Game, body: String, catalog: List<Character>, time: Long = System.currentTimeMillis()): ImportFile {
        require(game != Game.WUWA) { "WuWa: use JSON ou cadastro manual; integração UID não verificada" }
        val root = codec.parseToJsonElement(body).jsonObject
        val items = when(game) {
            Game.GENSHIN -> { require("playerInfo" in root) { "Resposta Genshin incompatível" }; root.arr("avatarInfoList") }
            Game.HSR -> { require("detailInfo" in root) { "Resposta HSR incompatível" }; root.obj("detailInfo").arr("avatarDetailList") }
            Game.ZZZ -> { require("PlayerInfo" in root) { "Resposta ZZZ incompatível" }; root.obj("PlayerInfo").obj("ShowcaseDetail").arr("AvatarList") }
            else -> error("Não suportado")
        }
        require(items.size <= 100) { "Vitrine excede limite de segurança" }
        val additions = mutableListOf<Character>()
        val owned = items.map { item ->
            val c = item.jsonObject
            val providerId = c.str(if (game == Game.ZZZ) "Id" else "avatarId")
            require(providerId.matches(Regex("[0-9]{1,12}"))) { "ID de personagem ausente" }
            val known = catalog.find { it.game == game && it.providerId == providerId }
            val id = known?.id ?: "${game.name.lowercase()}:$providerId"
            if (known == null) additions += Character(id, game, "Personagem #$providerId", providerId=providerId)
            val level = if (game == Game.GENSHIN) c.obj("propMap").obj("4001").str("val", c.obj("propMap").obj("4001").str("ival","1")).toInt()
                else c.num(if(game == Game.ZZZ) "Level" else "level",1)
            val copies = when(game) { Game.GENSHIN -> c.arr("talentIdList").size; Game.ZZZ -> c.num("TalentLevel"); else -> c.num("rank") }
            val stats = if(game == Game.GENSHIN) {
                val props = c.obj("fightPropMap")
                val keys = mapOf("2000" to "HP", "2001" to "ATK", "2002" to "DEF", "20" to "CRIT Rate", "22" to "CRIT DMG", "23" to "Energy Recharge", "28" to "Elemental Mastery")
                keys.mapNotNull { (key, name) -> (props[key] as? JsonPrimitive)?.doubleOrNull?.let { name to it * if(key in listOf("20","22","23")) 100 else 1 } }.toMap()
            } else emptyMap()
            val skills = when(game) {
                Game.GENSHIN -> c.obj("skillLevelMap").mapValues { it.value.jsonPrimitive.int }
                Game.HSR -> c.arr("skillTreeList").associate { it.jsonObject.str("pointId") to it.jsonObject.num("level") }
                Game.ZZZ -> c.arr("SkillLevelList").associate { it.jsonObject.str("Index") to it.jsonObject.num("Level") }
                else -> emptyMap()
            }
            val weaponJson = when(game) {
                Game.ZZZ -> c["Weapon"] as? JsonObject
                Game.HSR -> c["equipment"] as? JsonObject
                Game.GENSHIN -> c.arr("equipList").map { it.jsonObject }.find { "weapon" in it }
                else -> null
            }
            val weapon = weaponJson?.takeIf { it.isNotEmpty() }?.let { w ->
                when(game) {
                    Game.ZZZ -> Gear(id=w.str("Id"), name="W-Engine #${w.str("Id")}", level=w.num("Level",1), refinement=w.num("UpgradeLevel",1),raw=w.toString())
                    Game.HSR -> Gear(id=w.str("tid"),name="Light Cone #${w.str("tid")}",level=w.num("level",1),refinement=w.num("rank",1),raw=w.toString())
                    else -> Gear(id=w.str("itemId"), name="Weapon #${w.str("itemId")}",level=w.obj("weapon").num("level",1),
                        refinement=(w.obj("weapon").obj("affixMap").values.firstOrNull()?.jsonPrimitive?.intOrNull ?: 0)+1,raw=w.toString())
                }
            }
            val equipment = when(game) {
                Game.GENSHIN -> c.arr("equipList").map { it.jsonObject }.filter { "reliquary" in it }.map { e ->
                    Gear(id=e.str("itemId"),name="Artifact #${e.str("itemId")}",level=e.obj("reliquary").num("level",1)-1,slot=e.obj("flat").str("equipType"),raw=e.toString())
                }
                Game.HSR -> c.arr("relicList").mapIndexed { i,e -> Gear(id=e.jsonObject.str("tid"),name="Relic #${e.jsonObject.str("tid")}",level=e.jsonObject.num("level"),slot=e.jsonObject.str("type",(i+1).toString()),raw=e.toString()) }
                Game.ZZZ -> c.arr("EquippedList").map { e -> val w=e.jsonObject.obj("Equipment"); Gear(id=w.str("Id"),name="Drive Disc #${w.str("Id")}",level=w.num("Level"),slot=e.jsonObject.str("Slot"),raw=e.toString()) }
                else -> emptyList()
            }
            Owned(id, level, copies, ascension=when(game) {
                Game.GENSHIN -> c.obj("propMap").obj("1002").str("val","0").toInt()
                Game.ZZZ -> c.num("PromotionLevel")
                else -> c.num("promotion")
            }, skills=skills,weapon=weapon,equipment=equipment,stats=stats,importedAt=time,raw=c.toString()).also { it.validate(game) }
        }
        require(owned.map { it.characterId }.distinct().size == owned.size) { "IDs repetidos na vitrine" }
        return ImportFile(game=game,characters=additions,owned=owned)
    }
}
class EnkaImporter(private val dao: HubDao, private val http: PublicHttp = PublicHttp()) : AccountImporter {
    private val mutex = Mutex()
    override fun supports(game: Game) = game != Game.WUWA
    override suspend fun import(game: Game, uid: String, catalog: List<Character>): ImportFile = mutex.withLock {
        require(supports(game) && uid.matches(Regex("[0-9]{8,12}"))) { "Informe um UID numérico válido" }
        val key = "enka:${game.name}:$uid"
        val now = System.currentTimeMillis()
        val cached = dao.cache(key)
        if(cached != null && ResourceMath.cacheFresh(now,cached.fetchedAt,cached.expiresAt)) {
            require(cached.payload.isNotBlank()) { "Aguarde o intervalo entre consultas do serviço" }
            return@withLock ShowcaseParser.parse(game,cached.payload,catalog,cached.fetchedAt)
        }
        val path = when(game) { Game.GENSHIN -> "uid"; Game.HSR -> "hsr/uid"; Game.ZZZ -> "zzz/uid"; else -> error("Não suportado") }
        val body = try { http.get("https://enka.network/api/$path/$uid") } catch(e: HttpFailure) {
            if(e.status == 429) dao.putCache(CacheEntry(key,"",now,now+e.retryAfterSeconds*1000))
            throw e
        }
        val parsed = ShowcaseParser.parse(game,body,catalog,now)
        val ttl = codec.parseToJsonElement(body).jsonObject["ttl"]?.jsonPrimitive?.longOrNull?.coerceIn(60,86400) ?: 60
        dao.putCache(CacheEntry(key,body,now,now+ttl*1000))
        parsed
    }
}

/** Bounded streaming read compatible with API 26 (no Java 9 InputStream APIs). */
fun java.io.InputStream.readBounded(limit: Int = 8*1024*1024): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if(n < 0) break
        require(out.size().toLong() + n <= limit) { "Arquivo excede 8 MB" }
        out.write(buffer,0,n)
    }
    return out.toByteArray()
}
