package cn.socialclock.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cn.socialclock.model.AlarmEvent;
import cn.socialclock.utils.DatetimeFormatter;
import cn.socialclock.utils.SocialClockLogger;

/**
 * @author mapler
 * Manage DB tables and queries.
 */
public class AlarmEventDbAdapter {

    private static final int DB_VERSION = 2;

    // table name
    private static final String TABLE_NAME = "alarm_event";

    /* table columns */
    private static final String COLUMN_EVENT_ID = "event_id";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_USER_NAME = "user_name";
    private static final String COLUMN_START_AT = "start_at";
    private static final String COLUMN_END_AT = "end_at";
    private static final String COLUMN_SNOOZE_TIMES = "snooze_times";
    private static final String COLUMN_SYNC_AT = "sync_at";
    private static final String COLUMN_DELETED_AT = "deleted_at";
    private static final String[] COLUMNS = {
            COLUMN_EVENT_ID,
            COLUMN_USER_ID,
            COLUMN_USER_NAME,
            COLUMN_START_AT,
            COLUMN_END_AT,
            COLUMN_SNOOZE_TIMES,
            COLUMN_SYNC_AT,
            COLUMN_DELETED_AT,
    };

    // alarm_event table create sql
    public static final String CREATE_TABLE_QUERY = "" +
            "CREATE TABLE " + TABLE_NAME + "(" +
            COLUMN_EVENT_ID + " TEXT PRIMARY KEY," +
            COLUMN_USER_ID + " TEXT," +
            COLUMN_USER_NAME + " TEXT," +
            COLUMN_START_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP," +
            COLUMN_END_AT + " DATETIME DEFAULT NULL," +
            COLUMN_SNOOZE_TIMES + " INTEGER DEFAULT 0," +
            COLUMN_SYNC_AT + " DATETIME DEFAULT NULL," +
            COLUMN_DELETED_AT + " DATETIME DEFAULT NULL" +
            ")";

    // alarm_event table drop sql
    public static final String DROP_TABLE_QUERY = "drop table if exists " + TABLE_NAME;

    AlarmEventDatabaseHelper dbHelper;

    // SQLiteDatabase
    private SQLiteDatabase db;

    /**
     * Constructor
     * @param context Context
     */
    public AlarmEventDbAdapter(Context context) {
        this.dbHelper = new AlarmEventDatabaseHelper(context, null, DB_VERSION);
    }

    /**
     * require db if db is closed
     */
    private void requireDb() {
        if (db == null || !db.isOpen()) {
            db = dbHelper.getReadableDatabase();
        }
    }

    /**
     * get data filter by selection order by orderBy
     * @param selection String or null, ex. "event_id = '1234' AND end_at NOT NULL"
     * @param orderBy String or null, ex. "start_at DESC"
     * @return list of AlarmEvent
     */
    public List<AlarmEvent> filterBy(String selection, String orderBy) {
        requireDb();
        try {
            List<AlarmEvent> alarmEventList = new ArrayList<>();
            Cursor cursor = db.query(
                    TABLE_NAME,
                    COLUMNS,
                    selection,
                    null,
                    null,
                    null,
                    orderBy);
            while (cursor.moveToNext()) {
                AlarmEvent alarmEvent = createAlarmEvent(cursor);
                alarmEventList.add(alarmEvent);
            }
            cursor.close();
            return alarmEventList;
        } finally {
            db.close();
        }
    }

    /**
     * get all data order by start_at
     * @return alarmEventList list of AlarmEvent
     */
    public List<AlarmEvent> findAll() {
        return filterBy(null, COLUMN_START_AT + " DESC");
    }

    /**
     * get one by event id
     * @param eventId String AlarmEvent eventId
     * @return AlarmEvent or null (if does not exist)
     */
    public AlarmEvent getByEventId(String eventId) {
        requireDb();
        try {
            String selection = COLUMN_EVENT_ID + "='" + eventId + "'";
            Cursor cursor = db.query(
                    TABLE_NAME,
                    COLUMNS,
                    selection,
                    null,
                    null,
                    null,
                    null);
            AlarmEvent alarmEvent;
            /* check if exist */
            if (cursor.getCount() > 0) {
                cursor.moveToNext();
                alarmEvent = createAlarmEvent(cursor);
            } else {
                alarmEvent = null;
            }
            cursor.close();
            return alarmEvent;
        } finally {
            db.close();
        }
    }

    /**
     * insert alarm event
     * @param alarmEvent AlarmEvent object
     */
    public long insert(AlarmEvent alarmEvent) {
        requireDb();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_EVENT_ID, alarmEvent.getEventId());
            values.put(COLUMN_USER_ID, alarmEvent.getUserId());
            values.put(COLUMN_USER_NAME, alarmEvent.getUserName());
            values.put(COLUMN_START_AT, DatetimeFormatter.calendarToString(alarmEvent.getStartAt()));
            values.put(COLUMN_END_AT, DatetimeFormatter.calendarToString(alarmEvent.getEndAt()));
            values.put(COLUMN_SNOOZE_TIMES, alarmEvent.getSnoozeTimes());
            values.put(COLUMN_SYNC_AT, DatetimeFormatter.calendarToString(alarmEvent.getSyncAt()));
            values.put(COLUMN_DELETED_AT, DatetimeFormatter.calendarToString(alarmEvent.getDeletedAt()));
            SocialClockLogger.log("Insert DB Record: " + alarmEvent.getEventId());
            return db.insert(TABLE_NAME, null, values);
        } finally {
            db.close();
        }
    }

    /**
     * update alarm event
     * @param alarmEvent AlarmEvent object
     */
    public int update(AlarmEvent alarmEvent) {
        requireDb();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_END_AT, DatetimeFormatter.calendarToString(alarmEvent.getEndAt()));
            values.put(COLUMN_SNOOZE_TIMES, alarmEvent.getSnoozeTimes());
            values.put(COLUMN_SYNC_AT, DatetimeFormatter.calendarToString(alarmEvent.getSyncAt()));
            values.put(COLUMN_DELETED_AT, DatetimeFormatter.calendarToString(alarmEvent.getDeletedAt()));
            String whereClause = COLUMN_EVENT_ID + "='" + alarmEvent.getEventId() + "'";
            SocialClockLogger.log("Update DB Record: " + alarmEvent.getEventId());
            return db.update(TABLE_NAME, values, whereClause, null);
        } finally {
            db.close();
        }
    }

    /**
     * delete record
     * @param eventId AlarmEvent eventId
     */
    public int delete(String eventId) {
        requireDb();
        try {
            String whereClause = COLUMN_EVENT_ID + "='" + eventId + "'";
            SocialClockLogger.log("Delete DB Record: " + eventId);
            return db.delete(TABLE_NAME, whereClause, null);
        } finally {
            db.close();
        }
    }

    /**
     * create an AlarmEvent object by cursor
     * @param cursor Cursor
     * @return AlarmEvent object
     */
    private static AlarmEvent createAlarmEvent(Cursor cursor) {
        return new AlarmEvent(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                DatetimeFormatter.stringToCalendar(cursor.getString(3)),
                DatetimeFormatter.stringToCalendar(cursor.getString(4)),
                cursor.getInt(5),
                DatetimeFormatter.stringToCalendar(cursor.getString(6)),
                DatetimeFormatter.stringToCalendar(cursor.getString(7))
        );
    }
}
