package com.example.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventReminderBottomSheet(
    initialDate: LocalDate,
    editingEvent: CalendarEventEntity?,
    allPlans: List<WorkoutPlanEntity>,
    onDismiss: () -> Unit,
    onSaveNew: (CalendarEventEntity) -> Unit,
    onUpdateAll: (CalendarEventEntity) -> Unit,
    onEditThisDayOnly: (CalendarEventEntity) -> Unit,
    onDeleteAll: (Long) -> Unit,
    onDeleteThisDayOnly: () -> Unit
) {
    val isEditMode = editingEvent != null

    // Determine initial form states for unsaved-changes detection
    val initialType = remember(editingEvent) {
        when {
            editingEvent == null -> "PLAN"
            editingEvent.planId != null -> "PLAN"
            editingEvent.title == "Rest day" -> "REST"
            else -> "CUSTOM"
        }
    }
    val initialPlanId = remember(editingEvent, allPlans) {
        editingEvent?.planId ?: allPlans.firstOrNull()?.id ?: ""
    }
    val initialCustomTitle = remember(editingEvent, initialType) {
        if (initialType == "CUSTOM") editingEvent?.title ?: "" else ""
    }
    val initialDateEpochDay = remember(initialDate) { initialDate.toEpochDay() }
    val defaultTime = remember { CalendarEventFormHelper.roundToNext15Minutes(ManilaDateTime.nowManilaZoned().toLocalTime()) }
    val initialHour = remember(editingEvent, defaultTime) { editingEvent?.timeHour ?: defaultTime.hour }
    val initialMinute = remember(editingEvent, defaultTime) { editingEvent?.timeMinute ?: defaultTime.minute }
    val initialRepeatType = remember(editingEvent) { editingEvent?.repeatType ?: "NONE" }
    val initialRepeatDaysMask = remember(editingEvent, initialDate) {
        if (editingEvent != null && editingEvent.repeatType == "WEEKLY") {
            editingEvent.repeatDaysMask
        } else {
            1 shl CalendarOccurrenceEngine.dayOfWeekToMaskBit(initialDate.dayOfWeek)
        }
    }
    val initialRepeatUntilEpochDay = remember(editingEvent) {
        editingEvent?.repeatUntil?.let {
            try { LocalDate.parse(it).toEpochDay() } catch (e: DateTimeParseException) { null }
        }
    }
    val initialPlan = remember(editingEvent, allPlans) {
        editingEvent?.planId?.let { pid -> allPlans.find { it.id == pid } }
    }
    val initialNote = remember(editingEvent, initialPlan) {
        CalendarEventFormHelper.prefillNote(editingEvent, initialPlan?.name)
    }
    val initialReminderMinutes = remember(editingEvent) {
        editingEvent?.reminderMinutesBefore ?: -1
    }

    var eventType by rememberSaveable { mutableStateOf(initialType) }
    var selectedPlanId by rememberSaveable { mutableStateOf(initialPlanId) }
    var customTitle by rememberSaveable { mutableStateOf(initialCustomTitle) }
    var selectedDateEpochDay by rememberSaveable { mutableLongStateOf(initialDateEpochDay) }
    var isDateExplicitlyChanged by rememberSaveable { mutableStateOf(false) }

    var selectedHour by rememberSaveable { mutableIntStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialMinute) }

    var repeatType by rememberSaveable { mutableStateOf(initialRepeatType) }
    var repeatDaysMask by rememberSaveable { mutableIntStateOf(initialRepeatDaysMask) }
    var repeatUntilEpochDay by rememberSaveable { mutableStateOf(initialRepeatUntilEpochDay) }
    var isUntilExplicitlyChanged by rememberSaveable { mutableStateOf(false) }

    var reminderMinutesBefore by rememberSaveable { mutableIntStateOf(initialReminderMinutes) }

    var noteText by rememberSaveable { mutableStateOf(initialNote) }

    // Dialog & validation states
    var showPlanPickerDialog by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showUntilDatePicker by rememberSaveable { mutableStateOf(false) }
    var showRecurringEditConfirmDialog by remember { mutableStateOf(false) }
    var showRecurringDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }

    val hasUnsavedChanges by remember {
        derivedStateOf {
            eventType != initialType ||
            (eventType == "PLAN" && selectedPlanId != initialPlanId) ||
            (eventType == "CUSTOM" && customTitle != initialCustomTitle) ||
            selectedDateEpochDay != initialDateEpochDay ||
            selectedHour != initialHour ||
            selectedMinute != initialMinute ||
            repeatType != initialRepeatType ||
            (repeatType == "WEEKLY" && repeatDaysMask != initialRepeatDaysMask) ||
            repeatUntilEpochDay != initialRepeatUntilEpochDay ||
            reminderMinutesBefore != initialReminderMinutes ||
            noteText != initialNote
        }
    }

    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val currentDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    val coroutineScope = rememberCoroutineScope()
    var isDiscarding by remember { mutableStateOf(false) }
    var sheetInstanceId by remember { mutableIntStateOf(0) }

    val selectedPlan = remember(selectedPlanId, allPlans) {
        allPlans.find { it.id == selectedPlanId } ?: allPlans.firstOrNull()
    }

    // Inline validation error messages
    val customTitleError by remember {
        derivedStateOf {
            if (validationAttempted) {
                CalendarEventFormHelper.validateCustomTitle(eventType, customTitle)
            } else null
        }
    }

    val repeatDaysError by remember {
        derivedStateOf {
            if (validationAttempted) {
                CalendarEventFormHelper.validateRepeatDays(repeatType, repeatDaysMask)
            } else null
        }
    }

    val repeatUntilError by remember {
        derivedStateOf {
            if (validationAttempted) {
                CalendarEventFormHelper.validateRepeatUntil(repeatUntilEpochDay, selectedDateEpochDay)
            } else null
        }
    }

    fun validate(): CalendarEventEntity? {
        validationAttempted = true
        if (eventType == "PLAN" && selectedPlan == null) {
            return null
        }
        if (CalendarEventFormHelper.validateCustomTitle(eventType, customTitle) != null) {
            return null
        }
        if (CalendarEventFormHelper.validateRepeatDays(repeatType, repeatDaysMask) != null) {
            return null
        }
        if (CalendarEventFormHelper.validateRepeatUntil(repeatUntilEpochDay, selectedDateEpochDay) != null) {
            return null
        }

        val titleAndNote = CalendarEventFormHelper.buildTitleAndNote(
            eventType = eventType,
            planName = selectedPlan?.name,
            customTitle = customTitle,
            noteText = noteText
        )
        val planIdToSave = if (eventType == "PLAN") selectedPlanId else null

        return CalendarEventEntity(
            id = editingEvent?.id ?: 0L,
            planId = planIdToSave,
            title = titleAndNote.title,
            startDate = currentDate.toString(),
            timeHour = selectedHour,
            timeMinute = selectedMinute,
            repeatType = repeatType,
            repeatDaysMask = if (repeatType == "WEEKLY") repeatDaysMask else 0,
            repeatUntil = repeatUntilEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
            reminderMinutesBefore = reminderMinutesBefore,
            isEnabled = true,
            note = titleAndNote.note
        )
    }

    // Outer BackHandler for host Activity dispatcher
    BackHandler(enabled = true) {
        if (showDiscardConfirmDialog) {
            showDiscardConfirmDialog = false
        } else if (hasUnsavedChanges) {
            showDiscardConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    key(sheetInstanceId) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden) {
                    if (hasUnsavedChanges && !isDiscarding) {
                        showDiscardConfirmDialog = true
                        false
                    } else {
                        true
                    }
                } else {
                    true
                }
            }
        )

        ModalBottomSheet(
            sheetState = sheetState,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            onDismissRequest = {
                if (hasUnsavedChanges && !isDiscarding) {
                    showDiscardConfirmDialog = true
                } else {
                    onDismiss()
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // Inner BackHandler inside sheet window to intercept back press and gesture
            BackHandler(enabled = true) {
                if (showDiscardConfirmDialog) {
                    showDiscardConfirmDialog = false
                } else if (hasUnsavedChanges) {
                    showDiscardConfirmDialog = true
                } else {
                    onDismiss()
                }
            }

            Column(
                modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "Edit planned event" else "Plan workout event",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardConfirmDialog = true
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 1. Event Type Selector Chips
                Text("Event type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf("PLAN" to "Workout plan", "REST" to "Rest day", "CUSTOM" to "Custom note")
                    types.forEach { (typeKey, label) ->
                        val isSelected = eventType == typeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BoldRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { eventType = typeKey }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. Workout Routine (Card with Picker Dialog) OR Custom Activity Name
                if (eventType == "PLAN") {
                    Text("Workout routine", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPlanPickerDialog = true }
                            .testTag("selected_workout_plan_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedPlan?.name ?: "Select a routine",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (selectedPlan != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${selectedPlan.category} • ${selectedPlan.estimatedDurationMinutes} mins",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "Change",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else if (eventType == "CUSTOM") {
                    Text("Activity name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        placeholder = { Text("e.g. 5km Morning Run, Swimming session", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth().testTag("custom_event_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = if (customTitleError != null) BoldRed else BoldRed,
                            unfocusedBorderColor = if (customTitleError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.controlBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = customTitleError != null
                    )
                    if (customTitleError != null) {
                        Text(
                            text = customTitleError!!,
                            color = BoldRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                // 3. Date & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date Button
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.controlBorder, RoundedCornerShape(12.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("date_picker_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = BoldRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    ManilaDateTime.formatDate(currentDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Time Button
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.controlBorder, RoundedCornerShape(12.dp))
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("time_picker_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = BoldRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CalendarDateHelper.formatTime(is24Hour, selectedHour, selectedMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 4. Repetition Section
                Text("Repeat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val repeatOptions = listOf("NONE" to "Does not repeat", "WEEKLY" to "Every week")
                    repeatOptions.forEach { (typeKey, label) ->
                        val isSelected = repeatType == typeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BoldRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { repeatType = typeKey }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Days of week selector if WEEKLY
                if (repeatType == "WEEKLY") {
                    Text("Repeat on days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val days = listOf(
                        DayOfWeek.SUNDAY to "S",
                        DayOfWeek.MONDAY to "M",
                        DayOfWeek.TUESDAY to "T",
                        DayOfWeek.WEDNESDAY to "W",
                        DayOfWeek.THURSDAY to "T",
                        DayOfWeek.FRIDAY to "F",
                        DayOfWeek.SATURDAY to "S"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        days.forEach { (day, label) ->
                            val isIncluded = CalendarOccurrenceEngine.isDayIncluded(repeatDaysMask, day)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        val bit = CalendarOccurrenceEngine.dayOfWeekToMaskBit(day)
                                        repeatDaysMask = repeatDaysMask xor (1 shl bit)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isIncluded) BoldRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (isIncluded) 1.5.dp else 1.dp,
                                            color = if (isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isIncluded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isIncluded) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    if (repeatDaysError != null) {
                        Text(
                            text = repeatDaysError!!,
                            color = BoldRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    // Repeat Until Date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.controlBorder, RoundedCornerShape(12.dp))
                            .clickable { showUntilDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = BoldRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Repeat until", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = repeatUntilEpochDay?.let {
                                        ManilaDateTime.formatDate(LocalDate.ofEpochDay(it))
                                    } ?: "Forever (no end date)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (repeatUntilEpochDay != null) {
                            IconButton(onClick = {
                                repeatUntilEpochDay = null
                                isUntilExplicitlyChanged = true
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear end date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (repeatUntilError != null) {
                        Text(
                            text = repeatUntilError!!,
                            color = BoldRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                // 5. Reminder Selector Chips
                Text("Remind me", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val reminderOptions = listOf(
                        -1 to "Off",
                        0 to "At start",
                        10 to "10 min before",
                        30 to "30 min before",
                        60 to "1 hour before"
                    )
                    reminderOptions.forEach { (minutes, label) ->
                        val isSelected = reminderMinutesBefore == minutes
                        val tagSuffix = if (minutes == -1) "off" else minutes.toString()
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BoldRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { reminderMinutesBefore = minutes }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("reminder_chip_$tagSuffix"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // 6. Note (optional)
                Text("Note (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = {
                        Text(
                            text = when (eventType) {
                                "REST" -> "e.g. Hydrate, light stretching, 8 hours sleep"
                                "CUSTOM" -> "e.g. Heart rate zone 2, trail route"
                                else -> "e.g. Focus on form, increase weight by 2kg"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("event_note_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = BoldRed,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Pinned Action Buttons (Outside scroll area)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasUnsavedChanges) showDiscardConfirmDialog = true else onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.controlBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val event = validate()
                            if (event != null) {
                                if (isEditMode) {
                                    if (editingEvent?.repeatType == "WEEKLY") {
                                        showRecurringEditConfirmDialog = true
                                    } else {
                                        onUpdateAll(event)
                                        onDismiss()
                                    }
                                } else {
                                    onSaveNew(event)
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp)
                            .testTag("save_event_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BoldRed)
                    ) {
                        Text(
                            if (isEditMode) "Save changes" else "Save event",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isEditMode) {
                    TextButton(
                        onClick = {
                            if (editingEvent?.repeatType == "WEEKLY") {
                                showRecurringDeleteConfirmDialog = true
                            } else {
                                onDeleteAll(editingEvent!!.id)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = BoldRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("delete_event_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = BoldRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete event", fontWeight = FontWeight.Bold, color = BoldRed)
                    }
                }
            }

            // Discard Changes Confirmation Dialog
            if (showDiscardConfirmDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showDiscardConfirmDialog = false
                        if (sheetState.currentValue == SheetValue.Hidden || !sheetState.isVisible) {
                            sheetInstanceId++
                        } else {
                            coroutineScope.launch {
                                try { sheetState.expand() } catch (e: Exception) { sheetInstanceId++ }
                            }
                        }
                    },
                    title = { Text("Discard changes?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "You have unsaved changes. Are you sure you want to discard them?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    confirmButton = {
                        TextButton(onClick = {
                            isDiscarding = true
                            showDiscardConfirmDialog = false
                            onDismiss()
                        }) {
                            Text("Discard", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDiscardConfirmDialog = false
                            if (sheetState.currentValue == SheetValue.Hidden || !sheetState.isVisible) {
                                sheetInstanceId++
                            } else {
                                coroutineScope.launch {
                                    try { sheetState.expand() } catch (e: Exception) { sheetInstanceId++ }
                                }
                            }
                        }) {
                            Text("Keep editing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
    }
}

    // Workout Routine Picker Dialog
    if (showPlanPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPlanPickerDialog = false },
            title = { Text("Choose workout routine", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allPlans) { plan ->
                        val isSelected = plan.id == selectedPlanId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BoldRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedPlanId = plan.id
                                    showPlanPickerDialog = false
                                }
                                .padding(12.dp)
                                .testTag("plan_picker_item_${plan.id}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plan.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${plan.category} • ${plan.estimatedDurationMinutes} mins",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BoldRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlanPickerDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CalendarDateHelper.localDateToPickerMillis(currentDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDateEpochDay = CalendarDateHelper.pickerMillisToLocalDate(millis).toEpochDay()
                        isDateExplicitlyChanged = true
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = BoldRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    yearContentColor = MaterialTheme.colorScheme.onSurface,
                    currentYearContentColor = MaterialTheme.colorScheme.primary,
                    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    // Repeat Until Date Picker Dialog
    if (showUntilDatePicker) {
        val untilPickerState = rememberDatePickerState(
            initialSelectedDateMillis = CalendarDateHelper.localDateToPickerMillis(
                repeatUntilEpochDay?.let { LocalDate.ofEpochDay(it) } ?: currentDate.plusMonths(1)
            ),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return CalendarDateHelper.isRepeatUntilDateSelectable(utcTimeMillis, currentDate)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showUntilDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    untilPickerState.selectedDateMillis?.let { millis ->
                        repeatUntilEpochDay = CalendarDateHelper.pickerMillisToLocalDate(millis).toEpochDay()
                        isUntilExplicitlyChanged = true
                    }
                    showUntilDatePicker = false
                }) {
                    Text("OK", color = BoldRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUntilDatePicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DatePicker(
                state = untilPickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    yearContentColor = MaterialTheme.colorScheme.onSurface,
                    currentYearContentColor = MaterialTheme.colorScheme.primary,
                    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) {
                    Text("OK", color = BoldRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Select start time", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectorColor = BoldRed,
                            containerColor = MaterialTheme.colorScheme.surface,
                            periodSelectorBorderColor = MaterialTheme.colorScheme.controlBorder,
                            periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                            periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        )
    }

    // Recurring Edit Confirmation Dialog
    if (showRecurringEditConfirmDialog) {
        val eventToSave = validate()
        if (eventToSave != null) {
            AlertDialog(
                onDismissRequest = { showRecurringEditConfirmDialog = false },
                title = { Text("Recurring event", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Save changes for only this day, or all events in this series?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showRecurringEditConfirmDialog = false
                                onEditThisDayOnly(
                                    eventToSave.copy(
                                        id = 0L,
                                        startDate = currentDate.toString(),
                                        repeatType = "NONE",
                                        repeatDaysMask = 0,
                                        repeatUntil = null,
                                        note = eventToSave.note
                                    )
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.controlBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("This day only", maxLines = 1)
                        }
                        Button(
                            onClick = {
                                showRecurringEditConfirmDialog = false
                                val allEventsToSave = if (editingEvent != null && !isDateExplicitlyChanged) {
                                    eventToSave.copy(
                                        startDate = editingEvent.startDate,
                                        repeatUntil = if (!isUntilExplicitlyChanged) editingEvent.repeatUntil else eventToSave.repeatUntil
                                    )
                                } else {
                                    eventToSave
                                }
                                onUpdateAll(allEventsToSave)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("All events", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        TextButton(
                            onClick = { showRecurringEditConfirmDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                dismissButton = null
            )
        }
    }

    // Recurring Delete Confirmation Dialog
    if (showRecurringDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRecurringDeleteConfirmDialog = false },
            title = { Text("Delete recurring event", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete only this day, or all events in this series?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showRecurringDeleteConfirmDialog = false
                            onDeleteThisDayOnly()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.controlBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("This day only", maxLines = 1)
                    }
                    TextButton(
                        onClick = {
                            showRecurringDeleteConfirmDialog = false
                            onDeleteAll(editingEvent!!.id)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("All events", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    TextButton(
                        onClick = { showRecurringDeleteConfirmDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            },
            dismissButton = null
        )
    }
}
