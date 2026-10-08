package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VinaPrelistLogo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.example.data.db.FinderKitDatabase
import com.example.data.repository.FinderKitRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RequestsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.screens.dialogs.CheckoutDialog
import com.example.ui.screens.dialogs.ListInStoreDialog
import com.example.ui.screens.dialogs.NewRequestDialog
import com.example.ui.screens.dialogs.ShipOrderDialog
import com.example.ui.theme.FinderKitTheme
import com.example.ui.viewmodel.AppNavigationTab
import com.example.ui.viewmodel.AuthMode
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FinderKitViewModel

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinderKitTheme {
                FinderKitApp()
            }
        }
    }
}

@Composable
fun FinderKitApp() {
    val context = LocalContext.current
    val database = remember { FinderKitDatabase.getDatabase(context) }
    val repository = remember { FinderKitRepository(database, context.applicationContext) }

    val authViewModel: AuthViewModel = viewModel { AuthViewModel(repository) }
    val finderKitViewModel: FinderKitViewModel = viewModel { FinderKitViewModel(repository) }

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val appState by finderKitViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val activity = context as? Activity
    var lastBackPressTime by remember { mutableStateOf(0L) }
    var isSplashFinished by remember { mutableStateOf(false) }

    // Sync user session to main viewmodel
    LaunchedEffect(authState.currentUser) {
        finderKitViewModel.setUser(authState.currentUser)
    }

    // Handle snackbar messages
    LaunchedEffect(appState.snackbarMessage) {
        val msg = appState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            finderKitViewModel.dismissSnackbar()
        }
    }

    val currentUser = authState.currentUser

    // Handle back button navigation & double press to exit
    BackHandler {
        if (currentUser == null) {
            if (authState.authMode != AuthMode.SIGN_IN) {
                authViewModel.setAuthMode(AuthMode.SIGN_IN)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    activity?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            when {
                appState.isCheckoutDialogOpen -> {
                    finderKitViewModel.closeCheckoutDialog()
                }
                appState.isNewRequestDialogOpen -> {
                    finderKitViewModel.closeNewRequestDialog()
                }
                appState.isListInStoreDialogOpen -> {
                    finderKitViewModel.closeListInStoreDialog()
                }
                appState.isShipOrderDialogOpen -> {
                    finderKitViewModel.closeShipOrderDialog()
                }
                appState.selectedRequest != null -> {
                    finderKitViewModel.selectRequest(null)
                }
                appState.selectedTab != AppNavigationTab.HOME -> {
                    finderKitViewModel.selectTab(AppNavigationTab.HOME)
                }
                else -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000) {
                        activity?.finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AnimatedContent(
        targetState = isSplashFinished,
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
        },
        label = "SplashTransition"
    ) { splashFinished ->
        if (!splashFinished) {
            SplashScreen(
                onSplashFinished = { isSplashFinished = true },
                modifier = Modifier.fillMaxSize()
            )
        } else if (currentUser == null) {
            AuthScreen(
                viewModel = authViewModel,
                state = authState,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (appState.selectedRequest == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .testTag("app_top_bar")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VinaPrelistLogo(size = 36)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Vina Prelist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Online Sourcing & Direct Import",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (currentUser.role == com.example.data.model.UserRole.ADMIN) {
                                Surface(
                                    color = com.example.ui.theme.BrandAmberSecondary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.BrandAmberSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Show bottom bar when not in detail view to keep clean focused reading
                if (appState.selectedRequest == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("main_bottom_nav")
                    ) {
                        val tabs = listOf(
                            Triple(AppNavigationTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
                            Triple(AppNavigationTab.REQUESTS, Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
                            Triple(AppNavigationTab.STORE, Icons.Filled.Store, Icons.Outlined.Store),
                            Triple(AppNavigationTab.PROFILE, Icons.Filled.Person, Icons.Outlined.Person)
                        )

                        tabs.forEach { (tab, filledIcon, outlinedIcon) ->
                            val selected = appState.selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { finderKitViewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) filledIcon else outlinedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (appState.selectedTab) {
                    AppNavigationTab.HOME -> HomeScreen(
                        user = currentUser,
                        viewModel = finderKitViewModel,
                        state = appState
                    )
                    AppNavigationTab.REQUESTS -> RequestsScreen(
                        user = currentUser,
                        viewModel = finderKitViewModel,
                        state = appState
                    )
                    AppNavigationTab.STORE -> StoreScreen(
                        user = currentUser,
                        viewModel = finderKitViewModel,
                        state = appState
                    )
                    AppNavigationTab.PROFILE -> ProfileScreen(
                        user = currentUser,
                        onSignOut = { authViewModel.signOut() },
                        viewModel = finderKitViewModel,
                        state = appState
                    )
                }

                // Global Dialogs
                if (appState.isNewRequestDialogOpen) {
                    NewRequestDialog(
                        onDismiss = { finderKitViewModel.closeNewRequestDialog() },
                        onSubmit = { title, desc, cat, qty, imgUrl, shippingMethod, transitDays ->
                            finderKitViewModel.createRequest(
                                title = title,
                                description = desc,
                                category = cat,
                                quantity = qty,
                                imageUrl = imgUrl,
                                shippingMethod = shippingMethod,
                                transitDays = transitDays
                            )
                        },
                        isProcessing = appState.isProcessing
                    )
                }

                if (appState.isListInStoreDialogOpen && appState.selectedRequest != null) {
                    ListInStoreDialog(
                        request = appState.selectedRequest!!,
                        onDismiss = { finderKitViewModel.closeListInStoreDialog() },
                        onSubmit = { price, quantity, batch, delivery, notes ->
                            finderKitViewModel.listInStore(
                                requestId = appState.selectedRequest!!.id,
                                price = price,
                                quantity = quantity,
                                batchNumber = batch,
                                estimatedDelivery = delivery,
                                conditionNotes = notes
                            )
                        },
                        isProcessing = appState.isProcessing
                    )
                }

                if (appState.isShipOrderDialogOpen && appState.selectedRequest != null) {
                    val defaultBatch = appState.selectedRequestListing?.batchNumber ?: ""
                    ShipOrderDialog(
                        request = appState.selectedRequest!!,
                        batchDefault = defaultBatch,
                        onDismiss = { finderKitViewModel.closeShipOrderDialog() },
                        onConfirmShipment = { batch, delivery, carrier, shippingDate ->
                            finderKitViewModel.markAsShipped(
                                orderId = appState.selectedRequest!!.id,
                                batchNumber = batch,
                                estimatedDelivery = delivery,
                                trackingCarrier = carrier,
                                shippingDate = shippingDate
                            )
                        },
                        isProcessing = appState.isProcessing
                    )
                }

                if (appState.isCheckoutDialogOpen && appState.selectedListingForCheckout != null) {
                    CheckoutDialog(
                        listing = appState.selectedListingForCheckout!!,
                        customerEmail = currentUser.email,
                        customerPhone = currentUser.phone,
                        hubtelCheckoutUrl = appState.hubtelCheckoutUrl,
                        isInitializingHubtel = appState.isInitializingHubtel,
                        onDismiss = { finderKitViewModel.closeCheckoutDialog() },
                        onPaymentSuccess = { method, ref ->
                            finderKitViewModel.processPayment(
                                listing = appState.selectedListingForCheckout!!,
                                paymentMethod = method,
                                paymentRef = ref
                            )
                        },
                        isProcessing = appState.isProcessing
                    )
                }
            }
        }
    }
}
}
