package com.bit747.clipboardfilter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class mCProvider extends ContentProvider {

    private Context mContext;
    DBHelper mDbHelper = null;
    SQLiteDatabase db = null;
    public static final String AUTOHORITY = "com.bit747.clipboardfilter";

    public static final int Rules_Code = 1;
    public static final int Log_Code = 2;

    private static final UriMatcher mMatcher;
    static{
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(AUTOHORITY,"rules", Rules_Code);
        mMatcher.addURI(AUTOHORITY, "log", Log_Code);
    }

    @Override
    public boolean onCreate() {
        mContext = getContext();
        mDbHelper = new DBHelper(getContext());
        db = mDbHelper.getWritableDatabase();
        return true;
    }
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String table = getTableName(uri);
        db.insert(table, null, values);
        mContext.getContentResolver().notifyChange(uri, null);
        return uri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String table = getTableName(uri);
        return db.query(table,projection,selection,selectionArgs,null,null,sortOrder,null);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        String table = getTableName(uri);
        return db.update(table,values,selection,selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    private String getTableName(Uri uri){
        String tableName = null;
        switch (mMatcher.match(uri)) {
            case Rules_Code:
                tableName = DBHelper.RULES_TABLE_NAME;
                break;
            case Log_Code:
                tableName = DBHelper.LOG_TABLE_NAME;
                break;
        }
        return tableName;
    }
}