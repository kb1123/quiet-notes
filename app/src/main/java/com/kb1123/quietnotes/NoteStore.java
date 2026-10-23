package com.kb1123.quietnotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** All notes live in one private SQLite database inside the app's own storage. */
public class NoteStore extends SQLiteOpenHelper {
    private static NoteStore instance;

    public static synchronized NoteStore get(Context context) {
        if (instance == null) instance = new NoteStore(context.getApplicationContext());
        return instance;
    }

    private NoteStore(Context context) {
        super(context, "notes.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "type INTEGER NOT NULL, "
                + "title TEXT NOT NULL DEFAULT '', "
                + "body TEXT NOT NULL DEFAULT '', "
                + "created INTEGER NOT NULL, "
                + "updated INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 only so far.
    }

    public List<Note> all() {
        List<Note> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("notes", null, null, null, null, null, "updated DESC")) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public Note get(long id) {
        try (Cursor c = getReadableDatabase().query("notes", null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public void save(Note n) {
        ContentValues v = new ContentValues();
        v.put("type", n.type);
        v.put("title", n.title == null ? "" : n.title);
        v.put("body", n.body == null ? "" : n.body);
        v.put("created", n.created);
        v.put("updated", n.updated);
        SQLiteDatabase db = getWritableDatabase();
        if (n.id < 0) {
            n.id = db.insert("notes", null, v);
        } else {
            db.update("notes", v, "id = ?", new String[]{String.valueOf(n.id)});
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("notes", "id = ?", new String[]{String.valueOf(id)});
    }

    private static Note read(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.type = c.getInt(c.getColumnIndexOrThrow("type"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.body = c.getString(c.getColumnIndexOrThrow("body"));
        n.created = c.getLong(c.getColumnIndexOrThrow("created"));
        n.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
        return n;
    }
}
