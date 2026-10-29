package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A message as the server sent it (raw JSON), kept so a conversation can be read without a connection. */
@Entity(tableName = "cached_messages", indices = {@Index("conversation")})
public class CachedMessage {
    @PrimaryKey
    public int messageID;
    /** "u:<user id>" for a person, "g:<group id>" for a group */
    @NonNull
    public String conversation = "";
    @NonNull
    public String json = "";
}
