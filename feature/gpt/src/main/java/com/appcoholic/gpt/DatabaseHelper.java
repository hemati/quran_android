package com.appcoholic.gpt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import com.appcoholic.gpt.data.model.Message;
import com.appcoholic.gpt.data.model.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "messages.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_MESSAGES = "messages";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TEXT = "text";
    public static final String COLUMN_USER_ID = "userId";
    public static final String COLUMN_USER_NAME = "userName";
    public static final String COLUMN_USER_AVATAR = "userAvatar";
    public static final String COLUMN_IS_USER = "isUser";
    public static final String COLUMN_CREATED_AT = "createdAt";


    private static final String DATABASE_CREATE = "create table "
            + TABLE_MESSAGES + "("
            + COLUMN_ID + " text primary key, "
            + COLUMN_TEXT + " text not null, "
            + COLUMN_USER_ID + " text not null, "
            + COLUMN_USER_NAME + " text not null, "
            + COLUMN_USER_AVATAR + " text, "
            + COLUMN_IS_USER + " integer not null, "
            + COLUMN_CREATED_AT + " integer not null);"; // Add this line


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    public void addMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, message.getId());
        values.put(COLUMN_TEXT, message.getText());
        values.put(COLUMN_USER_ID, message.getUser().getId());
        values.put(COLUMN_USER_NAME, message.getUser().getName());
        values.put(COLUMN_USER_AVATAR, message.getUser().getAvatar());
        values.put(COLUMN_IS_USER, message.getUser().isOnline() ? 1 : 0);
        values.put(COLUMN_CREATED_AT, message.getCreatedAt().getTime()); // Add this line


        db.insert(TABLE_MESSAGES, null, values);
        db.close();
    }

    public List<Message> getMessages(Integer offset) {
        List<Message> messages = new ArrayList<>();

        int limit = 10; // Number of items to load per page
        String selectQuery;

        if (offset == null) {
            // Return all messages if offset is null
            selectQuery = "SELECT * FROM " + TABLE_MESSAGES + " ORDER BY " + COLUMN_CREATED_AT + " ASC";
        } else {
            // Return paginated messages
            selectQuery = "SELECT * FROM " + TABLE_MESSAGES + " ORDER BY " + COLUMN_CREATED_AT + " DESC LIMIT " + limit + " OFFSET " + offset;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                User user = new User(cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5) == 1);
                Message message = new Message(cursor.getString(0), user, cursor.getString(1),
                        new Date(cursor.getLong(6)));

                messages.add(message);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return messages;
    }


    public void deleteAllMessages() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MESSAGES, null, null);
        db.close();
    }

    public void deleteMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MESSAGES, COLUMN_ID + " = ?", new String[]{message.getId()});
        db.close();
    }
}
