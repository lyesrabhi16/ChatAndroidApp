package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.databinding.ActivityGroupCreateBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Choose a name and members (from your friends) for a new group. With a "groupID" extra it is the same screen
 * in "add members" mode: no name, and the people already in the group are left out.
 */
public class GroupCreateActivity extends AppCompatActivity {

    private ActivityGroupCreateBinding b;
    private PersonItemAdapter adapter;
    private int groupID = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        b = ActivityGroupCreateBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        groupID = getIntent().getIntExtra("groupID", -1);
        boolean adding = groupID != -1;
        if (adding) {
            b.screenTitle.setText("Add members");
            b.groupNameLayout.setVisibility(View.GONE);
            b.membersLabel.setText("Choose who to add");
            b.buttonCreateGroup.setText("Add");
        }

        adapter = new PersonItemAdapter(new ArrayList<>(), position -> {});
        adapter.setSelectable(true);
        b.recyclerFriends.setAdapter(adapter);
        b.buttonBack.setOnClickListener(v -> finish());
        b.buttonCreateGroup.setOnClickListener(v -> submit());

        if (adding) {
            Api.post(this, "/groups/info", Api.body("groupID", groupID), new Api.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    Set<Integer> already = new HashSet<>();
                    JSONObject group = json.optJSONObject("group");
                    JSONArray members = group == null ? null : group.optJSONArray("members");
                    for (int i = 0; members != null && i < members.length(); i++) already.add(members.optJSONObject(i).optInt("userID"));
                    loadFriends(already);
                }

                @Override
                public void onError(String message) {
                    toast(message);
                }
            });
        } else {
            loadFriends(new HashSet<>());
        }
    }

    private void loadFriends(Set<Integer> exclude) {
        Api.post(this, "/friends", new JSONObject(), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                ArrayList<PersonItem> list = new ArrayList<>();
                JSONArray friends = json.optJSONArray("friends");
                for (int i = 0; friends != null && i < friends.length(); i++) {
                    JSONObject f = friends.optJSONObject(i);
                    if (f == null || exclude.contains(f.optInt("userID"))) continue;
                    PersonItem item = new PersonItem();
                    item.setUserID(f.optInt("userID"));
                    item.setTitle(f.optString("userName"));
                    item.setSubTitle(f.optString("userEmail"));
                    item.setAvatar(Api.str(f, "avatar"));
                    list.add(item);
                }
                adapter.setList(list);
                b.noFriends.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void submit() {
        JSONArray ids = new JSONArray();
        for (PersonItem item : adapter.getList()) if (item.isSelected()) ids.put(item.getUserID());
        if (ids.length() == 0) {
            toast("Choose at least one person");
            return;
        }
        b.buttonCreateGroup.setEnabled(false);
        if (groupID != -1) addMembers(ids, 0);
        else createGroup(ids);
    }

    private void createGroup(JSONArray ids) {
        String name = b.groupName.getText() == null ? "" : b.groupName.getText().toString().trim();
        if (name.isEmpty()) {
            b.buttonCreateGroup.setEnabled(true);
            b.groupNameLayout.setError("Give the group a name");
            return;
        }
        b.groupNameLayout.setError(null);
        JSONObject body = Api.body("name", name, "members", ids);
        Api.post(this, "/groups/create", body, new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                Intent chat = new Intent(GroupCreateActivity.this, ChatFragment.class);
                chat.putExtra("groupID", json.optInt("groupID"));
                chat.putExtra("groupName", name);
                startActivity(chat);
                finish();
            }

            @Override
            public void onError(String message) {
                b.buttonCreateGroup.setEnabled(true);
                toast(message);
            }
        });
    }

    private void addMembers(JSONArray ids, int index) {
        if (index >= ids.length()) {
            finish();
            return;
        }
        Api.post(this, "/groups/add", Api.body("groupID", groupID, "userID", ids.optInt(index)), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                addMembers(ids, index + 1);
            }

            @Override
            public void onError(String message) {
                b.buttonCreateGroup.setEnabled(true);
                toast(message);
            }
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
