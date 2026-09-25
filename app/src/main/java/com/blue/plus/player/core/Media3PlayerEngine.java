package com.blue.plus.player.core;

import android.content.Context;
import android.view.View;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.blue.plus.player.cache.MediaCacheManager;
import com.blue.plus.player.watchdog.LiveWatchdog;

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi.class)
public class Media3PlayerEngine implements PlayerEngine {
    private ExoPlayer exoPlayer;
    private final Context context;
    private PlayerListener listener;
    private LiveWatchdog watchdog;
    private String currentUrl;
    private boolean isLiveStream;

    public Media3PlayerEngine(Context context) {
        this.context = context;
        this.watchdog = new LiveWatchdog();
        this.watchdog.setListener(() -> {
            if (listener != null) listener.onReconnect();
            reconnect();
        });
    }

    @Override
    public void initialize(View playerView) {
        if (!(playerView instanceof PlayerView)) {
            throw new IllegalArgumentException("View must be androidx.media3.ui.PlayerView");
        }

        DataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        CacheDataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(MediaCacheManager.getInstance(context))
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        exoPlayer = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheDataSourceFactory))
                .build();

        ((PlayerView) playerView).setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    watchdog.stopWatching();
                    if (listener != null) listener.onReady();
                } else if (playbackState == Player.STATE_BUFFERING) {
                    if (isLiveStream) watchdog.startWatching();
                    if (listener != null) listener.onBuffering();
                } else if (playbackState == Player.STATE_ENDED) {
                    watchdog.stopWatching();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                watchdog.stopWatching();
                if (isLiveStream) {
                    if (listener != null) listener.onReconnect();
                    reconnect();
                } else {
                    if (listener != null) listener.onError(error.getMessage());
                }
            }
        });
    }

    @Override
    public void play(String url, boolean isLive) {
        this.currentUrl = url;
        this.isLiveStream = isLive;
        if (exoPlayer == null) return;
        MediaItem mediaItem = MediaItem.fromUri(url);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    private void reconnect() {
        if (currentUrl != null && exoPlayer != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentUrl));
            exoPlayer.prepare();
            exoPlayer.play();
        }
    }

    @Override
    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    public void resume() {
        if (exoPlayer != null) exoPlayer.play();
    }

    @Override
    public void stop() {
        if (exoPlayer != null) exoPlayer.stop();
        watchdog.stopWatching();
    }

    @Override
    public void release() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        watchdog.stopWatching();
    }

    @Override
    public void setVolume(float volume) {
        if (exoPlayer != null) exoPlayer.setVolume(volume);
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    @Override
    public void setPlayerListener(PlayerListener listener) {
        this.listener = listener;
    }
}
