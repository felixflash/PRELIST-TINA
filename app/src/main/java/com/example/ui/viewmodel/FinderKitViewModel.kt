package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuditLogEntity
import com.example.data.model.MessageEntity
import com.example.data.model.OrderEntity
import com.example.data.model.RequestEntity
import com.example.data.model.RequestStatus
import com.example.data.model.StoreListingEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.repository.FinderKitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppNavigationTab(val label: String) {
    HOME("Home"),
    REQUESTS("Requests"),
    STORE("Store"),
    PROFILE("Profile")
}

data class FinderKitUiState(
    val selectedTab: AppNavigationTab = AppNavigationTab.HOME,
    val requests: List<RequestEntity> = emptyList(),
    val activeStoreListings: List<StoreListingEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val selectedRequest: RequestEntity? = null,
    val selectedRequestMessages: List<MessageEntity> = emptyList(),
    val selectedRequestOrder: OrderEntity? = null,
    val selectedRequestListing: StoreListingEntity? = null,
    val recentAuditLogs: List<AuditLogEntity> = emptyList(),
    val integrityReport: FinderKitRepository.DataIntegrityReport? = null,
    val searchQuery: String = "",
    val statusFilter: RequestStatus? = null,
    val isNewRequestDialogOpen: Boolean = false,
    val isListInStoreDialogOpen: Boolean = false,
    val isShipOrderDialogOpen: Boolean = false,
    val isCheckoutDialogOpen: Boolean = false,
    val selectedListingForCheckout: StoreListingEntity? = null,
    val hubtelCheckoutUrl: String? = null,
    val isInitializingHubtel: Boolean = false,
    val snackbarMessage: String? = null,
    val isProcessing: Boolean = false
)

class FinderKitViewModel(private val repository: FinderKitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FinderKitUiState())
    val uiState: StateFlow<FinderKitUiState> = _uiState.asStateFlow()

    private var currentUser: UserEntity? = null
    private var requestsJob: Job? = null
    private var ordersJob: Job? = null
    private var messagesJob: Job? = null

    init {
        observeStoreListings()
        observeAuditLogs()
        refreshIntegrityReport()
    }

    fun setUser(user: UserEntity?) {
        currentUser = user
        if (user == null) {
            requestsJob?.cancel()
            ordersJob?.cancel()
            _uiState.update { it.copy(requests = emptyList(), orders = emptyList(), selectedRequest = null) }
            return
        }

        observeRequests(user)
        observeOrders(user)
    }

    private fun observeRequests(user: UserEntity) {
        requestsJob?.cancel()
        requestsJob = viewModelScope.launch {
            val flow = if (user.role == UserRole.ADMIN) {
                repository.getAllRequests()
            } else {
                repository.getRequestsForUser(user.id)
            }
            flow.collectLatest { reqList ->
                _uiState.update { current ->
                    val updatedSelected = reqList.find { it.id == current.selectedRequest?.id } ?: current.selectedRequest
                    current.copy(requests = reqList, selectedRequest = updatedSelected)
                }
            }
        }
    }

    private fun observeOrders(user: UserEntity) {
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            val flow = if (user.role == UserRole.ADMIN) {
                repository.getAllOrders()
            } else {
                repository.getOrdersForUser(user.id)
            }
            flow.collectLatest { ordList ->
                _uiState.update { it.copy(orders = ordList) }
            }
        }
    }

    private fun observeStoreListings() {
        viewModelScope.launch {
            repository.getAllActiveListings().collectLatest { listings ->
                _uiState.update { it.copy(activeStoreListings = listings) }
            }
        }
    }

    private fun observeAuditLogs() {
        viewModelScope.launch {
            repository.getRecentAuditLogs().collectLatest { logs ->
                _uiState.update { it.copy(recentAuditLogs = logs) }
            }
        }
    }

    fun selectTab(tab: AppNavigationTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setStatusFilter(status: RequestStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun selectRequest(request: RequestEntity?) {
        _uiState.update { it.copy(selectedRequest = request) }
        messagesJob?.cancel()
        if (request != null) {
            messagesJob = viewModelScope.launch {
                repository.getMessagesForRequest(request.id).collectLatest { msgs ->
                    _uiState.update { it.copy(selectedRequestMessages = msgs) }
                }
            }
            viewModelScope.launch {
                val order = repository.getOrderByRequestId(request.id)
                val listing = repository.getListingForRequest(request.id)
                _uiState.update { it.copy(selectedRequestOrder = order, selectedRequestListing = listing) }
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedRequestMessages = emptyList(),
                    selectedRequestOrder = null,
                    selectedRequestListing = null
                )
            }
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // Dialog controls
    fun openNewRequestDialog() {
        _uiState.update { it.copy(isNewRequestDialogOpen = true) }
    }

    fun closeNewRequestDialog() {
        _uiState.update { it.copy(isNewRequestDialogOpen = false) }
    }

    fun openListInStoreDialog() {
        _uiState.update { it.copy(isListInStoreDialogOpen = true) }
    }

    fun closeListInStoreDialog() {
        _uiState.update { it.copy(isListInStoreDialogOpen = false) }
    }

    fun openShipOrderDialog() {
        _uiState.update { it.copy(isShipOrderDialogOpen = true) }
    }

    fun closeShipOrderDialog() {
        _uiState.update { it.copy(isShipOrderDialogOpen = false) }
    }

    fun openCheckoutDialog(listing: StoreListingEntity) {
        _uiState.update {
            it.copy(
                isCheckoutDialogOpen = true,
                selectedListingForCheckout = listing,
                isInitializingHubtel = false,
                hubtelCheckoutUrl = null
            )
        }
    }

    fun closeCheckoutDialog() {
        _uiState.update {
            it.copy(
                isCheckoutDialogOpen = false,
                selectedListingForCheckout = null,
                hubtelCheckoutUrl = null,
                isInitializingHubtel = false
            )
        }
    }

    // Business Actions
    fun createRequest(
        title: String,
        description: String,
        category: String,
        quantity: Int = 1,
        targetBudget: Double = 0.0,
        urgency: String = "",
        imageUrl: String = "",
        shippingMethod: String = com.example.data.model.ShippingConfig.AIR_METHOD,
        transitDays: Int = com.example.data.model.ShippingConfig.AIR_DAYS
    ) {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = repository.createRequest(
                user = user,
                title = title,
                description = description,
                category = category,
                quantity = quantity,
                targetBudget = targetBudget,
                urgency = urgency,
                imageUrl = imageUrl,
                shippingMethod = shippingMethod,
                transitDays = transitDays
            )
            result.fold(
                onSuccess = { req ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isNewRequestDialogOpen = false,
                            snackbarMessage = "Sourcing request for '${req.title}' (Qty: ${req.quantity}) submitted!"
                        )
                    }
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to create request."
                        )
                    }
                }
            )
        }
    }

    fun updateRequest(updatedRequest: RequestEntity) {
        val user = currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = repository.updateRequest(updatedRequest, user)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            selectedRequest = updatedRequest,
                            snackbarMessage = "Request updated successfully."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to update request."
                        )
                    }
                }
            )
        }
    }

    fun deleteRequest(requestId: String) {
        val user = currentUser ?: return
        if (user.role != UserRole.ADMIN) {
            showSnackbar("Only administrators can delete requests.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = repository.deleteRequest(requestId, user)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            selectedRequest = null,
                            snackbarMessage = "Sourcing request deleted successfully."
                        )
                    }
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to delete request."
                        )
                    }
                }
            )
        }
    }

    fun updateStatus(requestId: String, newStatus: RequestStatus, notes: String = "") {
        val user = currentUser ?: return
        viewModelScope.launch {
            val result = repository.updateRequestStatus(requestId, newStatus, user, notes)
            result.fold(
                onSuccess = {
                    showSnackbar("Status updated to ${newStatus.label}.")
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    showSnackbar(err.message ?: "Could not update status.")
                }
            )
        }
    }

    fun markItemNotFound(requestId: String, reason: String, suggestions: String = "") {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val messageContent = buildString {
                append("📢 SOURCING UPDATE: Item could not be located.\n\n")
                append("Reason: $reason\n")
                if (suggestions.isNotBlank()) {
                    append("\nRecommended Next Steps / Alternatives:\n$suggestions")
                }
            }
            val result = repository.updateRequestStatus(
                requestId = requestId,
                newStatus = RequestStatus.UNAVAILABLE,
                actor = user,
                notes = "Item Not Found: $reason"
            )
            repository.sendMessage(requestId, user, messageContent)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = "Customer notified that item could not be sourced."
                        )
                    }
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to update status."
                        )
                    }
                }
            )
        }
    }

    fun listInStore(
        requestId: String,
        price: Double,
        quantity: Int,
        batchNumber: String,
        estimatedDelivery: String,
        conditionNotes: String
    ) {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = repository.listInStore(
                requestId = requestId,
                price = price,
                quantity = quantity,
                batchNumber = batchNumber,
                estimatedDelivery = estimatedDelivery,
                conditionNotes = conditionNotes,
                actor = user
            )
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isListInStoreDialogOpen = false,
                            snackbarMessage = "Item published to Store at GH₵ ${String.format("%.2f", listing.price)}!"
                        )
                    }
                    selectRequest(_uiState.value.selectedRequest)
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to list item in Store."
                        )
                    }
                }
            )
        }
    }

    fun deleteStoreListing(listingId: String) {
        val user = currentUser ?: return
        if (user.role != UserRole.ADMIN) {
            _uiState.update { it.copy(snackbarMessage = "Only admins can remove items from the Store.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = repository.deleteStoreListing(listingId, user)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = "Item removed from Store successfully."
                        )
                    }
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to remove item from Store."
                        )
                    }
                }
            )
        }
    }

    fun processPayment(listing: StoreListingEntity, paymentMethod: String, paymentRef: String? = null) {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val txRef = paymentRef ?: "HUB-TXN-${(100000..999999).random()}-${listing.batchNumber.takeLast(4)}"
            val result = repository.processPayment(
                requestId = listing.requestId,
                storeListingId = listing.id,
                user = user,
                paymentMethod = paymentMethod,
                paymentReference = txRef
            )
            result.fold(
                onSuccess = { order ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckoutDialogOpen = false,
                            snackbarMessage = "Payment successful! Order reference: ${order.paymentReference}"
                        )
                    }
                    selectRequest(_uiState.value.selectedRequest)
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Payment processing failed."
                        )
                    }
                }
            )
        }
    }

    fun markAsShipped(
        orderId: String,
        batchNumber: String,
        estimatedDelivery: String,
        trackingCarrier: String,
        shippingDate: Long
    ) {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = repository.markAsShipped(
                orderId = orderId,
                batchNumber = batchNumber,
                estimatedDelivery = estimatedDelivery,
                trackingCarrier = trackingCarrier,
                shippingDate = shippingDate,
                actor = user
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isShipOrderDialogOpen = false,
                            snackbarMessage = "Order marked as dispatched and shipped!"
                        )
                    }
                    selectRequest(_uiState.value.selectedRequest)
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Failed to update shipment status."
                        )
                    }
                }
            )
        }
    }

    fun markAsDelivered(requestId: String) {
        val user = currentUser ?: return
        viewModelScope.launch {
            val result = repository.markAsDelivered(requestId, user)
            result.fold(
                onSuccess = {
                    showSnackbar("Item confirmed Delivered! Sourcing completed.")
                    selectRequest(_uiState.value.selectedRequest)
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    showSnackbar(err.message ?: "Could not confirm delivery.")
                }
            )
        }
    }

    fun markAsReceivedByCustomer(requestId: String, notes: String = "") {
        val user = currentUser ?: return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = repository.markAsReceivedByCustomer(requestId, user, notes)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = "Order confirmed received! Thank you."
                        )
                    }
                    selectRequest(_uiState.value.selectedRequest)
                    refreshIntegrityReport()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            snackbarMessage = err.message ?: "Could not confirm receipt."
                        )
                    }
                }
            )
        }
    }

    fun sendMessage(content: String) {
        val user = currentUser ?: return
        val req = _uiState.value.selectedRequest ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(
                requestId = req.id,
                user = user,
                content = content
            )
        }
    }

    fun refreshIntegrityReport() {
        viewModelScope.launch {
            val report = repository.verifyDataIntegrity()
            _uiState.update { it.copy(integrityReport = report) }
        }
    }

    fun clearAuditLogs() {
        val user = currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.clearAuditLogs(user)
            _uiState.update { it.copy(isProcessing = false) }
            refreshIntegrityReport()
        }
    }
}
