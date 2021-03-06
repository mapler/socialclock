package cn.socialclock.manager;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.socialclock.model.AlarmEvent;
import cn.socialclock.model.ClockSettings;
import cn.socialclock.utils.ConstantData;
import cn.socialclock.utils.DatetimeFormatter;
import cn.socialclock.utils.SocialClockLogger;

/**
 * @author mapler
 * Alarm Manager
 */
public class SocialClockManager {

    private ClockSettings clockSettings;
    private NotificationServiceManager notificationServiceManager;
    private AlarmServiceManager alarmServiceManager;
    private AlarmEventManager alarmEventManager;
    private SnsManager snsManager;
    private Context context;

    /**
     * Constructor
     * @param context Context
     */
    public SocialClockManager(Context context) {
        // init settings
        this.clockSettings = new ClockSettings(context);
        // init managers
        this.alarmEventManager = new AlarmEventManager(context);
        this.notificationServiceManager = new NotificationServiceManager(context);
        this.alarmServiceManager = new AlarmServiceManager(context);
        this.snsManager = new SnsManager(context);
        this.context = context;
    }

    /**
     * Create a normal alarm
     * 1. read alarm time from preference settings
     * 2. init alarm event
     * 3. set alarm
     * 4. cancel if any snooze notification
     * @return alarmEventId String
     */
    public String createAlarm() {
        // get settings
        int hour = clockSettings.getHour();
        int minute = clockSettings.getMinute();

        // make a time obj
        Calendar alarmAt = Calendar.getInstance();
        alarmAt.set(Calendar.HOUR_OF_DAY, hour);
        alarmAt.set(Calendar.MINUTE, minute);
        alarmAt.set(Calendar.SECOND, 0);
        alarmAt.set(Calendar.MILLISECOND, 0);

        // get alarm time stamp
        if (Calendar.getInstance().after(alarmAt)) {
            // plus 1 day if alarm time has past
            alarmAt.add(Calendar.DATE, 1);
        }
        long alarmTimeStamp = alarmAt.getTimeInMillis();

        // generate alarm event id
        String alarmEventId = alarmEventManager.initAlarmEvent();

        // set an alarm
        alarmServiceManager.setAlarm(alarmEventId,
                ConstantData.AlarmType.ALARM_NORMAL,
                alarmTimeStamp);

        // cancel notifications
        notificationServiceManager.cancelAllNotifications();

        // write log
        SocialClockLogger.log("AlarmEventManager: createAlarm: "
                + "clock is set at "
                + DatetimeFormatter.calendarToString(alarmAt));

        return alarmEventId;
    }

    /**
     * Start alarm
     * 1. new alarm event if not exist
     * 2. cancel notification
     * @param alarmEventId String alarmed alarm event id
     */
    public void startAlarm(String alarmEventId) {

        // cancel notifications
        notificationServiceManager.cancelAllNotifications();

        AlarmEvent alarmEvent = alarmEventManager.getAlarmEventById(alarmEventId);
        Calendar startAt = Calendar.getInstance();
        // start an alarm event if not exist
        if (alarmEvent == null){
            String userId = clockSettings.getUserId();
            String userName = clockSettings.getUserName();
            alarmEventManager.startAlarmEvent(alarmEventId, userId, userName, startAt);
        }
        notificationServiceManager.createAlarmNotification(alarmEventId, startAt);

        // start playing ringtone
        AlarmRingtoneManager.playRingtone(context);
    }

    /**
     * Update(or create) a snooze alarm
     * if alarm event is null or is finished, return
     * 1. count up alarm event snooze times
     * 2. set next snooze alarm with intent
     * 3. cancel if any snooze notification
     * 4. create new notification
     * @param alarmEventId String
     */
    public void snoozeAlarm(String alarmEventId) {

        // cancel notifications
        notificationServiceManager.cancelAllNotifications();

        // stop ringtone
        AlarmRingtoneManager.stopRingtone();

        // exit if alarm event is not exist or finished
        AlarmEvent alarmEvent = alarmEventManager.getAlarmEventById(alarmEventId);
        if (alarmEvent == null || alarmEvent.isFinished()) {
            return;
        }

        // count up alarm event snooze
        alarmEventManager.snoozeAlarmEvent(alarmEventId);

        // get snooze timestamp
        int snoozeDuration = clockSettings.getSnoozeDuration();
        Calendar snoozeTime = Calendar.getInstance();
        snoozeTime.add(Calendar.MINUTE, snoozeDuration);
        snoozeTime.set(Calendar.SECOND, 0);
        long snoozeTimeStamp = snoozeTime.getTimeInMillis();

        // create snooze alarm
        alarmServiceManager.setAlarm(alarmEventId,
                ConstantData.AlarmType.ALARM_SNOOZE,
                snoozeTimeStamp);

        // create next notification
        notificationServiceManager.createSnoozeNotification(alarmEventId, snoozeTime);

        // write log
        SocialClockLogger.log("AlarmEventManager: snooze to "
                + DatetimeFormatter.calendarToString(snoozeTime));
    }

    /**
     * Cancel an alarm with alarm event id
     * 1. cancel alarm
     * 2. cancel notification
     */
    public void cancelAlarm() {
        // cancel alarm
        alarmServiceManager.cancelAlarm();
        // stop alarm
        AlarmRingtoneManager.stopRingtone();
        // cancel notifications
        notificationServiceManager.cancelAllNotifications();
    }

    /** Get up
     * 1. cancel notification
     * 2. finish an alarm event
     * 3. create next alarm
     */
    public void getUp(String alarmEventId) {
        SocialClockLogger.log("GetUpAction");

        // cancel notifications
        notificationServiceManager.cancelAllNotifications();

        // stop ringtone
        AlarmRingtoneManager.stopRingtone();

        // finish alarm event
        alarmEventManager.finishAlarmEvent(alarmEventId);

        // create next alarm
        createAlarm();
    }

    /** Sns
     * @param alarmEventId String
     */
    public void sendSns(String alarmEventId) {
        String snsMessage = snsManager.buildSnsMessage(alarmEventManager.getAlarmEventById(alarmEventId));
        snsManager.tweet(snsMessage);
        // write log
        SocialClockLogger.log(snsMessage);
    }

    /**
     * Get finished AlarmEvent for SimpleAdapter
     * @return parsedAlarmEvents List<Map<String, Object>>
     */
    public List<Map<String, Object>> getParsedFinishedAlarmEvents() {
        List<AlarmEvent> allAlarmEvents = alarmEventManager.getAllAlarmEvents();
        List<Map<String, Object>> parsedAlarmEvents = new ArrayList<>();

        for (AlarmEvent alarmEvent : allAlarmEvents) {
            // filter event is finished
            if (!alarmEvent.isFinished()){
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put(ConstantData.AdapterKey.ALARM_EVENT_USER_NAME_KEY, alarmEvent.getUserName());
            map.put(ConstantData.AdapterKey.ALARM_EVENT_START_AT_KEY,
                    DatetimeFormatter.calendarToString(alarmEvent.getStartAt()));
            map.put(ConstantData.AdapterKey.ALARM_EVENT_END_AT_KEY,
                    DatetimeFormatter.calendarToString(alarmEvent.getEndAt()));
            map.put(ConstantData.AdapterKey.ALARM_EVENT_SNOOZE_TIMES_KEY,
                    alarmEvent.getSnoozeTimes());
            parsedAlarmEvents.add(map);
        }
        return parsedAlarmEvents;
    }
}
