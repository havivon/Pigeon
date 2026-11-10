package com.havivon.pigeon.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The settings on a real DataStore file: what is written is there after the app is started again. */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    /** A repository on the file, as one run of the app; the store is closed after [block], as when the app ends. */
    private fun <T> session(block: suspend (DataStoreSettingsRepository) -> T): T {
        val job = SupervisorJob()
        try {
            val repo = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
            return runBlocking { block(repo) }
        } finally {
            // Closed for good before the next session opens the same file
            runBlocking { job.cancelAndJoin() }
        }
    }

    @Test
    fun `the skin is Sunset until one is chosen, and the choice is kept`() {
        assertEquals("sunset", session { it.settings.first().skin })
        session { it.setSkin("forest") }
        assertEquals("forest", session { it.settings.first().skin })
    }

    @Test
    fun `an unknown skin is stored as Sunset`() {
        session { it.setSkin("ocean") }
        session { it.setSkin("neon") }
        assertEquals("sunset", session { it.settings.first().skin })
    }

    @Test
    fun `an import writes the skin with the other settings`() {
        session { it.restoreBackedUp(AppSettings(theme = AppTheme.Dark, skin = "graphite")) }
        val read = session { it.settings.first() }
        assertEquals("graphite", read.skin)
        assertEquals(AppTheme.Dark, read.theme)
    }
}
