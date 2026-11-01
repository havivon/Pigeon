package com.havivon.pigeon.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.havivon.pigeon.domain.bin.BinCopy
import com.havivon.pigeon.domain.bin.BinMessage
import com.havivon.pigeon.domain.bin.BinRepository
import com.havivon.pigeon.domain.model.SmsType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A deleted message, copied from the system database just before it was deleted. */
@Entity(tableName = "bin_messages", indices = [Index(value = ["deletedAtMillis"])])
data class BinMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalId: Long,
    val threadId: Long,
    /** The conversation's addresses then, one per line. */
    val threadAddresses: String,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val dateSentMillis: Long,
    /** The system's message type (`Telephony.Sms.MESSAGE_TYPE_*`). */
    val type: Int,
    val read: Boolean,
    val subId: Int?,
    val deletedAtMillis: Long,
    val locked: Boolean,
)

@Dao
interface BinDao {
    @Query("SELECT * FROM bin_messages ORDER BY deletedAtMillis DESC, dateMillis DESC, id DESC")
    fun observeAll(): Flow<List<BinMessageEntity>>

    @Insert
    suspend fun insertAll(rows: List<BinMessageEntity>)

    @Query("DELETE FROM bin_messages WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM bin_messages WHERE deletedAtMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM bin_messages")
    suspend fun clear()
}

/**
 * The recycle bin, in a database file of its own ([NAME]) in the app's private storage. It holds message
 * content, so it is left out of the cloud backup and the device-to-device transfer (`res/xml/backup_rules.xml`,
 * `res/xml/data_extraction_rules.xml`); the rest of the app's data backs up as before.
 */
@Database(entities = [BinMessageEntity::class], version = 1, exportSchema = true)
abstract class BinDatabase : RoomDatabase() {
    abstract fun bin(): BinDao

    companion object {
        /** Named in the backup rules: keep the two in step. */
        const val NAME = "pigeon-bin.db"

        fun build(context: Context): BinDatabase =
            Room.databaseBuilder(context.applicationContext, BinDatabase::class.java, NAME).build()
    }
}

/** Deleted in chunks below SQLite's limit of arguments per statement. */
private const val BIN_CHUNK = 500

class RoomBinRepository(db: BinDatabase) : BinRepository {
    private val dao = db.bin()

    override val messages: Flow<List<BinMessage>> = dao.observeAll().map { list -> list.map { it.toMessage() } }

    override suspend fun add(copies: List<BinCopy>, lockedThreads: Set<Long>, deletedAtMillis: Long) {
        dao.insertAll(
            copies.map { (m, addresses) ->
                BinMessageEntity(
                    originalId = m.id,
                    threadId = m.threadId,
                    threadAddresses = addresses.joinToString("\n") { it.replace('\n', ' ') },
                    address = m.address,
                    body = m.body,
                    dateMillis = m.dateMillis,
                    dateSentMillis = m.dateSentMillis,
                    type = m.type.providerValue,
                    read = m.read,
                    subId = m.subId,
                    deletedAtMillis = deletedAtMillis,
                    locked = m.threadId in lockedThreads,
                )
            },
        )
    }

    override suspend fun remove(ids: Collection<Long>) = ids.toList().chunked(BIN_CHUNK).forEach { dao.delete(it) }

    override suspend fun removeDeletedBefore(cutoffMillis: Long): Int = dao.deleteBefore(cutoffMillis)

    override suspend fun clear() = dao.clear()

    private fun BinMessageEntity.toMessage() = BinMessage(
        id = id,
        originalId = originalId,
        threadId = threadId,
        threadAddresses = threadAddresses.split('\n').filter { it.isNotBlank() },
        address = address,
        body = body,
        dateMillis = dateMillis,
        dateSentMillis = dateSentMillis,
        type = SmsType.fromProvider(type),
        read = read,
        subId = subId,
        deletedAtMillis = deletedAtMillis,
        locked = locked,
    )
}
