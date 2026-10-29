package com.example.chatapp;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * A StringRequest that proves who is calling: it adds the signed-in user's token as
 * "Authorization: Bearer ...". Every server route except sign up and sign in needs it.
 * If the server answers 401 the token is no longer valid, so the session is closed and the sign-in screen opens.
 */
public class AuthRequest extends StringRequest {

    public AuthRequest(int method, String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(method, url, listener, errorListener);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return withAuth(super.getHeaders());
    }

    @Override
    public void deliverError(VolleyError error) {
        if (!sessionEnded(error)) super.deliverError(error);  // anything else: the screen's own listener reports it
    }

    /** The headers plus the signed-in user's token (shared with the upload and download requests). */
    static Map<String, String> withAuth(Map<String, String> base) {
        Map<String, String> headers = new HashMap<>(base);
        User user = User.peek();
        if (user != null && user.getToken() != null) {
            headers.put("Authorization", "Bearer " + user.getToken());
        }
        return headers;
    }

    /**
     * True when the error is a 401 that is handled here (the session is closed, the sign-in screen opens) and the
     * screen's own error message would only confuse.
     */
    static boolean sessionEnded(VolleyError error) {
        if (error.networkResponse == null || error.networkResponse.statusCode != 401) return false;
        User user = User.peek();
        if (user == null) return false;
        // several requests can fail at once: only the first one closes the session
        if (user.isLoggedin()) {
            user.expireSession();
            return true;
        }
        return user.getToken() == null;  // already closed by an earlier failed request
    }
}
