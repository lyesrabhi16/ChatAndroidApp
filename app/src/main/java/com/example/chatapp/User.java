package com.example.chatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

/**
 * The signed-in user: profile fields plus the token the server issued at sign in. Everything is kept in
 * SharedPreferences, so the session survives restarts until the user logs out or the token is refused.
 */
public final class User {
    private static User Instance;
    private Context Ctx;
    private static final String SHARED_PREF_NAME = "sharedPref";
    private static final String KEY_ID = "userID";
    private static final String KEY_EMAIL = "userEmail";
    private static final String KEY_USERNAME = "userName";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_AVATAR = "avatar";


    private User(Context context){
        // the application context: this singleton outlives every activity
        Ctx = context.getApplicationContext();
    }

    public static synchronized User getInstance(Context context){
        if (Instance == null){
            Instance = new User(context);
        }
        return Instance;
    }

    /** The instance if one was already created (it always is once an activity has run), else null. */
    public static synchronized User peek(){
        return Instance;
    }

    private SharedPreferences prefs(){
        return Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean userLogin(int id,String email, String username, String token){
        SharedPreferences.Editor editor = prefs().edit();

        editor.putInt(KEY_ID, id);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_TOKEN, token);

        editor.apply();
        return true;

    }

    /** Stores the token alone, for the moment between sign up (which returns it) and loading the profile. */
    public void setToken(String token){
        prefs().edit().putString(KEY_TOKEN, token).apply();
    }

    public boolean isLoggedin(){
        // a session saved before tokens existed has no token: the user has to sign in again
        return getUserID() != -1 && getToken() != null;
    }

    public boolean logout(){
        SocketClient.closeSocket();  // the connection belongs to the user who signed in
        SharedPreferences.Editor editor = prefs().edit();
        editor.clear();
        editor.apply();
        return true;
    }

    /** The server refused our token (expired or invalid): forget the session and go back to the sign-in screen. */
    public void expireSession(){
        logout();
        Toast.makeText(Ctx, "Your session has expired, please sign in again", Toast.LENGTH_LONG).show();
        Intent signin = new Intent(Ctx, SignInActivity.class);
        signin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Ctx.startActivity(signin);
    }

    public String getAvatar(){
        return prefs().getString(KEY_AVATAR, null);
    }
    public void setAvatar(String avatar){
        prefs().edit().putString(KEY_AVATAR, avatar).apply();
    }

    public String getToken(){
        return prefs().getString(KEY_TOKEN, null);
    }
    public String getUserName(){
        return prefs().getString(KEY_USERNAME, null);
    }
    public String getUserEmail(){
        return prefs().getString(KEY_EMAIL, null);
    }
    public int getUserID(){
        return prefs().getInt(KEY_ID, -1);
    }


}
