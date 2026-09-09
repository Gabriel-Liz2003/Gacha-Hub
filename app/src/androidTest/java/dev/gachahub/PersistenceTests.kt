package dev.gachahub

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gachahub.data.*
import dev.gachahub.data.Target
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTests {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun editingAndDeletingReservationsNeverChangesInventory() = runBlocking {
        val name="edit-${UUID.randomUUID()}.db"
        var db=HubDatabase.open(context,name)
        try {
            var r=Repository(db);r.seed(context)
            val character=r.snapshot().characters.first { it.game==Game.HSR }.id
            val material="hsr:credit"
            r.saveAccount(Account("a",Game.HSR,"A",characters=listOf(Owned(character)),inventory=mapOf(material to 100L)))
            r.saveAccount(Account("b",Game.HSR,"B",characters=listOf(Owned(character))))
            val p=Project("p","a",character,"First",costs=mapOf(material to 80L),manual=true)
            r.saveProject(p)
            r.saveProject(p.copy(id="q",title="Second",priority=1))
            assertEquals(20L,Planner.allocations(r.snapshot(),"a")["q"]?.get(material))
            r.saveProject(p.copy(costs=mapOf(material to 30L)))
            assertEquals(70L,Planner.allocations(r.snapshot(),"a")["q"]?.get(material))
            assertEquals(100L,r.snapshot().accounts.first{it.id=="a"}.inventory[material])
            try {r.deleteProject("b","p");fail("Cross-account delete accepted")}catch(_:IllegalArgumentException){}
            try {r.saveProject(p.copy(accountId="b"));fail("Project moved accounts")}catch(_:IllegalArgumentException){}
            r.renameProject("a","q","Priority changed",0)
            r.deleteProject("a","p")
            assertEquals(80L,Planner.allocations(r.snapshot(),"a")["q"]?.get(material))
            r.completeProject("q")
            assertEquals(20L,r.snapshot().accounts.first{it.id=="a"}.inventory[material])
            try {r.saveProject(p.copy(id="q"));fail("Completed project rewritten")}catch(_:IllegalArgumentException){}
            r.renameProject("a","q","Completed record",2)
            r.deleteProject("a","q")
            assertEquals(20L,r.snapshot().accounts.first{it.id=="a"}.inventory[material])
            val expected=r.snapshot()
            db.close();db=HubDatabase.open(context,name);r=Repository(db)
            assertEquals(expected,r.snapshot())
        }finally{db.close();context.deleteDatabase(name)}
    }
    @Test fun teamUpdatesSurviveReopenAndRespectAccountOwnership() = runBlocking {
        val name="teams-${UUID.randomUUID()}.db"
        var db=HubDatabase.open(context,name)
        try {
            var r=Repository(db);r.seed(context)
            val members=r.snapshot().characters.filter{it.game==Game.HSR}.take(2).map{it.id}
            r.saveAccount(Account("a",Game.HSR,"A",characters=members.map{Owned(it)}))
            r.saveAccount(Account("b",Game.HSR,"B",characters=members.map{Owned(it)}))
            r.saveTeam(Team("t","a","Original",members))
            val updated=Team("t","a","Edited",members.reversed(),"Rotation")
            r.saveTeam(updated)
            assertEquals(listOf(updated),r.snapshot().teams)
            try{r.saveTeam(updated.copy(accountId="b"));fail("Team moved accounts")}catch(_:IllegalArgumentException){}
            try{r.deleteTeam("b","t");fail("Cross-account delete accepted")}catch(_:IllegalArgumentException){}
            db.close();db=HubDatabase.open(context,name);r=Repository(db)
            assertEquals(listOf(updated),r.snapshot().teams)
            r.deleteTeam("a","t")
            assertTrue(r.snapshot().teams.isEmpty())
            assertEquals(members.size,r.snapshot().accounts.first{it.id=="a"}.characters.size)
        }finally{db.close();context.deleteDatabase(name)}
    }
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
