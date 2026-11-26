package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["isEnabled"])
    ]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: String? = null,
    val title: String,
    val startDate: String,
    val timeHour: Int,
    val timeMinute: Int,
    val repeatType: String,
    val repeatDaysMask: Int = 0,
    val repeatUntil: String? = null,
    val reminderMinutesBefore: Int = -1,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

@Entity(
    tableName = "calendar_event_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId", "occurrenceDate"], unique = true)
    ]
)
data class CalendarEventExceptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val occurrenceDate: String,
    val type: String = "SKIPPED", // SKIPPED (deleted occurrence) or CANCELLED (visible cancelled occurrence)
    val cancelledReason: String? = null,
    val cancelledByUid: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtUtcMs: Long? = null
)
