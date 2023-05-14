package com.example.chatapp;

public class ChatItem {
    public static final int  SENDER = 0;
    public static final int  RECEIVER = 1;
    private int messageID, type;
    private String  header, message, footer, avatarText;


    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public String getAvatarText() {
        return avatarText;
    }

    public void setAvatarText(String avatarText) {
        this.avatarText = avatarText;
    }

    public int getMessageID() {
        return messageID;
    }

    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }



    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
