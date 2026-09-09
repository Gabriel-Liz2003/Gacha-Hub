package dev.gachahub.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records", primaryKeys = ["kind", "id"])
data class Record(val kind: String, val id: String, val payload: String)
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val payload: String, val fetchedAt: Long, val expiresAt: Long)
@Dao interface HubDao {
    @Query("SELECT * FROM records ORDER BY kind,id") fun observe(): Flow<List<Record>>
    @Query("SELECT * FROM records ORDER BY kind,id") suspend fun all(): List<Record>
    @Query("SELECT * FROM records WHERE kind=:kind AND id=:id") suspend fun get(kind: String, id: String): Record?
    @Upsert suspend fun put(record: Record)
    @Upsert suspend fun putAll(records: List<Record>)
    @Query("DELETE FROM records WHERE kind=:kind AND id=:id") suspend fun delete(kind: String, id: String)
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM records WHERE kind IN ('catalog_character','catalog_build','catalog_material','catalog_cost','catalog_team','catalog_banner')") suspend fun deleteCatalog()
    @Query("SELECT * FROM cache WHERE `key`=:key") suspend fun cache(key: String): CacheEntry?
    @Upsert suspend fun putCache(entry: CacheEntry)
    @Query("DELETE FROM cache") suspend fun clearCache()
}
@Database(entities = [Record::class, CacheEntry::class], version = 3, exportSchema = true)
abstract class HubDatabase : RoomDatabase() {
    abstract fun dao(): HubDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cache` (`key` TEXT NOT NULL, `payload` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val length = db.query("SELECT length(payload) FROM records WHERE kind='content' AND id='current'").use {
                    if(it.moveToFirst()) it.getInt(0) else 0
                }
                if(length == 0) return
                // Read legacy JSON in bounded slices, including catalogs already larger
                // than CursorWindow. Account, inventory and project rows are untouched.
                val payload=buildString {
                    var offset=1
                    while(offset<=length) {
                        db.query("SELECT substr(payload, ?, 32768) FROM records WHERE kind='content' AND id='current'",arrayOf(offset)).use {
                            check(it.moveToFirst());append(it.getString(0))
                        }
                        offset+=32768
                    }
                }
                val pack=codec.decodeFromString<ContentPack>(payload)
                val records=ContentStorage.records(pack)
                db.execSQL("DELETE FROM records WHERE kind IN ('catalog_character','catalog_build','catalog_material','catalog_cost','catalog_team','catalog_banner')")
                records.forEach { db.execSQL("INSERT OR REPLACE INTO records (kind,id,payload) VALUES (?,?,?)",arrayOf(it.kind,it.id,it.payload)) }
            }
        }
        fun open(context: Context, name: String = "gacha-hub.db") = Room.databaseBuilder(context, HubDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2,MIGRATION_2_3).build()
    }
}
