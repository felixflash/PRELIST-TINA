package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.db.FinderKitDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.MessageEntity
import com.example.data.model.OrderEntity
import com.example.data.model.RequestEntity
import com.example.data.model.RequestStatus
import com.example.data.model.SecurityUtils
import com.example.data.model.StoreListingEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.network.SupabaseStorageClient
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FinderKitRepository(
    private val database: FinderKitDatabase,
    private val context: Context? = null
) {
    private val userDao = database.userDao()
    private val requestDao = database.requestDao()
    private val storeListingDao = database.storeListingDao()
    private val orderDao = database.orderDao()
    private val messageDao = database.messageDao()
    private val auditLogDao = database.auditLogDao()

    private val prefs by lazy {
        context?.getSharedPreferences("vina_prelist_session", Context.MODE_PRIVATE)
    }

    fun saveSessionUser(userId: String) {
        prefs?.edit()?.putString("session_user_id", userId)?.apply()
    }

    fun clearSessionUser() {
        prefs?.edit()?.remove("session_user_id")?.apply()
    }

    suspend fun getPersistedSessionUser(): UserEntity? {
        val userId = prefs?.getString("session_user_id", null) ?: return null
        return userDao.getUserById(userId)
    }

    suspend fun checkAndSeed() {
        FinderKitDatabase.seedDatabase(database)
        // Purge any pre-existing sample requests, listings and orders to give the user a clean slate
        val sampleRequestIds = listOf("req-001", "req-002", "req-003")
        requestDao.deleteRequestsByIds(sampleRequestIds)
        storeListingDao.deleteListingsByIds(listOf("store-001", "store-003"))
        orderDao.deleteOrdersByIds(listOf("ord-001"))
        messageDao.deleteMessagesByRequestIds(sampleRequestIds)
    }

    // --- Authentication ---
    suspend fun signUp(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPhone = phone.trim()
        val trimmedName = fullName.trim()

        if (trimmedName.isBlank()) return Result.failure(IllegalArgumentException("Full name cannot be empty."))
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (trimmedPhone.isBlank() || trimmedPhone.length < 7) {
            return Result.failure(IllegalArgumentException("Please enter a valid phone number."))
        }

        val passwordError = SecurityUtils.validatePassword(password)
        if (passwordError != null) {
            return Result.failure(IllegalArgumentException(passwordError))
        }

        val existing = userDao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }

        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            email = trimmedEmail,
            fullName = trimmedName,
            phone = trimmedPhone,
            passwordHash = SecurityUtils.hashPassword(password),
            role = UserRole.CUSTOMER
        )

        try {
            userDao.insertUser(user)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "USER",
                entityId = user.id,
                action = "SIGN_UP",
                previousState = "NONE",
                newState = "CUSTOMER_REGISTERED",
                actorEmail = user.email,
                actorRole = "CUSTOMER",
                checksum = SecurityUtils.computeChecksum("${user.id}:${user.email}")
            )
        )

        return Result.success(user)
    }

    suspend fun signIn(email: String, password: String): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        val user = userDao.getUserByEmail(trimmedEmail)
            ?: return Result.failure(IllegalArgumentException("No account found with this email."))

        val hash = SecurityUtils.hashPassword(password)
        if (user.passwordHash != hash) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "USER",
                entityId = user.id,
                action = "SIGN_IN",
                previousState = "ACTIVE",
                newState = "SESSION_AUTHENTICATED",
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum("${user.id}:${System.currentTimeMillis()}")
            )
        )

        return Result.success(user)
    }

    suspend fun getUserByEmail(email: String): UserEntity? = userDao.getUserByEmail(email.trim().lowercase())

    suspend fun resetPassword(email: String, phone: String = "", newPassword: String): Result<Unit> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPhone = phone.trim()

        val user = if (trimmedPhone.isNotBlank()) {
            userDao.getUserByEmailAndPhone(trimmedEmail, trimmedPhone)
        } else {
            userDao.getUserByEmail(trimmedEmail)
        } ?: return Result.failure(IllegalArgumentException("No matching account found for email: $email"))

        val passwordError = SecurityUtils.validatePassword(newPassword)
        if (passwordError != null) {
            return Result.failure(IllegalArgumentException(passwordError))
        }

        val newHash = SecurityUtils.hashPassword(newPassword)
        userDao.updatePassword(user.id, newHash)

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "USER",
                entityId = user.id,
                action = "RESET_PASSWORD",
                previousState = "PASSWORD_RESET_REQUESTED",
                newState = "PASSWORD_UPDATED",
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum(user.id + newHash)
            )
        )

        return Result.success(Unit)
    }

    // --- Two-Factor / Verification Code Authentication ---
    private val activeVerificationCodes = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun sendVerificationCode(emailOrPhone: String, purpose: String): String {
        val cleanTarget = emailOrPhone.trim().lowercase()
        val code = String.format("%06d", (100000..999999).random())
        activeVerificationCodes[cleanTarget] = code

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "AUTH",
                entityId = cleanTarget,
                action = "VERIFICATION_CODE_SENT",
                previousState = "UNVERIFIED",
                newState = "AWAITING_CODE",
                actorEmail = cleanTarget,
                actorRole = purpose,
                checksum = SecurityUtils.computeChecksum("$cleanTarget:$code:$purpose")
            )
        )

        // Attempt real Gmail SMTP email delivery if target is an email address
        if (cleanTarget.contains("@")) {
            val emailResult = com.example.data.network.GmailSmtpClient.sendVerificationEmail(context, cleanTarget, code)
            emailResult.exceptionOrNull()?.let { err ->
                android.util.Log.e("FinderKitRepository", "Gmail SMTP dispatch failed: ${err.message}", err)
            }
        }

        return code
    }

    suspend fun verifyCode(emailOrPhone: String, codeEntered: String): Result<Boolean> {
        val cleanTarget = emailOrPhone.trim().lowercase()
        val cleanCode = codeEntered.trim()
        val expected = activeVerificationCodes[cleanTarget]

        val isValid = expected != null && expected == cleanCode
        if (!isValid) {
            return Result.failure(IllegalArgumentException("Invalid verification code. Please check and try again."))
        }

        activeVerificationCodes.remove(cleanTarget)
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "AUTH",
                entityId = cleanTarget,
                action = "VERIFICATION_CODE_VERIFIED",
                previousState = "AWAITING_CODE",
                newState = "AUTHENTICATED",
                actorEmail = cleanTarget,
                actorRole = "AUTHENTICATED_USER",
                checksum = SecurityUtils.computeChecksum("$cleanTarget:SUCCESS")
            )
        )
        return Result.success(true)
    }

    // --- Requests ---
    fun getAllRequests(): Flow<List<RequestEntity>> = requestDao.getAllRequests()

    fun getRequestsForUser(userId: String): Flow<List<RequestEntity>> =
        requestDao.getRequestsForUser(userId)

    suspend fun getRequestById(id: String): RequestEntity? = requestDao.getRequestById(id)

    suspend fun createRequest(
        user: UserEntity,
        title: String,
        description: String,
        category: String,
        quantity: Int = 1,
        targetBudget: Double = 0.0,
        urgency: String = "",
        imageUrl: String = "",
        shippingMethod: String = com.example.data.model.ShippingConfig.AIR_METHOD,
        transitDays: Int = com.example.data.model.ShippingConfig.AIR_DAYS
    ): Result<RequestEntity> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Title is required."))
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Description is required."))
        val finalQuantity = if (quantity < 1) 1 else quantity

        val finalImageUrl = if (imageUrl.isNotBlank()) {
            SupabaseStorageClient.uploadImageSync(context, imageUrl)
        } else {
            ""
        }

        val request = RequestEntity(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            userEmail = user.email,
            userName = user.fullName,
            title = title.trim(),
            description = description.trim(),
            category = category,
            quantity = finalQuantity,
            targetBudget = targetBudget,
            urgency = urgency,
            imageUrl = finalImageUrl.trim(),
            status = RequestStatus.OPEN,
            shippingMethod = shippingMethod,
            transitDays = transitDays
        )

        requestDao.insertRequest(request)

        messageDao.insertMessage(
            MessageEntity(
                requestId = request.id,
                senderId = user.id,
                senderName = user.fullName,
                senderRole = user.role.name,
                content = "Created new sourcing request for '${request.title}' (Qty: $finalQuantity).",
                isSystemEvent = false
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "REQUEST",
                entityId = request.id,
                action = "CREATE_REQUEST",
                previousState = "NONE",
                newState = RequestStatus.OPEN.name,
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum("${request.id}:${request.title}:$finalQuantity:${request.targetBudget}")
            )
        )

        return Result.success(request)
    }

    suspend fun updateRequestStatus(
        requestId: String,
        newStatus: RequestStatus,
        actor: UserEntity,
        notes: String = ""
    ): Result<Unit> {
        val request = requestDao.getRequestById(requestId)
            ?: return Result.failure(IllegalArgumentException("Request not found."))

        val previousState = request.status.name
        requestDao.updateRequestStatus(requestId, newStatus)

        val messageContent = if (notes.isNotBlank()) {
            "Status updated to ${newStatus.label}. Note: $notes"
        } else {
            "Status moved from ${request.status.label} to ${newStatus.label}."
        }

        messageDao.insertMessage(
            MessageEntity(
                requestId = requestId,
                senderId = actor.id,
                senderName = actor.fullName,
                senderRole = actor.role.name,
                content = messageContent,
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "REQUEST",
                entityId = requestId,
                action = "STATUS_TRANSITION",
                previousState = previousState,
                newState = newStatus.name,
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("$requestId:$previousState:${newStatus.name}")
            )
        )

        return Result.success(Unit)
    }

    // --- Store & Sourcing ---
    fun getAllActiveListings(): Flow<List<StoreListingEntity>> =
        storeListingDao.getAllActiveListings()

    suspend fun getListingForRequest(requestId: String): StoreListingEntity? =
        storeListingDao.getListingByRequestId(requestId)

    suspend fun listInStore(
        requestId: String,
        price: Double,
        quantity: Int,
        batchNumber: String,
        estimatedDelivery: String,
        conditionNotes: String,
        actor: UserEntity
    ): Result<StoreListingEntity> {
        if (price <= 0) return Result.failure(IllegalArgumentException("Price must be greater than 0."))
        if (quantity <= 0) return Result.failure(IllegalArgumentException("Quantity must be at least 1."))
        if (batchNumber.isBlank()) return Result.failure(IllegalArgumentException("Batch number is required."))

        val request = requestDao.getRequestById(requestId)
            ?: return Result.failure(IllegalArgumentException("Request not found."))

        val listing = StoreListingEntity(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            title = request.title,
            description = request.description,
            price = price,
            quantity = quantity,
            batchNumber = batchNumber.trim(),
            estimatedDelivery = estimatedDelivery.trim(),
            conditionNotes = conditionNotes.trim().ifBlank { "Verified Authentic" },
            imageUrl = request.imageUrl,
            isPublished = true
        )

        storeListingDao.insertListing(listing)
        requestDao.updateRequestStatus(requestId, RequestStatus.AWAITING_PAYMENT)

        val messageText = "Item listed in Store at GH₵ ${String.format("%.2f", price)}. Batch: ${listing.batchNumber}."

        messageDao.insertMessage(
            MessageEntity(
                requestId = requestId,
                senderId = actor.id,
                senderName = actor.fullName,
                senderRole = actor.role.name,
                content = messageText,
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "STORE_LISTING",
                entityId = listing.id,
                action = "PUBLISH_STORE_LISTING",
                previousState = request.status.name,
                newState = RequestStatus.AWAITING_PAYMENT.name,
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("${listing.id}:${listing.batchNumber}:$price")
            )
        )

        return Result.success(listing)
    }

    suspend fun deleteStoreListing(listingId: String, actor: UserEntity): Result<Unit> {
        if (actor.role != UserRole.ADMIN) {
            return Result.failure(IllegalAccessException("Only administrators can remove items from the Store."))
        }
        val listing = storeListingDao.getListingById(listingId)
            ?: return Result.failure(IllegalArgumentException("Listing not found."))

        storeListingDao.deleteListingById(listingId)

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "STORE_LISTING",
                entityId = listingId,
                action = "ADMIN_REMOVE_STORE_LISTING",
                previousState = "PUBLISHED",
                newState = "DELETED",
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("${actor.id}:$listingId:${System.currentTimeMillis()}")
            )
        )
        return Result.success(Unit)
    }

    // --- Payments & Orders ---
    fun getAllOrders(): Flow<List<OrderEntity>> = orderDao.getAllOrders()
    fun getOrdersForUser(userId: String): Flow<List<OrderEntity>> = orderDao.getOrdersForUser(userId)
    suspend fun getOrderByRequestId(requestId: String): OrderEntity? = orderDao.getOrderByRequestId(requestId)

    suspend fun processPayment(
        requestId: String,
        storeListingId: String,
        user: UserEntity,
        paymentMethod: String,
        paymentReference: String
    ): Result<OrderEntity> {
        val request = requestDao.getRequestById(requestId)
            ?: return Result.failure(IllegalArgumentException("Request not found."))
        val listing = storeListingDao.getListingById(storeListingId)
            ?: return Result.failure(IllegalArgumentException("Listing not found."))

        val order = OrderEntity(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            storeListingId = storeListingId,
            userId = user.id,
            customerEmail = user.email,
            customerName = user.fullName,
            customerPhone = user.phone,
            itemTitle = listing.title,
            amount = listing.price * listing.quantity,
            paymentReference = paymentReference,
            paymentMethod = paymentMethod,
            batchNumber = listing.batchNumber,
            estimatedDelivery = listing.estimatedDelivery,
            status = RequestStatus.PAID,
            paidAt = System.currentTimeMillis()
        )

        orderDao.insertOrder(order)
        requestDao.updateRequestStatus(requestId, RequestStatus.PAID)

        messageDao.insertMessage(
            MessageEntity(
                requestId = requestId,
                senderId = "system",
                senderName = "FinderKit Payments",
                senderRole = "SYSTEM",
                content = "Payment of GH₵ ${String.format("%.2f", order.amount)} confirmed successfully via $paymentMethod. Reference: $paymentReference. Order is queued for dispatch.",
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "ORDER",
                entityId = order.id,
                action = "PAYMENT_CONFIRMED",
                previousState = RequestStatus.AWAITING_PAYMENT.name,
                newState = RequestStatus.PAID.name,
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum("${order.id}:$paymentReference:${order.amount}")
            )
        )

        return Result.success(order)
    }

    suspend fun markAsShipped(
        orderId: String,
        batchNumber: String,
        estimatedDelivery: String,
        trackingCarrier: String,
        shippingDate: Long,
        actor: UserEntity
    ): Result<Unit> {
        if (actor.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Administrators can update shipping information."))
        }
        val orders = orderDao.getAllOrders()
        // Find order
        val order = orderDao.getOrderByRequestId(orderId) ?: run {
            // Check if orderId was passed directly
            null
        }
        val targetOrder = if (order != null) order else {
            // Find in flow or lookup
            null
        }

        val request = requestDao.getRequestById(orderId)
        val finalRequestId = request?.id ?: orderId
        val existingOrder = orderDao.getOrderByRequestId(finalRequestId)
            ?: return Result.failure(IllegalArgumentException("Order not found."))

        val transitDays = request?.transitDays ?: 18
        val computedExpectedDeliveryDate = shippingDate + (transitDays.toLong() * 24 * 60 * 60 * 1000)

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val formattedExpectedDeliveryDate = formatter.format(java.util.Date(computedExpectedDeliveryDate))

        val updatedOrder = existingOrder.copy(
            batchNumber = batchNumber.ifBlank { existingOrder.batchNumber },
            estimatedDelivery = formattedExpectedDeliveryDate,
            trackingCarrier = trackingCarrier.ifBlank { "FinderKit Express Carrier" },
            status = RequestStatus.SHIPPED,
            shippedAt = shippingDate
        )
        orderDao.updateOrder(updatedOrder)

        if (request != null) {
            requestDao.insertRequest(
                request.copy(
                    shippedAt = shippingDate,
                    expectedDeliveryDate = computedExpectedDeliveryDate,
                    status = RequestStatus.SHIPPED,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            requestDao.updateRequestStatus(finalRequestId, RequestStatus.SHIPPED)
        }

        messageDao.insertMessage(
            MessageEntity(
                requestId = finalRequestId,
                senderId = actor.id,
                senderName = actor.fullName,
                senderRole = actor.role.name,
                content = "Order dispatched! Batch: ${updatedOrder.batchNumber}. Delivery: $formattedExpectedDeliveryDate (Estimated). Carrier: ${updatedOrder.trackingCarrier}.",
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "ORDER",
                entityId = existingOrder.id,
                action = "DISPATCH_SHIPPED",
                previousState = RequestStatus.PAID.name,
                newState = RequestStatus.SHIPPED.name,
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("${existingOrder.id}:${updatedOrder.batchNumber}:SHIPPED")
            )
        )

        return Result.success(Unit)
    }

    suspend fun markAsDelivered(requestId: String, actor: UserEntity): Result<Unit> {
        val existingOrder = orderDao.getOrderByRequestId(requestId)
        if (existingOrder != null) {
            orderDao.updateOrder(
                existingOrder.copy(
                    status = RequestStatus.DELIVERED,
                    deliveredAt = System.currentTimeMillis()
                )
            )
        }
        requestDao.updateRequestStatus(requestId, RequestStatus.DELIVERED)

        messageDao.insertMessage(
            MessageEntity(
                requestId = requestId,
                senderId = actor.id,
                senderName = actor.fullName,
                senderRole = actor.role.name,
                content = "Package confirmed Delivered. Sourcing lifecycle successfully closed.",
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "REQUEST",
                entityId = requestId,
                action = "DELIVERY_CONFIRMED",
                previousState = RequestStatus.SHIPPED.name,
                newState = RequestStatus.DELIVERED.name,
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("$requestId:DELIVERED:${System.currentTimeMillis()}")
            )
        )

        return Result.success(Unit)
    }

    suspend fun markAsReceivedByCustomer(
        requestId: String,
        user: UserEntity,
        notes: String = ""
    ): Result<Unit> {
        val existingOrder = orderDao.getOrderByRequestId(requestId)
        if (existingOrder != null) {
            orderDao.updateOrder(
                existingOrder.copy(
                    status = RequestStatus.DELIVERED,
                    deliveredAt = System.currentTimeMillis()
                )
            )
        }
        requestDao.updateRequestStatus(requestId, RequestStatus.DELIVERED)

        val messageNote = if (notes.isNotBlank()) {
            "🎉 Customer ${user.fullName} confirmed receipt of order: \"$notes\"."
        } else {
            "🎉 Customer ${user.fullName} confirmed receipt of the order. Sourcing cycle complete!"
        }

        messageDao.insertMessage(
            MessageEntity(
                requestId = requestId,
                senderId = user.id,
                senderName = user.fullName,
                senderRole = user.role.name,
                content = messageNote,
                isSystemEvent = true
            )
        )

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "ORDER",
                entityId = requestId,
                action = "CUSTOMER_RECEIVED_CONFIRMED",
                previousState = RequestStatus.SHIPPED.name,
                newState = RequestStatus.DELIVERED.name,
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum("$requestId:CUSTOMER_RECEIVED:${System.currentTimeMillis()}")
            )
        )

        return Result.success(Unit)
    }

    // --- Messages ---
    fun getMessagesForRequest(requestId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForRequest(requestId)

    suspend fun sendMessage(
        requestId: String,
        user: UserEntity,
        content: String
    ): Result<MessageEntity> {
        if (content.isBlank()) return Result.failure(IllegalArgumentException("Message content cannot be blank."))

        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            senderId = user.id,
            senderName = user.fullName,
            senderRole = user.role.name,
            content = content.trim(),
            isSystemEvent = false
        )
        messageDao.insertMessage(msg)
        return Result.success(msg)
    }

    // --- Hubtel Checkout Integration ---
    suspend fun initializeHubtelTransaction(
        email: String,
        amountInCurrency: Double
    ): Result<String> {
        return try {
            val taskId = "VINA-TASK-${java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            val request = com.example.data.network.HubtelPaymentRequest(
                totalAmount = amountInCurrency,
                description = "Payment for prelisted store items",
                callbackUrl = "https://vina-prelist-checkout.completed/callback",
                returnUrl = "https://vina-prelist-checkout.completed/success",
                cancellationUrl = "https://vina-prelist-checkout.completed/cancel",
                merchantTaskId = taskId
            )
            val credentials = "${com.example.BuildConfig.HUBTEL_CLIENT_ID}:${com.example.BuildConfig.HUBTEL_CLIENT_SECRET}"
            val base64 = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
            val authHeader = "Basic $base64"
            val response = com.example.data.network.HubtelClient.apiService.initiateCheckout(
                authHeader = authHeader,
                request = request
            )
            if (response.isSuccessful && response.body()?.data?.checkoutUrl != null) {
                Result.success(response.body()!!.data!!.checkoutUrl!!)
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                val errMsg = if (errBody.contains("message")) errBody else "Failed to initialize with Hubtel (${response.code()})"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRequest(requestId: String, adminUser: UserEntity): Result<Unit> {
        if (adminUser.role != UserRole.ADMIN) {
            return Result.failure(IllegalAccessException("Only administrators can delete requests."))
        }
        val request = requestDao.getRequestById(requestId)
            ?: return Result.failure(IllegalArgumentException("Request not found."))

        requestDao.deleteRequestsByIds(listOf(requestId))
        messageDao.deleteMessagesByRequestIds(listOf(requestId))
        storeListingDao.deleteListingsByRequestId(requestId)

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "REQUEST",
                entityId = requestId,
                action = "ADMIN_DELETE_REQUEST",
                previousState = request.status.name,
                newState = "DELETED",
                actorEmail = adminUser.email,
                actorRole = adminUser.role.name,
                checksum = SecurityUtils.computeChecksum("${adminUser.id}:$requestId:${System.currentTimeMillis()}")
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateRequest(request: RequestEntity, user: UserEntity): Result<Unit> {
        if (request.status != RequestStatus.OPEN) {
            return Result.failure(IllegalAccessException("Requests can only be edited while they are OPEN and not yet reviewed by admin."))
        }
        val existing = requestDao.getRequestById(request.id)
            ?: return Result.failure(IllegalArgumentException("Request not found."))
        if (existing.userId != user.id && user.role != UserRole.ADMIN) {
            return Result.failure(IllegalAccessException("You can only edit your own requests."))
        }
        val updated = request.copy(updatedAt = System.currentTimeMillis())
        requestDao.insertRequest(updated)

        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "REQUEST",
                entityId = request.id,
                action = "UPDATE_REQUEST",
                previousState = existing.status.name,
                newState = updated.status.name,
                actorEmail = user.email,
                actorRole = user.role.name,
                checksum = SecurityUtils.computeChecksum("${user.id}:${request.id}:${System.currentTimeMillis()}")
            )
        )
        return Result.success(Unit)
    }

    // --- Audit & Data Integrity ---
    fun getRecentAuditLogs(): Flow<List<AuditLogEntity>> = auditLogDao.getRecentAuditLogs()
    fun getAuditLogsForEntity(entityId: String): Flow<List<AuditLogEntity>> =
        auditLogDao.getAuditLogsForEntity(entityId)

    suspend fun clearAuditLogs(actor: com.example.data.model.UserEntity): Result<Unit> {
        if (actor.role != com.example.data.model.UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Administrators can clear audit logs."))
        }
        auditLogDao.clearAuditLogs()
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                entityType = "AUDIT",
                entityId = "ALL",
                action = "CLEAR_AUDIT_LOGS",
                previousState = "ACTIVE",
                newState = "CLEARED",
                actorEmail = actor.email,
                actorRole = actor.role.name,
                checksum = SecurityUtils.computeChecksum("CLEAR:${actor.email}")
            )
        )
        return Result.success(Unit)
    }

    data class DataIntegrityReport(
        val totalUsers: Int,
        val totalRequests: Int,
        val totalListings: Int,
        val totalOrders: Int,
        val totalAuditEntries: Int,
        val isConsistent: Boolean,
        val auditChecksum: String
    )

    suspend fun verifyDataIntegrity(): DataIntegrityReport {
        val users = userDao.getUserCount()
        val requests = requestDao.getRequestCount()
        val listings = storeListingDao.getListingCount()
        val orders = orderDao.getOrderCount()
        val audits = auditLogDao.getAuditCount()

        val isConsistent = requests >= orders && requests >= listings && audits > 0
        val summaryString = "u:$users;r:$requests;l:$listings;o:$orders;a:$audits"
        val checksum = SecurityUtils.computeChecksum(summaryString)

        return DataIntegrityReport(
            totalUsers = users,
            totalRequests = requests,
            totalListings = listings,
            totalOrders = orders,
            totalAuditEntries = audits,
            isConsistent = isConsistent,
            auditChecksum = checksum
        )
    }
}
