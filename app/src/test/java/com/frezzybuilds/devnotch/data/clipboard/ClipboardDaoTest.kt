package com.frezzybuilds.devnotch.data.clipboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardDaoTest {

    private lateinit var db: DevNotchDatabase
    private lateinit var dao: ClipboardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DevNotchDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.clipboardDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(text: String, timestamp: Long) =
        dao.insertAndTrim(ClipboardItem(text = text, timestamp = timestamp), keep = 10)

    @Test
    fun `keeps only the 10 newest entries, newest first`() = runTest {
        (1..12).forEach { save("clip $it", timestamp = it.toLong()) }

        val texts = dao.observeAll().first().map { it.text }
        assertEquals((12 downTo 3).map { "clip $it" }, texts)
    }

    @Test
    fun `copying the same text again moves it to the top without duplicate`() = runTest {
        save("a", timestamp = 1)
        save("b", timestamp = 2)
        save("a", timestamp = 3)

        val items = dao.observeAll().first()
        assertEquals(listOf("a", "b"), items.map { it.text })
        assertEquals(3L, items.first().timestamp)
    }

    @Test
    fun `clear removes everything`() = runTest {
        save("a", timestamp = 1)
        dao.clear()
        assertEquals(emptyList<ClipboardItem>(), dao.observeAll().first())
    }
}
