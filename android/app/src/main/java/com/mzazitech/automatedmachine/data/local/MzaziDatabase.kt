package com.mzazitech.automatedmachine.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BundleEntity::class,
        TransactionEntity::class,
        UssdJobEntity::class,
        UssdWorkflowEntity::class,
        SettingEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MzaziDatabase : RoomDatabase() {

    abstract fun bundleDao(): BundleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun ussdJobDao(): UssdJobDao
    abstract fun workflowDao(): UssdWorkflowDao
    abstract fun settingsDao(): SettingsDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun dashboardDao(): DashboardDao

    companion object {
        private const val DB_NAME = "mzazi_machine.db"

        @Volatile
        private var instance: MzaziDatabase? = null

        fun get(context: Context): MzaziDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MzaziDatabase::class.java,
                    DB_NAME
                )
                    // v1 is the first shipped schema. Future versions must add explicit
                    // migrations here; destructive fallback is deliberately not enabled.
                    .build()
                    .also { instance = it }
            }
    }
}
