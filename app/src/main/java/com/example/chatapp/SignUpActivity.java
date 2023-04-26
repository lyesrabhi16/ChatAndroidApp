package com.example.chatapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.ActivitySignUpBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private ActivitySignUpBinding SUBinding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SUBinding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(SUBinding.getRoot());

        SUBinding.textViewSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
                finish();
            }
        });

        SUBinding.buttonSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String username  = SUBinding.inputUsername.getText().toString().trim();
                String email     = SUBinding.inputEmail.getText().toString().trim();
                String password  = SUBinding.inputPassword.getText().toString().trim();
                String password2 = SUBinding.inputPassword2.getText().toString().trim();
                Boolean err = false;
                if(username.isEmpty()) {
                    SUBinding.textInputLayoutUsername.setError("username required");
                    err = true;
                }
                else{
                    SUBinding.textInputLayoutUsername.setError(null);
                }
                if(email.isEmpty()) {
                    SUBinding.textInputLayoutEmail.setError("email required");
                    err = true;
                }

                else{
                    SUBinding.textInputLayoutEmail.setError(null);
                }
                if(password.isEmpty()) {
                    SUBinding.textinputLayoutPassword.setError("password required");
                    err = true;
                }

                else{
                    SUBinding.textinputLayoutPassword.setError(null);
                }
                if(password2.isEmpty()) {
                    SUBinding.textinputLayoutPassword2.setError("confirm password required");
                    err = true;
                }
                else{
                    SUBinding.textinputLayoutPassword2.setError(null);

                    if(!password.equals(password2)) {
                        SUBinding.textinputLayoutPassword2.setError("confirm password is different from password");
                        err = true;
                    }
                    else{
                        SUBinding.textinputLayoutPassword2.setError(null);
                    }
                }

                if(!err) {
                    SignUp(username, email, password);
                }
            }
        });

    }

    private void SignUp(String username, String email, String password) {
        ProgressDialog prgrs = new ProgressDialog(SignUpActivity.this);
        prgrs.setMessage("Attempting Sign Up...");
        prgrs.show();
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getSignupURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                prgrs.dismiss();
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(SignUpActivity.this, "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        Toast.makeText(SignUpActivity.this, "Sign up successful" , Toast.LENGTH_SHORT).show();
                        setUser(res.getInt("userID"));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(SignUpActivity.this, "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                prgrs.dismiss();
                Toast.makeText(getApplicationContext(), "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userName", username);
                Params.put("userEmail", email);
                Params.put("userPassword", password);
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);

    }
    private void setUser(int id){
        ProgressDialog prgrs = new ProgressDialog(SignUpActivity.this);
        prgrs.setMessage("Attempting Sign In...");
        prgrs.show();
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getUserURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                prgrs.dismiss();
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(SignUpActivity.this, "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        Toast.makeText(SignUpActivity.this, "Sign in successful" , Toast.LENGTH_SHORT).show();
                        User user = User.getInstance(getApplicationContext());
                        user.userLogin(res.getInt("userID"), res.getString("userEmail"), res.getString("userName"));
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(i);
                        finish();
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(SignUpActivity.this, "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
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
                Params.put("userID", id+"");
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);
    }
}