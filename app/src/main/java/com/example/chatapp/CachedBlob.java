package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A named piece of text: the last conversation list the server sent. */
@Entity(tableName = "cached_blobs")
public class CachedBlob {
    @PrimaryKey
    @NonNull
    public String name = "";
    @NonNull
    public String value = "";
}
