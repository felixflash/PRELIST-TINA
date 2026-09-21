package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.model.RequestStatus
import com.example.data.repository.FinderKitRepository
import com.example.ui.theme.BrandAmberSecondary
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandEmeraldTertiary
import com.example.ui.theme.StatusAwaitingPaymentColor
import com.example.ui.theme.StatusCancelledColor
import com.example.ui.theme.StatusCompletedColor
import com.example.ui.theme.StatusDeliveredColor
import com.example.ui.theme.StatusOpenColor
import com.example.ui.theme.StatusPaidColor
import com.example.ui.theme.StatusPendingColor
import com.example.ui.theme.StatusShippedColor

@Composable
fun FinderKitSealLogo(
    modifier: Modifier = Modifier,
    size: Int = 88,
    onTap: (() -> Unit)? = null
) {
    VinaPrelistLogo(
        modifier = modifier,
        size = size,
        onTap = onTap
    )
}

@Composable
fun VinaPrelistLogo(
    modifier: Modifier = Modifier,
    size: Int = 88,
    onTap: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cornerRadius = (size * 0.22f).dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("vina_prelist_logo")
            .size(size.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White)
            .border(1.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onTap != null,
                onClick = { onTap?.invoke() }
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.vina_prelist_logo),
            contentDescription = "Vina Prelist Logo",
            modifier = Modifier
                .fillMaxSize()
                .padding((size * 0.04f).dp)
                .clip(RoundedCornerShape(cornerRadius * 0.9f))
        )
    }
}

@Composable
fun StatusBadge(
    status: RequestStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        RequestStatus.OPEN -> StatusOpenColor.copy(alpha = 0.14f) to StatusOpenColor
        RequestStatus.PENDING -> StatusPendingColor.copy(alpha = 0.14f) to StatusPendingColor
        RequestStatus.COMPLETED -> StatusCompletedColor.copy(alpha = 0.14f) to StatusCompletedColor
        RequestStatus.AWAITING_PAYMENT -> StatusAwaitingPaymentColor.copy(alpha = 0.14f) to StatusAwaitingPaymentColor
        RequestStatus.PAID -> StatusPaidColor.copy(alpha = 0.14f) to StatusPaidColor
        RequestStatus.SHIPPED -> StatusShippedColor.copy(alpha = 0.14f) to StatusShippedColor
        RequestStatus.DELIVERED -> StatusDeliveredColor.copy(alpha = 0.14f) to StatusDeliveredColor
        RequestStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        RequestStatus.CANCELLED -> StatusCancelledColor.copy(alpha = 0.14f) to StatusCancelledColor
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(100.dp),
        modifier = modifier.testTag("status_badge_${status.name.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TimelineTracker(
    currentStatus: RequestStatus,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        RequestStatus.OPEN to "Requested",
        RequestStatus.PENDING to "Sourcing",
        RequestStatus.AWAITING_PAYMENT to "In Store",
        RequestStatus.PAID to "Paid",
        RequestStatus.SHIPPED to "Shipped",
        RequestStatus.DELIVERED to "Delivered"
    )

    val currentStepIndex = when (currentStatus) {
        RequestStatus.OPEN -> 0
        RequestStatus.PENDING -> 1
        RequestStatus.COMPLETED -> 1 // transitioning to store
        RequestStatus.AWAITING_PAYMENT -> 2
        RequestStatus.PAID -> 3
        RequestStatus.SHIPPED -> 4
        RequestStatus.DELIVERED -> 5
        RequestStatus.UNAVAILABLE -> -2
        RequestStatus.CANCELLED -> -1
    }

    val currentStepLabel = if (currentStepIndex in steps.indices) {
        steps[currentStepIndex].second
    } else if (currentStatus == RequestStatus.UNAVAILABLE) {
        "Item Not Found"
    } else if (currentStatus == RequestStatus.CANCELLED) {
        "Cancelled"
    } else {
        "Processing"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SOURCING & FULFILLMENT TIMELINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentStepIndex >= 0) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Stage ${currentStepIndex + 1} of 6: $currentStepLabel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step dots & connector lines row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, _ ->
                    val isPast = currentStepIndex > index
                    val isCurrent = currentStepIndex == index

                    val circleColor = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isPast -> BrandEmeraldTertiary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }

                    // Circle Node
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(circleColor)
                    ) {
                        if (isPast) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Connector line between nodes
                    if (index < steps.size - 1) {
                        val linePassed = currentStepIndex > index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (linePassed) BrandEmeraldTertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Step labels row with equal weights so every title has maximum room and wraps if needed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                steps.forEachIndexed { index, pair ->
                    val isPast = currentStepIndex > index
                    val isCurrent = currentStepIndex == index

                    val textColor = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isPast -> BrandEmeraldTertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium

                    Text(
                        text = pair.second,
                        fontSize = 9.sp,
                        fontWeight = fontWeight,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp,
                        softWrap = true,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DataIntegrityCard(
    report: FinderKitRepository.DataIntegrityReport?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_integrity_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Data Integrity",
                        tint = BrandEmeraldTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Data Integrity Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = BrandEmeraldTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = if (report?.isConsistent == true) "VERIFIED ACID" else "SYNCED",
                        color = BrandEmeraldTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (report != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IntegrityMetric("Users", "${report.totalUsers}")
                    IntegrityMetric("Requests", "${report.totalRequests}")
                    IntegrityMetric("Listings", "${report.totalListings}")
                    IntegrityMetric("Orders", "${report.totalOrders}")
                    IntegrityMetric("Audit Logs", "${report.totalAuditEntries}")
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cryptographic Audit Checksum: ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = report.auditChecksum,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegrityMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ItemProductImage(
    imageUrl: String?,
    category: String,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16
) {
    val cleanUrl = imageUrl?.trim().orEmpty()
    val (icon, gradientColors) = when {
        category.contains("Electronics", ignoreCase = true) || title.contains("phone", ignoreCase = true) || title.contains("laptop", ignoreCase = true) || title.contains("audio", ignoreCase = true) || title.contains("Sony", ignoreCase = true) ->
            Icons.Default.Devices to listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
        category.contains("Fashion", ignoreCase = true) || category.contains("Apparel", ignoreCase = true) || title.contains("shirt", ignoreCase = true) || title.contains("shoes", ignoreCase = true) || title.contains("jacket", ignoreCase = true) ->
            Icons.Default.Checkroom to listOf(Color(0xFF831843), Color(0xFFEC4899))
        category.contains("Luxury", ignoreCase = true) || title.contains("watch", ignoreCase = true) || title.contains("jewelry", ignoreCase = true) ->
            Icons.Default.Watch to listOf(Color(0xFF78350F), Color(0xFFD97706))
        category.contains("Auto", ignoreCase = true) || title.contains("car", ignoreCase = true) || title.contains("parts", ignoreCase = true) ->
            Icons.Default.DirectionsCar to listOf(Color(0xFF1F2937), Color(0xFF4B5563))
        category.contains("Beauty", ignoreCase = true) || category.contains("Health", ignoreCase = true) || category.contains("Cosmetics", ignoreCase = true) ->
            Icons.Default.Spa to listOf(Color(0xFF064E3B), Color(0xFF10B981))
        category.contains("Home", ignoreCase = true) || category.contains("Living", ignoreCase = true) || category.contains("Kitchen", ignoreCase = true) ->
            Icons.Default.Home to listOf(Color(0xFF4C1D95), Color(0xFF8B5CF6))
        else ->
            Icons.Default.ShoppingBag to listOf(Color(0xFF0F172A), Color(0xFF2563EB))
    }

    val placeholderContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.ifBlank { "Sourced Item" },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (cleanUrl.isNotBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val persistentModel = remember(cleanUrl) {
                if (cleanUrl.startsWith("content://")) {
                    com.example.data.network.SupabaseStorageClient.uploadImageSync(context, cleanUrl)
                } else {
                    cleanUrl
                }
            }

            SubcomposeAsyncImage(
                model = persistentModel,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { placeholderContent() },
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White.copy(alpha = 0.8f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        } else {
            placeholderContent()
        }
    }
}
