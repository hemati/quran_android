package com.appcoholic.gpt;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class UniqueIDGenerator {

    private static final String PREFS_FILE = "MyPrefsFile";
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";

    public static String getUniqueID(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);

        if (uniqueID == null) {
            uniqueID = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(PREF_UNIQUE_ID, uniqueID);
            editor.apply();
        }

        return uniqueID;
    }
}
