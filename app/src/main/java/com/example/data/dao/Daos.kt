package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AuditLogEntity
import com.example.data.model.MessageEntity
import com.example.data.model.OrderEntity
import com.example.data.model.RequestEntity
import com.example.data.model.RequestStatus
import com.example.data.model.StoreListingEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email AND phone = :phone LIMIT 1")
    suspend fun getUserByEmailAndPhone(email: String, phone: String): UserEntity?

    @Query("UPDATE users SET passwordHash = :passwordHash WHERE id = :userId")
    suspend fun updatePassword(userId: String, passwordHash: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface RequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: RequestEntity)

    @Update
    suspend fun updateRequest(request: RequestEntity)

    @Query("SELECT * FROM requests ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE userId = :userId ORDER BY createdAt DESC")
    fun getRequestsForUser(userId: String): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: String): RequestEntity?

    @Query("UPDATE requests SET status = :status, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: RequestStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM requests")
    suspend fun getRequestCount(): Int

    @Query("DELETE FROM requests WHERE id IN (:ids)")
    suspend fun deleteRequestsByIds(ids: List<String>)

    @Query("DELETE FROM requests")
    suspend fun deleteAllRequests()
}

@Dao
interface StoreListingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListing(listing: StoreListingEntity)

    @Update
    suspend fun updateListing(listing: StoreListingEntity)

    @Query("SELECT * FROM store_listings WHERE isPublished = 1 ORDER BY createdAt DESC")
    fun getAllActiveListings(): Flow<List<StoreListingEntity>>

    @Query("SELECT * FROM store_listings WHERE requestId = :requestId LIMIT 1")
    suspend fun getListingByRequestId(requestId: String): StoreListingEntity?

    @Query("SELECT * FROM store_listings WHERE id = :id LIMIT 1")
    suspend fun getListingById(id: String): StoreListingEntity?

    @Query("SELECT COUNT(*) FROM store_listings")
    suspend fun getListingCount(): Int

    @Query("DELETE FROM store_listings WHERE id IN (:ids)")
    suspend fun deleteListingsByIds(ids: List<String>)

    @Query("DELETE FROM store_listings WHERE id = :id")
    suspend fun deleteListingById(id: String)

    @Query("DELETE FROM store_listings WHERE requestId = :requestId")
    suspend fun deleteListingsByRequestId(requestId: String)
}

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders ORDER BY paidAt DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY paidAt DESC")
    fun getOrdersForUser(userId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE requestId = :requestId LIMIT 1")
    suspend fun getOrderByRequestId(requestId: String): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getOrderCount(): Int

    @Query("DELETE FROM orders WHERE id IN (:ids)")
    suspend fun deleteOrdersByIds(ids: List<String>)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE requestId = :requestId ORDER BY timestamp ASC")
    fun getMessagesForRequest(requestId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE requestId IN (:requestIds)")
    suspend fun deleteMessagesByRequestIds(requestIds: List<String>)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE entityId = :entityId ORDER BY timestamp DESC")
    fun getAuditLogsForEntity(entityId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getAuditCount(): Int

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}
