package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShippingMethodBadge(method: String, transitDays: Int, modifier: Modifier = Modifier) {
    val isAir = method.lowercase() == "air"
    val containerColor = if (isAir) Color(0xFFE3F2FD) else Color(0xFFE0F7FA)
    val textColor = if (isAir) Color(0xFF1565C0) else Color(0xFF006064)
    val label = if (isAir) "AIR" else "SEA"
    
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$transitDays days",
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = textColor
            )
        }
    }
}
