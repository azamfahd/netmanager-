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
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Indices for local_cards
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cards_createdTimestamp` ON `local_cards` (`createdTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cards_profile` ON `local_cards` (`profile`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cards_disabled` ON `local_cards` (`disabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cards_sourceSystem` ON `local_cards` (`sourceSystem`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cards_cardId` ON `local_cards` (`cardId`)")

                // Indices for pos_card_assignments
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pos_card_assignments_cardName` ON `pos_card_assignments` (`cardName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pos_card_assignments_soldAt` ON `pos_card_assignments` (`soldAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pos_card_assignments_agentId_isSold` ON `pos_card_assignments` (`agentId`, `isSold`)")

                // Indices for router_task_queue
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_router_task_queue_status` ON `router_task_queue` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_router_task_queue_nextAttemptTimestamp` ON `router_task_queue` (`nextAttemptTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_router_task_queue_status_nextAttemptTimestamp` ON `router_task_queue` (`status`, `nextAttemptTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_router_task_queue_cardName` ON `router_task_queue` (`cardName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_router_task_queue_createdAt` ON `router_task_queue` (`createdAt`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = mutableSetOf<String>()
                try {
                    val cursor = db.query("PRAGMA table_info(network_nodes)")
                    while (cursor.moveToNext()) {
                        val nameIdx = cursor.getColumnIndex("name")
                        if (nameIdx >= 0) {
                            existingColumns.add(cursor.getString(nameIdx))
                        }
                    }
                    cursor.close()
                } catch (_: Exception) {}

                if (!existingColumns.contains("firmwareVersion")) {
                    try {
                        db.execSQL("ALTER TABLE `network_nodes` ADD COLUMN `firmwareVersion` TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {}
                }
                if (!existingColumns.contains("vlanId")) {
                    try {
                        db.execSQL("ALTER TABLE `network_nodes` ADD COLUMN `vlanId` INTEGER DEFAULT NULL")
                    } catch (_: Exception) {}
                }
                if (!existingColumns.contains("parentInterface")) {
                    try {
                        db.execSQL("ALTER TABLE `network_nodes` ADD COLUMN `parentInterface` TEXT DEFAULT NULL")
                    } catch (_: Exception) {}
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netmanager_database"
                )
                    .addMigrations(MIGRATION_15_16, MIGRATION_16_17)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
