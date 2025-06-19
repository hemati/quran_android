package com.appcoholic.gpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class that manages the daily message quota for QuranGPT.
 */
public class MessageQuotaManager {

    private static final String KEY_MESSAGE_COUNT = "messageCount";
    private static final String KEY_DATE = "date";

    private final SharedPreferences preferences;
    private int maxMessagesPerDay;

    public MessageQuotaManager(Context context, int maxMessagesPerDay) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        this.maxMessagesPerDay = maxMessagesPerDay;
    }

    /**
     * Updates the stored message count and returns whether the user can send another message.
     */
    public boolean incrementAndCheck() {
        String today = getCurrentDate();
        String storedDate = preferences.getString(KEY_DATE, "");
        int messageCount = preferences.getInt(KEY_MESSAGE_COUNT, 0);

        if (!today.equals(storedDate)) {
            // New day, reset counter
            messageCount = 0;
        }

        messageCount++;
        preferences.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_MESSAGE_COUNT, messageCount)
                .apply();

        return messageCount <= maxMessagesPerDay;
    }

    public void setMaxMessagesPerDay(int maxMessagesPerDay) {
        this.maxMessagesPerDay = maxMessagesPerDay;
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
