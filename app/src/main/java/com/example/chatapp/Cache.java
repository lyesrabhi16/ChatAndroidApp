package com.example.chatapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the app remembers of the server: the conversation list and the latest messages of each conversation.
 * It makes screens appear instantly and keeps them readable offline; the server's answer always replaces it.
 * Everything is erased when the user signs out.
 */
public final class Cache {
    private Cache() {}

    /** Messages kept per conversation. */
    static final int KEEP = 60;
    private static final String CONVERSATIONS = "conversations";

    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    public interface Result<T> {
        void on(T value);
    }

    public static String key(boolean group, int id) {
        return (group ? "g:" : "u:") + id;
    }

    /** The cached messages of a conversation, oldest first (empty when there are none). */
    public static void messages(Context context, String conversation, @NonNull Result<List<JSONObject>> callback) {
        io.execute(() -> {
            List<JSONObject> out = new ArrayList<>();
            for (CachedMessage m : CacheDb.get(context).dao().latest(conversation, KEEP)) {
                try {
                    out.add(new JSONObject(m.json));
                } catch (JSONException ignored) {
                    // a damaged row is just skipped
                }
            }
            Collections.reverse(out);
            main.post(() -> callback.on(out));
        });
    }

    public static void saveMessages(Context context, String conversation, @Nullable JSONArray messages) {
        if (messages == null) return;
        io.execute(() -> {
            List<CachedMessage> rows = new ArrayList<>();
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                CachedMessage row = new CachedMessage();
                row.messageID = m.optInt("messageID");
                row.conversation = conversation;
                row.json = m.toString();
                rows.add(row);
            }
            CacheDao dao = CacheDb.get(context).dao();
            dao.put(rows);
            dao.trim(conversation, KEEP);
        });
    }

    public static void saveMessage(Context context, String conversation, JSONObject message) {
        saveMessages(context, conversation, new JSONArray().put(message));
    }

    public static void conversations(Context context, @NonNull Result<JSONArray> callback) {
        io.execute(() -> {
            CachedBlob blob = CacheDb.get(context).dao().blob(CONVERSATIONS);
            JSONArray array = null;
            try {
                if (blob != null) array = new JSONArray(blob.value);
            } catch (JSONException ignored) {
                // unreadable: behave as if nothing was cached
            }
            JSONArray result = array;
            main.post(() -> callback.on(result));
        });
    }

    public static void saveConversations(Context context, @Nullable JSONArray conversations) {
        if (conversations == null) return;
        io.execute(() -> {
            CachedBlob blob = new CachedBlob();
            blob.name = CONVERSATIONS;
            blob.value = conversations.toString();
            CacheDb.get(context).dao().putBlob(blob);
        });
    }

    public static void clear(Context context) {
        io.execute(() -> {
            CacheDao dao = CacheDb.get(context).dao();
            dao.clearMessages();
            dao.clearBlobs();
        });
    }
}
