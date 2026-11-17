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
    version = 19,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netmanager_database"
                )
                    .addMigrations(MIGRATION_18_19)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
