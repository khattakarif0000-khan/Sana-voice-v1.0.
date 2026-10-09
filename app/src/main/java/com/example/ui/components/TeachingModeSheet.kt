package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TeachingSessionState
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachingModeSheet(
    teachingSession: TeachingSessionState,
    onStartTeaching: (subject: String, level: String, language: String) -> Unit,
    onExitTeaching: () -> Unit,
    onDismiss: () -> Unit
) {
    val subjects = listOf(
        "Mathematics (ریاضی)",
        "Science (سائنس)",
        "English (انگریزی)",
        "General Knowledge (معلومات عامہ)",
        "Computer Science (کمپیوٹر)",
        "Islamic Studies (اسلامیات)"
    )

    val levels = listOf(
        "Beginner (بنیادی)",
        "Intermediate (درمیانہ)",
        "Advanced (اعلیٰ)"
    )

    val languages = listOf(
        "Pakistani Urdu (اردو)",
        "English",
        "Roman Urdu"
    )

    var selectedSubject by remember { mutableStateOf(teachingSession.subject) }
    var selectedLevel by remember { mutableStateOf(teachingSession.level) }
    var selectedLanguage by remember { mutableStateOf(teachingSession.language) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = SanaNeonCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Personal Teaching Mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ذاتی استاد سیشن — Step-by-Step Learning",
                        style = MaterialTheme.typography.bodySmall,
                        color = SanaTextSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active State Banner if in session
        if (teachingSession.isTeachingActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SanaNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .border(1.dp, SanaNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE SESSION: ${teachingSession.subject}",
                            color = SanaNeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Step ${teachingSession.step}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Level: ${teachingSession.level} • Language: ${teachingSession.language}",
                        color = SanaTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onExitTeaching()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SanaNeonRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exit Teaching Mode (پڑھائی ختم کریں)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Switch Subject or Configure New Session:",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 1. Subject Selection
        Text(text = "1. Choose Subject (مضمون منتخب کریں)", color = SanaNeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subjects.forEach { subj ->
                val isSelected = selectedSubject == subj
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) SanaNeonCyan.copy(alpha = 0.25f) else SanaSurfaceElevated,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) SanaNeonCyan else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedSubject = subj }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = subj,
                        color = if (isSelected) Color.White else SanaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Learning Level
        Text(text = "2. Learning Level (سطح علم)", color = SanaNeonPink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levels.forEach { lvl ->
                val isSelected = selectedLevel == lvl
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) SanaNeonPink.copy(alpha = 0.25f) else SanaSurfaceElevated,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) SanaNeonPink else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedLevel = lvl }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lvl,
                        color = if (isSelected) Color.White else SanaTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Language
        Text(text = "3. Instruction Language (زبان)", color = SanaNeonGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languages.forEach { lang ->
                val isSelected = selectedLanguage == lang
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) SanaNeonGreen.copy(alpha = 0.25f) else SanaSurfaceElevated,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) SanaNeonGreen else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedLanguage = lang }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lang,
                        color = if (isSelected) Color.White else SanaTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Button
        Button(
            onClick = {
                onStartTeaching(selectedSubject, selectedLevel, selectedLanguage)
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = SanaNeonCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (teachingSession.isTeachingActive) "Update Teaching Session" else "Start Personal Teaching (پڑھائی شروع کریں)",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
