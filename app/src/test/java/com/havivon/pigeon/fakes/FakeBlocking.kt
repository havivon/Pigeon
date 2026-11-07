package com.havivon.pigeon.fakes

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.blocking.SeenBlockedMemory
import com.havivon.pigeon.domain.blocking.SystemBlockList
import com.havivon.pigeon.domain.codes.CodeClipboard
import com.havivon.pigeon.domain.codes.PendingClear
import com.havivon.pigeon.domain.codes.PendingClearStore
import kotlinx.coroutines.flow.MutableStateFlow

/** The phone's blocked list in memory; [available] false is a viewer (null list, nothing written). */
class FakeSystemBlockList(initial: List<String> = emptyList(), available: Boolean = true) : SystemBlockList {
    val state = MutableStateFlow<List<String>?>(if (available) initial else null)
    override val numbers = state
    val blocked = mutableListOf<String>()
    val unblocked = mutableListOf<String>()

    override suspend fun block(number: String): Boolean {
        val list = state.value ?: return false
        blocked += number
        if (list.none { PhoneNumbers.same(it, number) }) state.value = list + number
        return true
    }

    override suspend fun unblock(number: String): Int {
        val list = state.value ?: return 0
        unblocked += number
        val gone = list.filter { PhoneNumbers.same(it, number) }
        state.value = list - gone.toSet()
        return gone.size
    }
}

class FakeSeenBlocked(var keys: Set<String>? = null) : SeenBlockedMemory {
    override fun load() = keys
    override fun save(keys: Set<String>) {
        this.keys = keys
    }
}

/** A clipboard that remembers what was copied, and whether Pigeon may look at it ([readable]). */
class FakeClipboard : CodeClipboard {
    var text: String? = null
    var tag: String? = null
    var readable = true
    var clears = 0

    override fun copy(code: String, tag: String) {
        text = code
        this.tag = tag
    }

    /** The user copies something else. */
    fun userCopies(other: String) {
        text = other
        tag = null
    }

    override fun currentTag(): String? = if (!readable || text == null) null else tag.orEmpty()

    override fun clear() {
        clears++
        text = null
        tag = null
    }
}

class FakePendingClear : PendingClearStore {
    var value: PendingClear? = null
    override fun load() = value
    override fun save(value: PendingClear?) {
        this.value = value
    }
}
