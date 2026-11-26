package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY timeHour ASC, timeMinute ASC")
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE isEnabled = 1 ORDER BY timeHour ASC, timeMinute ASC")
    fun getActiveEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE isEnabled = 1 AND reminderMinutesBefore >= 0")
    suspend fun getEventsWithReminders(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: CalendarEventEntity): Long

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    // Exceptions
    @Query("SELECT * FROM calendar_event_exceptions")
    fun getAllExceptions(): Flow<List<CalendarEventExceptionEntity>>

    @Query("SELECT * FROM calendar_event_exceptions WHERE eventId = :eventId")
    suspend fun getExceptionsForEvent(eventId: Long): List<CalendarEventExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: CalendarEventExceptionEntity): Long

    @Query("DELETE FROM calendar_event_exceptions WHERE eventId = :eventId AND occurrenceDate = :occurrenceDate")
    suspend fun deleteException(eventId: Long, occurrenceDate: String)

    @Transaction
    suspend fun editThisDayOnly(
        eventId: Long,
        occurrenceDate: String,
        newEvent: CalendarEventEntity
    ): Long {
        insertException(
            CalendarEventExceptionEntity(
                eventId = eventId,
                occurrenceDate = occurrenceDate,
                type = "SKIPPED"
            )
        )
        return insertEvent(newEvent.copy(id = 0L))
    }

    @Transaction
    suspend fun deleteThisDayOnly(
        eventId: Long,
        occurrenceDate: String
    ): Long {
        return insertException(
            CalendarEventExceptionEntity(
                eventId = eventId,
                occurrenceDate = occurrenceDate,
                type = "SKIPPED"
            )
        )
    }

    @Transaction
    suspend fun cancelThisDayOnly(
        eventId: Long,
        occurrenceDate: String,
        reason: String,
        cancelledByUid: String,
        cancelledByName: String,
        cancelledByRole: String,
        cancelledAtUtcMs: Long
    ): Long {
        return insertException(
            CalendarEventExceptionEntity(
                eventId = eventId,
                occurrenceDate = occurrenceDate,
                type = "CANCELLED",
                cancelledReason = reason,
                cancelledByUid = cancelledByUid,
                cancelledByName = cancelledByName,
                cancelledByRole = cancelledByRole,
                cancelledAtUtcMs = cancelledAtUtcMs
            )
        )
    }
}
