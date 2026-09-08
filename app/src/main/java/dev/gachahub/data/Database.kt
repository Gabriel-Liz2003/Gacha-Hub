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
    @Query("SELECT * FROM cache WHERE `key`=:key") suspend fun cache(key: String): CacheEntry?
    @Upsert suspend fun putCache(entry: CacheEntry)
    @Query("DELETE FROM cache") suspend fun clearCache()
}
@Database(entities = [Record::class, CacheEntry::class], version = 2, exportSchema = true)
abstract class HubDatabase : RoomDatabase() {
    abstract fun dao(): HubDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cache` (`key` TEXT NOT NULL, `payload` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        fun open(context: Context, name: String = "gacha-hub.db") = Room.databaseBuilder(context, HubDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
    }
}
