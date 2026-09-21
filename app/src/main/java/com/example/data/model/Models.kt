package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.util.UUID

enum class UserRole {
    CUSTOMER,
    ADMIN
}

enum class RequestStatus(val label: String, val stepIndex: Int) {
    OPEN("Open", 0),
    PENDING("Sourcing / Pending", 1),
    COMPLETED("Sourced / Ready", 2),
    AWAITING_PAYMENT("Listed in Store", 3),
    PAID("Paid", 4),
    SHIPPED("Dispatched / Shipped", 5),
    DELIVERED("Delivered", 6),
    UNAVAILABLE("Item Not Found", -2),
    CANCELLED("Cancelled", -1)
}

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val email: String,
    val fullName: String,
    val phone: String,
    val passwordHash: String,
    val role: UserRole = UserRole.CUSTOMER,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "requests",
    indices = [Index(value = ["userId"]), Index(value = ["status"])]
)
data class RequestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userEmail: String,
    val userName: String,
    val title: String,
    val description: String,
    val category: String,
    val quantity: Int = 1,
    val targetBudget: Double = 0.0,
    val urgency: String = "",
    val imageUrl: String = "",
    val status: RequestStatus = RequestStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "store_listings",
    indices = [Index(value = ["requestId"], unique = true)]
)
data class StoreListingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val title: String,
    val description: String,
    val price: Double,
    val quantity: Int,
    val batchNumber: String,
    val estimatedDelivery: String,
    val conditionNotes: String = "Brand New / Certified Authentic",
    val imageUrl: String = "",
    val isPublished: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    indices = [Index(value = ["requestId"]), Index(value = ["userId"]), Index(value = ["paymentReference"], unique = true)]
)
data class OrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val storeListingId: String,
    val userId: String,
    val customerEmail: String,
    val customerName: String,
    val customerPhone: String,
    val itemTitle: String,
    val amount: Double,
    val paymentReference: String,
    val paymentMethod: String,
    val batchNumber: String,
    val estimatedDelivery: String,
    val trackingCarrier: String = "FinderKit Priority Logistics",
    val status: RequestStatus = RequestStatus.PAID,
    val paidAt: Long = System.currentTimeMillis(),
    val shippedAt: Long? = null,
    val deliveredAt: Long? = null
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["requestId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val content: String,
    val isSystemEvent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["entityId"]), Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val action: String,
    val previousState: String,
    val newState: String,
    val actorEmail: String,
    val actorRole: String,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SecurityUtils {
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun computeChecksum(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.take(4).joinToString("") { "%02x".format(it) }
    }

    /**
     * FinderKit Password Rules:
     * - Minimum 8 characters
     * - At least one letter
     * - At least one number
     * - At least one special character
     */
    fun validatePassword(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters long."
        if (!password.any { it.isLetter() }) return "Password must include at least one letter."
        if (!password.any { it.isDigit() }) return "Password must include at least one number."
        val specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?/~`"
        if (!password.any { it in specialChars }) return "Password must include at least one special character."
        return null
    }
}
