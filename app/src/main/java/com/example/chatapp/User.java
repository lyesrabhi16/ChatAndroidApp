package com.example.chatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class User {
    private static User Instance;
    private Context Ctx;
    private static final String SHARED_PREF_NAME = "sharedPref";
    private static final String KEY_ID = "userID";
    private static final String KEY_EMAIL = "userEmail";
    private static final String KEY_USERNAME = "userName";


    private User(Context context){
        Ctx = context;
    }

    public static synchronized User getInstance(Context context){
        if (Instance == null){
            Instance = new User(context);
        }
        return Instance;
    }

    public boolean userLogin(int id,String email, String username){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedprefs.edit();

        editor.putInt(KEY_ID, id);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, username);

        editor.apply();
        return true;

    }

    public boolean isLoggedin(){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if(sharedprefs.getInt(KEY_ID, -1) != -1){
            return true;
        }
        return false;
    }

    public boolean logout(){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedprefs.edit();
        editor.clear();
        editor.apply();
        return true;
    }

    public String getUserName(){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedprefs.getString(KEY_USERNAME, null);
    }
    public String getUserEmail(){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedprefs.getString(KEY_EMAIL, null);
    }
    public int getUserID(){
        SharedPreferences sharedprefs = Ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedprefs.getInt(KEY_ID, -1);
    }


}
