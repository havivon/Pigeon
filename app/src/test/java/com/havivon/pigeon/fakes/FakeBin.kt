package com.havivon.pigeon.fakes

import com.havivon.pigeon.domain.bin.BinCopy
import com.havivon.pigeon.domain.bin.BinMessage
import com.havivon.pigeon.domain.bin.BinRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The recycle bin's storage in memory. [fail] makes the next copies fail, as a full disk would. */
class FakeBinStore : BinRepository {
    val rows = MutableStateFlow<List<BinMessage>>(emptyList())
    var fail = false
    private var nextId = 1L

    override val messages: Flow<List<BinMessage>> = rows.map { list -> list.sortedByDescending { it.deletedAtMillis } }

    override suspend fun add(copies: List<BinCopy>, lockedThreads: Set<Long>, deletedAtMillis: Long) {
        if (fail) throw IOException("disk full")
        rows.update { list ->
            list + copies.map { (m, addresses) ->
                BinMessage(
                    nextId++, m.id, m.threadId, addresses, m.address, m.body, m.dateMillis, m.dateSentMillis, m.type, m.read, m.subId,
                    deletedAtMillis, locked = m.threadId in lockedThreads,
                )
            }
        }
    }

    override suspend fun remove(ids: Collection<Long>) = rows.update { list -> list.filter { it.id !in ids } }

    override suspend fun removeDeletedBefore(cutoffMillis: Long): Int {
        val gone = rows.value.count { it.deletedAtMillis < cutoffMillis }
        rows.update { list -> list.filter { it.deletedAtMillis >= cutoffMillis } }
        return gone
    }

    override suspend fun clear() = rows.update { emptyList() }
}
