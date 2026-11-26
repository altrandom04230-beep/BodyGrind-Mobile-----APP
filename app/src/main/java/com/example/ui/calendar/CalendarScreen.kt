package com.example.ui.calendar

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ui.calendar.reminder.CalendarEventReminderScheduler
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutScheduleEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.scheduling.ScheduledSessionDoc
import com.example.data.scheduling.TrainerAvailabilityDoc
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    sessions: List<WorkoutSessionEntity>,
    schedules: List<WorkoutScheduleEntity>,
    calendarEvents: List<CalendarEventEntity>,
    calendarExceptions: List<CalendarEventExceptionEntity>,
    allPlans: List<WorkoutPlanEntity>,
    streakDays: Int,
    onToggleSchedule: (WorkoutScheduleEntity) -> Unit,
    onUpdateSchedule: (WorkoutScheduleEntity) -> Unit,
    onSaveEvent: (CalendarEventEntity) -> Unit,
    onUpdateEvent: (CalendarEventEntity) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onEditThisDayOnly: (Long, String, CalendarEventEntity) -> Unit,
    onDeleteThisDayOnly: (Long, String) -> Unit,
    calendarErrorMessage: String? = null,
    onClearCalendarErrorMessage: () -> Unit = {},
    onRestoreEvent: (CalendarEventEntity) -> Unit = {},
    onRestoreSeries: (CalendarEventEntity, List<CalendarEventExceptionEntity>) -> Unit = { _, _ -> },
    onUndoDeleteThisDayOnly: (Long, String) -> Unit = { _, _ -> },
    onLoadExceptionsForEvent: suspend (Long) -> List<CalendarEventExceptionEntity> = { emptyList() },
    onStartAssignedPlan: (WorkoutPlanEntity) -> Unit = {},
    trainerAvailability: TrainerAvailabilityDoc? = null,
    scheduledSessions: List<ScheduledSessionDoc> = emptyList(),
    activeCoachName: String? = null
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val today = ManilaDateTime.todayManila()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(calendarErrorMessage) {
        calendarErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            onClearCalendarErrorMessage()
        }
    }

    // Date state surviving rotation
    var selectedDateEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var currentYearMonthString by rememberSaveable { mutableStateOf(ManilaDateTime.currentYearMonthManila().toString()) }

    val selectedLocalDate = remember(selectedDateEpochDay) { LocalDate.ofEpochDay(selectedDateEpochDay) }
    val currentYearMonth = remember(currentYearMonthString) {
        try { YearMonth.parse(currentYearMonthString) } catch (e: DateTimeParseException) { ManilaDateTime.currentYearMonthManila() }
    }

    // Bottom sheet state
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var editingEventId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Reminder Permission Dialog States
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showExactAlarmPermissionDialog by rememberSaveable { mutableStateOf(false) }

    val checkExactAlarmPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canSchedule = alarmManager?.canScheduleExactAlarms() ?: true
            if (!canSchedule && !CalendarEventReminderScheduler.hasOptedOutOfExactAlarm(context)) {
                showExactAlarmPermissionDialog = true
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showNotificationPermissionDialog = true
        } else {
            checkExactAlarmPermission()
        }
    }

    val checkReminderPermissions: (Int) -> Unit = { reminderMinutesBefore ->
        if (reminderMinutesBefore >= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotificationPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasNotificationPermission) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    checkExactAlarmPermission()
                }
            } else {
                checkExactAlarmPermission()
            }
        }
    }

    // Collapsed Weekly Routine Template state
    var isWeeklyTemplateExpanded by rememberSaveable { mutableStateOf(false) }

    // Weekly Template Schedule Dialog state
    var editingSchedule by remember { mutableStateOf<WorkoutScheduleEntity?>(null) }
    var selectedPlanForSchedule by remember { mutableStateOf<WorkoutPlanEntity?>(null) }
    var scheduleHour by remember { mutableIntStateOf(8) }
    var scheduleMinute by remember { mutableIntStateOf(0) }
    var isScheduleEnabled by remember { mutableStateOf(true) }
    var planDropdownExpanded by remember { mutableStateOf(false) }

    // Map completed (non-cancelled) dates to Asia/Manila LocalDate set
    val completedDateSet = remember(sessions) {
        sessions
            .filter { !it.status.equals("CANCELLED", ignoreCase = true) }
            .map { session -> ManilaDateTime.utcMillisToManilaDate(session.completedAt) }
            .toSet()
    }

    // Completed non-cancelled sessions on selected day (Asia/Manila)
    val sessionsOnSelectedDay = remember(sessions, selectedLocalDate) {
        sessions.filter { session ->
            !session.status.equals("CANCELLED", ignoreCase = true) &&
                ManilaDateTime.utcMillisToManilaDate(session.completedAt) == selectedLocalDate
        }
    }

    // Expand occurrences in memory for current visible month only
    val monthlyOccurrences = remember(currentYearMonth, calendarEvents, calendarExceptions) {
        CalendarOccurrenceEngine.expandForMonth(currentYearMonth, calendarEvents, calendarExceptions)
    }

    val occurrencesOnSelectedDay = remember(monthlyOccurrences, selectedLocalDate) {
        monthlyOccurrences[selectedLocalDate] ?: emptyList()
    }

    val coachPlansOnSelectedDay = remember(allPlans, selectedLocalDate) {
        allPlans.filter { plan ->
            !plan.assignedByTrainerName.isNullOrBlank() &&
                plan.startDateEpochMs != null &&
                ManilaDateTime.assignedPlanManilaDate(plan.startDateEpochMs) == selectedLocalDate
        }
    }

    val selectedDateKey = remember(selectedLocalDate) { ManilaDateTime.toDateKey(selectedLocalDate) }

    val scheduledSessionsOnSelectedDay = remember(scheduledSessions, selectedDateKey) {
        scheduledSessions.filter { session ->
            val key = session.scheduledDateKey.takeIf { it.isNotBlank() }
                ?: if (session.scheduledDateUtcMs > 0L) ManilaDateTime.toDateKey(session.scheduledDateUtcMs) else ""
            key == selectedDateKey
        }.sortedBy { it.startTime }
    }

    val activeScheduledSessionDateKeys = remember(scheduledSessions) {
        scheduledSessions
            .filter { !it.isCancelled }
            .mapNotNull { session ->
                session.scheduledDateKey.takeIf { it.isNotBlank() }
                    ?: if (session.scheduledDateUtcMs > 0L) ManilaDateTime.toDateKey(session.scheduledDateUtcMs) else null
            }
            .toSet()
    }

    // Rebuild event for editing from editingEventId
    val editingEvent = remember(editingEventId, calendarEvents) {
        editingEventId?.let { id -> calendarEvents.find { it.id == id } }
    }

    // Bottom Sheet for Add / Edit Event
    if (showBottomSheet) {
        EventReminderBottomSheet(
            initialDate = selectedLocalDate,
            editingEvent = editingEvent,
            allPlans = allPlans,
            onDismiss = {
                showBottomSheet = false
                editingEventId = null
            },
            onSaveNew = { newEvent ->
                onSaveEvent(newEvent)
                showBottomSheet = false
                editingEventId = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Event saved", duration = SnackbarDuration.Short)
                }
                checkReminderPermissions(newEvent.reminderMinutesBefore)
            },
            onUpdateAll = { updatedEvent ->
                onUpdateEvent(updatedEvent)
                showBottomSheet = false
                editingEventId = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Event saved", duration = SnackbarDuration.Short)
                }
                checkReminderPermissions(updatedEvent.reminderMinutesBefore)
            },
            onEditThisDayOnly = { overrideEvent ->
                editingEvent?.let { parent ->
                    onEditThisDayOnly(parent.id, selectedLocalDate.toString(), overrideEvent)
                }
                showBottomSheet = false
                editingEventId = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Event saved", duration = SnackbarDuration.Short)
                }
                checkReminderPermissions(overrideEvent.reminderMinutesBefore)
            },
            onDeleteAll = { id ->
                val eventToDelete = editingEvent
                showBottomSheet = false
                editingEventId = null
                if (eventToDelete != null) {
                    if (eventToDelete.repeatType == "WEEKLY") {
                        coroutineScope.launch {
                            val allExceptions = onLoadExceptionsForEvent(eventToDelete.id)
                            onDeleteEvent(eventToDelete.id)
                            val result = snackbarHostState.showSnackbar(
                                message = CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.WHOLE_SERIES),
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onRestoreSeries(eventToDelete, allExceptions)
                            }
                        }
                    } else {
                        onDeleteEvent(eventToDelete.id)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.ONE_TIME),
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onRestoreEvent(eventToDelete)
                            }
                        }
                    }
                } else {
                    onDeleteEvent(id)
                }
            },
            onDeleteThisDayOnly = {
                val parent = editingEvent
                val occDate = selectedLocalDate.toString()
                showBottomSheet = false
                editingEventId = null
                if (parent != null) {
                    onDeleteThisDayOnly(parent.id, occDate)
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.THIS_DAY_ONLY),
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndoDeleteThisDayOnly(parent.id, occDate)
                        }
                    }
                }
            }
        )
    }

    // Edit Weekly Template Dialog
    if (editingSchedule != null) {
        val sched = editingSchedule!!
        val dayName = when (sched.dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Sunday"
        }

        AlertDialog(
            onDismissRequest = { editingSchedule = null },
            title = { Text("Edit $dayName Template", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Select default routine for $dayName:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Plan Selector Box
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { planDropdownExpanded = true }
                            .testTag("schedule_plan_selector"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Assigned Routine:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = selectedPlanForSchedule?.name ?: sched.planName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BoldRed
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    DropdownMenu(
                        expanded = planDropdownExpanded,
                        onDismissRequest = { planDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        allPlans.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("${p.category} • ${p.estimatedDurationMinutes} mins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedPlanForSchedule = p
                                    planDropdownExpanded = false
                                }
                            )
                        }
                    }

                    // Time selection
                    Text("Routine Time:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scheduleHour = (scheduleHour - 1 + 24) % 24 }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease hour", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "%02d".format(scheduleHour),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                color = BoldRed
                            )
                            IconButton(onClick = { scheduleHour = (scheduleHour + 1) % 24 }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase hour", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Text(":", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scheduleMinute = (scheduleMinute - 15 + 60) % 60 }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease minute", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "%02d".format(scheduleMinute),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { scheduleMinute = (scheduleMinute + 15) % 60 }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase minute", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Enabled toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Workout Day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = isScheduleEnabled,
                            onCheckedChange = { isScheduleEnabled = it },
                            colors = appSwitchColors()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosenPlan = selectedPlanForSchedule
                        val updated = sched.copy(
                            planId = chosenPlan?.id ?: sched.planId,
                            planName = chosenPlan?.name ?: sched.planName,
                            timeHour = scheduleHour,
                            timeMinute = scheduleMinute,
                            isEnabled = isScheduleEnabled
                        )
                        onUpdateSchedule(updated)
                        editingSchedule = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier.testTag("save_schedule_button")
                ) {
                    Text("Save Template", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSchedule = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Permission Dialog 1: Notification Permission Disabled
    if (showNotificationPermissionDialog) {
        AlertDialog(
            modifier = Modifier.testTag("notification_permission_dialog"),
            onDismissRequest = {
                showNotificationPermissionDialog = false
                checkExactAlarmPermission()
            },
            title = {
                Text(
                    text = "Turn on notifications",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Allow notifications to receive workout reminders before your scheduled events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                        checkExactAlarmPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("open_notification_settings_button")
                ) {
                    Text("Open settings", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationPermissionDialog = false
                        checkExactAlarmPermission()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dismiss_notification_dialog_button")
                ) {
                    Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Permission Dialog 2: Exact Alarm Permission
    if (showExactAlarmPermissionDialog) {
        AlertDialog(
            modifier = Modifier.testTag("exact_alarm_dialog"),
            onDismissRequest = {
                showExactAlarmPermissionDialog = false
            },
            title = {
                Text(
                    text = "Allow on-time reminders?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "To receive workout reminders at the exact time, allow alarms and reminders in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        showExactAlarmPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("open_exact_alarm_settings_button")
                ) {
                    Text("Open settings", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExactAlarmPermissionDialog = false
                        CalendarEventReminderScheduler.setOptedOutOfExactAlarm(context, true)
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("opt_out_exact_alarm_button")
                ) {
                    Text("Not now, use approximate time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = "Workout Schedule & History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Streak & Consistency Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text("Current Streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$streakDays Days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = BoldRed)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BoldRed.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BoldRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${sessions.count { !it.status.equals("CANCELLED", ignoreCase = true) }} Workouts Total",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BoldRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Calendar Month Navigator & Grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month Navigator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            currentYearMonthString = currentYearMonth.minusMonths(1).toString()
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Text(
                            text = ManilaDateTime.formatMonthYear(currentYearMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(onClick = {
                            currentYearMonthString = currentYearMonth.plusMonths(1).toString()
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Day of week headers (Sun..Sat)
                    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        daysOfWeek.forEach { day ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Month Grid
                    val daysInMonth = currentYearMonth.lengthOfMonth()
                    val firstDayOfMonth = currentYearMonth.atDay(1)
                    // DayOfWeek: SUNDAY=7, MONDAY=1 -> convert to 0 for Sunday
                    val startOffset = if (firstDayOfMonth.dayOfWeek == java.time.DayOfWeek.SUNDAY) 0 else firstDayOfMonth.dayOfWeek.value
                    val totalCells = startOffset + daysInMonth
                    val rows = (totalCells + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - startOffset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val cellDate = currentYearMonth.atDay(dayNum)
                                        val isSelected = cellDate == selectedLocalDate
                                        val isToday = cellDate == today
                                        val hasCompletedWorkout = completedDateSet.contains(cellDate)
                                        val occurrencesThisDay = monthlyOccurrences[cellDate] ?: emptyList()

                                        val baseDotState = CalendarOccurrenceEngine.computeDotState(
                                            date = cellDate,
                                            today = today,
                                            hasCompletedWorkout = hasCompletedWorkout,
                                            occurrencesOnDay = occurrencesThisDay
                                        )
                                        val dotState = if (
                                            baseDotState == CalendarDayDot.NONE &&
                                            activeScheduledSessionDateKeys.contains(ManilaDateTime.toDateKey(cellDate)) &&
                                            !cellDate.isBefore(today)
                                        ) {
                                            CalendarDayDot.PLANNED
                                        } else {
                                            baseDotState
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .clickable {
                                                    selectedDateEpochDay = cellDate.toEpochDay()
                                                }
                                                .testTag("calendar_day_$dayNum"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) BoldRed else Color.Transparent)
                                                    .then(
                                                        if (isToday && !isSelected) {
                                                            Modifier.border(1.5.dp, BoldRed, CircleShape)
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = "$dayNum",
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 13.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Dot indicator
                                                    when (dotState) {
                                                        CalendarDayDot.COMPLETED -> {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                        CalendarDayDot.PLANNED -> {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, CircleShape)
                                                            )
                                                        }
                                                        CalendarDayDot.MISSED -> {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                                            )
                                                        }
                                                        CalendarDayDot.NONE -> {
                                                            Spacer(modifier = Modifier.size(5.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // AGENDA SECTION (Directly Below Month Grid)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val agendaTitle = when (selectedLocalDate) {
                        today -> "Today's Agenda"
                        today.plusDays(1) -> "Tomorrow's Agenda"
                        else -> ManilaDateTime.formatDate(selectedLocalDate)
                    }
                    val agendaSubtitle = when (selectedLocalDate) {
                        today -> "${ManilaDateTime.formatDate(selectedLocalDate)} • Asia/Manila"
                        today.plusDays(1) -> "${ManilaDateTime.formatDate(selectedLocalDate)} • Asia/Manila"
                        else -> "Scheduled & Completed • Asia/Manila"
                    }
                    Text(
                        text = agendaTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = agendaSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        editingEventId = null
                        showBottomSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_event_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Coach Availability Summary for Selected Day (read-only for Member)
        if (trainerAvailability != null) {
            item {
                val coachLabel = activeCoachName?.takeIf { it.isNotBlank() }
                    ?: trainerAvailability.trainerName.takeIf { it.isNotBlank() }
                    ?: "Coach"
                val availText = trainerAvailability.availabilitySummaryFor(selectedLocalDate)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_coach_availability_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BoldRed.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = BoldRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$coachLabel's Availability",
                                style = MaterialTheme.typography.labelSmall,
                                color = BoldRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = availText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${ManilaDateTime.formatDate(selectedLocalDate)} • Asia/Manila (UTC+8)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Scheduled Coaching Sessions for Selected Day
        if (scheduledSessionsOnSelectedDay.isNotEmpty()) {
            item {
                Text(
                    text = "Coaching sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoldRed,
                    fontWeight = FontWeight.Bold
                )
            }
            items(scheduledSessionsOnSelectedDay, key = { "sched_${it.id}" }) { session ->
                val badgeBg = when {
                    session.isCancelled -> MaterialTheme.colorScheme.errorContainer
                    session.isCompleted -> SuccessGreen.copy(alpha = 0.18f)
                    else -> BoldRed.copy(alpha = 0.14f)
                }
                val badgeFg = when {
                    session.isCancelled -> MaterialTheme.colorScheme.onErrorContainer
                    session.isCompleted -> SuccessGreen
                    else -> BoldRed
                }
                val badgeLabel = when {
                    session.isCancelled -> "Cancelled"
                    session.isCompleted -> "Completed"
                    else -> "Scheduled"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_scheduled_session_${session.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.isCancelled) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (session.isCancelled) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else BoldRed.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.title.ifBlank { "Coaching Session" },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "${session.formattedScheduledDate()} • ${ManilaDateTime.formatSlotTime(session.startTime)} – ${ManilaDateTime.formatSlotTime(session.endTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (session.trainerName.isNotBlank()) {
                            Text(
                                text = "Coach: ${session.trainerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.isCancelled) {
                            val reason = session.cancelledReason.orEmpty().ifBlank { "No reason provided" }
                            val who = session.cancelledByName.orEmpty().ifBlank { session.cancelledByRole.orEmpty().ifBlank { "Staff" } }
                            Text(
                                text = "Cancelled: $reason",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "By $who • ${session.formattedCancelledAt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (session.notes.isNotBlank()) {
                            Text(
                                text = session.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Planned Items for Selected Day
        if (occurrencesOnSelectedDay.isNotEmpty()) {
            item {
                Text(
                    text = "Planned events",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            items(occurrencesOnSelectedDay) { occ ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (occ.planId == null && occ.title == "Rest day") Icons.Default.Bedtime else Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = BoldRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = occ.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!occ.note.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = occ.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                val parentEvent = calendarEvents.find { it.id == occ.eventId }
                                val reminderMinutes = parentEvent?.reminderMinutesBefore ?: -1
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = CalendarDateHelper.formatTime(is24Hour, occ.timeHour, occ.timeMinute),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (occ.isCancelled) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = "Cancelled",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (occ.isRecurring) BoldRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (occ.isRecurring) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline)
                                        ) {
                                            Text(
                                                text = if (occ.isRecurring) "Repeats weekly" else "One-time",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (occ.isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (reminderMinutes >= 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = CalendarDateHelper.formatReminderContentDescription(reminderMinutes)
                                                }
                                                .testTag("agenda_reminder_${occ.eventId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = BoldRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = CalendarDateHelper.formatReminder(reminderMinutes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BoldRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (occ.isCancelled && !occ.cancelledReason.isNullOrBlank()) {
                                        Text(
                                            text = "Reason: ${occ.cancelledReason}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                editingEventId = occ.eventId
                                showBottomSheet = true
                            },
                            modifier = Modifier.testTag("edit_event_${occ.eventId}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit event", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Coach-Assigned Plans for Selected Day
        if (coachPlansOnSelectedDay.isNotEmpty()) {
            item {
                Text(
                    text = "Assigned by Coach",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlameOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            items(coachPlansOnSelectedDay, key = { it.id }) { plan ->
                val isCancelled = plan.status.equals("CANCELLED", ignoreCase = true)
                val isCompleted = !isCancelled && plan.isCompletedByMember
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!isCancelled) Modifier.clickable { onStartAssignedPlan(plan) } else Modifier)
                        .testTag("calendar_coach_plan_${plan.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCancelled) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCancelled) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else FlameOrange.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "COACH • ${plan.assignedByTrainerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCancelled) MaterialTheme.colorScheme.error else FlameOrange,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = plan.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isCancelled) {
                                Text(
                                    text = "Cancelled: ${plan.cancelledReason.orEmpty().ifBlank { "Cancelled by staff" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val who = plan.cancelledByName.orEmpty().ifBlank { plan.cancelledByRole.orEmpty().ifBlank { "Staff" } }
                                Text(
                                    text = "By $who • ${ManilaDateTime.formatDateTime(plan.cancelledAtUtcMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "${plan.estimatedDurationMinutes} mins • ${plan.difficulty}" +
                                        if (!plan.trainerNote.isNullOrBlank()) " • ${plan.trainerNote}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        when {
                            isCancelled -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Cancelled",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            isCompleted -> {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = SuccessGreen)
                            }
                            else -> {
                                FilledTonalButton(onClick = { onStartAssignedPlan(plan) }) {
                                    Text("Start", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Completed Sessions for Selected Day
        if (sessionsOnSelectedDay.isNotEmpty()) {
            item {
                Text(
                    text = "Completed workouts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            items(sessionsOnSelectedDay) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BoldRed)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.planName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${session.durationSeconds / 60} mins • ${session.caloriesBurned} kcal • ${session.exercisesCompleted} exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Empty state when nothing is planned or completed
        if (
            occurrencesOnSelectedDay.isEmpty() &&
            sessionsOnSelectedDay.isEmpty() &&
            coachPlansOnSelectedDay.isEmpty() &&
            scheduledSessionsOnSelectedDay.isEmpty()
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No events or completed workouts for this day. Tap '+ Add' to plan a session!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        // ========================================================
        // WEEKLY ROUTINE TEMPLATE (Collapsed Section Below Agenda)
        // ========================================================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isWeeklyTemplateExpanded = !isWeeklyTemplateExpanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weekly routine template",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = if (isWeeklyTemplateExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isWeeklyTemplateExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("weekly_template_chevron")
                    )
                }
            }
        }

        if (isWeeklyTemplateExpanded) {
            items(schedules) { schedule ->
                val dayName = when (schedule.dayOfWeek) {
                    1 -> "Monday"
                    2 -> "Tuesday"
                    3 -> "Wednesday"
                    4 -> "Thursday"
                    5 -> "Friday"
                    6 -> "Saturday"
                    else -> "Sunday"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingSchedule = schedule
                            selectedPlanForSchedule = allPlans.find { it.id == schedule.planId }
                            scheduleHour = schedule.timeHour
                            scheduleMinute = schedule.timeMinute
                            isScheduleEnabled = schedule.isEnabled
                        }
                        .testTag("schedule_card_${schedule.dayOfWeek}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (schedule.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!schedule.isEnabled) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "REST DAY",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (schedule.isEnabled) {
                                    "${schedule.planName} • ${CalendarDateHelper.formatTime(is24Hour, schedule.timeHour, schedule.timeMinute)}"
                                } else {
                                    "Scheduled Rest & Recovery"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (schedule.isEnabled) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingSchedule = schedule
                                    selectedPlanForSchedule = allPlans.find { it.id == schedule.planId }
                                    scheduleHour = schedule.timeHour
                                    scheduleMinute = schedule.timeMinute
                                    isScheduleEnabled = schedule.isEnabled
                                },
                                modifier = Modifier.testTag("edit_schedule_button_${schedule.dayOfWeek}")
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = "Edit schedule", tint = BoldRed)
                            }

                            Switch(
                                checked = schedule.isEnabled,
                                onCheckedChange = { onToggleSchedule(schedule.copy(isEnabled = it)) },
                                colors = appSwitchColors(),
                                modifier = Modifier.testTag("schedule_switch_${schedule.dayOfWeek}")
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    ) { snackbarData ->
        Snackbar(
            snackbarData = snackbarData,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
        )
    }
    }
}
