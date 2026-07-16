package com.eatrecord.app;

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
import java.util.List;

public class MealCameraProvider extends ContentProvider {
    public static final String AUTHORITY = "com.eatrecord.app.meal-camera";
    private static final String ROOT = "capture";

    public static Uri createOutputUri(Context context) throws IOException {
        File dir = cameraDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create camera cache directory");
        }
        String name = "meal_camera_" + System.currentTimeMillis() + ".jpg";
        File file = new File(dir, name);
        if (!file.createNewFile()) {
            throw new IOException("Unable to create camera output file");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(ROOT)
                .appendPath(name)
                .build();
    }

    private static File cameraDir(Context context) {
        return new File(context.getCacheDir(), "meal_camera");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireFile(uri);
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = requireFile(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = requireFile(uri);
        int flags;
        if (mode != null && mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
            flags |= mode.contains("r")
                    ? ParcelFileDescriptor.MODE_READ_WRITE
                    : ParcelFileDescriptor.MODE_WRITE_ONLY;
        } else {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File file = requireFile(uri);
        return !file.exists() || file.delete() ? 1 : 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert is not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File requireFile(Uri uri) {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority())) {
            throw new IllegalArgumentException("Unknown camera URI");
        }
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !ROOT.equals(segments.get(0))) {
            throw new IllegalArgumentException("Unknown camera path");
        }
        String name = segments.get(1);
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("Invalid camera file name");
        }
        File dir = cameraDir(getContext());
        File file = new File(dir, name);
        try {
            String root = dir.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(root)) {
                throw new IllegalArgumentException("Invalid camera path");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid camera path", e);
        }
        return file;
    }
}
