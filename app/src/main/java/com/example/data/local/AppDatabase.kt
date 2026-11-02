package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        RouterTaskEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netmanager_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
