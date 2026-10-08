package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AuditLogDao
import com.example.data.dao.MessageDao
import com.example.data.dao.OrderDao
import com.example.data.dao.RequestDao
import com.example.data.dao.StoreListingDao
import com.example.data.dao.UserDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.MessageEntity
import com.example.data.model.OrderEntity
import com.example.data.model.RequestEntity
import com.example.data.model.RequestStatus
import com.example.data.model.SecurityUtils
import com.example.data.model.StoreListingEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        RequestEntity::class,
        StoreListingEntity::class,
        OrderEntity::class,
        MessageEntity::class,
        AuditLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FinderKitDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun requestDao(): RequestDao
    abstract fun storeListingDao(): StoreListingDao
    abstract fun orderDao(): OrderDao
    abstract fun messageDao(): MessageDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: FinderKitDatabase? = null

        fun getDatabase(context: Context): FinderKitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinderKitDatabase::class.java,
                    "finderkit_database"
                )
                .fallbackToDestructiveMigration(true)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDatabase(database)
                    }
                }
            }
        }

        suspend fun seedDatabase(db: FinderKitDatabase) {
            val userDao = db.userDao()
            val requestDao = db.requestDao()
            val listingDao = db.storeListingDao()
            val orderDao = db.orderDao()
            val messageDao = db.messageDao()
            val auditDao = db.auditLogDao()

            if (userDao.getUserCount() > 0) return

            // 1. Seed Admin user (admin@finderkit.app / Admin123!)
            val adminUser = UserEntity(
                id = "admin-root-001",
                email = "admin@finderkit.app",
                fullName = "FinderKit Operations Admin",
                phone = "+1234567890",
                passwordHash = SecurityUtils.hashPassword("Admin123!"),
                role = UserRole.ADMIN
            )
            userDao.insertUser(adminUser)
            auditDao.insertAuditLog(
                AuditLogEntity(
                    entityType = "USER",
                    entityId = adminUser.id,
                    action = "SEED_ADMIN",
                    previousState = "NONE",
                    newState = "ADMIN_CREATED",
                    actorEmail = "system@finderkit.internal",
                    actorRole = "SYSTEM",
                    checksum = SecurityUtils.computeChecksum(adminUser.email)
                )
            )

            // 2. Seed Sample Customer (customer@example.com / Customer123!)
            val customerUser = UserEntity(
                id = "cust-demo-001",
                email = "customer@example.com",
                fullName = "Alex Mercer",
                phone = "+1987654321",
                passwordHash = SecurityUtils.hashPassword("Customer123!"),
                role = UserRole.CUSTOMER
            )
            userDao.insertUser(customerUser)
        }
    }
}
