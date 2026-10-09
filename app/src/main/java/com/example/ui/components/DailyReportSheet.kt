package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyConversationReport
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonPurple
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyReportSheet(
    report: DailyConversationReport,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = SanaNeonCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Daily Conversation Report",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = report.dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = SanaNeonCyan
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Exchange Count",
                        color = SanaTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${report.totalInteractions} Messages",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(SanaNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, SanaNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Zero Raw Audio Retained",
                        color = SanaNeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Topics Discussed
        Text(
            text = "Topics Discussed",
            color = SanaNeonPink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            report.topicsDiscussed.forEach { topic ->
                Box(
                    modifier = Modifier
                        .background(SanaNeonPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, SanaNeonPink.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(topic, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Time Sections
        Text(
            text = "Time Sections",
            color = SanaNeonCyan,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        report.timeSections.forEach { section ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = SanaNeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(section, color = Color.White, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Key Points & Insights
        Text(
            text = "Key Points & Insights",
            color = SanaNeonPurple,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        report.keyPoints.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = SanaNeonPurple, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(point, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Action Items & Plans
        Text(
            text = "Plans & Action Items",
            color = SanaNeonGreen,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        report.actionItemsAndPlans.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SanaNeonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(action, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
