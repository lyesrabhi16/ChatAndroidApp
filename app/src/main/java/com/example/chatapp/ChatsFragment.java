package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.FragmentChatsBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ChatsFragment extends Fragment {

    private FragmentChatsBinding BChats;
    private PersonItemAdapter adapter;


    public ChatsFragment() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BChats = FragmentChatsBinding.inflate(getLayoutInflater());

        User user = User.getInstance(getContext());

        ArrayList<PersonItem> list = new ArrayList<PersonItem>();
        adapter = new PersonItemAdapter(list, new RecyclerViewInterface() {
            @Override
            public void onItemClick(int position) {
                Intent chat = new Intent(getContext(), ChatFragment.class);
                chat.putExtra("userID", adapter.getList().get(position).getUserID());
                chat.putExtra("userName", adapter.getList().get(position).getTitle());
                startActivity(chat);
            }
        });
        BChats.recyclerChats.setAdapter(adapter);

    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        BChats = FragmentChatsBinding.inflate(inflater, container, false);
        View view = BChats.getRoot();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = User.getInstance(getContext());
        getChats(user.getUserID());


    }

    private void getChats(int userID) {
        BChats.progressBar.setVisibility(View.VISIBLE);
        StringRequest Sreq = new AuthRequest(Request.Method.POST, Configs.getChatsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    BChats.progressBar.setVisibility(View.GONE);
                    if(res.has("error")){
                        Toast.makeText(getContext(), "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        User user = User.getInstance(getContext());
                        JSONArray resArr = res.getJSONArray("messages");
                        if(resArr.length()<1) {
                            Toast.makeText(getActivity(), "0 messages", Toast.LENGTH_LONG).show();
                            return;
                        }
                        ArrayList<PersonItem> l = new ArrayList<PersonItem>();
                        ArrayList<Integer> ids = new ArrayList<>();
                        for (int i = 0; i < resArr.length(); i++) {

                            JSONObject r = resArr.getJSONObject(i);
                            if(ids.contains(r.getInt("userID")))
                                continue;
                            else{

                                if(!r.get("userID").equals(user.getUserID()))
                                {

                                    PersonItem item = new PersonItem();
                                    item.setUserID(r.getInt("userID"));
                                    item.setTitle(r.getString("userName"));
                                    item.setSubTitle("");
                                    if(user.getUserID() == r.getInt("sender")){
                                        item.setSubTitle("You: ");
                                    }
                                    item.setSubTitle(item.getSubTitle()+""+r.getString("content"));
                                    item.setData(r.getString("date"));
                                    l.add(item);
                                    ids.add(item.getUserID());
                                }
                            }
                            adapter.setList(l);
                            BChats.recyclerChats.setAdapter(adapter);

                        }
                    }

                } catch (JSONException e) {
                    BChats.progressBar.setVisibility(View.GONE);
                    e.printStackTrace();
                    Toast.makeText(getActivity(), "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                BChats.progressBar.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID", userID+"");
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getContext());
        reqQ.add(Sreq);

    }
}