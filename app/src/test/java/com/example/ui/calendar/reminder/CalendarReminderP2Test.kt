package com.example.ui.calendar.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarReminderP2Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    private val zone = ZoneId.of("America/New_York")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTesting(db)
        repository = WorkoutRepository(db, context)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)

        // Clear prefs before each test
        context.getSharedPreferences("calendar_event_reminder_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase.setInstanceForTesting(null)
    }

    // ==========================================
    // 1. Calculator Wrapper Tests (Pure Logic)
    // ==========================================

    @Test
    fun `wrapper returns occurrenceDate, occurrenceStart, and triggerAt for one-time event`() {
        val now = ZonedDateTime.of(2026, 11, 8, 8, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 101L,
            title = "Leg Day",
            startDate = "2026-11-08",
            timeHour = 10,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 15,
            isEnabled = true
        )

        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(event, emptyList(), now)
        assertNotNull(schedule)
        assertEquals(LocalDate.of(2026, 11, 8), schedule!!.occurrenceDate)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 10, 0, 0, 0, zone), schedule.occurrenceStart)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 9, 45, 0, 0, zone), schedule.triggerAt)
    }

    @Test
    fun `wrapper returns occurrenceDate, occurrenceStart, and triggerAt for weekly event`() {
        // Saturday 2026-11-07 10:00
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 102L,
            title = "Monday HIIT",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 30,
            repeatType = "WEEKLY",
            repeatDaysMask = 1 shl 1, // Monday
            reminderMinutesBefore = 30,
            isEnabled = true
        )

        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(event, emptyList(), now)
        assertNotNull(schedule)
        assertEquals(LocalDate.of(2026, 11, 9), schedule!!.occurrenceDate)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 30, 0, 0, zone), schedule.occurrenceStart)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, zone), schedule.triggerAt)
    }

    @Test
    fun `wrapper skips occurrence present in exceptions for weekly event`() {
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 103L,
            title = "Weekly Run",
            startDate = "2026-11-01",
            timeHour = 7,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 1 shl 1, // Monday
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        // Exception skips Monday 2026-11-09
        val exceptions = listOf(
            CalendarEventExceptionEntity(
                eventId = 103L,
                occurrenceDate = "2026-11-09",
                type = "SKIPPED"
            )
        )

        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(event, exceptions, now)
        assertNotNull(schedule)
        assertEquals(LocalDate.of(2026, 11, 16), schedule!!.occurrenceDate)
        assertEquals(ZonedDateTime.of(2026, 11, 16, 7, 0, 0, 0, zone), schedule.occurrenceStart)
        assertEquals(ZonedDateTime.of(2026, 11, 16, 6, 50, 0, 0, zone), schedule.triggerAt)
    }

    @Test
    fun `imminent event returns now as triggerAt with correct occurrenceDate and occurrenceStart`() {
        // Event at 10:00, reminder is 30 mins before (window starts at 09:30).
        // Current time is 09:40 (inside the window, event has not started yet).
        val now = ZonedDateTime.of(2026, 11, 8, 9, 40, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 104L,
            title = "Imminent Workout",
            startDate = "2026-11-08",
            timeHour = 10,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 30,
            isEnabled = true
        )

        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(event, emptyList(), now)
        assertNotNull(schedule)
        assertEquals(LocalDate.of(2026, 11, 8), schedule!!.occurrenceDate)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 10, 0, 0, 0, zone), schedule.occurrenceStart)
        assertEquals(now, schedule.triggerAt)
    }

    @Test
    fun `passing lastNotifiedOccurrence skips that occurrence and returns the one after it`() {
        val now = ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, zone) // Monday morning
        val event = CalendarEventEntity(
            id = 105L,
            title = "Mon and Thu Workout",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = (1 shl 1) or (1 shl 4), // Mon (bit 1) and Thu (bit 4)
            reminderMinutesBefore = 15,
            isEnabled = true
        )

        // If Monday 2026-11-09 was already notified, next should be Thursday 2026-11-12
        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(
            event = event,
            exceptions = emptyList(),
            now = now,
            lastNotifiedOccurrence = LocalDate.of(2026, 11, 9)
        )
        assertNotNull(schedule)
        assertEquals(LocalDate.of(2026, 11, 12), schedule!!.occurrenceDate)
        assertEquals(ZonedDateTime.of(2026, 11, 12, 18, 0, 0, 0, zone), schedule.occurrenceStart)
        assertEquals(ZonedDateTime.of(2026, 11, 12, 17, 45, 0, 0, zone), schedule.triggerAt)
    }

    // ==========================================
    // 2. Scheduler & Repository Sync Tests
    // ==========================================

    @Test
    fun `scheduling an alarm for a future event creates an alarm in AlarmManager`() = runBlocking {
        val futureDate = LocalDate.now().plusDays(2).toString()
        val event = CalendarEventEntity(
            title = "Future Workout",
            startDate = futureDate,
            timeHour = 14,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )

        val id = repository.insertCalendarEvent(event)
        assertTrue(id > 0)

        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertFalse("AlarmManager should have a scheduled alarm", scheduledAlarms.isEmpty())

        val expectedRequestCode = CalendarEventReminderScheduler.getRequestCode(id)
        val alarm = scheduledAlarms.find {
            val shadowPi = shadowOf(it.operation)
            shadowPi.requestCode == expectedRequestCode
        }
        assertNotNull("Should find scheduled alarm matching event request code", alarm)
    }

    @Test
    fun `deleting event cancels the alarm and clears lastNotified in preferences`() = runBlocking {
        val futureDate = LocalDate.now().plusDays(2).toString()
        val event = CalendarEventEntity(
            title = "To Delete",
            startDate = futureDate,
            timeHour = 15,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 15,
            isEnabled = true
        )

        val id = repository.insertCalendarEvent(event)
        CalendarEventReminderScheduler.recordNotified(context, id, "2026-11-08|15:00|15")
        assertNotNull(CalendarEventReminderScheduler.getSignature(context, id))

        // Delete event
        repository.deleteCalendarEvent(id)

        // Check signature cleared
        assertNull("Signature should be cleared after deletion", CalendarEventReminderScheduler.getSignature(context, id))

        // Check alarm cancelled
        val expectedRequestCode = CalendarEventReminderScheduler.getRequestCode(id)
        val alarm = shadowAlarmManager.scheduledAlarms.find {
            shadowOf(it.operation).requestCode == expectedRequestCode
        }
        assertNull("Alarm should be cancelled after deletion", alarm)
    }

    @Test
    fun `editing this day only schedules alarms for both series and override event`() = runBlocking {
        val futureMonday = LocalDate.now().plusDays(1)
        val event = CalendarEventEntity(
            title = "Weekly Routine",
            startDate = futureMonday.toString(),
            timeHour = 16,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val parentId = repository.insertCalendarEvent(event)

        val override = CalendarEventEntity(
            title = "Modified Single Day",
            startDate = futureMonday.toString(),
            timeHour = 17,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        repository.editCalendarEventThisDayOnly(parentId, futureMonday.toString(), override)

        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        val parentReqCode = CalendarEventReminderScheduler.getRequestCode(parentId)
        val parentAlarm = scheduledAlarms.find { shadowOf(it.operation).requestCode == parentReqCode }
        assertNotNull("Parent series should have an alarm scheduled", parentAlarm)

        // Find override event ID
        val allEvents = db.calendarEventDao().getEventsWithReminders()
        val overrideEvent = allEvents.find { it.title == "Modified Single Day" }
        assertNotNull(overrideEvent)
        val overrideReqCode = CalendarEventReminderScheduler.getRequestCode(overrideEvent!!.id)
        val overrideAlarm = scheduledAlarms.find { shadowOf(it.operation).requestCode == overrideReqCode }
        assertNotNull("Override event should have an alarm scheduled", overrideAlarm)
    }

    @Test
    fun `undoing delete restores the alarm`() = runBlocking {
        val futureDate = LocalDate.now().plusDays(3).toString()
        val event = CalendarEventEntity(
            title = "Undoable Workout",
            startDate = futureDate,
            timeHour = 11,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val id = repository.insertCalendarEvent(event)
        val createdEvent = db.calendarEventDao().getEventById(id)!!

        repository.deleteCalendarEvent(id)
        val reqCode = CalendarEventReminderScheduler.getRequestCode(id)
        var alarm = shadowAlarmManager.scheduledAlarms.find { shadowOf(it.operation).requestCode == reqCode }
        assertNull("Alarm should be cancelled after delete", alarm)

        // Restore event
        repository.insertCalendarEvent(createdEvent)
        alarm = shadowAlarmManager.scheduledAlarms.find { shadowOf(it.operation).requestCode == reqCode }
        assertNotNull("Alarm should be restored after undo/re-insert", alarm)
    }

    // ==========================================
    // 3. Receiver & Loop Prevention Tests
    // ==========================================

    @Test
    fun `receiver fires, posts notification, stores signature, and suppresses duplicate firing`() = runBlocking {
        val today = LocalDate.now()
        val event = CalendarEventEntity(
            title = "Chest & Triceps",
            startDate = today.toString(),
            timeHour = 19,
            timeMinute = 30,
            repeatType = "NONE",
            reminderMinutesBefore = 15,
            isEnabled = true
        )
        val eventId = db.calendarEventDao().insertEvent(event)

        val receiver = CalendarReminderReceiver()
        receiver.handleAlarm(context, eventId, today.toString())

        // Verify notification was posted
        val notifications = shadowNotificationManager.allNotifications
        assertEquals("Notification should be posted", 1, notifications.size)

        // Verify signature was recorded
        val sig = CalendarEventReminderScheduler.getSignature(context, eventId)
        val expectedSig = CalendarEventReminderScheduler.buildSignature(today, 19, 30, 15)
        assertEquals("Signature must match occurrence", expectedSig, sig)

        // Duplicate firing for same occurrence
        receiver.handleAlarm(context, eventId, today.toString())
        assertEquals("Duplicate firing must not post a second notification", 1, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `receiver does not notify if occurrence was marked skipped or deleted`() = runBlocking {
        val today = LocalDate.now()
        val event = CalendarEventEntity(
            title = "Skipped Workout",
            startDate = today.toString(),
            timeHour = 20,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val eventId = db.calendarEventDao().insertEvent(event)

        // Add exception marking this date as SKIPPED
        db.calendarEventDao().insertException(
            CalendarEventExceptionEntity(
                eventId = eventId,
                occurrenceDate = today.toString(),
                type = "SKIPPED"
            )
        )

        val receiver = CalendarReminderReceiver()
        receiver.handleAlarm(context, eventId, today.toString())

        assertEquals("No notification should be posted for skipped occurrence", 0, shadowNotificationManager.allNotifications.size)
    }

    // ==========================================
    // 4. Boot Receiver Test
    // ==========================================

    @Test
    fun `boot receiver on BOOT_COMPLETED calls syncAll and schedules alarms`() = runBlocking {
        val futureDate = LocalDate.now().plusDays(2).toString()
        val event = CalendarEventEntity(
            title = "Post Boot Event",
            startDate = futureDate,
            timeHour = 12,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val id = db.calendarEventDao().insertEvent(event)

        // Clear any alarms in manager
        shadowAlarmManager.scheduledAlarms.clear()
        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())

        val bootReceiver = CalendarReminderBootReceiver()
        bootReceiver.handleBoot(context, Intent.ACTION_BOOT_COMPLETED)

        // Verify alarm is scheduled
        val reqCode = CalendarEventReminderScheduler.getRequestCode(id)
        val alarm = shadowAlarmManager.scheduledAlarms.find { shadowOf(it.operation).requestCode == reqCode }
        assertNotNull("Alarm should be restored on BOOT_COMPLETED", alarm)
    }
}
