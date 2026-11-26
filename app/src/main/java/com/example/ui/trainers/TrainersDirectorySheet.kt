package com.example.ui.trainers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class TrainerProfile(
    val id: String,
    val name: String,
    val title: String,
    val specialties: List<String>,
    val floorSchedule: String,
    val isAvailableNow: Boolean,
    val experienceYears: Int,
    val bio: String,
    val avatarInitial: String,
    val badgeColor: Color,
    val consultationTip: String
)

object TrainersData {
    val availableTrainers = listOf(
        TrainerProfile(
            id = "cobie",
            name = "Coach Cobie",
            title = "Head Strength & Hypertrophy Coach",
            specialties = listOf("Hypertrophy", "Compound Lifts", "Biomechanics & Form", "Injury Prevention"),
            floorSchedule = "Mon-Sat: 6:00 AM - 2:00 PM",
            isAvailableNow = true,
            experienceYears = 8,
            bio = "Coach Cobie specializes in helping gym beginners and intermediate lifters build clean muscle through biomechanically sound form, progressive overload, and joint-friendly training.",
            avatarInitial = "CC",
            badgeColor = FlameOrange,
            consultationTip = "Ask Coach Cobie about perfecting your push-up and squat mechanics or setting up your first muscle-building split!"
        ),
        TrainerProfile(
            id = "jun",
            name = "Coach Jun",
            title = "Functional Conditioning & Mobility Specialist",
            specialties = listOf("HIIT & Fat Loss", "Core Stability", "Functional Mobility", "Athletic Conditioning"),
            floorSchedule = "Mon-Sat: 1:00 PM - 9:00 PM",
            isAvailableNow = true,
            experienceYears = 6,
            bio = "Coach Jun focuses on high-energy conditioning, explosive endurance, and posture correction. He is on the gym floor to help members stay injury-free and boost cardiovascular capacity.",
            avatarInitial = "CJ",
            badgeColor = ElectricCyan,
            consultationTip = "Ask Coach Jun about dynamic warm-up drills, core stability routines, or high-intensity interval pacing!"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainersDirectorySheet(
    onDismiss: () -> Unit,
    onConsultWithAi: (trainerName: String, topic: String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(FlameOrange.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Available Gym Trainers",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Find on-duty coaches for guidance & spot checks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_trainers_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice Banner for Gym Members
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, FlameOrange.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = FlameOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Our certified coaches are stationed on the gym floor. Feel free to approach them anytime for form checks, spot assistance, or routine adjustments!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(TrainersData.availableTrainers) { trainer ->
                    TrainerCard(
                        trainer = trainer,
                        onConsult = {
                            onConsultWithAi(trainer.name, trainer.specialties.first())
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrainerCard(
    trainer: TrainerProfile,
    onConsult: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trainer_card_${trainer.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Profile Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trainer Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(trainer.badgeColor, trainer.badgeColor.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trainer.avatarInitial,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trainer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Availability status badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(SuccessGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Available Now",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }

                    Text(
                        text = trainer.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = trainer.badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = trainer.floorSchedule,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bio
            Text(
                text = trainer.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specialties Chips
            Text(
                text = "Specialties:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                trainer.specialties.take(3).forEach { spec ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = spec,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button
            Button(
                onClick = onConsult,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("consult_trainer_button_${trainer.id}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = trainer.badgeColor)
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ask AI Assistant About ${trainer.name}'s Routine",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
