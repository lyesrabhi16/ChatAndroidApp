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
        Map<String, String> headers = new HashMap<>(super.getHeaders());
        User user = User.peek();
        if (user != null && user.getToken() != null) {
            headers.put("Authorization", "Bearer " + user.getToken());
        }
        return headers;
    }

    @Override
    public void deliverError(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
            User user = User.peek();
            // several requests can fail at once: only the first one closes the session
            if (user != null && user.isLoggedin()) {
                user.expireSession();
            }
        }
        super.deliverError(error);  // the screen's own listener still runs (it dismisses its progress dialog)
    }
}
