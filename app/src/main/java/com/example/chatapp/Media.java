package com.example.chatapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures, voice notes and documents sent through the server. A file is downloaded once into the app's cache folder
 * (the server's names never change, so the cache never goes stale) and pictures are also kept decoded in memory.
 */
public final class Media {
    private Media() {}

    public interface FileCallback {
        void onFile(@Nullable File file);
    }

    public interface BitmapCallback {
        void onBitmap(@Nullable Bitmap bitmap);
    }

    private static final ExecutorService io = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, List<FileCallback>> pending = new HashMap<>();  // main thread only
    private static final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    static File folder(Context context) {
        File folder = new File(context.getCacheDir(), "attachments");
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    /** The cached file of an attachment, downloading it first when needed. Calls back on the main thread (null on failure). */
    public static void file(Context context, String attachment, @NonNull FileCallback callback) {
        File file = new File(folder(context), attachment);
        if (file.length() > 0) {
            callback.onFile(file);
            return;
        }
        List<FileCallback> waiting = pending.get(attachment);
        if (waiting != null) {   // already being downloaded: share the result
            waiting.add(callback);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(callback);
        pending.put(attachment, waiting);
        Api.download(context, attachment, new Api.BytesCallback() {
            @Override
            public void onSuccess(byte[] data) {
                io.execute(() -> {
                    File saved = write(file, data);
                    main.post(() -> finish(attachment, saved));
                });
            }

            @Override
            public void onError(String message) {
                finish(attachment, null);
            }
        });
    }

    private static void finish(String attachment, @Nullable File file) {
        List<FileCallback> waiting = pending.remove(attachment);
        if (waiting != null) for (FileCallback callback : waiting) callback.onFile(file);
    }

    @Nullable
    private static File write(File target, byte[] data) {
        File temp = new File(target.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
        } catch (IOException e) {
            return null;
        }
        return temp.renameTo(target) ? target : null;
    }

    /** A picture no larger than maxPx on its longest side. Calls back on the main thread (null on failure). */
    public static void image(Context context, String attachment, int maxPx, @NonNull BitmapCallback callback) {
        String key = attachment + "@" + maxPx;
        Bitmap hit = bitmaps.get(key);
        if (hit != null) {
            callback.onBitmap(hit);
            return;
        }
        file(context, attachment, file -> {
            if (file == null) {
                callback.onBitmap(null);
                return;
            }
            io.execute(() -> {
                Bitmap bitmap = decode(file, maxPx);
                if (bitmap != null) bitmaps.put(key, bitmap);
                main.post(() -> callback.onBitmap(bitmap));
            });
        });
    }

    @Nullable
    static Bitmap decode(File file, int maxPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx);
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    /** The largest power of two that keeps the picture at least maxPx on its longest side. */
    static int sampleSize(int width, int height, int maxPx) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= maxPx) sample *= 2;
        return sample;
    }

    /** "850 B", "12 KB", "3.4 MB" */
    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** "0:07", "2:30" */
    public static String clock(long millis) {
        long seconds = Math.max(0, Math.round(millis / 1000.0));
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
