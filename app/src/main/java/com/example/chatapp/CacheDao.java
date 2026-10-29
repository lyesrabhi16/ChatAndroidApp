package com.example.chatapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CacheDao {
    @Query("SELECT * FROM cached_messages WHERE conversation = :conversation ORDER BY messageID DESC LIMIT :limit")
    List<CachedMessage> latest(String conversation, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(List<CachedMessage> messages);

    @Query("DELETE FROM cached_messages WHERE conversation = :conversation AND messageID NOT IN "
            + "(SELECT messageID FROM cached_messages WHERE conversation = :conversation ORDER BY messageID DESC LIMIT :keep)")
    void trim(String conversation, int keep);

    @Query("SELECT * FROM cached_blobs WHERE name = :name")
    CachedBlob blob(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putBlob(CachedBlob blob);

    @Query("DELETE FROM cached_messages")
    void clearMessages();

    @Query("DELETE FROM cached_blobs")
    void clearBlobs();
}
