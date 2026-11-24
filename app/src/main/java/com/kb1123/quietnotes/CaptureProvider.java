package com.kb1123.quietnotes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Lets the phone's camera app write one photo into our private cache.
 * Only the single file below is reachable, and only by an app we've just
 * granted temporary access to (the provider itself is not exported).
 * This is why Quiet Notes needs no camera permission: the camera app takes
 * the picture, we only receive the file.
 */
public class CaptureProvider extends ContentProvider {
    static final String AUTHORITY = "com.kb1123.quietnotes.capture";
    static final Uri PHOTO_URI = Uri.parse("content://" + AUTHORITY + "/photo.jpg");

    static File photoFile(Context c) {
        File d = new File(c.getCacheDir(), "capture");
        if (!d.isDirectory()) d.mkdirs();
        return new File(d, "photo.jpg");
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        if (!"photo.jpg".equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        return photoFile(getContext());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (mode.contains("w") && !f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
