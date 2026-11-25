package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedRouterEntity::class,
        BatchHistoryEntity::class,
        PosAgentEntity::class,
        PosCardAssignmentEntity::class,
        PosPaymentEntity::class,
        PackagePricingEntity::class,
        SessionArchiveEntity::class,
        RouterErrorLogEntity::class,
        UserLoginHistoryEntity::class,
        NetworkNodeEntity::class,
        SystemEventLogEntity::class,
        LocalCardEntity::class,
        MonthlyProfitRecordEntity::class,
        RouterTaskEntity::class,
        MaterializedSummaryEntity::class
    ],
    version = 21,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS network_nodes_new (
                        id TEXT NOT NULL,
                        routerIp TEXT NOT NULL,
                        name TEXT NOT NULL,
                        ipAddress TEXT NOT NULL,
                        macAddress TEXT NOT NULL,
                        interfaceName TEXT NOT NULL,
                        model TEXT NOT NULL,
                        role TEXT NOT NULL,
                        nodeType TEXT NOT NULL,
                        status TEXT NOT NULL,
                        parentTowerId TEXT,
                        parentTowerName TEXT,
                        isOnline INTEGER NOT NULL,
                        pingMs INTEGER NOT NULL,
                        connectedUsers INTEGER NOT NULL,
                        uptime TEXT NOT NULL,
                        rxSpeedKbps REAL NOT NULL,
                        txSpeedKbps REAL NOT NULL,
                        totalBytesConsumed INTEGER NOT NULL,
                        signalDbm TEXT NOT NULL,
                        location TEXT NOT NULL,
                        cpuLoadPercent INTEGER NOT NULL,
                        memoryUsagePercent INTEGER NOT NULL,
                        temperature TEXT NOT NULL,
                        voltage TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        channelWidth TEXT NOT NULL,
                        ssid TEXT NOT NULL,
                        firmwareVersion TEXT NOT NULL,
                        vlanId INTEGER,
                        parentInterface TEXT,
                        lastSeenTime INTEGER NOT NULL,
                        lastCheckTime INTEGER NOT NULL,
                        PRIMARY KEY(id, routerIp)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS network_nodes")
                db.execSQL("ALTER TABLE network_nodes_new RENAME TO network_nodes")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clean any legacy orphan rows whose parent agent no longer exists before enforcing FK
                db.execSQL("DELETE FROM pos_card_assignments WHERE agentId NOT IN (SELECT id FROM pos_agents)")
                db.execSQL("DELETE FROM pos_payments WHERE agentId NOT IN (SELECT id FROM pos_agents)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pos_card_assignments_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        agentId INTEGER NOT NULL,
                        agentName TEXT NOT NULL,
                        cardName TEXT NOT NULL,
                        batchId TEXT NOT NULL,
                        profileName TEXT NOT NULL,
                        priceAmount REAL NOT NULL,
                        discountPercent REAL NOT NULL,
                        assignedAt INTEGER NOT NULL,
                        isSold INTEGER NOT NULL,
                        soldAt INTEGER NOT NULL,
                        FOREIGN KEY(agentId) REFERENCES pos_agents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO pos_card_assignments_new (id, agentId, agentName, cardName, batchId, profileName, priceAmount, discountPercent, assignedAt, isSold, soldAt)
                    SELECT id, agentId, agentName, cardName, batchId, profileName, priceAmount, discountPercent, assignedAt, isSold, soldAt
                    FROM pos_card_assignments
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE pos_card_assignments")
                db.execSQL("ALTER TABLE pos_card_assignments_new RENAME TO pos_card_assignments")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_agentId ON pos_card_assignments(agentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_isSold ON pos_card_assignments(isSold)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_batchId ON pos_card_assignments(batchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_cardName ON pos_card_assignments(cardName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_soldAt ON pos_card_assignments(soldAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_card_assignments_agentId_isSold ON pos_card_assignments(agentId, isSold)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pos_payments_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        agentId INTEGER NOT NULL,
                        agentName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        paymentDate INTEGER NOT NULL,
                        paymentNote TEXT NOT NULL,
                        FOREIGN KEY(agentId) REFERENCES pos_agents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO pos_payments_new (id, agentId, agentName, amount, paymentDate, paymentNote)
                    SELECT id, agentId, agentName, amount, paymentDate, paymentNote
                    FROM pos_payments
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE pos_payments")
                db.execSQL("ALTER TABLE pos_payments_new RENAME TO pos_payments")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_payments_agentId ON pos_payments(agentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_payments_paymentDate ON pos_payments(paymentDate)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE router_task_queue ADD COLUMN routerKey TEXT NOT NULL DEFAULT ''")
                // The target of old offline tasks cannot be recovered reliably; retain for review,
                // but do not ever execute them against whichever router happens to be connected.
                db.execSQL("UPDATE router_task_queue SET status = 'FAILED', lastError = 'Legacy task has no router identity; create a new task' WHERE status != 'COMPLETED'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_router_task_queue_routerKey_status_nextAttemptTimestamp ON router_task_queue(routerKey, status, nextAttemptTimestamp)")
                // Old events could contain voucher passwords and even the full REST request body.
                db.execSQL("UPDATE system_event_logs SET details = '' WHERE lower(details) LIKE '%password%' OR details LIKE '%كلمة المرور%'")
                db.execSQL("UPDATE system_event_logs SET description = '[تم حجب تفاصيل حساسة]' WHERE lower(description) LIKE '%password%' OR description LIKE '%كلمة المرور%'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netmanager_database"
                )
                    .addMigrations(MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
