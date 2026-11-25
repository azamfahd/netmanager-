package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.LocalCardEntity
import com.example.data.local.RouterTaskEntity
import com.example.data.local.RouterTaskQueueManager
import com.example.data.model.HotspotCard
import com.example.data.network.MikrotikRepository
import com.example.data.util.SensitiveLogRedactor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CriticalFixesTest {
    @Test
    fun incrementalCardSyncDoesNotDeleteOtherCards() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.appDao()
            dao.insertOrUpdateLocalCard(LocalCardEntity(name = "old", cardId = "*1"))
            val repository = MikrotikRepository(appDao = dao)
            repository.syncCardsToRoom(listOf(HotspotCard(id = "*2", name = "new", password = "secret")), pruneMissing = false)
            assertEquals(setOf("old", "new"), dao.getAllLocalCardsSync().map { it.name }.toSet())

            repository.syncCardsToRoom(listOf(HotspotCard(id = "*2", name = "new", password = "secret")))
            assertEquals(listOf("new"), dao.getAllLocalCardsSync().map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun queuedTasksAreScopedPerRouterAndLegacyTasksAreNotReady() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.appDao()
            val a = RouterTaskQueueManager(dao) { "192.168.1.1" }
            val b = RouterTaskQueueManager(dao) { "10.1.1.1" }
            val card = HotspotCard(id = "*1", name = "same-card", password = "secret")
            a.enqueueEditCard(card)
            b.enqueueEditCard(card)
            a.enqueueEditCard(card.copy(comment = "new edit"))
            assertEquals(1, dao.getAllRetryableTasks("192.168.1.1").size)
            assertEquals(1, dao.getAllRetryableTasks("10.1.1.1").size)
            a.enqueueDeleteCard("*1", "same-card")
            assertEquals(RouterTaskEntity.TYPE_DELETE_CARD, dao.getAllRetryableTasks("192.168.1.1").single().taskType)
            assertEquals(RouterTaskEntity.TYPE_EDIT_CARD, dao.getAllRetryableTasks("10.1.1.1").single().taskType)

            dao.insertTask(RouterTaskEntity.createEditTask(card, ""))
            val now = System.currentTimeMillis() + 1000
            assertEquals(1, dao.getPendingTasksReady("192.168.1.1", now).size)
            assertEquals(1, dao.getPendingTasksReady("10.1.1.1", now).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun operatorLogsNeverContainVoucherPasswords() {
        val input = "البيانات المرسلة: {\"name\":\"abc\",\"password\":\"1234\"}; كلمة المرور: abcde; Authorization: Basic c2VjcmV0"
        val sanitized = SensitiveLogRedactor.redact(input)
        assertFalse(sanitized.contains("1234"))
        assertFalse(sanitized.contains("abcde"))
        assertFalse(sanitized.contains("c2VjcmV0"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }
}
