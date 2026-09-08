package dev.gachahub

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gachahub.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTests {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun importBuildPlanCloseReopenBackup() = runBlocking {
        val name="test-${UUID.randomUUID()}.db"
        var db=HubDatabase.open(context,name)
        try {
            var repo=Repository(db);repo.seed(context)
            repo.saveAccount(Account("a",Game.HSR,"Test account"))
            val raw="""{"detailInfo":{"avatarDetailList":[{"avatarId":1202,"level":1,"rank":0}]},"ttl":60}"""
            repo.import("a",ShowcaseParser.parse(Game.HSR,raw,repo.snapshot().characters))
            val s=repo.snapshot();val p=requireNotNull(s.pack)
            val owned=s.accounts.single().characters.single()
            val b=p.builds.first{it.characterId==owned.characterId}
            repo.editOwned("a",owned.copy(buildId=b.id,favorite=true))
            val targets=listOf(Target("Ascensão total (sem EXP)",0,6))
            val costs=Planner.calculate(p,owned.characterId,targets)
            repo.saveProject(Project("p","a",owned.characterId,"Ascender Tingyun",targets=targets,costs=costs,dataVersion=p.version))
            repo.inventory("a","hsr:credit",246400)
            repo.saveTeam(Team("t","a","Meus personagens",listOf(owned.characterId)))
            val before=repo.snapshot()
            db.close();db=HubDatabase.open(context,name);repo=Repository(db)
            assertEquals(before,repo.snapshot())
            assertTrue(repo.snapshot().accounts.single().characters.single().favorite)
            val backup=codec.decodeFromString<Backup>(repo.export())
            repo.restore(backup);assertEquals(before,repo.snapshot())
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun migrationPreservesVersionOneAccount() = runBlocking {
        val name="migration-${UUID.randomUUID()}.db"
        val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            old.execSQL("CREATE TABLE records (kind TEXT NOT NULL, id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,id))")
            old.execSQL("INSERT INTO records VALUES ('account','legacy',?)",arrayOf("""{"id":"legacy","game":"ZZZ","name":"Legacy"}"""))
            old.version=1
        }
        val db=HubDatabase.open(context,name)
        try {
            assertEquals("Legacy",Repository(db).snapshot().accounts.single().name)
            db.dao().putCache(CacheEntry("x","{}",1,20))
            assertEquals(20L,db.dao().cache("x")?.expiresAt)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun failedImportRollsBackAndUpdateKeepsAccounts() = runBlocking {
        val name="atomic-${UUID.randomUUID()}.db";val db=HubDatabase.open(context,name)
        try {
            val r=Repository(db);r.seed(context);r.saveAccount(Account("a",Game.ZZZ,"ZZZ"))
            val before=r.snapshot()
            try {
                r.import("a",ImportFile(game=Game.ZZZ,characters=listOf(Character("zzz:new",Game.ZZZ,"New")),owned=listOf(Owned("zzz:new",999))))
                fail("Expected rejection")
            } catch(_:IllegalArgumentException) { }
            assertEquals(before,r.snapshot())
            r.content(requireNotNull(before.pack).copy(version=2))
            assertEquals(before.accounts,r.snapshot().accounts)
            try {r.content(requireNotNull(before.pack));fail("Downgrade accepted")}catch(_:IllegalArgumentException){}
            assertEquals(2,r.snapshot().pack?.version)
        }finally{db.close();context.deleteDatabase(name)}
    }
}
