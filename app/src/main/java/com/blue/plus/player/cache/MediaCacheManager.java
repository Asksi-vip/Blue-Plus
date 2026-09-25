package com.blue.plus.player.cache;

import android.content.Context;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi.class)
public class MediaCacheManager {
    private static SimpleCache instance;
    private static final long CACHE_SIZE = 100 * 1024 * 1024; // 100 MB Cache

    public static synchronized SimpleCache getInstance(Context context) {
        if (instance == null) {
            File cacheDir = new File(context.getCacheDir(), "media_cache");
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(CACHE_SIZE);
            StandaloneDatabaseProvider provider = new StandaloneDatabaseProvider(context);
            instance = new SimpleCache(cacheDir, evictor, provider);
        }
        return instance;
    }
}
