package com.example.chatapp;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The calls to the chat server, in one place: JSON in, JSON out, the token added to every request.
 * Callbacks always run on the main thread.
 */
public final class Api {
    private Api() {}

    public interface Callback {
        void onSuccess(JSONObject json);
        void onError(String message);
    }

    public interface BytesCallback {
        void onSuccess(byte[] data);
        void onError(String message);
    }

    private static RequestQueue queue;

    private static synchronized RequestQueue queue(Context context) {
        if (queue == null) queue = Volley.newRequestQueue(context.getApplicationContext());
        return queue;
    }

    /** { key, value, key, value, ... } as a JSON object; null values are left out. */
    public static JSONObject body(Object... keyValues) {
        JSONObject json = new JSONObject();
        try {
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                if (keyValues[i + 1] != null) json.put((String) keyValues[i], keyValues[i + 1]);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return json;
    }

    /** A string field that may be missing or null (JSONObject.optString would answer "null"). */
    @Nullable
    public static String str(JSONObject json, String key) {
        return json == null || json.isNull(key) ? null : json.optString(key, null);
    }

    /** POST `path` (for example "/messages/send") with a JSON body. A response with an "error" field is an error. */
    public static void post(Context context, String path, JSONObject body, @NonNull Callback callback) {
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        Request<String> request = new AuthRequest(Request.Method.POST, Configs.getServerURL() + path,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.has("error")) callback.onError(json.optString("error"));
                        else callback.onSuccess(json);
                    } catch (JSONException e) {
                        callback.onError("Unexpected answer from the server");
                    }
                },
                error -> callback.onError(describe(error))) {
            @Override
            public byte[] getBody() {
                return payload;
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };
        queue(context).add(request);
    }

    /** Sends a file as the raw request body; answers { attachment, mime, size }. */
    public static void upload(Context context, byte[] data, String mime, String fileName, @NonNull Callback callback) {
        String url = Configs.getServerURL() + "/upload?name=" + android.net.Uri.encode(fileName);
        Request<String> request = new BytesRequest<String>(Request.Method.POST, url, error -> callback.onError(describe(error))) {
            @Override
            public byte[] getBody() {
                return data;
            }

            @Override
            public String getBodyContentType() {
                return mime;
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new String(response.data, StandardCharsets.UTF_8), HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            protected void deliverResponse(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.has("error")) callback.onError(json.optString("error"));
                    else callback.onSuccess(json);
                } catch (JSONException e) {
                    callback.onError("Unexpected answer from the server");
                }
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(60_000, 0, 1f));  // a photo takes longer than the default 2.5 s
        queue(context).add(request);
    }

    /** Downloads an uploaded file by the name the server gave it. */
    public static void download(Context context, String attachment, @NonNull BytesCallback callback) {
        Request<byte[]> request = new BytesRequest<byte[]>(Request.Method.GET, Configs.getServerURL() + "/files/" + attachment,
                error -> callback.onError(describe(error))) {
            @Override
            protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
                return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            protected void deliverResponse(byte[] response) {
                callback.onSuccess(response);
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(30_000, 1, 1f));
        queue(context).add(request);
    }

    /** A request with the token in its headers; the subclasses decide what the body and the answer are. */
    private abstract static class BytesRequest<T> extends Request<T> {
        BytesRequest(int method, String url, Response.ErrorListener errorListener) {
            super(method, url, errorListener);
        }

        @Override
        public Map<String, String> getHeaders() {
            return AuthRequest.withAuth(java.util.Collections.emptyMap());
        }

        @Override
        public void deliverError(VolleyError error) {
            if (!AuthRequest.sessionEnded(error)) super.deliverError(error);
        }
    }

    private static String describe(VolleyError error) {
        if (error.networkResponse != null) {
            int status = error.networkResponse.statusCode;
            if (status == 413) return "The file is too large (15 MB maximum)";
            if (status == 403) return "You are not allowed to do this";
            if (status == 404) return "Not found";
            return "Server error (" + status + ")";
        }
        return "Cannot reach the server";
    }
}
