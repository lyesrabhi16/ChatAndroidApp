package com.example.chatapp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.example.chatapp.databinding.FragmentChatBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A conversation, with one person or with a group: history in pages, live messages, photos, voice messages and
 * documents, replies, edit and delete, read ticks, "typing…" and who is online.
 * (The name is historical: this is an Activity.)
 */
public class ChatFragment extends AppCompatActivity {

    private static final int PAGE = 30;

    private FragmentChatBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isGroup;
    private int peerID = -1, groupID = -1, myID;
    private String title, peerAvatar, peerLastSeen;
    private boolean peerOnline, resumed;
    private int memberCount;
    private final Map<Integer, String> memberNames = new HashMap<>();

    private ChatItemAdapter adapter;
    private LinearLayoutManager layoutManager;
    private boolean hasMore, loading;
    private String cacheKey;
    private boolean showingCache;

    private ChatItem replyTarget, editTarget;

    // who is typing right now (user id -> name), each entry expires by itself
    private final Map<Integer, String> typingNow = new LinkedHashMap<>();
    private boolean sentTyping;
    private final Runnable stopTyping = () -> sendTyping(false);

    private VoiceRecorder recorder;
    private final List<Float> recordedLevels = new ArrayList<>();   // one per tick, kept to draw the sent message
    private final Runnable recordingTick = new Runnable() {
        @Override
        public void run() {
            if (recorder == null) return;
            long elapsed = recorder.elapsed();
            b.recordingTime.setText(Media.clock(elapsed));
            float level = Waveforms.level(recorder.amplitude());
            recordedLevels.add(level);
            b.recordingWave.push(level);
            if (elapsed >= VoiceRecorder.MAX_MS) finishRecording(true);
            else handler.postDelayed(this, 60);
        }
    };

    private MediaPlayer player;
    private int playingID = -1;
    private final Runnable playbackTick = new Runnable() {
        @Override
        public void run() {
            if (player == null || !player.isPlaying()) return;
            int duration = Math.max(player.getDuration(), 1);
            adapter.setPlayback(playingID, true, player.getCurrentPosition() / (float) duration);
            handler.postDelayed(this, 150);
        }
    };

    private ActivityResultLauncher<String> pickPhoto, pickDocument, askMicrophone;

    private final ChatEvents.Listener events = new ChatEvents.Listener() {
        @Override
        public void onChatMessage(JSONObject message) {
            if (!belongsHere(message)) return;
            Cache.saveMessage(ChatFragment.this, cacheKey, message);
            ChatItem item = ChatItem.fromJson(message, myID);
            boolean added = addIfNew(item);
            if (added && item.getType() == ChatItem.RECEIVER) {
                typingNow.remove(item.getSender());
                updateStatus();
                if (resumed) markRead();
            }
        }

        @Override
        public void onMessageUpdated(JSONObject message) {
            if (!belongsHere(message)) return;
            Cache.saveMessage(ChatFragment.this, cacheKey, message);
            ChatItem updated = ChatItem.fromJson(message, myID);
            int index = adapter.indexOf(updated.getMessageID());
            if (index < 0) return;
            adapter.replace(index, updated);
            // the messages that quote it show the new state
            if (updated.isDeleted()) {
                List<ChatItem> list = adapter.getList();
                for (int i = 0; i < list.size(); i++) {
                    ChatItem.Reply reply = list.get(i).getReply();
                    if (reply != null && reply.messageID == updated.getMessageID()) {
                        reply.deleted = true;
                        adapter.changed(i);
                    }
                }
            }
        }

        @Override
        public void onMessagesRead(int by, JSONArray ids) {
            if (isGroup || by != peerID || ids == null) return;
            for (int i = 0; i < ids.length(); i++) {
                int index = adapter.indexOf(ids.optInt(i));
                if (index >= 0) {
                    adapter.getList().get(index).setRead(true);
                    adapter.changed(index);
                }
            }
        }

        @Override
        public void onTyping(int from, int group, boolean typing) {
            if (isGroup ? group != groupID : (group != -1 || from != peerID)) return;
            if (typing) {
                typingNow.put(from, isGroup ? memberNames.getOrDefault(from, "Someone") : "");
                handler.removeCallbacks(expireTyping(from));
                handler.postDelayed(expireTyping(from), 5000);
            } else {
                typingNow.remove(from);
            }
            updateStatus();
        }

        @Override
        public void onPresence(int userID, boolean online, String lastSeen) {
            if (isGroup || userID != peerID) return;
            peerOnline = online;
            if (lastSeen != null) peerLastSeen = lastSeen;
            updateStatus();
        }

        @Override
        public void onGroupUpdated(int id, boolean removed) {
            if (!isGroup || id != groupID) return;
            if (removed) {
                Toast.makeText(ChatFragment.this, "You are no longer in this group", Toast.LENGTH_LONG).show();
                finish();
            } else {
                loadHeader();
            }
        }

        @Override
        public void onReconnected() {
            loadPage(null);   // whatever arrived while the connection was down
            loadHeader();
        }
    };

    private final Map<Integer, Runnable> expiry = new HashMap<>();

    private Runnable expireTyping(int from) {
        Runnable r = expiry.get(from);
        if (r == null) {
            r = () -> {
                typingNow.remove(from);
                updateStatus();
            };
            expiry.put(from, r);
        }
        return r;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();  // the theme has no action bar

        b = FragmentChatBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        User me = User.getInstance(getApplicationContext());
        myID = me.getUserID();
        groupID = getIntent().getIntExtra("groupID", -1);
        isGroup = groupID != -1;
        peerID = getIntent().getIntExtra("userID", -1);
        title = getIntent().getStringExtra(isGroup ? "groupName" : "userName");
        peerAvatar = getIntent().getStringExtra("avatar");

        b.chatTitle.setText(title);
        Avatars.apply(b.chatHeaderAvatar, title, isGroup ? null : peerAvatar);
        cacheKey = Cache.key(isGroup, isGroup ? groupID : peerID);

        adapter = new ChatItemAdapter(myID, new ChatItemAdapter.Actions() {
            @Override
            public void onLongClick(ChatItem item, View bubble) {
                showMessageMenu(item, bubble);
            }

            @Override
            public void onReplyClick(int messageID) {
                int index = adapter.indexOf(messageID);
                if (index >= 0) b.recylcerChat.smoothScrollToPosition(index);
                else toast("That message is further up in the conversation");
            }

            @Override
            public void onImageClick(ChatItem item) {
                Intent view = new Intent(ChatFragment.this, ImageViewerActivity.class);
                view.putExtra("attachment", item.getAttachment());
                startActivity(view);
            }

            @Override
            public void onFileClick(ChatItem item) {
                openFile(item);
            }

            @Override
            public void onAudioClick(ChatItem item) {
                toggleAudio(item);
            }
        });
        adapter.setGroup(isGroup);
        layoutManager = (LinearLayoutManager) b.recylcerChat.getLayoutManager();
        b.recylcerChat.setAdapter(adapter);
        ((SimpleItemAnimator) b.recylcerChat.getItemAnimator()).setSupportsChangeAnimations(false);
        b.recylcerChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                // near the top: the page of older messages
                if (dy < 0 && hasMore && !loading && layoutManager.findFirstVisibleItemPosition() <= 3 && adapter.getItemCount() > 0) {
                    loadPage(adapter.getList().get(0).getMessageID());
                }
            }
        });

        pickPhoto = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) Attachments.photo(this, uri, attachmentReady(ChatItem.IMAGE, 0, null));
        });
        pickDocument = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) Attachments.document(this, uri, attachmentReady(ChatItem.FILE, 0, null));
        });
        askMicrophone = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startRecording();
            else toast("Allow the microphone to send voice messages");
        });

        b.buttonBack.setOnClickListener(v -> finish());
        b.headerInfo.setOnClickListener(v -> {
            if (isGroup) {
                Intent info = new Intent(this, GroupInfoActivity.class);
                info.putExtra("groupID", groupID);
                startActivity(info);
            } else {
                Intent account = new Intent(this, AccountActivity.class);
                account.putExtra("userID", peerID);
                startActivity(account);
            }
        });
        b.buttonAttach.setOnClickListener(this::showAttachMenu);
        b.buttonSend.setOnClickListener(v -> {
            if (b.edittextMessage.getText().toString().trim().isEmpty()) onMicrophone();
            else sendText();
        });
        b.buttonRecordCancel.setOnClickListener(v -> finishRecording(false));
        b.buttonRecordSend.setOnClickListener(v -> finishRecording(true));
        b.replyBarClose.setOnClickListener(v -> clearReplyBar(true));
        b.edittextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean empty = s.toString().trim().isEmpty();
                b.buttonSend.setIconResource(empty ? R.drawable.ic_mic : R.drawable.baseline_send_24);
                b.buttonSend.setContentDescription(empty ? "Record a voice message" : "Send");
                if (editTarget != null) return;
                if (empty) {
                    sendTyping(false);
                } else {
                    sendTyping(true);
                    handler.removeCallbacks(stopTyping);
                    handler.postDelayed(stopTyping, 3000);
                }
            }
        });

        // what was saved last time shows at once; the server's answer then replaces it
        Cache.messages(this, cacheKey, cached -> {
            if (cached.isEmpty() || adapter.getItemCount() > 0 || isDestroyed()) return;
            List<ChatItem> items = new ArrayList<>();
            for (JSONObject m : cached) items.add(ChatItem.fromJson(m, myID));
            showingCache = true;
            adapter.setList(items);
            b.recylcerChat.scrollToPosition(items.size() - 1);
        });
        loadHeader();
        loadPage(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ChatEvents.add(events);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ChatEvents.remove(events);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        SocketClient.getSocket();   // makes sure the connection is open
        markRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        sendTyping(false);
        if (recorder != null) finishRecording(false);
        stopPlayback();
    }

    // ---- loading ---------------------------------------------------------------------------------------------------

    /** Name, picture and presence of the other person, or the name and members of the group. */
    private void loadHeader() {
        if (isGroup) {
            Api.post(this, "/groups/info", Api.body("groupID", groupID), new Api.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    JSONObject group = json.optJSONObject("group");
                    JSONArray members = group == null ? null : group.optJSONArray("members");
                    if (group == null || members == null) return;
                    title = group.optString("name", title);
                    b.chatTitle.setText(title);
                    Avatars.apply(b.chatHeaderAvatar, title);
                    memberNames.clear();
                    for (int i = 0; i < members.length(); i++) {
                        JSONObject m = members.optJSONObject(i);
                        if (m != null) memberNames.put(m.optInt("userID"), m.optString("userName"));
                    }
                    memberCount = members.length();
                    updateStatus();
                }

                @Override
                public void onError(String message) {}
            });
        } else {
            Api.post(this, "/user", Api.body("userID", peerID), new Api.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    peerOnline = json.optBoolean("online");
                    peerLastSeen = Api.str(json, "lastSeen");
                    String avatar = Api.str(json, "avatar");
                    if (avatar != null) Avatars.apply(b.chatHeaderAvatar, title, avatar);
                    updateStatus();
                }

                @Override
                public void onError(String message) {}
            });
        }
    }

    private void updateStatus() {
        String status;
        if (isGroup) {
            if (!typingNow.isEmpty()) {
                List<String> names = new ArrayList<>(typingNow.values());
                status = names.size() == 1 ? names.get(0) + " is typing…" : "Several people are typing…";
            } else {
                status = memberCount == 0 ? "" : memberCount + " members";
            }
        } else if (!typingNow.isEmpty()) {
            status = "typing…";
        } else if (peerOnline) {
            status = "online";
        } else {
            status = peerLastSeen == null ? "" : TimeFormat.lastSeen(peerLastSeen);
        }
        b.chatStatus.setText(status);
        b.chatStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        boolean highlight = !typingNow.isEmpty() || (!isGroup && peerOnline);
        b.chatStatus.setTextColor(com.google.android.material.color.MaterialColors.getColor(b.chatStatus,
                highlight ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    /** The latest page when `before` is null, otherwise the page of messages older than that id. */
    private void loadPage(@Nullable Integer before) {
        if (loading) return;
        loading = true;
        if (before == null && adapter.getItemCount() == 0) b.progressBar2.setVisibility(View.VISIBLE);
        JSONObject body = isGroup
                ? Api.body("groupID", groupID, "limit", PAGE, "before", before)
                : Api.body("userID1", myID, "userID2", peerID, "limit", PAGE, "before", before);
        Api.post(this, isGroup ? "/groups/messages" : "/messages", body, new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                loading = false;
                b.progressBar2.setVisibility(View.GONE);
                List<ChatItem> page = parse(json.optJSONArray("messages"));
                if (before == null) Cache.saveMessages(ChatFragment.this, cacheKey, json.optJSONArray("messages"));
                if (before != null) {
                    hasMore = json.optBoolean("hasMore");
                    prependKeepingPosition(page);
                } else if (adapter.getItemCount() == 0 || showingCache) {
                    showingCache = false;
                    hasMore = json.optBoolean("hasMore");
                    adapter.setList(page);
                    if (!page.isEmpty()) b.recylcerChat.scrollToPosition(page.size() - 1);
                } else {
                    mergeLatest(page);   // after a lost connection
                }
                if (resumed) markRead();
            }

            @Override
            public void onError(String message) {
                loading = false;
                b.progressBar2.setVisibility(View.GONE);
                toast(showingCache ? "Offline: showing saved messages" : message);
            }
        });
    }

    private List<ChatItem> parse(@Nullable JSONArray array) {
        List<ChatItem> items = new ArrayList<>();
        if (array == null) return items;
        for (int i = 0; i < array.length(); i++) {
            JSONObject m = array.optJSONObject(i);
            if (m != null) items.add(ChatItem.fromJson(m, myID));
        }
        return items;
    }

    private void prependKeepingPosition(List<ChatItem> older) {
        if (older.isEmpty()) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        View view = layoutManager.findViewByPosition(first);
        int offset = view == null ? 0 : view.getTop() - b.recylcerChat.getPaddingTop();
        adapter.prepend(older);
        layoutManager.scrollToPositionWithOffset(first + older.size(), offset);
    }

    private void mergeLatest(List<ChatItem> latest) {
        for (ChatItem item : latest) {
            int index = adapter.indexOf(item.getMessageID());
            if (index >= 0) adapter.replace(index, item);
            else adapter.add(item);
        }
        if (adapter.getItemCount() > 0) b.recylcerChat.scrollToPosition(adapter.getItemCount() - 1);
    }

    private boolean belongsHere(JSONObject m) {
        if (isGroup) return !m.isNull("groupID") && m.optInt("groupID") == groupID;
        if (!m.isNull("groupID")) return false;
        int sender = m.optInt("sender"), receiver = m.optInt("receiver", -1);
        return (sender == peerID && receiver == myID) || (sender == myID && receiver == peerID);
    }

    /** A message can arrive twice (the reply to our own request and the live echo): it is shown once. */
    private boolean addIfNew(ChatItem item) {
        if (adapter.indexOf(item.getMessageID()) >= 0) return false;
        boolean nearBottom = layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
        adapter.add(item);
        if (nearBottom || item.getType() == ChatItem.SENDER) b.recylcerChat.smoothScrollToPosition(adapter.getItemCount() - 1);
        return true;
    }

    private void markRead() {
        JSONObject body = isGroup ? Api.body("groupID", groupID) : Api.body("userID", peerID);
        Api.post(this, "/messages/read", body, new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {}

            @Override
            public void onError(String message) {}
        });
    }

    // ---- sending ---------------------------------------------------------------------------------------------------

    private JSONObject newMessage(String type) {
        JSONObject body = Api.body(isGroup ? "groupID" : "receiver", isGroup ? groupID : peerID, "type", type);
        if (replyTarget != null) {
            try {
                body.put("replyTo", replyTarget.getMessageID());
            } catch (JSONException ignored) {
                // cannot happen: the key is a constant
            }
        }
        return body;
    }

    private void post(JSONObject body) {
        Api.post(this, "/messages/send", body, new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                b.progressBar2.setVisibility(View.GONE);
                JSONObject message = json.optJSONObject("message");
                if (message != null) addIfNew(ChatItem.fromJson(message, myID));
            }

            @Override
            public void onError(String message) {
                b.progressBar2.setVisibility(View.GONE);
                toast(message);
            }
        });
    }

    private void sendText() {
        String text = b.edittextMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        if (editTarget != null) {
            saveEdit(editTarget, text);
            return;
        }
        JSONObject body = newMessage(ChatItem.TEXT);
        try {
            body.put("content", text);
        } catch (JSONException e) {
            return;
        }
        b.edittextMessage.setText("");
        clearReplyBar(false);
        sendTyping(false);
        Api.post(this, "/messages/send", body, new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject message = json.optJSONObject("message");
                if (message != null) addIfNew(ChatItem.fromJson(message, myID));
            }

            @Override
            public void onError(String message) {
                toast(message);
                if (b.edittextMessage.getText().toString().isEmpty()) b.edittextMessage.setText(text);   // nothing is lost
            }
        });
    }

    /** What to do once a photo, document or recording has been read: upload it, then send the message. */
    private Attachments.Callback attachmentReady(String type, int durationMs, @Nullable JSONArray waveform) {
        return new Attachments.Callback() {
            @Override
            public void onPicked(Attachments.Picked picked) {
                b.progressBar2.setVisibility(View.VISIBLE);
                Api.upload(ChatFragment.this, picked.data, picked.mime, picked.name, new Api.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        JSONObject body = newMessage(type);
                        try {
                            body.put("attachment", json.getString("attachment"));
                            if (durationMs > 0) body.put("duration", durationMs);
                            if (waveform != null) body.put("waveform", waveform);
                        } catch (JSONException e) {
                            b.progressBar2.setVisibility(View.GONE);
                            return;
                        }
                        clearReplyBar(false);
                        post(body);
                    }

                    @Override
                    public void onError(String message) {
                        b.progressBar2.setVisibility(View.GONE);
                        toast(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        };
    }

    private void sendTyping(boolean typing) {
        if (typing == sentTyping) return;
        sentTyping = typing;
        handler.removeCallbacks(stopTyping);
        JSONObject body = Api.body(isGroup ? "groupID" : "to", isGroup ? groupID : peerID, "typing", typing);
        SocketClient.getSocket().emit("typing", body.toString());
    }

    // ---- attach menu -----------------------------------------------------------------------------------------------

    private void showAttachMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Photo");
        menu.getMenu().add(0, 2, 1, "File");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) pickPhoto.launch("image/*");
            else pickDocument.launch("*/*");
            return true;
        });
        menu.show();
    }

    // ---- message menu: reply, copy, edit, delete -------------------------------------------------------------------

    private void showMessageMenu(ChatItem item, View anchor) {
        if (item.isDeleted()) return;
        boolean mine = item.getType() == ChatItem.SENDER;
        boolean text = ChatItem.TEXT.equals(item.getMsgType());
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Reply");
        if (!item.getMessage().isEmpty()) menu.getMenu().add(0, 2, 1, "Copy");
        if (mine && text) menu.getMenu().add(0, 3, 2, "Edit");
        if (mine) menu.getMenu().add(0, 4, 3, "Delete");
        menu.setOnMenuItemClickListener(m -> {
            switch (m.getItemId()) {
                case 1: startReply(item); break;
                case 2:
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("message", item.getMessage()));
                    toast("Copied");
                    break;
                case 3: startEdit(item); break;
                case 4: confirmDelete(item); break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }

    private void startReply(ChatItem item) {
        cancelEdit();
        replyTarget = item;
        String who = item.getType() == ChatItem.SENDER ? "yourself" : (item.getSenderName() != null ? item.getSenderName() : title);
        showReplyBar("Replying to " + who, item.summary());
        b.edittextMessage.requestFocus();
    }

    private void startEdit(ChatItem item) {
        clearReplyBar(false);
        editTarget = item;
        showReplyBar("Editing message", item.getMessage());
        b.edittextMessage.setText(item.getMessage());
        b.edittextMessage.setSelection(b.edittextMessage.getText().length());
        b.edittextMessage.requestFocus();
    }

    private void cancelEdit() {
        if (editTarget == null) return;
        editTarget = null;
        b.edittextMessage.setText("");
        b.replyBar.setVisibility(View.GONE);
    }

    private void saveEdit(ChatItem item, String text) {
        Api.post(this, "/messages/edit", Api.body("messageID", item.getMessageID(), "content", text), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject message = json.optJSONObject("message");
                if (message != null) {
                    ChatItem updated = ChatItem.fromJson(message, myID);
                    int index = adapter.indexOf(updated.getMessageID());
                    if (index >= 0) adapter.replace(index, updated);
                }
                editTarget = null;
                b.edittextMessage.setText("");
                b.replyBar.setVisibility(View.GONE);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void confirmDelete(ChatItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this message?")
                .setMessage("It will be removed for everyone in the conversation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        Api.post(this, "/messages/delete", Api.body("messageID", item.getMessageID()), new Api.Callback() {
                            @Override
                            public void onSuccess(JSONObject json) {
                                JSONObject message = json.optJSONObject("message");
                                if (message != null) events.onMessageUpdated(message);
                            }

                            @Override
                            public void onError(String message) {
                                toast(message);
                            }
                        }))
                .show();
    }

    private void showReplyBar(String titleText, String text) {
        b.replyBarTitle.setText(titleText);
        b.replyBarText.setText(text);
        b.replyBar.setVisibility(View.VISIBLE);
    }

    /** `cancelEditing`: the X was pressed, which also abandons an edit in progress. */
    private void clearReplyBar(boolean cancelEditing) {
        replyTarget = null;
        if (cancelEditing) cancelEdit();
        if (editTarget == null) b.replyBar.setVisibility(View.GONE);
    }

    // ---- voice messages --------------------------------------------------------------------------------------------

    private void onMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        stopPlayback();
        recorder = new VoiceRecorder();
        if (!recorder.start(this)) {
            recorder = null;
            toast("Could not start the microphone");
            return;
        }
        b.inputRow.setVisibility(View.GONE);
        b.recordingBar.setVisibility(View.VISIBLE);
        b.recordingTime.setText("0:00");
        recordedLevels.clear();
        b.recordingWave.clearLive();
        handler.postDelayed(recordingTick, 60);
    }

    private void finishRecording(boolean send) {
        if (recorder == null) return;
        handler.removeCallbacks(recordingTick);
        VoiceRecorder current = recorder;
        recorder = null;
        b.recordingBar.setVisibility(View.GONE);
        b.inputRow.setVisibility(View.VISIBLE);
        if (!send) {
            current.cancel();
            return;
        }
        VoiceRecorder.Result result = current.stop();
        if (result == null || result.durationMs < 700) {
            if (result != null) //noinspection ResultOfMethodCallIgnored
                result.file.delete();
            toast("Recording too short");
            return;
        }
        Attachments.local(result.file, "audio/mp4", "voice-" + System.currentTimeMillis() + ".m4a",
                attachmentReady(ChatItem.AUDIO, (int) result.durationMs, Waveforms.downsample(recordedLevels, Waveforms.BARS)));
    }

    // ---- playing and opening attachments -------------------------------------------------------------------------

    private void toggleAudio(ChatItem item) {
        if (player != null && playingID == item.getMessageID()) {
            if (player.isPlaying()) {
                player.pause();
                adapter.setPlayback(playingID, false, player.getCurrentPosition() / (float) Math.max(player.getDuration(), 1));
            } else {
                player.start();
                handler.post(playbackTick);
            }
            return;
        }
        stopPlayback();
        Media.file(this, item.getAttachment(), file -> {
            if (file == null || isDestroyed()) {
                toast("Could not load the voice message");
                return;
            }
            try {
                MediaPlayer p = new MediaPlayer();
                p.setDataSource(file.getPath());
                p.prepare();
                p.setOnCompletionListener(mp -> stopPlayback());
                player = p;
                playingID = item.getMessageID();
                p.start();
                handler.post(playbackTick);
            } catch (IOException | RuntimeException e) {
                toast("Could not play the voice message");
            }
        });
    }

    private void stopPlayback() {
        handler.removeCallbacks(playbackTick);
        if (player != null) {
            player.release();
            player = null;
        }
        if (playingID != -1) {
            adapter.setPlayback(playingID, false, 0f);
            playingID = -1;
        }
    }

    private void openFile(ChatItem item) {
        Media.file(this, item.getAttachment(), file -> {
            if (file == null) {
                toast("Could not download the file");
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            String name = item.getFileName() == null ? file.getName() : item.getFileName();
            String ext = MimeTypeMap.getFileExtensionFromUrl(name.replace(" ", "_"));
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext == null ? "" : ext.toLowerCase());
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime == null ? "*/*" : mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(view, name));
            } catch (ActivityNotFoundException e) {
                toast("No app can open this file");
            }
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (recorder != null) recorder.cancel();
        stopPlayback();
    }
}
