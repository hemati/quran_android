package com.quran.labs.androidquran.util;
import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefHelper {
    private static final String PREF_NAME = "AppUsagePref";
    private static final String KEY_USAGE_TIME = "usageTime";
    private static final String KEY_OPEN_COUNT = "openCount";
    private static final String KEY_LAST_RATING_TIME = "lastRatingTime";
    private static final String IS_ONBOARDING_COMPLETED = "isOnboardingCompleted";

    private SharedPreferences sharedPreferences;

    public SharedPrefHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveUsageTime(long time) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(KEY_USAGE_TIME, time);
        editor.apply();
    }

    public long getUsageTime() {
        return sharedPreferences.getLong(KEY_USAGE_TIME, 0);
    }

    public void saveOpenCount(int count) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_OPEN_COUNT, count);
        editor.apply();
    }

    public int getOpenCount() {
        return sharedPreferences.getInt(KEY_OPEN_COUNT, 0);
    }

    public void saveLastRatingTime(long time) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(KEY_LAST_RATING_TIME, time);
        editor.apply();
    }

    public long getLastRatingTime() {
        return sharedPreferences.getLong(KEY_LAST_RATING_TIME, 0);
    }

    public void setOnboardingCompleted(boolean isCompleted) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(IS_ONBOARDING_COMPLETED, isCompleted);
        editor.apply();
    }

    public boolean isOnboardingCompleted() {
        return sharedPreferences.getBoolean(IS_ONBOARDING_COMPLETED, false);
    }
}
