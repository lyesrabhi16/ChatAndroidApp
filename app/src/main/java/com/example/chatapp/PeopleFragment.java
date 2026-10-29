package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.FragmentChatsBinding;
import com.example.chatapp.databinding.FragmentPeopleBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class PeopleFragment extends Fragment {

    private FragmentPeopleBinding BPeople;
    private PersonItemAdapter adapter;
    private ArrayList<PersonItem> personItemArrayList;

    public PeopleFragment() {
        // Required empty public constructor
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BPeople = FragmentPeopleBinding.inflate(getLayoutInflater());
        User user = User.getInstance(getContext());

        personItemArrayList = new ArrayList<PersonItem>();
        adapter = new PersonItemAdapter(personItemArrayList, new RecyclerViewInterface() {
            @Override
            public void onItemClick(int position) {
                Intent chat = new Intent(getContext(), ChatFragment.class);
                chat.putExtra("userID", adapter.getList().get(position).getUserID());
                chat.putExtra("userName", adapter.getList().get(position).getTitle());
                startActivity(chat);
            }
        });
        BPeople.recyclerPeople.setAdapter(adapter);
                        BPeople.emptyState.setVisibility(View.GONE);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        BPeople = FragmentPeopleBinding.inflate(inflater, container, false);
        View root = BPeople.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = User.getInstance(getContext());
        getFriends(user.getUserID());
    }

    private void getFriends(int userID) {
        BPeople.progressBarPeople.setVisibility(View.VISIBLE);
        StringRequest Sreq = new AuthRequest(Request.Method.POST, Configs.getFriendsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    BPeople.progressBarPeople.setVisibility(View.GONE);
                    if(res.has("error")){
                        Toast.makeText(getContext(), "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        JSONArray resArr = res.getJSONArray("friends");
                        if(resArr.length()<1) {
                            BPeople.emptyState.setVisibility(View.VISIBLE);
                            return;
                        }
                        for (int i = 0; i < resArr.length(); i++) {

                            JSONObject r = resArr.getJSONObject(i);
                            PersonItem item = new PersonItem();
                            item.setUserID(r.getInt("userID"));
                            item.setTitle(r.getString("userName"));
                            item.setSubTitle(r.getString("userEmail"));
                            personItemArrayList.add(item);

                            if(!Friends.getFriends().contains(item.getUserID())){
                                Friends.getFriends().add(item.getUserID());
                            }

                        }
                        adapter.setList(personItemArrayList);
                        BPeople.recyclerPeople.setAdapter(adapter);
                        BPeople.emptyState.setVisibility(View.GONE);
                        BPeople.recyclerPeople.smoothScrollToPosition(personItemArrayList.size()-1);
                    }

                } catch (JSONException e) {
                    BPeople.progressBarPeople.setVisibility(View.GONE);
                    e.printStackTrace();
                    Toast.makeText(getActivity(), "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                BPeople.progressBarPeople.setVisibility(View.GONE);
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