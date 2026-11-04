package com.example.data.local

import android.util.Log
import com.example.data.model.HotspotCard
import com.example.data.network.MikrotikRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class QueueProcessingResult(
    val processedCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val remainingPendingCount: Int = 0,
    val message: String = ""
)

class RouterTaskQueueManager(
    private val appDao: AppDao
) {
    private val TAG = "RouterTaskQueue"
    private val queueMutex = Mutex()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastProcessResult = MutableStateFlow<QueueProcessingResult?>(null)
    val lastProcessResult: StateFlow<QueueProcessingResult?> = _lastProcessResult.asStateFlow()

    /**
     * Enqueue a card edit/update operation to be executed on the router.
     */
    suspend fun enqueueEditCard(card: HotspotCard): Long {
        return withContext(Dispatchers.IO) {
            // Remove any older pending edit tasks for this same card to avoid redundant overwrites
            try {
                appDao.deleteTasksByCardName(card.name)
            } catch (_: Exception) {}

            val task = RouterTaskEntity.createEditTask(card)
            val insertedId = appDao.insertTask(task)
            Log.d(TAG, "Enqueued EDIT_CARD task #$insertedId for card ${card.name}")
            insertedId
        }
    }

    /**
     * Enqueue a card deletion operation to be executed on the router.
     */
    suspend fun enqueueDeleteCard(cardId: String, cardName: String): Long {
        return withContext(Dispatchers.IO) {
            // If there are pending edits for this card, clean them up since the card is being deleted
            if (cardName.isNotBlank()) {
                try {
                    appDao.deleteTasksByCardName(cardName)
                } catch (_: Exception) {}
            }
            val task = RouterTaskEntity.createDeleteTask(cardId, cardName)
            val insertedId = appDao.insertTask(task)
            Log.d(TAG, "Enqueued DELETE_CARD task #$insertedId for card $cardName (ID: $cardId)")
            insertedId
        }
    }

    /**
     * Enqueue a toggle disabled operation.
     */
    suspend fun enqueueToggleDisabled(cardId: String, cardName: String, targetDisabled: Boolean): Long {
        return withContext(Dispatchers.IO) {
            val task = RouterTaskEntity.createToggleTask(cardId, cardName, targetDisabled)
            val insertedId = appDao.insertTask(task)
            Log.d(TAG, "Enqueued TOGGLE_CARD task #$insertedId for card $cardName")
            insertedId
        }
    }

    /**
     * Enqueue a card reactivation operation.
     */
    suspend fun enqueueReactivateCard(card: HotspotCard): Long {
        return withContext(Dispatchers.IO) {
            val task = RouterTaskEntity.createReactivateTask(card)
            val insertedId = appDao.insertTask(task)
            Log.d(TAG, "Enqueued REACTIVATE_CARD task #$insertedId for card ${card.name}")
            insertedId
        }
    }

    /**
     * Enqueue a card verification and activation operation.
     */
    suspend fun enqueueVerifyAndActivateCard(card: HotspotCard): Long {
        return withContext(Dispatchers.IO) {
            val task = RouterTaskEntity.createVerifyActivateTask(card)
            val insertedId = appDao.insertTask(task)
            Log.d(TAG, "Enqueued VERIFY_ACTIVATE_CARD task #$insertedId for card ${card.name}")
            insertedId
        }
    }

    /**
     * Process pending tasks in queue using Exponential Backoff retry mechanism.
     * Only processes tasks where nextAttemptTimestamp <= current system time.
     */
    suspend fun processPendingTasks(repository: MikrotikRepository): QueueProcessingResult {
        return withContext(Dispatchers.IO) {
            if (_isProcessing.value) {
                Log.d(TAG, "Queue processing already in progress, skipping concurrent run.")
                return@withContext QueueProcessingResult(message = "المعالجة قيد التشغيل بالفعل")
            }

            queueMutex.withLock {
                _isProcessing.value = true
                var successCount = 0
                var failedCount = 0
                var processedCount = 0

                try {
                    val now = System.currentTimeMillis()
                    val tasks = appDao.getPendingTasksReady(now)

                    if (tasks.isEmpty()) {
                        val totalPending = appDao.getPendingTaskCount()
                        val result = QueueProcessingResult(
                            processedCount = 0,
                            remainingPendingCount = totalPending,
                            message = if (totalPending > 0) "المهام المعلقة قيد انتظار موعد إعادة المحاولة التالي" else "طابور المهام فارغ"
                        )
                        _lastProcessResult.value = result
                        return@withContext result
                    }

                    Log.d(TAG, "Found ${tasks.size} ready pending tasks to process.")

                    for (task in tasks) {
                        processedCount++
                        // Mark task as PROCESSING
                        appDao.updateTaskStatus(task.id, RouterTaskEntity.STATUS_PROCESSING)

                        val execResult = executeTaskOnRouter(task, repository)
                        if (execResult.isSuccess) {
                            successCount++
                            // Task succeeded - remove from queue
                            appDao.deleteTaskById(task.id)
                            Log.d(TAG, "Task #${task.id} (${task.taskType}) completed successfully and removed from queue.")
                        } else {
                            failedCount++
                            val errorMsg = execResult.exceptionOrNull()?.message ?: "خطأ غير محدد في الراوتر"
                            val nextRetry = task.retryCount + 1
                            
                            if (nextRetry >= task.maxRetries) {
                                Log.w(TAG, "Task #${task.id} exceeded max retries ($nextRetry/${task.maxRetries}). Marking as FAILED.")
                                appDao.updateTask(
                                    task.copy(
                                        status = RouterTaskEntity.STATUS_FAILED,
                                        retryCount = nextRetry,
                                        lastError = errorMsg
                                    )
                                )
                            } else {
                                val nextAttemptTime = RouterTaskEntity.calculateNextAttempt(nextRetry)
                                val delaySeconds = (nextAttemptTime - System.currentTimeMillis()) / 1000
                                Log.d(TAG, "Task #${task.id} failed: $errorMsg. Scheduled retry #$nextRetry after ${delaySeconds}s (Exponential Backoff).")
                                appDao.updateTask(
                                    task.copy(
                                        status = RouterTaskEntity.STATUS_PENDING,
                                        retryCount = nextRetry,
                                        nextAttemptTimestamp = nextAttemptTime,
                                        lastError = errorMsg
                                    )
                                )
                            }
                        }
                    }

                    val remainingPending = appDao.getPendingTaskCount()
                    val summaryMessage = when {
                        processedCount == 0 -> "لا توجد عمليات مؤجلة جاهزة"
                        failedCount == 0 -> "تم تنفيذ $successCount عملية على الراوتر بنجاح!"
                        successCount == 0 -> "تعذر تنفيذ $failedCount عملية، سيتم إعادة المحاولة تلقائياً"
                        else -> "تم تنفيذ $successCount عملية بنجاح، وتأجيل $failedCount للمحاولة لاحقاً"
                    }

                    val finalResult = QueueProcessingResult(
                        processedCount = processedCount,
                        successCount = successCount,
                        failedCount = failedCount,
                        remainingPendingCount = remainingPending,
                        message = summaryMessage
                    )
                    _lastProcessResult.value = finalResult
                    return@withContext finalResult

                } catch (e: Exception) {
                    Log.e(TAG, "Error while processing router task queue: ${e.message}", e)
                    val result = QueueProcessingResult(
                        processedCount = processedCount,
                        successCount = successCount,
                        failedCount = failedCount,
                        remainingPendingCount = appDao.getPendingTaskCount(),
                        message = "خطأ أثناء معالجة الطابور: ${e.message}"
                    )
                    _lastProcessResult.value = result
                    return@withContext result
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    /**
     * Executes the individual task against MikrotikRepository.
     */
    private suspend fun executeTaskOnRouter(task: RouterTaskEntity, repository: MikrotikRepository): Result<Boolean> {
        return try {
            when (task.taskType) {
                RouterTaskEntity.TYPE_EDIT_CARD -> {
                    val card = RouterTaskEntity.parseCardFromPayload(task.payloadJson)
                        ?: return Result.failure(Exception("تعذر استخراج بيانات الكرت من الحمولة المحفوظة"))
                    repository.updateCard(card)
                }
                RouterTaskEntity.TYPE_DELETE_CARD -> {
                    val cardId = task.cardId
                    val cardName = task.cardName
                    repository.deleteCard(cardId, cardName)
                }
                RouterTaskEntity.TYPE_TOGGLE_CARD -> {
                    val json = JSONObject(task.payloadJson)
                    val targetDisabled = json.optBoolean("disabled", false)
                    // In MikrotikRepository.toggleCardDisabled(cardId, currentDisabled), 
                    // currentDisabled should be !targetDisabled so it flips to targetDisabled
                    val currentDisabled = !targetDisabled
                    repository.toggleCardDisabled(task.cardId, currentDisabled)
                }
                RouterTaskEntity.TYPE_REACTIVATE_CARD -> {
                    val card = RouterTaskEntity.parseCardFromPayload(task.payloadJson)
                        ?: return Result.failure(Exception("تعذر استخراج بيانات الكرت للإعادة"))
                    repository.reactivateCard(card)
                }
                RouterTaskEntity.TYPE_VERIFY_ACTIVATE_CARD -> {
                    val card = RouterTaskEntity.parseCardFromPayload(task.payloadJson)
                        ?: return Result.failure(Exception("تعذر استخراج بيانات الكرت للتفعيل"))
                    repository.verifyAndActivateCard(card)
                }
                else -> {
                    Result.failure(Exception("نوع المهمة غير معروف: ${task.taskType}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Force retry all tasks immediately (resets nextAttemptTimestamp to now for all pending tasks).
     */
    suspend fun forceRetryAllPending(repository: MikrotikRepository): QueueProcessingResult {
        return withContext(Dispatchers.IO) {
            try {
                // Update all pending tasks to be ready right now
                val allTasks = appDao.getPendingTasksReady(Long.MAX_VALUE)
                val now = System.currentTimeMillis()
                for (t in allTasks) {
                    if (t.status == RouterTaskEntity.STATUS_PENDING || t.status == RouterTaskEntity.STATUS_FAILED) {
                        appDao.updateTask(t.copy(status = RouterTaskEntity.STATUS_PENDING, nextAttemptTimestamp = now))
                    }
                }
            } catch (_: Exception) {}
            processPendingTasks(repository)
        }
    }

    /**
     * Clear all completed and failed tasks.
     */
    suspend fun clearCompletedAndFailedTasks() {
        withContext(Dispatchers.IO) {
            appDao.clearCompletedTasks()
        }
    }
}
