package com.example.chatapp;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Turns what the user picked (a photo, a document, a recording) into the bytes to upload. Work happens off the main thread. */
public final class Attachments {
    private Attachments() {}

    /** The server refuses anything larger. */
    public static final int MAX_BYTES = 15 * 1024 * 1024;
    /** Photos are shrunk to this size on their longest side before they are sent. */
    static final int PHOTO_MAX_PX = 1600;

    public static class Picked {
        public byte[] data;
        public String mime, name;
    }

    public interface Callback {
        void onPicked(Picked picked);
        void onError(String message);
    }

    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private interface Job {
        Picked run() throws Exception;
    }

    private static void run(Job job, Callback callback) {
        io.execute(() -> {
            try {
                Picked picked = job.run();
                main.post(() -> callback.onPicked(picked));
            } catch (TooLarge e) {
                main.post(() -> callback.onError("The file is too large (15 MB maximum)"));
            } catch (Exception e) {
                main.post(() -> callback.onError("Could not read the file"));
            }
        });
    }

    private static class TooLarge extends Exception {}

    /** A photo: rotated upright, shrunk and compressed as JPEG (GIFs are sent untouched to stay animated). */
    public static void photo(Context context, Uri uri, @NonNull Callback callback) {
        ContentResolver resolver = context.getContentResolver();
        run(() -> {
            String mime = resolver.getType(uri);
            String name = displayName(resolver, uri, "photo");
            Picked picked = new Picked();
            if ("image/gif".equals(mime)) {
                picked.data = readAll(resolver.openInputStream(uri));
                picked.mime = mime;
                picked.name = name;
                return picked;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0) throw new IOException("not an image");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Media.sampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX_PX);
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) throw new IOException("not an image");
            bitmap = upright(bitmap, orientation(resolver, uri));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            picked.data = out.toByteArray();
            picked.mime = "image/jpeg";
            picked.name = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + ".jpg";
            return picked;
        }, callback);
    }

    /** Any document, sent as it is. */
    public static void document(Context context, Uri uri, @NonNull Callback callback) {
        ContentResolver resolver = context.getContentResolver();
        run(() -> {
            Picked picked = new Picked();
            picked.name = displayName(resolver, uri, "file");
            String mime = resolver.getType(uri);
            picked.mime = mime == null ? "application/octet-stream" : mime;
            picked.data = readAll(resolver.openInputStream(uri));
            return picked;
        }, callback);
    }

    /** A file the app wrote itself (a voice recording). */
    public static void local(File file, String mime, String name, @NonNull Callback callback) {
        run(() -> {
            Picked picked = new Picked();
            picked.mime = mime;
            picked.name = name;
            try (InputStream in = new FileInputStream(file)) {
                picked.data = readAll(in);
            }
            return picked;
        }, callback);
    }

    private static byte[] readAll(InputStream in) throws IOException, TooLarge {
        if (in == null) throw new IOException("cannot open");
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) throw new TooLarge();
            }
            return out.toByteArray();
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException ignored) {
            // some providers refuse the query: the fallback name is good enough
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? fallback : last.substring(last.lastIndexOf('/') + 1);
    }

    private static int orientation(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap upright(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
