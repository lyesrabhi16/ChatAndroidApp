package com.example.chatapp;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CachedMessage.class, CachedBlob.class}, version = 1)
public abstract class CacheDb extends RoomDatabase {
    public abstract CacheDao dao();

    private static CacheDb instance;

    static synchronized CacheDb get(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(), CacheDb.class, "cache.db")
                    .fallbackToDestructiveMigration()   // it is only a cache: the server has the truth
                    .build();
        }
        return instance;
    }
}
