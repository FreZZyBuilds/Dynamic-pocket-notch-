package com.frezzybuilds.devnotch.data.clipboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import com.frezzybuilds.devnotch.feature.billing.ProPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardProLimitTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        DevNotchDatabase::class.java
    ).allowMainThreadQueries().build()

    private var isPro = false
    private val repository = ClipboardRepository(ApplicationProvider.getApplicationContext(), db.clipboardDao()) { isPro }

    @After
    fun tearDown() = db.close()

    @Test
    fun `free keeps only the newest entries`() = runTest {
        (1..8).forEach { repository.save("clip $it") }
        val texts = repository.history.first().map { it.text }
        assertEquals(ProPlan.FREE_CLIPBOARD_ENTRIES, texts.size)
        assertEquals((8 downTo 4).map { "clip $it" }.toSet(), texts.toSet())
    }

    @Test
    fun `pro keeps the whole history`() = runTest {
        isPro = true
        (1..30).forEach { repository.save("clip $it") }
        assertEquals(30, repository.history.first().size)
    }
}
