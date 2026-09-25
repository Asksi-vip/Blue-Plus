package com.blue.plus.player.core;

import android.view.View;

public interface PlayerEngine {
    void initialize(View playerView);
    void play(String url, boolean isLive);
    void pause();
    void resume();
    void stop();
    void release();
    void setVolume(float volume);
    boolean isPlaying();
    void setPlayerListener(PlayerListener listener);

    interface PlayerListener {
        void onBuffering();
        void onReady();
        void onError(String message);
        void onReconnect();
    }
}
