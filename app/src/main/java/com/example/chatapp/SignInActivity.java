package com.example.chatapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.ActivitySigninBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public class SignInActivity extends AppCompatActivity {
    private ActivitySigninBinding SIBinding;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        User user = User.getInstance(getApplicationContext());
        if(user.isLoggedin()){
            startActivity(new Intent(SignInActivity.this, MainActivity.class));
            overridePendingTransition(0,0);
            finish();

        }

        getSupportActionBar().hide();
        SIBinding = ActivitySigninBinding.inflate(getLayoutInflater());
        setContentView(SIBinding.getRoot());

        SIBinding.buttonSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signin(
                        SIBinding.inputEmail.getText().toString().trim(),
                        SIBinding.inputPassword.getText().toString().trim()
                );
            }
        });

        SIBinding.textViewResetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* TODO: password reset redirect */
                Toast.makeText(SignInActivity.this, "TODO: Password reset", Toast.LENGTH_SHORT).show();
            }
        });
        SIBinding.textViewSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SignInActivity.this, SignUpActivity.class));
                finish();
            }
        });

    }

    private void signin(String userEmail, String userPassword) {
        ProgressDialog prgrs = new ProgressDialog(SignInActivity.this);
        prgrs.setMessage("Attempting Sign In...");
        prgrs.show();
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getSigninURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                prgrs.dismiss();
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(SignInActivity.this, "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        Toast.makeText(SignInActivity.this, "Sign in successful" , Toast.LENGTH_SHORT).show();
                        User user = User.getInstance(getApplicationContext());
                        user.userLogin(res.getInt("userID"), res.getString("userEmail"), res.getString("userName"));
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(i);
                        finish();
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(SignInActivity.this, "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                prgrs.dismiss();
                Toast.makeText(getApplicationContext(), "connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userEmail", userEmail);
                Params.put("userPassword", userPassword);
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);

    }

}
