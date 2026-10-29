package com.example.chatapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.socket.client.Socket;

/**
 * The real-time events of the server, fanned out to whichever screens are open.
 * A screen extends Listener, overrides what it cares about, and adds itself in onStart / removes itself in onStop.
 * Everything is delivered on the main thread.
 */
public final class ChatEvents {
    private ChatEvents() {}

    public abstract static class Listener {
        /** A new message (yours too: it is echoed to all your devices). */
        public void onChatMessage(JSONObject message) {}
        /** A message was edited or deleted. */
        public void onMessageUpdated(JSONObject message) {}
        /** `by` has read your messages `ids`. */
        public void onMessagesRead(int by, JSONArray ids) {}
        /** `from` started or stopped typing, in a group when groupID != -1. */
        public void onTyping(int from, int groupID, boolean typing) {}
        public void onPresence(int userID, boolean online, String lastSeen) {}
        /** A group was created, renamed, changed its members, or you were removed from it. */
        public void onGroupUpdated(int groupID, boolean removed) {}
        /** The connection came back after a loss: events may have been missed. */
        public void onReconnected() {}
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void add(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static void remove(Listener listener) {
        listeners.remove(listener);
    }

    /** Called once for every new Socket (see SocketClient). */
    static void attach(Socket socket) {
        final boolean[] connectedBefore = {false};
        socket.on(Socket.EVENT_CONNECT, args -> {
            if (connectedBefore[0]) main.post(() -> { for (Listener l : listeners) l.onReconnected(); });
            connectedBefore[0] = true;
        });
        socket.on("chat message", args -> with(args, json -> { for (Listener l : listeners) l.onChatMessage(json); }));
        socket.on("message updated", args -> with(args, json -> { for (Listener l : listeners) l.onMessageUpdated(json); }));
        socket.on("messages read", args -> with(args, json -> {
            for (Listener l : listeners) l.onMessagesRead(json.optInt("by", -1), json.optJSONArray("ids"));
        }));
        socket.on("typing", args -> with(args, json -> {
            for (Listener l : listeners) {
                l.onTyping(json.optInt("from", -1), json.isNull("groupID") ? -1 : json.optInt("groupID", -1), json.optBoolean("typing"));
            }
        }));
        socket.on("presence", args -> with(args, json -> {
            for (Listener l : listeners) l.onPresence(json.optInt("userID", -1), json.optBoolean("online"), Api.str(json, "lastSeen"));
        }));
        socket.on("group updated", args -> with(args, json -> {
            for (Listener l : listeners) l.onGroupUpdated(json.optInt("groupID", -1), json.optBoolean("removed"));
        }));
    }

    private interface JsonHandler {
        void handle(JSONObject json);
    }

    private static void with(Object[] args, JsonHandler handler) {
        if (args == null || args.length == 0) return;
        try {
            JSONObject json = args[0] instanceof JSONObject ? (JSONObject) args[0] : new JSONObject(args[0].toString());
            main.post(() -> handler.handle(json));
        } catch (Exception ignored) {
            // a malformed event is not worth crashing the app
        }
    }
}
