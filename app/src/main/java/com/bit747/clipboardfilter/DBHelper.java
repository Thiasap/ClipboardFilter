package com.bit747.clipboardfilter;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "conf.db";

    public static final String RULES_TABLE_NAME = "rules";
    public static final String LOG_TABLE_NAME = "log";

    private static final int DATABASE_VERSION = 1;

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + RULES_TABLE_NAME + "(_id INTEGER PRIMARY KEY AUTOINCREMENT," + " rules TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LOG_TABLE_NAME + "(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " LogEnable TEXT,"
                + " LogDetails TEXT,"
                + " LogAll TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)   {

    }
}
