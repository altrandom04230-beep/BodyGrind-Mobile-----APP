package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.UserProfileEntity
import com.example.ui.theme.appSwitchColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndHomeSettingsScreen(
    userProfile: UserProfileEntity,
    currentThemeMode: String = "LIGHT",
    onThemeModeChange: (String) -> Unit = {},
    onUpdateProfile: (UserProfileEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Look & Home Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("look_home_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Theme Mode (Dark / Light / System) Segmented Control
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Theme Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Control
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "System")
                        modes.forEachIndexed { index, (modeKey, label) ->
                            SegmentedButton(
                                selected = currentThemeMode == modeKey,
                                onClick = {
                                    onThemeModeChange(modeKey)
                                    onUpdateProfile(userProfile.copy(darkThemeMode = modeKey))
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                modifier = Modifier.testTag("theme_segment_$modeKey")
                            ) {
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 2. "On Home": Gym Check-in Pass Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Gym Check-in Pass On Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = userProfile.gymCheckinEnabled,
                            onCheckedChange = { onUpdateProfile(userProfile.copy(gymCheckinEnabled = it)) },
                            colors = appSwitchColors(),
                            modifier = Modifier.testTag("gym_checkin_toggle")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
