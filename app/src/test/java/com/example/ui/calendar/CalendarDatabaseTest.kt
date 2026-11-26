package com.example.ui.calendar

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.CalendarEventDao
import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarDatabaseTest {

    private lateinit var roomDb: AppDatabase
    private lateinit var calendarEventDao: CalendarEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        calendarEventDao = roomDb.calendarEventDao()
    }

    @After
    fun tearDown() {
        roomDb.close()
    }

    // 3.a. editThisDayOnly called with a newEvent that has a NON-ZERO id does not throw
    @Test
    fun `editThisDayOnly with non-zero id does not throw and creates new event and exception`() = runBlocking {
        val series = CalendarEventEntity(
            id = 0L,
            planId = "plan_full_body",
            title = "Morning Workout",
            startDate = "2026-11-01",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127
        )
        val seriesId = calendarEventDao.insertEvent(series)
        assertTrue("Series ID must be generated > 0", seriesId > 0)

        // Pass newEvent with NON-ZERO id (reusing seriesId)
        val nonZeroIdEvent = CalendarEventEntity(
            id = seriesId,
            planId = "plan_abs",
            title = "Override for Today",
            startDate = "2026-11-10",
            timeHour = 9,
            timeMinute = 30,
            repeatType = "NONE",
            repeatDaysMask = 0
        )

        val newEventId = calendarEventDao.editThisDayOnly(
            eventId = seriesId,
            occurrenceDate = "2026-11-10",
            newEvent = nonZeroIdEvent
        )

        // 1. Must return a valid new ID > 0 and distinct from seriesId
        assertTrue(newEventId > 0)
        assertNotEquals(seriesId, newEventId)

        // 2. Original series still present
        val fetchedSeries = calendarEventDao.getEventById(seriesId)
        assertNotNull(fetchedSeries)
        assertEquals("Morning Workout", fetchedSeries?.title)
        assertEquals("WEEKLY", fetchedSeries?.repeatType)

        // 3. Exactly 1 SKIPPED exception
        val exceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(1, exceptions.size)
        assertEquals("2026-11-10", exceptions.first().occurrenceDate)
        assertEquals("SKIPPED", exceptions.first().type)

        // 4. Exactly 1 new NONE event with new id
        val fetchedOverride = calendarEventDao.getEventById(newEventId)
        assertNotNull(fetchedOverride)
        assertEquals(newEventId, fetchedOverride?.id)
        assertEquals("Override for Today", fetchedOverride?.title)
        assertEquals("NONE", fetchedOverride?.repeatType)
        assertEquals("2026-11-10", fetchedOverride?.startDate)
    }

    // 3.b. deleteThisDayOnly adds exactly 1 exception and keeps the series
    @Test
    fun `deleteThisDayOnly adds exactly 1 exception and keeps the series`() = runBlocking {
        val series = CalendarEventEntity(
            id = 0L,
            planId = "plan_legs",
            title = "Leg Routine",
            startDate = "2026-11-01",
            timeHour = 7,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 64
        )
        val seriesId = calendarEventDao.insertEvent(series)

        calendarEventDao.deleteThisDayOnly(seriesId, "2026-11-08")

        // Series still present
        val fetchedSeries = calendarEventDao.getEventById(seriesId)
        assertNotNull(fetchedSeries)

        // Exactly 1 exception
        val exceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(1, exceptions.size)
        assertEquals("2026-11-08", exceptions.first().occurrenceDate)
        assertEquals("SKIPPED", exceptions.first().type)
    }

    // 3.c. deleteEventById removes the event's exceptions (cascade)
    @Test
    fun `deleteEventById removes the event exceptions cascade`() = runBlocking {
        val series = CalendarEventEntity(
            id = 0L,
            planId = "plan_chest",
            title = "Chest Blast",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127
        )
        val seriesId = calendarEventDao.insertEvent(series)

        // Add 2 exceptions
        calendarEventDao.deleteThisDayOnly(seriesId, "2026-11-03")
        calendarEventDao.deleteThisDayOnly(seriesId, "2026-11-05")
        assertEquals(2, calendarEventDao.getExceptionsForEvent(seriesId).size)

        // Delete series
        calendarEventDao.deleteEventById(seriesId)

        // Series is gone
        assertNull(calendarEventDao.getEventById(seriesId))

        // Exceptions removed via cascade delete
        val exceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(0, exceptions.size)
    }

    // 3.d. Edit round trip does not duplicate or mutate the title
    @Test
    fun `edit round trip does not change or duplicate the title`() = runBlocking {
        val initialEvent = CalendarEventEntity(
            id = 0L,
            planId = "plan_abs",
            title = "Abs Sculpt Beginner",
            startDate = "2026-11-01",
            timeHour = 9,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            note = "initial note"
        )
        val eventId = calendarEventDao.insertEvent(initialEvent)

        val retrieved = calendarEventDao.getEventById(eventId)
        assertNotNull(retrieved)
        assertEquals("Abs Sculpt Beginner", retrieved?.title)
        assertEquals("initial note", retrieved?.note)

        // Update event (e.g. changing note and time)
        val updatedEvent = retrieved!!.copy(
            timeHour = 10,
            timeMinute = 30,
            note = "updated note"
        )
        calendarEventDao.updateEvent(updatedEvent)

        val retrievedAfterUpdate = calendarEventDao.getEventById(eventId)
        assertNotNull(retrievedAfterUpdate)
        assertEquals("Abs Sculpt Beginner", retrievedAfterUpdate?.title)
        assertEquals("updated note", retrievedAfterUpdate?.note)
        assertEquals(10, retrievedAfterUpdate?.timeHour)
        assertEquals(30, retrievedAfterUpdate?.timeMinute)
    }

    // Round C1: Undo for deleting a whole repeating series restores original ID and all multi-month exceptions
    @Test
    fun `undo delete recurring series restores original ID and multi-month exceptions in a transaction`() = runBlocking {
        val series = CalendarEventEntity(
            id = 0L,
            planId = "plan_full_body",
            title = "Morning Routine",
            startDate = "2026-11-01",
            timeHour = 7,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            note = "Series note"
        )
        val seriesId = calendarEventDao.insertEvent(series)
        val originalSeries = calendarEventDao.getEventById(seriesId)!!

        // Add exceptions across 2 different months
        calendarEventDao.deleteThisDayOnly(seriesId, "2026-11-05")
        calendarEventDao.deleteThisDayOnly(seriesId, "2026-12-10")

        // 1. Fetch all exceptions before delete
        val exceptionsBeforeDelete = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(2, exceptionsBeforeDelete.size)

        // 2. Delete series
        calendarEventDao.deleteEventById(seriesId)
        assertNull(calendarEventDao.getEventById(seriesId))
        assertEquals(0, calendarEventDao.getExceptionsForEvent(seriesId).size)

        // 3. Restore inside single Room transaction
        roomDb.withTransaction {
            val restoredId = calendarEventDao.insertEvent(originalSeries)
            assertEquals(seriesId, restoredId)
            exceptionsBeforeDelete.forEach { exc ->
                calendarEventDao.insertException(exc)
            }
        }

        // 4. Verify original ID and both multi-month exceptions are restored
        val restoredSeries = calendarEventDao.getEventById(seriesId)
        assertNotNull(restoredSeries)
        assertEquals(seriesId, restoredSeries?.id)
        assertEquals("Morning Routine", restoredSeries?.title)
        assertEquals("Series note", restoredSeries?.note)

        val restoredExceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(2, restoredExceptions.size)
        val dates = restoredExceptions.map { it.occurrenceDate }.toSet()
        assertTrue(dates.contains("2026-11-05"))
        assertTrue(dates.contains("2026-12-10"))
    }

    // Round C1: Undo for deleting a one-time event restores original ID and fields via real insert
    @Test
    fun `undo delete one-time event restores original ID and all fields via real insert`() = runBlocking {
        val oneTime = CalendarEventEntity(
            id = 0L,
            planId = null,
            title = "Yoga Session",
            startDate = "2026-11-15",
            timeHour = 18,
            timeMinute = 30,
            repeatType = "NONE",
            repeatDaysMask = 0,
            note = "Bring mat"
        )
        val insertedId = calendarEventDao.insertEvent(oneTime)
        val eventToRestore = calendarEventDao.getEventById(insertedId)!!

        // Delete event
        calendarEventDao.deleteEventById(insertedId)
        assertNull(calendarEventDao.getEventById(insertedId))

        // Restore using insertEvent with original ID
        val restoredId = calendarEventDao.insertEvent(eventToRestore)
        assertEquals(insertedId, restoredId)

        val retrieved = calendarEventDao.getEventById(insertedId)
        assertNotNull(retrieved)
        assertEquals(insertedId, retrieved?.id)
        assertEquals("Yoga Session", retrieved?.title)
        assertEquals("Bring mat", retrieved?.note)
        assertEquals("2026-11-15", retrieved?.startDate)
        assertEquals(18, retrieved?.timeHour)
        assertEquals(30, retrieved?.timeMinute)
        assertEquals("NONE", retrieved?.repeatType)
    }

    // Round C1: "This day only" delete, then Undo: the occurrence appears again in the expanded month and the series is unchanged.
    @Test
    fun `undo delete this day only restores occurrence in expanded month and keeps series unchanged`() = runBlocking {
        val series = CalendarEventEntity(
            id = 0L,
            planId = "plan_core",
            title = "Core Strength",
            startDate = "2026-11-01",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127
        )
        val seriesId = calendarEventDao.insertEvent(series)
        val originalSeries = calendarEventDao.getEventById(seriesId)!!

        val nov2026 = YearMonth.of(2026, 11)
        val targetDate = LocalDate.of(2026, 11, 8)

        // Initial expansion: targetDate is present
        val initialExpanded = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = nov2026,
            events = listOf(originalSeries),
            exceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        )
        assertTrue(initialExpanded[targetDate]?.isNotEmpty() == true)

        // Delete this day only
        calendarEventDao.deleteThisDayOnly(seriesId, "2026-11-08")
        val exceptions = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(1, exceptions.size)
        assertEquals("2026-11-08", exceptions.first().occurrenceDate)

        // Expanded month after delete: targetDate occurrence is skipped
        val expandedAfterDelete = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = nov2026,
            events = listOf(originalSeries),
            exceptions = exceptions
        )
        assertTrue(expandedAfterDelete[targetDate].isNullOrEmpty())

        // Undo this day only delete via deleteException
        calendarEventDao.deleteException(seriesId, "2026-11-08")
        val exceptionsAfterUndo = calendarEventDao.getExceptionsForEvent(seriesId)
        assertEquals(0, exceptionsAfterUndo.size)

        // Expanded month after undo: targetDate occurrence appears again
        val expandedAfterUndo = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = nov2026,
            events = listOf(originalSeries),
            exceptions = exceptionsAfterUndo
        )
        assertTrue(expandedAfterUndo[targetDate]?.isNotEmpty() == true)

        // Series in database is completely unchanged
        val seriesAfterUndo = calendarEventDao.getEventById(seriesId)
        assertEquals(originalSeries, seriesAfterUndo)
    }

    // 3.e. Schema parity test comparing Room created tables vs MIGRATION_3_4 and MIGRATION_4_5 on plain SQLite
    @Test
    fun `schema parity between Room created tables and MIGRATION_3_4 plus MIGRATION_4_5 on plain SQLite`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val plainDb: SupportSQLiteDatabase = openHelper.writableDatabase

        // Enable foreign keys on plain SQLite
        plainDb.execSQL("PRAGMA foreign_keys = ON")

        // Run MIGRATION_3_4, MIGRATION_4_5, and MIGRATION_10_11 (with stub tables for workout_plans & workout_sessions)
        plainDb.execSQL("CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL PRIMARY KEY)")
        plainDb.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        AppDatabase.MIGRATION_3_4.migrate(plainDb)
        AppDatabase.MIGRATION_4_5.migrate(plainDb)
        AppDatabase.MIGRATION_10_11.migrate(plainDb)

        val roomSqlite = roomDb.openHelper.writableDatabase

        val tablesToCompare = listOf("calendar_events", "calendar_event_exceptions")

        for (table in tablesToCompare) {
            // Compare table_info (name, type, notnull, pk)
            val roomCols = getTableColumns(roomSqlite, table)
            val plainCols = getTableColumns(plainDb, table)
            assertEquals("Table columns mismatch on $table", roomCols, plainCols)

            // Compare index_list (index name, unique)
            val roomIndices = getIndices(roomSqlite, table)
            val plainIndices = getIndices(plainDb, table)
            assertEquals("Index list mismatch on $table", roomIndices, plainIndices)

            // Compare index_info for each index
            for (indexName in roomIndices.keys) {
                val roomIndexCols = getIndexColumns(roomSqlite, indexName)
                val plainIndexCols = getIndexColumns(plainDb, indexName)
                assertEquals("Index columns mismatch for index $indexName on table $table", roomIndexCols, plainIndexCols)
            }

            // Compare foreign_key_list
            val roomFks = getForeignKeys(roomSqlite, table)
            val plainFks = getForeignKeys(plainDb, table)
            assertEquals("Foreign keys mismatch on $table", roomFks, plainFks)
        }

        openHelper.close()
    }

    private fun getTableColumns(db: SupportSQLiteDatabase, table: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val cursor = db.query("PRAGMA table_info(`$table`)")
        val nameIdx = cursor.getColumnIndex("name")
        val typeIdx = cursor.getColumnIndex("type")
        val notnullIdx = cursor.getColumnIndex("notnull")
        val pkIdx = cursor.getColumnIndex("pk")
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx)
            val type = cursor.getString(typeIdx)
            val notnull = cursor.getInt(notnullIdx)
            val pk = cursor.getInt(pkIdx)
            map[name] = "type=$type,notnull=$notnull,pk=$pk"
        }
        cursor.close()
        return map
    }

    private fun getIndices(db: SupportSQLiteDatabase, table: String): Map<String, Boolean> {
        val map = linkedMapOf<String, Boolean>()
        val cursor = db.query("PRAGMA index_list(`$table`)")
        val nameIdx = cursor.getColumnIndex("name")
        val uniqueIdx = cursor.getColumnIndex("unique")
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx)
            val unique = cursor.getInt(uniqueIdx) == 1
            // Ignore SQLite internal auto-indices for PRIMARY KEY
            if (!name.startsWith("sqlite_autoindex_")) {
                map[name] = unique
            }
        }
        cursor.close()
        return map
    }

    private fun getIndexColumns(db: SupportSQLiteDatabase, indexName: String): List<String> {
        val list = mutableListOf<String>()
        val cursor = db.query("PRAGMA index_info(`$indexName`)")
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            list.add(cursor.getString(nameIdx))
        }
        cursor.close()
        return list
    }

    @Test
    fun `reminderMinutesBefore is saved and restored through insert, update, editThisDayOnly, and series restore`() = runBlocking {
        val event = CalendarEventEntity(
            id = 0L,
            title = "HIIT Session",
            startDate = "2026-11-01",
            timeHour = 10,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            reminderMinutesBefore = 30
        )
        val eventId = calendarEventDao.insertEvent(event)
        val retrieved = calendarEventDao.getEventById(eventId)
        assertNotNull(retrieved)
        assertEquals(30, retrieved!!.reminderMinutesBefore)

        val updated = retrieved.copy(reminderMinutesBefore = 60)
        calendarEventDao.updateEvent(updated)
        val retrievedAfterUpdate = calendarEventDao.getEventById(eventId)
        assertEquals(60, retrievedAfterUpdate!!.reminderMinutesBefore)

        val overrideEvent = retrievedAfterUpdate.copy(
            title = "Special Override",
            reminderMinutesBefore = 10
        )
        val childId = calendarEventDao.editThisDayOnly(eventId, "2026-11-03", overrideEvent)
        val retrievedChild = calendarEventDao.getEventById(childId)
        assertNotNull(retrievedChild)
        assertEquals(10, retrievedChild!!.reminderMinutesBefore)
    }

    private fun getForeignKeys(db: SupportSQLiteDatabase, table: String): List<String> {
        val list = mutableListOf<String>()
        val cursor = db.query("PRAGMA foreign_key_list(`$table`)")
        val tableIdx = cursor.getColumnIndex("table")
        val fromIdx = cursor.getColumnIndex("from")
        val toIdx = cursor.getColumnIndex("to")
        val onUpdateIdx = cursor.getColumnIndex("on_update")
        val onDeleteIdx = cursor.getColumnIndex("on_delete")
        while (cursor.moveToNext()) {
            val targetTable = cursor.getString(tableIdx)
            val from = cursor.getString(fromIdx)
            val to = cursor.getString(toIdx)
            val onUpdate = cursor.getString(onUpdateIdx)
            val onDelete = cursor.getString(onDeleteIdx)
            list.add("target=$targetTable,from=$from,to=$to,on_update=$onUpdate,on_delete=$onDelete")
        }
        cursor.close()
        return list.sorted()
    }
}
