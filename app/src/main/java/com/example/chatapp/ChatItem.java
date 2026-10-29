package com.example.chatapp;

import androidx.annotation.Nullable;

import org.json.JSONObject;

/** One message of a conversation, as the server describes it, ready to be shown. */
public class ChatItem {
    /** Whose message it is: yours (right) or the other person's (left). */
    public static final int SENDER = 0;
    public static final int RECEIVER = 1;

    public static final String TEXT = "text", IMAGE = "image", AUDIO = "audio", FILE = "file";

    /** What a reply quotes. */
    public static class Reply {
        public int messageID, sender;
        public String senderName, type, content;
        public boolean deleted;
    }

    private int messageID, type, sender, groupID = -1, replyTo = -1, fileSize, duration;
    private String senderName, msgType = TEXT, message = "", attachment, fileName, date;
    private boolean edited, deleted, read;
    private Reply reply;
    private float[] waveform;

    /** `myID` decides the side of the bubble. */
    public static ChatItem fromJson(JSONObject m, int myID) {
        ChatItem item = new ChatItem();
        item.messageID = m.optInt("messageID");
        item.sender = m.optInt("sender");
        item.type = item.sender == myID ? SENDER : RECEIVER;
        item.groupID = m.isNull("groupID") ? -1 : m.optInt("groupID", -1);
        item.senderName = Api.str(m, "senderName");
        item.msgType = m.optString("type", TEXT);
        item.message = m.optString("content", "");
        item.attachment = Api.str(m, "attachment");
        item.fileName = Api.str(m, "fileName");
        item.fileSize = m.optInt("fileSize");
        item.duration = m.optInt("duration");
        item.waveform = Waveforms.fromJson(m.optJSONArray("waveform"));
        item.replyTo = m.isNull("replyTo") ? -1 : m.optInt("replyTo", -1);
        item.edited = m.optBoolean("edited");
        item.deleted = m.optBoolean("deleted");
        item.read = !m.isNull("readAt");
        item.date = Api.str(m, "date");
        JSONObject r = m.optJSONObject("reply");
        if (r != null) {
            item.reply = new Reply();
            item.reply.messageID = r.optInt("messageID");
            item.reply.sender = r.optInt("sender");
            item.reply.senderName = Api.str(r, "senderName");
            item.reply.type = r.optString("type", TEXT);
            item.reply.content = r.optString("content", "");
            item.reply.deleted = r.optBoolean("deleted");
        }
        return item;
    }

    /** The one-line description of a message in the conversation list and in the reply quote. */
    public static String summary(String msgType, String content, @Nullable String fileName, boolean deleted) {
        if (deleted) return "This message was deleted";
        switch (msgType == null ? TEXT : msgType) {
            case IMAGE: return content == null || content.isEmpty() ? "Photo" : "Photo: " + content;
            case AUDIO: return "Voice message";
            case FILE: return fileName == null || fileName.isEmpty() ? "File" : fileName;
            default: return content == null ? "" : content.replace('\n', ' ');
        }
    }

    public String summary() {
        return summary(msgType, message, fileName, deleted);
    }

    public int getMessageID() { return messageID; }
    public int getType() { return type; }
    public int getSender() { return sender; }
    public int getGroupID() { return groupID; }
    public int getReplyTo() { return replyTo; }
    public int getFileSize() { return fileSize; }
    public int getDuration() { return duration; }
    public String getSenderName() { return senderName; }
    public String getMsgType() { return msgType; }
    public String getMessage() { return message; }
    public String getAttachment() { return attachment; }
    public String getFileName() { return fileName; }
    public String getDate() { return date; }
    public boolean isEdited() { return edited; }
    public boolean isDeleted() { return deleted; }
    public boolean isRead() { return read; }
    public Reply getReply() { return reply; }
    /** The loudness bars of a voice message: the recorded ones, or a stand-in for a message that has none. */
    public float[] getWaveform() {
        return waveform != null ? waveform : Waveforms.pseudo(messageID, Waveforms.BARS);
    }

    public void setRead(boolean read) { this.read = read; }
}
