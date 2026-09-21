package com.blue.plus;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class LiveActivity extends Activity {

    public static List<ChannelItem> cachedChannels = null;
    public static Map<String, Integer> cachedCategoryCounts = null;
    public static Map<String, String> cachedCategoryIdToName = null;

    private RecyclerView rvCategories, rvChannels;
    private CatAdapter catAdapter;
    private ChanAdapter chanAdapter;
    private StyledPlayerView playerView;
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private int retryCount = 0;
    private String attemptedMimeType = null;
    private static final int MAX_RETRIES = 5;
    private static com.google.android.exoplayer2.upstream.cache.SimpleCache simpleCache;
    private static synchronized com.google.android.exoplayer2.upstream.cache.SimpleCache getSimpleCache(android.content.Context context) {
        if (simpleCache == null) {
            java.io.File baseDir = context.getExternalCacheDir();
            if (baseDir == null) {
                baseDir = context.getCacheDir();
            }
            java.io.File cacheDir = new java.io.File(baseDir, "live_cache");
            com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor evictor = 
                new com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024); // 150MB live cache size
            com.google.android.exoplayer2.database.StandaloneDatabaseProvider databaseProvider = 
                new com.google.android.exoplayer2.database.StandaloneDatabaseProvider(context);
            simpleCache = new com.google.android.exoplayer2.upstream.cache.SimpleCache(cacheDir, evictor, databaseProvider);
        }
        return simpleCache;
    }
    private final Handler reconnectHandler = new Handler();
    private boolean isReconnecting = false;
    private TextView tvChannelTitle;

    private final Handler watchdogHandler = new Handler();
    private int lastRenderedFrames = -1;
    private int videoFrozenSeconds = 0;
    private int bufferingDurationSeconds = 0;
    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && !isReconnecting) {
                int state = exoPlayer.getPlaybackState();
                boolean playWhenReady = exoPlayer.getPlayWhenReady();
                
                if (state == Player.STATE_BUFFERING) {
                    bufferingDurationSeconds++;
                    videoFrozenSeconds = 0;
                    lastRenderedFrames = -1;
                    if (bufferingDurationSeconds >= 15) { // 15 seconds buffering watchdog (matching mytv-android)
                        bufferingDurationSeconds = 0;
                        android.util.Log.d("PlayerWatchdog", "Buffering watchdog fired, retrying...");
                        long currentPos = exoPlayer.getCurrentPosition();
                        retryPlayback(currentPos);
                    }
                } else if (state == Player.STATE_READY && playWhenReady) {
                    bufferingDurationSeconds = 0;
                    com.google.android.exoplayer2.decoder.DecoderCounters counters = exoPlayer.getVideoDecoderCounters();
                    if (counters != null) {
                        int currentRenderedFrames = counters.renderedOutputBufferCount;
                        if (lastRenderedFrames == currentRenderedFrames) {
                            videoFrozenSeconds++;
                            if (videoFrozenSeconds >= 10) { // 10 seconds frozen watchdog
                                videoFrozenSeconds = 0;
                                lastRenderedFrames = -1;
                                android.util.Log.d("PlayerWatchdog", "Video freeze detected (stuck at " + currentRenderedFrames + " frames), retrying...");
                                long currentPos = exoPlayer.getCurrentPosition();
                                retryPlayback(currentPos);
                            }
                        } else {
                            lastRenderedFrames = currentRenderedFrames;
                            videoFrozenSeconds = 0;
                        }
                    }
                } else if (state == Player.STATE_ENDED) {
                    bufferingDurationSeconds = 0;
                    videoFrozenSeconds = 0;
                    lastRenderedFrames = -1;
                    android.util.Log.d("PlayerWatchdog", "State ended, retrying...");
                    retryPlayback(0);
                } else {
                    bufferingDurationSeconds = 0;
                    videoFrozenSeconds = 0;
                    lastRenderedFrames = -1;
                }
            }
            watchdogHandler.postDelayed(this, 1000);
        }
    };

    private final android.content.BroadcastReceiver networkReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if (isConnected) {
                if (exoPlayer != null && (exoPlayer.getPlaybackState() == Player.STATE_IDLE || exoPlayer.getPlayerError() != null)) {
                    retryCount = 0;
                    long currentPos = exoPlayer.getCurrentPosition();
                    retryPlayback(currentPos);
                }
            }
        }
    };

    private FrameLayout activityRoot;
    private FrameLayout fullscreenOverlay;
    private FrameLayout playerWrapper;
    private LinearLayout playerOriginalParent;
    private boolean isPlayerFullscreen = false;
    private boolean showedControlsOnDown = false;
    private View clickOverlay;

    private List<CategoryItem> categories       = new ArrayList<CategoryItem>();
    private List<ChannelItem>  allChannels      = new ArrayList<ChannelItem>();
    private List<ChannelItem>  filteredChannels = new ArrayList<ChannelItem>();
    private List<ChannelItem>  favoriteChannels = new ArrayList<ChannelItem>();
    private List<ChannelItem>  recentChannels   = new ArrayList<ChannelItem>();
    private List<ChannelItem>  displayedChannels = new ArrayList<ChannelItem>();

    private int channelsPerPage = 60;
    private void loadNextPage() {
        if (displayedChannels.size() >= filteredChannels.size()) return;
        int start = displayedChannels.size();
        int end = Math.min(start + channelsPerPage, filteredChannels.size());
        for (int i = start; i < end; i++) {
            displayedChannels.add(filteredChannels.get(i));
        }
        if (rvChannels != null && rvChannels.getAdapter() != null) {
            rvChannels.getAdapter().notifyItemRangeInserted(start, end - start);
        }
    }

    private String selectedCategory = "ALL";
    private ChannelItem currentChannel;
    private Map<String, String> categoryIdToName = new HashMap<>();

    // Custom media player variables inside LiveActivity
    private boolean isLocked = false;
    private int currentAspectIndex = 0; // 0 = FIT, 1 = FILL, 2 = ZOOM
    private android.media.AudioManager audioManager;
    private float initialY;
    private int maxVolume;
    private int initialVolume;
    private float initialBrightness;
    private FrameLayout customOverlay;
    private LinearLayout fsTopBar;
    private LinearLayout fsBottomBar;
    private FrameLayout fsLeftSliderContainer;
    private FrameLayout fsRightSliderContainer;
    private android.widget.SeekBar fsLeftSlider;
    private android.widget.SeekBar fsRightSlider;
    private ImageView btnFsLock;
    private ImageView btnFsFav;
    private ImageView fsCenterPlay;
    private LinearLayout fsCenterControls;
    private TextView btnFavoriteCtrl;
    private TextView fsTvLive;
    private TextView tvServerStatus;
    private View serverStatusDot;
    private ImageView localSelectorTemp;
    private LinearLayout mainLayout;

    private static final String CAT_ALL     = "ALL";
    private static final String CAT_FAV     = "المفضلات";
    private static final String CAT_RECENT  = "شوهدت مؤخراً";

    private static final String BLUE_ACTIVE = "#2196F3";
    private static final String BLUE_TRANS  = "#442196F3";
    private static final String BG_DARK    = "#1A1A1A";
    private static final String BG_DARKER  = "#111111";
    private static final String BG_PANEL   = "#212121";
    private static final String[] TABS     = {"رجوع", "بث مباشر", "افلام", "مسلسلات"};
    private List<TextView> tabViews = new ArrayList<TextView>();
    private EditText searchView;
    private String currentLang;

    private static final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(250);

    // ─── Lifecycle ───────────────────────────────────────────────────────────────
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = TvUtil.getAppLanguage(this);
        TvUtil.enableTls12(this); // Fix SSL for older devices
        try {
            java.net.CookieManager cookieManager = new java.net.CookieManager();
            cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            java.net.CookieHandler.setDefault(cookieManager);
        } catch (Exception e) {}
        if (TvUtil.isTvMode(this)) {
            try {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } catch (Exception e) {}
        }
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        selectedCategory = CAT_ALL; // Always start with ALL category for better TV experience
        buildUI(); // Call immediately to draw the UI layout and prevent white flashes

        loadData(); // Load local split live channels file instantly on the main thread
        refreshChannels();
        autoPlayLastChannel();
        focusSelectedChannel();
        watchdogHandler.post(watchdogRunnable);
    }

    private void focusSelectedCategory() {
        if (rvCategories == null || categories.isEmpty()) return;
        
        int selectedIndex = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name.equals(selectedCategory)) {
                selectedIndex = i;
                break;
            }
        }
        
        final int targetIdx = selectedIndex;
        rvCategories.post(new Runnable() {
            @Override
            public void run() {
                if (rvCategories != null && rvCategories.getLayoutManager() != null) {
                    rvCategories.getLayoutManager().scrollToPosition(targetIdx);
                    rvCategories.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvCategories.findViewHolderForAdapterPosition(targetIdx);
                            if (holder != null && holder.itemView instanceof ViewGroup) {
                                View row = ((ViewGroup) holder.itemView).getChildAt(0);
                                if (row != null) {
                                    row.requestFocus();
                                }
                            } else {
                                // Fallback
                                if (rvCategories.getChildCount() > targetIdx) {
                                    View child = rvCategories.getChildAt(targetIdx);
                                    if (child instanceof ViewGroup) {
                                        View row = ((ViewGroup) child).getChildAt(0);
                                        if (row != null) row.requestFocus();
                                    }
                                } else if (rvCategories.getChildCount() > 0) {
                                    View child = rvCategories.getChildAt(0);
                                    if (child instanceof ViewGroup) {
                                        View row = ((ViewGroup) child).getChildAt(0);
                                        if (row != null) row.requestFocus();
                                    }
                                }
                            }
                        }
                    }, 150);
                }
            }
        });
    }

    private void focusSelectedChannel() {
        if (rvChannels == null || displayedChannels.isEmpty()) return;
        
        int selectedIndex = 0;
        if (currentChannel != null) {
            for (int i = 0; i < displayedChannels.size(); i++) {
                if (displayedChannels.get(i).name.equals(currentChannel.name)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        
        final int targetIdx = selectedIndex;
        rvChannels.post(new Runnable() {
            @Override
            public void run() {
                if (rvChannels != null && rvChannels.getLayoutManager() != null) {
                    rvChannels.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(targetIdx);
                            if (holder != null && holder.itemView instanceof ViewGroup) {
                                View row = ((ViewGroup) holder.itemView).getChildAt(0);
                                if (row != null) {
                                    row.requestFocus();
                                }
                            } else {
                                // Fallback
                                if (rvChannels.getChildCount() > targetIdx) {
                                    View child = rvChannels.getChildAt(targetIdx);
                                    if (child instanceof ViewGroup) {
                                        View row = ((ViewGroup) child).getChildAt(0);
                                        if (row != null) row.requestFocus();
                                    }
                                }
                            }
                        }
                    }, 150);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.setPlayWhenReady(false);
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception e) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLang != null && !currentLang.equals(TvUtil.getAppLanguage(this))) {
            recreate();
            return;
        }
        TvUtil.hideSystemUI(this);
        if (exoPlayer != null && currentChannel != null) exoPlayer.setPlayWhenReady(true);
        try {
            registerReceiver(networkReceiver, new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception e) {}

        focusLastSelectedCategory();
    }

    private void focusLastSelectedCategory() {
        if (TvUtil.isTvMode(this) && rvCategories != null && !categories.isEmpty()) {
            rvCategories.post(new Runnable() {
                @Override
                public void run() {
                    int targetPos = 0;
                    for (int i = 0; i < categories.size(); i++) {
                        if (selectedCategory != null && selectedCategory.equals(categories.get(i).name)) {
                            targetPos = i;
                            break;
                        }
                    }
                    final int pos = targetPos;
                    rvCategories.scrollToPosition(pos);
                    rvCategories.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvCategories.findViewHolderForAdapterPosition(pos);
                            if (holder != null && holder.itemView != null) {
                                View row = null;
                                if (holder.itemView instanceof ViewGroup) {
                                    ViewGroup container = (ViewGroup) holder.itemView;
                                    if (container.getChildCount() > 0) {
                                        row = container.getChildAt(0);
                                    }
                                }
                                if (row != null) {
                                    row.requestFocus();
                                } else {
                                    holder.itemView.requestFocus();
                                }
                            }
                        }
                    }, 150);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        watchdogHandler.removeCallbacks(watchdogRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (isPlayerFullscreen) {
            if (isLocked) {
                if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        float dp = getResources().getDisplayMetrics().density;
                        exitFullscreenPlayer(dp);
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            }

            int keyCode = event.getKeyCode();
            int action = event.getAction();

            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (fsTopBar != null && fsTopBar.getVisibility() == View.VISIBLE) {
                        fsTopBar.setVisibility(View.GONE);
                        fsBottomBar.setVisibility(View.GONE);
                        fsCenterPlay.setVisibility(View.GONE);
                        if (fsCenterControls != null) fsCenterControls.setVisibility(View.GONE);
                        fsLeftSliderContainer.setVisibility(View.GONE);
                        fsRightSliderContainer.setVisibility(View.GONE);
                        return true;
                    } else {
                        float dp = getResources().getDisplayMetrics().density;
                        exitFullscreenPlayer(dp);
                        return true;
                    }
                }
                return true;
            }

            // DPAD CENTER / ENTER / PLAY_PAUSE / SPACE: Toggle Play/Pause in fullscreen
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                
                boolean isFsVisible = (fsTopBar != null && fsTopBar.getVisibility() == View.VISIBLE);
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (!isFsVisible) {
                        showedControlsOnDown = true;
                        if (fsTopBar != null) {
                            fsTopBar.setVisibility(View.VISIBLE);
                            fsBottomBar.setVisibility(View.VISIBLE);
                            fsCenterPlay.setVisibility(View.VISIBLE);
                            if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                            if (!TvUtil.isTvMode(LiveActivity.this)) {
                                fsLeftSliderContainer.setVisibility(View.VISIBLE);
                                fsRightSliderContainer.setVisibility(View.VISIBLE);
                            }
                            if (fsCenterPlay != null) fsCenterPlay.requestFocus();
                        }
                        return true;
                    } else {
                        showedControlsOnDown = false;
                    }
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    if (showedControlsOnDown) {
                        showedControlsOnDown = false;
                        return true;
                    }
                }
                
                if (isFsVisible) {
                    View focusedView = getCurrentFocus();
                    if (focusedView != null) {
                        if (action == android.view.KeyEvent.ACTION_UP) {
                            focusedView.performClick();
                        }
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
                return true;
            }

            if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || 
                keyCode == android.view.KeyEvent.KEYCODE_SPACE) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (exoPlayer != null) {
                        boolean playing = exoPlayer.getPlayWhenReady();
                        exoPlayer.setPlayWhenReady(!playing);
                        if (fsTopBar != null) {
                            fsTopBar.setVisibility(View.VISIBLE);
                            fsBottomBar.setVisibility(View.VISIBLE);
                            fsCenterPlay.setVisibility(View.VISIBLE);
                            if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                            if (!TvUtil.isTvMode(LiveActivity.this)) {
                                fsLeftSliderContainer.setVisibility(View.VISIBLE);
                                fsRightSliderContainer.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
                return true;
            }

            // Volume Keys handling in fullscreen (only volume buttons, NOT DPAD UP/DOWN which are for navigation)
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (audioManager != null) {
                        int currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                        int nextVol = currentVol;
                        
                        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                            nextVol = Math.min(maxVolume, currentVol + 1);
                        } else {
                            nextVol = Math.max(0, currentVol - 1);
                        }
                        
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, nextVol, android.media.AudioManager.FLAG_SHOW_UI);
                        if (fsRightSlider != null) fsRightSlider.setProgress(nextVol);
                        
                        // Show controls when volume is adjusted
                        if (fsTopBar != null && fsTopBar.getVisibility() != View.VISIBLE) {
                            fsTopBar.setVisibility(View.VISIBLE);
                            fsBottomBar.setVisibility(View.VISIBLE);
                            fsCenterPlay.setVisibility(View.VISIBLE);
                            if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                            if (!TvUtil.isTvMode(LiveActivity.this)) {
                                fsLeftSliderContainer.setVisibility(View.VISIBLE);
                                fsRightSliderContainer.setVisibility(View.VISIBLE);
                            }
                            if (TvUtil.isTvMode(LiveActivity.this) && fsCenterPlay != null) {
                                fsCenterPlay.requestFocus();
                            }
                        }
                    }
                }
                return true;
            }

            // DPAD LEFT / RIGHT: Switch channels in fullscreen (only when controls are hidden)
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || 
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                
                if (fsTopBar != null && fsTopBar.getVisibility() != View.VISIBLE) {
                    if (action == android.view.KeyEvent.ACTION_DOWN) {
                        playNextChannel(keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                    }
                    return true;
                }
            }

            if (fsTopBar != null && fsTopBar.getVisibility() != View.VISIBLE) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    fsTopBar.setVisibility(View.VISIBLE);
                    fsBottomBar.setVisibility(View.VISIBLE);
                    fsCenterPlay.setVisibility(View.VISIBLE);
                    if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                    if (!TvUtil.isTvMode(LiveActivity.this)) {
                        fsLeftSliderContainer.setVisibility(View.VISIBLE);
                        fsRightSliderContainer.setVisibility(View.VISIBLE);
                    }
                    if (TvUtil.isTvMode(LiveActivity.this) && fsCenterPlay != null) {
                        fsCenterPlay.requestFocus();
                    }
                    return true;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isPlayerFullscreen) {
            float dp = getResources().getDisplayMetrics().density;
            exitFullscreenPlayer(dp);
        } else {
            finish();
        }
    }

    // ─── Data ────────────────────────────────────────────────────────────────────
    private void logToFile(String msg) {
        try {
            File logFile = new File("/storage/emulated/0/Download/player_log.txt");
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            fw.write(sdf.format(new java.util.Date()) + " : " + msg + "\n");
            fw.close();
        } catch (Exception e) {
            Log.e("LiveActivity", "Failed to write log: " + e.getMessage());
        }
    }

    private ProgressBar loadingBar;

    private static final int INSTANT_PREVIEW_COUNT = 80; // Show first 80 channels instantly

    private void loadData() {
        if (cachedChannels != null) {
            allChannels = cachedChannels;
            categoryIdToName = cachedCategoryIdToName;
            setupInitialData();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(getExternalFilesDir(null), "xtream_live.json");
                    if (!f.exists()) {
                        runOnUiThread(new Runnable() { @Override public void run() { addDemoData(); } });
                        return;
                    }

                    android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                    String activeDns = sp.getString("active_dns", "");
                    String activeUser = sp.getString("active_username", "");
                    String activePass = sp.getString("active_password", "");

                    if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                        String json = sp.getString("list", "[]");
                        java.util.ArrayList<java.util.HashMap<String,Object>> list =
                            new com.google.gson.Gson().fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String,Object>>>(){}.getType());
                        if (list != null && !list.isEmpty()) {
                            java.util.Map<String,Object> last = list.get(list.size()-1);
                            activeDns = last.get("dns")      != null ? (String)last.get("dns")      : "";
                            activeUser = last.get("username") != null ? (String)last.get("username") : "";
                            activePass = last.get("password") != null ? (String)last.get("password") : "";
                        }
                    }

                    String dns = activeDns, user = activeUser, pass = activePass;

                    final String finalDns = dns, finalUser = user, finalPass = pass;
                    final Map<String, Integer> tempCategoryCounts = new java.util.LinkedHashMap<>();
                    final Map<String, String> tempCategoryIdToName = new java.util.LinkedHashMap<>();
                    final List<ChannelItem> tempAllChannels = new ArrayList<>();
                    final boolean[] firstBatchPosted = {false};

                    String streamFormat = getSharedPreferences("Settings", MODE_PRIVATE).getString("stream_format", "ts");
                    if ("auto".equals(streamFormat)) {
                        streamFormat = "ts";
                    }

                    try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String name = reader.nextName();
                            if (name.equals("data")) {
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    String key = reader.nextName();
                                    if (key.equals("get_live_categories")) {
                                        try {
                                            parseCategoriesToMap(reader, tempCategoryIdToName);
                                            for (String catName : tempCategoryIdToName.values()) {
                                                if (catName != null) {
                                                    tempCategoryCounts.put(catName, 0);
                                                }
                                            }
                                        } catch (Exception catEx) {
                                            android.util.Log.e("LiveActivity", "Error parsing categories: " + catEx.getMessage());
                                            try { reader.skipValue(); } catch (Exception ignored) {}
                                        }
                                    } else if (key.equals("get_live_streams")) {
                                        try {
                                            reader.beginArray();
                                            while (reader.hasNext()) {
                                                try {
                                                    if (reader.peek() == JsonToken.NULL) {
                                                        reader.nextNull();
                                                        continue;
                                                    }
                                                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                                                        reader.skipValue();
                                                        continue;
                                                    }
                                                    reader.beginObject();
                                                    int num = 0;
                                                    String cname = "", logo = "", category = "", streamId = "", catId = "";
                                                    while (reader.hasNext()) {
                                                        String k = reader.nextName();
                                                        if (k.equals("num")) num = getNextInt(reader);
                                                        else if (k.equals("name")) cname = getNextString(reader);
                                                        else if (k.equals("stream_icon")) logo = getNextString(reader);
                                                        else if (k.equals("category_name")) category = getNextString(reader);
                                                        else if (k.equals("category_id")) catId = getNextString(reader);
                                                        else if (k.equals("stream_id")) streamId = getNextString(reader);
                                                        else reader.skipValue();
                                                    }
                                                    reader.endObject();

                                                    if ((category == null || category.isEmpty()) && !catId.isEmpty()) {
                                                        category = tempCategoryIdToName.get(catId);
                                                    }
                                                    if (category == null || category.isEmpty()) category = "General";

                                                    Integer cc = tempCategoryCounts.get(category);
                                                    tempCategoryCounts.put(category, (cc == null ? 0 : cc) + 1);

                                                    String streamUrl = finalDns + "/live/" + finalUser + "/" + finalPass + "/" + streamId + "." + streamFormat;
                                                    tempAllChannels.add(new ChannelItem(num, cname, logo, category, streamUrl));

                                                    // === POST FIRST BATCH INSTANTLY ===
                                                    if (!firstBatchPosted[0] && tempAllChannels.size() >= INSTANT_PREVIEW_COUNT) {
                                                        firstBatchPosted[0] = true;
                                                        final List<ChannelItem> firstBatch = new ArrayList<>(tempAllChannels);
                                                        final Map<String, String> firstCatMap = new java.util.HashMap<>(tempCategoryIdToName);
                                                        final Map<String, Integer> firstCounts = new java.util.LinkedHashMap<>(tempCategoryCounts);
                                                        runOnUiThread(new Runnable() {
                                                            @Override public void run() {
                                                                allChannels = firstBatch;
                                                                categoryIdToName = firstCatMap;
                                                                cachedCategoryIdToName = firstCatMap;
                                                                cachedCategoryCounts = firstCounts;
                                                                setupInitialData();
                                                            }
                                                        });
                                                    }
                                                } catch (Exception itemEx) {
                                                    android.util.Log.e("LiveActivity", "Error parsing stream item: " + itemEx.getMessage());
                                                    try { reader.skipValue(); } catch (Exception ignored) {}
                                                }
                                            }
                                            reader.endArray();
                                        } catch (Exception streamEx) {
                                            android.util.Log.e("LiveActivity", "Error parsing stream array: " + streamEx.getMessage());
                                            try { reader.skipValue(); } catch (Exception ignored) {}
                                        }
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } catch (Exception parseEx) {
                        android.util.Log.e("LiveActivity", "JSON Parsing warning/interruption: " + parseEx.getMessage());
                        logToFile("JSON Parsing warning/interruption: " + android.util.Log.getStackTraceString(parseEx));
                    }

                    // === POST FULL DATA (seamless update) ===
                    // Runs even if parsing was interrupted by a local error
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            allChannels = tempAllChannels;
                            categoryIdToName = tempCategoryIdToName;
                            cachedChannels = tempAllChannels;
                            cachedCategoryCounts = tempCategoryCounts;
                            cachedCategoryIdToName = tempCategoryIdToName;

                            // Update favorites and recents from the full list
                            favoriteChannels.clear();
                            android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
                            for (ChannelItem ch : allChannels) {
                                if (favSp.getBoolean("fav_" + ch.name, false)) {
                                    favoriteChannels.add(ch);
                                }
                            }
                            loadRecentChannels();

                            // Update category list silently with the full counts
                            categories.clear();
                            categories.add(new CategoryItem(CAT_ALL, allChannels.size()));
                            categories.add(new CategoryItem(CAT_FAV, favoriteChannels.size()));
                            categories.add(new CategoryItem(CAT_RECENT, recentChannels.size()));
                            android.content.SharedPreferences settingsSp = getSharedPreferences("Settings", MODE_PRIVATE);
                            for (Map.Entry<String, Integer> entry : cachedCategoryCounts.entrySet()) {
                                String catName = entry.getKey();
                                if (entry.getValue() > 0) {
                                    if (!settingsSp.getBoolean("hide_live_cat_" + catName, false)) {
                                        categories.add(new CategoryItem(catName, entry.getValue()));
                                    }
                                }
                            }
                            if (catAdapter != null) catAdapter.notifyDataSetChanged();

                            if (!firstBatchPosted[0]) {
                                setupInitialData();
                            } else {
                                // Silent background update: refresh filtered list based on current selection
                                filteredChannels.clear();
                                if (selectedCategory.equals(CAT_ALL)) {
                                    filteredChannels.addAll(allChannels);
                                } else if (selectedCategory.equals(CAT_FAV)) {
                                    filteredChannels.addAll(favoriteChannels);
                                } else if (selectedCategory.equals(CAT_RECENT)) {
                                    filteredChannels.addAll(recentChannels);
                                } else {
                                    for (ChannelItem ch : allChannels) {
                                        if (ch.category.equals(selectedCategory)) {
                                            filteredChannels.add(ch);
                                        }
                                    }
                                }

                                // Update displayed channels fully
                                displayedChannels.clear();
                                displayedChannels.addAll(filteredChannels);
                                if (rvChannels != null && rvChannels.getAdapter() != null) {
                                    rvChannels.getAdapter().notifyDataSetChanged();
                                }
                            }
                        }
                    });

                } catch (Exception e) {
                    android.util.Log.e("LiveActivity", "Background load error: " + e.getMessage());
                    logToFile("Background load error: " + android.util.Log.getStackTraceString(e));
                }
            }
        }).start();
    }

    private void parseCategoriesToMap(JsonReader reader, Map<String, String> map) throws java.io.IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            String id = "", name = "";
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("category_id")) id = getNextString(reader);
                else if (key.equals("category_name")) name = getNextString(reader);
                else reader.skipValue();
            }
            reader.endObject();
            if (!id.isEmpty() && !name.isEmpty()) map.put(id, name);
        }
        reader.endArray();
    }

    private void parseLiveStreamsToList(JsonReader reader, Map<String, Integer> categoryCounts, Map<String, String> catIdMap, String dns, String user, String pass, List<ChannelItem> list) throws java.io.IOException {
        reader.beginArray();
        String streamFormat = getSharedPreferences("Settings", MODE_PRIVATE).getString("stream_format", "ts");
        if ("auto".equals(streamFormat)) {
            streamFormat = "ts";
        }
        while (reader.hasNext()) {
            reader.beginObject();
            int num = 0;
            String name = "", logo = "", category = "", streamId = "", catId = "";
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("num")) num = getNextInt(reader);
                else if (key.equals("name")) name = getNextString(reader);
                else if (key.equals("stream_icon")) logo = getNextString(reader);
                else if (key.equals("category_name")) category = getNextString(reader);
                else if (key.equals("category_id")) catId = getNextString(reader);
                else if (key.equals("stream_id")) streamId = getNextString(reader);
                else reader.skipValue();
            }
            reader.endObject();

            if ((category == null || category.isEmpty()) && !catId.isEmpty()) {
                category = catIdMap.get(catId);
            }
            if (category == null || category.isEmpty()) category = "General";
            
            Integer currentCount = categoryCounts.get(category);
            categoryCounts.put(category, (currentCount == null ? 0 : currentCount) + 1);

            String streamUrl = dns + "/live/" + user + "/" + pass + "/" + streamId + "." + streamFormat;
            list.add(new ChannelItem(num, name, logo, category, streamUrl));
        }
        reader.endArray();
    }

    private String getNextString(JsonReader reader) throws java.io.IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return "";
        }
        if (token == JsonToken.STRING) {
            return reader.nextString();
        }
        if (token == JsonToken.NUMBER) {
            double val = reader.nextDouble();
            if (val == (long) val) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        }
        if (token == JsonToken.BOOLEAN) {
            return String.valueOf(reader.nextBoolean());
        }
        reader.skipValue();
        return "";
    }

    private int getNextInt(JsonReader reader) throws java.io.IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NUMBER) {
            double val = reader.nextDouble();
            return (int) val;
        }
        if (token == JsonToken.STRING) {
            try {
                return (int) Double.parseDouble(reader.nextString());
            } catch (Exception e) {
                return 0;
            }
        }
        if (token == JsonToken.BOOLEAN) {
            return reader.nextBoolean() ? 1 : 0;
        }
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return 0;
        }
        reader.skipValue();
        return 0;
    }

    private void initSelectedCategory() {
        android.content.SharedPreferences sp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        String lastChanName = sp.getString("last_channel_name", "");
        String lastCatName = sp.getString("last_category_name", "");

        ChannelItem targetChannel = null;
        if (!lastChanName.isEmpty() && !lastCatName.isEmpty()) {
            for (ChannelItem ch : allChannels) {
                if (ch.name.equals(lastChanName)) {
                    targetChannel = ch;
                    selectedCategory = lastCatName;
                    break;
                }
            }
        }

        if (targetChannel == null && !allChannels.isEmpty()) {
            selectedCategory = CAT_ALL;
        }
    }

    private void setupInitialData() {
        // Load persistent favorites
        favoriteChannels.clear();
        android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        for (ChannelItem ch : allChannels) {
            if (favSp.getBoolean("fav_" + ch.name, false)) {
                favoriteChannels.add(ch);
            }
        }

        // Load persistent recents
        loadRecentChannels();

        // Initialize the selected category first!
        initSelectedCategory();

        categories.clear();
        categories.add(new CategoryItem(CAT_ALL, allChannels.size()));
        categories.add(new CategoryItem(CAT_FAV, favoriteChannels.size()));
        categories.add(new CategoryItem(CAT_RECENT, recentChannels.size()));
        
        android.content.SharedPreferences settingsSp = getSharedPreferences("Settings", MODE_PRIVATE);
        for (Map.Entry<String, Integer> entry : cachedCategoryCounts.entrySet()) {
            String catName = entry.getKey();
            if (entry.getValue() > 0) {
                if (!settingsSp.getBoolean("hide_live_cat_" + catName, false)) {
                    categories.add(new CategoryItem(catName, entry.getValue()));
                }
            }
        }
        
        if (catAdapter != null) catAdapter.notifyDataSetChanged();
        refreshChannels();
        autoPlayLastChannel();
        focusSelectedChannel();
    }

    private void addDemoData() {
        categories.add(new CategoryItem("ALL",                  3572));
        categories.add(new CategoryItem("المفضلات",             0));
        categories.add(new CategoryItem("Lock",                 0));
        categories.add(new CategoryItem("كأس العالم FIFA 2026", 6));
        categories.add(new CategoryItem("BSR | BeinSport 8K",  4));
        categories.add(new CategoryItem("CeMe | BEIN 4K a",    11));
        categories.add(new CategoryItem("CeMe | BEIN 4K b",    11));
        categories.add(new CategoryItem("CeMe | BEIN FHD",     11));

        allChannels.add(new ChannelItem(1,   "BEIN MAX 1 4K",    "", "BSR | BeinSport 8K",     ""));
        allChannels.add(new ChannelItem(7,   "BSR SPORTS 1 8K",  "", "BSR | BeinSport 8K",     ""));
        allChannels.add(new ChannelItem(10,  "Real Madrid TV",   "", "كأس العالم FIFA 2026",    ""));
        allChannels.add(new ChannelItem(12,  "CeMe BEIN 2 4K a", "", "CeMe | BEIN 4K a",       ""));
        allChannels.add(new ChannelItem(19,  "CeMe BEIN 9 4K a", "", "CeMe | BEIN 4K a",       ""));
        allChannels.add(new ChannelItem(33,  "CeMe BEIN 1 FHD",  "", "CeMe | BEIN FHD",        ""));
        allChannels.add(new ChannelItem(22,  "CeMe BEIN 1 4K b", "", "CeMe | BEIN 4K b",       ""));
        allChannels.add(new ChannelItem(2,   "BEIN MAX 2 4K",    "", "BSR | BeinSport 8K",     ""));
        allChannels.add(new ChannelItem(395, "MBC 1 HD",         "", "ALL",                     ""));
    }

    private void loadCachedBackground(View v) {
        try {
            File f = new File(getFilesDir(), "splash_bg.jpg");
            if (f.exists()) {
                if (f.length() < 100) {
                    f.delete();
                    return;
                }
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                    drawable.setGravity(android.view.Gravity.FILL);
                    v.setBackground(drawable);
                } else {
                    f.delete();
                }
            }
        } catch (Exception e) {}
    }

    private void buildUI() {
        float dp = getResources().getDisplayMetrics().density;

        activityRoot = new FrameLayout(this);
        activityRoot.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        activityRoot.setBackgroundResource(R.drawable.bg_sports);
        activityRoot.setFocusable(true);
        activityRoot.setFocusableInTouchMode(true);
        activityRoot.requestFocus();
        loadCachedBackground(activityRoot);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#55000000")); // semi-transparent overlay
        mainLayout.addView(buildHeader(dp));

        loadingBar = new ProgressBar(this);
        loadingBar.setIndeterminate(true);
        loadingBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(px(dp, 40), px(dp, 40));
        lbLp.gravity = Gravity.CENTER;
        lbLp.topMargin = px(dp, 20);
        mainLayout.addView(loadingBar, lbLp);

        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0);
        bodyLp.weight = 1;
        mainLayout.addView(buildBody(dp), bodyLp);

        activityRoot.addView(mainLayout, new FrameLayout.LayoutParams(-1, -1));

        fullscreenOverlay = new FrameLayout(this);
        fullscreenOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        fullscreenOverlay.setBackgroundColor(Color.BLACK);
        fullscreenOverlay.setVisibility(View.GONE);

        activityRoot.addView(fullscreenOverlay);

        setContentView(activityRoot);
    }

    private View buildHeader(float dp) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#080808"));
        header.setPadding(px(dp, 16), px(dp, 10), px(dp, 16), px(dp, 10));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(TvUtil.translate(this, TABS[i]));
            tv.setTextSize(14);
            tv.setPadding(px(dp, 15), 0, px(dp, 15), 0);
            styleTab(tv, i == 1);
            TvUtil.applyTvFocusHighlight(tv);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (idx == 0) {
                        Intent intent = new Intent(LiveActivity.this, Ot2Activity.class);
                        startActivity(intent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    } else if (idx == 1) {
                        // Already in LiveActivity
                    } else if (idx == 2) {
                        Intent intent = new Intent(LiveActivity.this, SeriesActivity.class);
                        intent.putExtra("type", "movies");
                        startActivity(intent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    } else if (idx == 3) {
                        Intent intent = new Intent(LiveActivity.this, SeriesActivity.class);
                        intent.putExtra("type", "series");
                        startActivity(intent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }
            });
            tabViews.add(tv);
            tabs.addView(tv);

            if (i < TABS.length - 1) {
                View sep = new View(this);
                sep.setBackgroundColor(Color.parseColor("#333333"));
                tabs.addView(sep, new LinearLayout.LayoutParams(2, px(dp, 20)));
            }
        }
        header.addView(tabs);

        // Spacer to push logo to the right
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        // Premium Search Bar (Harmonized with left panel buttons)
        EditText search = new EditText(this);
        searchView = search;
        search.setHint(TvUtil.translate(this, "بحث عن قناة..."));
        search.setHintTextColor(Color.parseColor("#66A0C0F0")); // Translucent icy-blue hint
        search.setTextColor(Color.WHITE);
        search.setTextSize(12);
        search.setSingleLine(true);
        search.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        searchBg.setColors(new int[]{Color.parseColor("#0F172A"), Color.parseColor("#1E293B")}); // Premium deep slate-blue gradient
        searchBg.setCornerRadius(px(dp, 8));
        searchBg.setStroke((int)(dp * 1.4f), Color.parseColor("#552196F3")); // Glowing neon-blue outline
        search.setBackground(searchBg);
        search.setPadding(px(dp, 12), 0, px(dp, 12), 0);
        search.setFocusable(true);
        search.setFocusableInTouchMode(true);
        TvUtil.applyTvFocusHighlight(search, 8.0f);
        
        android.graphics.drawable.Drawable searchIcon = getResources().getDrawable(R.drawable.ic_material_search);
        if (searchIcon != null) {
            searchIcon.setColorFilter(Color.parseColor(BLUE_ACTIVE), android.graphics.PorterDuff.Mode.SRC_IN); // Glowing active neon-blue search icon
            search.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
            search.setCompoundDrawablePadding(px(dp, 8));
        }

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, px(dp, 38));
        searchLp.setMargins(px(dp, 6), px(dp, 6), px(dp, 6), px(dp, 12));
        search.setLayoutParams(searchLp);
        
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        search.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH 
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    v.clearFocus();
                    if (TvUtil.isTvMode(LiveActivity.this)) {
                        if (rvCategories != null && rvCategories.getChildCount() > 0) {
                            rvCategories.requestFocus();
                        }
                    } else {
                        activityRoot.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        search.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (rvChannels != null && rvChannels.getChildCount() > 0) {
                                rvChannels.requestFocus();
                                View first = rvChannels.getChildAt(0);
                                if (first != null) first.requestFocus();
                                return true;
                            }
                        } else {
                            if (rvCategories != null && rvCategories.getChildCount() > 0) {
                                rvCategories.requestFocus();
                                View first = rvCategories.getChildAt(0);
                                if (first != null) first.requestFocus();
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        setupSearchListener(search);

        FrameLayout logoBox = new FrameLayout(this);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(Color.parseColor("#111111"));
        lb.setCornerRadius(px(dp, 4));
        lb.setStroke(1, Color.parseColor("#333333"));
        logoBox.setBackground(lb);
        logoBox.setPadding(px(dp, 4), px(dp, 4), px(dp, 4), px(dp, 4));

        ImageView logoIv = new ImageView(this);
        logoIv.setImageResource(R.drawable.home_logo);
        logoIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoBox.addView(logoIv, new FrameLayout.LayoutParams(px(dp, 20), px(dp, 20)));
        header.addView(logoBox);

        return header;
    }

    private void styleTab(TextView tv, boolean active) {
        tv.setTextColor(active ? Color.parseColor(BLUE_ACTIVE) : Color.WHITE);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private View buildBody(float dp) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBackgroundColor(Color.TRANSPARENT);
        body.setPadding(0, px(dp, 8), px(dp, 8), px(dp, 8));

        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(px(dp, 190), -1);
        catLp.rightMargin = px(dp, 2);
        body.addView(buildCategoriesPanel(dp), catLp);
        
        LinearLayout.LayoutParams chanLp = new LinearLayout.LayoutParams(px(dp, 250), -1);
        chanLp.rightMargin = px(dp, 6);
        body.addView(buildChannelsPanel(dp), chanLp);

        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(0, -1);
        playerLp.weight = 1;
        body.addView(buildPlayerPanel(dp), playerLp);
        return body;
    }

    private View buildCategoriesPanel(float dp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(BLUE_TRANS));
        gd.setStroke(2, Color.parseColor("#552196F3"));
        gd.setCornerRadius(px(dp, 10));
        panel.setBackground(gd);
        
        // Add category icon ImageView above search
        ImageView catIcon = new ImageView(this);
        catIcon.setImageResource(R.drawable.picsart_26_05_20_21_51_20_597);
        catIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-1, px(dp, 60));
        iconLp.setMargins(px(dp, 12), px(dp, 12), px(dp, 12), px(dp, 6));
        catIcon.setLayoutParams(iconLp);
        panel.addView(catIcon);

        if (searchView != null) {
            panel.addView(searchView);
        }
        
        rvCategories = new RecyclerView(this);
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        catAdapter = new CatAdapter();
        rvCategories.setAdapter(catAdapter);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(-1, -1);
        int m = px(dp, 2);
        rvLp.setMargins(m, m, m, m);
        panel.addView(rvCategories, rvLp);
        return panel;
    }

    private View buildChannelsPanel(float dp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(BLUE_TRANS));
        gd.setStroke(2, Color.parseColor("#552196F3"));
        gd.setCornerRadius(px(dp, 10));
        panel.setBackground(gd);
        rvChannels = new RecyclerView(this);
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        chanAdapter = new ChanAdapter();
        rvChannels.setAdapter(chanAdapter);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(-1, -1);
        int m = px(dp, 2);
        rvLp.setMargins(m, m, m, m);
        panel.addView(rvChannels, rvLp);
        return panel;
    }

    private void updateFsFavIcon() {
        if (btnFsFav == null) return;
        if (currentChannel == null) return;
        android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        boolean isFav = favSp.getBoolean("fav_" + currentChannel.name, false);
        if (isFav) {
            btnFsFav.setImageResource(R.drawable.ic_material_star);
            btnFsFav.setColorFilter(Color.parseColor("#FFD600"));
        } else {
            btnFsFav.setImageResource(R.drawable.ic_material_star_border);
            btnFsFav.setColorFilter(Color.WHITE);
        }
    }

    private void updateFavButtonText() {
        if (btnFavoriteCtrl == null) return;
        if (currentChannel == null) {
            btnFavoriteCtrl.setText(TvUtil.translate(this, "اضافة الى المفضلة"));
            return;
        }
        android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        boolean isFav = favSp.getBoolean("fav_" + currentChannel.name, false);
        if (isFav) {
            btnFavoriteCtrl.setText(TvUtil.translate(this, "إزالة من المفضلة"));
        } else {
            btnFavoriteCtrl.setText(TvUtil.translate(this, "اضافة الى المفضلة"));
        }
    }

    private void toggleLiveChannelFavorite() {
        if (currentChannel == null) return;
        android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        boolean isFav = favSp.getBoolean("fav_" + currentChannel.name, false);
        isFav = !isFav;
        favSp.edit().putBoolean("fav_" + currentChannel.name, isFav).apply();

        if (isFav) {
            if (!favoriteChannels.contains(currentChannel)) {
                favoriteChannels.add(currentChannel);
            }
            Toast.makeText(this, "تمت الإضافة للمفضلة", Toast.LENGTH_SHORT).show();
        } else {
            for (int i = favoriteChannels.size() - 1; i >= 0; i--) {
                if (favoriteChannels.get(i).name.equals(currentChannel.name)) {
                    favoriteChannels.remove(i);
                }
            }
            Toast.makeText(this, "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show();
        }

        for (CategoryItem cat : categories) {
            if (cat.name.equals(CAT_FAV)) {
                cat.count = favoriteChannels.size();
                break;
            }
        }
        if (rvCategories != null) rvCategories.getAdapter().notifyDataSetChanged();
        if (rvChannels != null) rvChannels.getAdapter().notifyDataSetChanged();

        updateFsFavIcon();
        updateFavButtonText();
    }

    private void updateFsLockIcon() {
        if (btnFsLock == null) return;
        if (isLocked) {
            btnFsLock.setImageResource(R.drawable.ic_material_lock);
            btnFsLock.setColorFilter(Color.RED);
        } else {
            btnFsLock.setImageResource(R.drawable.ic_material_lock_open);
            btnFsLock.setColorFilter(Color.WHITE);
        }
    }

    private void triggerFsAutohide() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlayerFullscreen && !isLocked && fsTopBar != null) {
                    fsTopBar.setVisibility(View.GONE);
                    fsBottomBar.setVisibility(View.GONE);
                    fsCenterPlay.setVisibility(View.GONE);
                    if (fsCenterControls != null) fsCenterControls.setVisibility(View.GONE);
                    fsLeftSliderContainer.setVisibility(View.GONE);
                    fsRightSliderContainer.setVisibility(View.GONE);
                }
            }
        }, 4000);
    }

    private void enterFullscreenPlayer(final float dp) {
        if (isPlayerFullscreen) return;
        isPlayerFullscreen = true;
        ImageView localQualityTemp = null;
        ImageView localAudioTemp = null;
        ImageView localSubTemp = null;

        // Force landscape orientation in fullscreen
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}

        // Detach player to prevent surface destruction issues when moving view hierarchy
        playerView.setPlayer(null);

        // Remove from original parent
        playerOriginalParent.removeView(playerWrapper);

        // Update layout parameters to fill screen
        playerWrapper.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }

        // Hide clickOverlay so user can interact with controls
        clickOverlay.setVisibility(View.GONE);

        // Disable standard controller
        playerView.setUseController(false);

        // Add to fullscreen overlay
        fullscreenOverlay.removeAllViews();
        fullscreenOverlay.addView(playerWrapper);
        
        // Re-attach player once in new hierarchy so it binds to the new surface
        playerView.setPlayer(exoPlayer);

        audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);

        // Initialize state
        isLocked = false;
        int savedAspect = getSharedPreferences("LivePrefs", MODE_PRIVATE).getInt("aspect_ratio", 1);
        applyAspectRatio(savedAspect, false);

        // ─── Build Custom Overlay ───
        customOverlay = new FrameLayout(this);
        customOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        customOverlay.setBackgroundColor(Color.TRANSPARENT);

        // Slider Containers - Premium Long SeekBars using FrameLayout layout rules
        fsLeftSliderContainer = new FrameLayout(this);
        fsLeftSliderContainer.setFocusable(false);
        fsLeftSliderContainer.setFocusableInTouchMode(false);
        fsLeftSliderContainer.setVisibility(TvUtil.isTvMode(this) ? View.GONE : View.VISIBLE);
        fsLeftSliderContainer.setBackgroundColor(Color.TRANSPARENT);
        fsLeftSliderContainer.setClipChildren(false);
        fsLeftSliderContainer.setClipToPadding(false);
        FrameLayout.LayoutParams leftSliderParams = new FrameLayout.LayoutParams(px(dp, 48), px(dp, 300));
        leftSliderParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        leftSliderParams.leftMargin = px(dp, 20);
        fsLeftSliderContainer.setLayoutParams(leftSliderParams);

        ImageView ivBright = new ImageView(this);
        ivBright.setImageResource(R.drawable.ic_material_brightness);
        ivBright.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams ivBrightLp = new FrameLayout.LayoutParams(px(dp, 24), px(dp, 24));
        ivBrightLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ivBrightLp.topMargin = px(dp, 10);
        fsLeftSliderContainer.addView(ivBright, ivBrightLp);

        fsLeftSlider = new android.widget.SeekBar(this);
        fsLeftSlider.setFocusable(false);
        fsLeftSlider.setFocusableInTouchMode(false);
        fsLeftSlider.setMax(100);
        
        // Initialize with actual brightness
        float currentBrightness = 0.5f;
        try {
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            if (wlp.screenBrightness >= 0) {
                currentBrightness = wlp.screenBrightness;
            } else {
                currentBrightness = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
            }
        } catch (Exception e) {}
        fsLeftSlider.setProgress((int)(currentBrightness * 100));
        
        fsLeftSlider.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams sliderLp = new FrameLayout.LayoutParams(px(dp, 220), px(dp, 48));
        sliderLp.gravity = Gravity.CENTER;
        fsLeftSlider.setLayoutParams(sliderLp);
        fsLeftSlider.setRotation(270f);
        
        // Add KeyListener for DPAD support on the slider itself
        fsLeftSlider.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        int prog = fsLeftSlider.getProgress();
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) prog = Math.min(100, prog + 5);
                        else prog = Math.max(0, prog - 5);
                        fsLeftSlider.setProgress(prog);
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = prog / 100f == 0 ? 0.01f : prog / 100f;
                        getWindow().setAttributes(lp);
                        return true;
                    }
                }
                return false;
            }
        });
        if (!TvUtil.isTvMode(LiveActivity.this)) {
            TvUtil.applyTvFocusHighlight(fsLeftSlider);
        }
        
        // Premium transparent and solid white track design
        android.graphics.drawable.Drawable progressDrawableLeft = fsLeftSlider.getProgressDrawable();
        if (progressDrawableLeft instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progressDrawableLeft;
            android.graphics.drawable.Drawable bg = ld.findDrawableByLayerId(android.R.id.background);
            if (bg != null) {
                bg.setColorFilter(Color.parseColor("#4DFFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            android.graphics.drawable.Drawable prog = ld.findDrawableByLayerId(android.R.id.progress);
            if (prog != null) {
                prog.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } else {
            progressDrawableLeft.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            android.graphics.drawable.Drawable thumb = fsLeftSlider.getThumb();
            if (thumb != null) {
                thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
        fsLeftSliderContainer.addView(fsLeftSlider);

        // Premium gesture handling using screen coordinates relative to container
        View.OnTouchListener brightTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        int[] loc = new int[2];
                        fsLeftSliderContainer.getLocationOnScreen(loc);
                        float containerTop = loc[1];
                        float containerHeight = fsLeftSliderContainer.getHeight();
                        
                        float startY = containerTop + px(dp, 40);
                        float endY = containerTop + containerHeight - px(dp, 40);
                        float trackLength = endY - startY;
                        
                        float relativeY = event.getRawY() - startY;
                        float percentage = 1.0f - (relativeY / trackLength);
                        if (percentage < 0.0f) percentage = 0.0f;
                        if (percentage > 1.0f) percentage = 1.0f;
                        
                        int progress = (int) (percentage * 100);
                        fsLeftSlider.setProgress(progress);
                        
                        float newBrightness = progress / 100f;
                        if (newBrightness < 0.01f) newBrightness = 0.01f;
                        WindowManager.LayoutParams wlp = getWindow().getAttributes();
                        wlp.screenBrightness = newBrightness;
                        getWindow().setAttributes(wlp);
                        break;
                }
                return true;
            }
        };
        fsLeftSliderContainer.setOnTouchListener(brightTouchListener);
        fsLeftSlider.setOnTouchListener(brightTouchListener);

        customOverlay.addView(fsLeftSliderContainer);

        fsRightSliderContainer = new FrameLayout(this);
        fsRightSliderContainer.setFocusable(false);
        fsRightSliderContainer.setFocusableInTouchMode(false);
        fsRightSliderContainer.setVisibility(TvUtil.isTvMode(this) ? View.GONE : View.VISIBLE);
        fsRightSliderContainer.setBackgroundColor(Color.TRANSPARENT);
        fsRightSliderContainer.setClipChildren(false);
        fsRightSliderContainer.setClipToPadding(false);
        FrameLayout.LayoutParams rightSliderParams = new FrameLayout.LayoutParams(px(dp, 48), px(dp, 300));
        rightSliderParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        rightSliderParams.rightMargin = px(dp, 20);
        fsRightSliderContainer.setLayoutParams(rightSliderParams);

        ImageView ivVol = new ImageView(this);
        ivVol.setImageResource(R.drawable.ic_material_volume);
        ivVol.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams ivVolLp = new FrameLayout.LayoutParams(px(dp, 24), px(dp, 24));
        ivVolLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ivVolLp.topMargin = px(dp, 10);
        fsRightSliderContainer.addView(ivVol, ivVolLp);

        fsRightSlider = new android.widget.SeekBar(this);
        fsRightSlider.setFocusable(false);
        fsRightSlider.setFocusableInTouchMode(false);
        fsRightSlider.setMax(maxVolume);
        fsRightSlider.setProgress(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        fsRightSlider.setPadding(0, 0, 0, 0);
        fsRightSlider.setLayoutParams(sliderLp);
        fsRightSlider.setRotation(270f);
        
        fsRightSlider.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        int cur = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                        int next = cur;
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) next = Math.min(maxVolume, cur + 1);
                        else next = Math.max(0, cur - 1);
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, android.media.AudioManager.FLAG_SHOW_UI);
                        fsRightSlider.setProgress(next);
                        return true;
                    }
                }
                return false;
            }
        });
        if (!TvUtil.isTvMode(LiveActivity.this)) {
            TvUtil.applyTvFocusHighlight(fsRightSlider);
        }
        
        // Premium transparent and solid white track design
        android.graphics.drawable.Drawable progressDrawableRight = fsRightSlider.getProgressDrawable();
        if (progressDrawableRight instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progressDrawableRight;
            android.graphics.drawable.Drawable bg = ld.findDrawableByLayerId(android.R.id.background);
            if (bg != null) {
                bg.setColorFilter(Color.parseColor("#4DFFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            android.graphics.drawable.Drawable prog = ld.findDrawableByLayerId(android.R.id.progress);
            if (prog != null) {
                prog.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } else {
            progressDrawableRight.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            android.graphics.drawable.Drawable thumb = fsRightSlider.getThumb();
            if (thumb != null) {
                thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
        fsRightSliderContainer.addView(fsRightSlider);

        // Premium gesture handling using screen coordinates relative to container
        View.OnTouchListener volTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        int[] loc = new int[2];
                        fsRightSliderContainer.getLocationOnScreen(loc);
                        float containerTop = loc[1];
                        float containerHeight = fsRightSliderContainer.getHeight();
                        
                        float startY = containerTop + px(dp, 40);
                        float endY = containerTop + containerHeight - px(dp, 40);
                        float trackLength = endY - startY;
                        
                        float relativeY = event.getRawY() - startY;
                        float percentage = 1.0f - (relativeY / trackLength);
                        if (percentage < 0.0f) percentage = 0.0f;
                        if (percentage > 1.0f) percentage = 1.0f;
                        
                        int newVolume = (int) (percentage * maxVolume);
                        if (newVolume < 0) newVolume = 0;
                        if (newVolume > maxVolume) newVolume = maxVolume;
                        
                        fsRightSlider.setProgress(newVolume);
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0);
                        break;
                }
                return true;
            }
        };
        fsRightSliderContainer.setOnTouchListener(volTouchListener);
        fsRightSlider.setOnTouchListener(volTouchListener);

        customOverlay.addView(fsRightSliderContainer);

        // Custom Top Bar Layout
        fsTopBar = new LinearLayout(this);
        fsTopBar.setOrientation(LinearLayout.HORIZONTAL);
        fsTopBar.setGravity(Gravity.CENTER_VERTICAL);
        fsTopBar.setBackgroundResource(getResources().getIdentifier("player_top_gradient", "drawable", getPackageName()));
        fsTopBar.setPadding(px(dp, 15), px(dp, 10), px(dp, 15), px(dp, 10));
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(-1, -2);
        topBarParams.gravity = Gravity.TOP;
        fsTopBar.setLayoutParams(topBarParams);

        // Back Button
        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(R.drawable.ic_back_arrow);
        btnBack.setColorFilter(Color.WHITE);
        btnBack.setFocusable(true);
        LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams(px(dp, 26), px(dp, 26));
        lpBack.rightMargin = px(dp, 10);
        btnBack.setLayoutParams(lpBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exitFullscreenPlayer(dp); }
        });
        TvUtil.applyTvFocusHighlight(btnBack, 15.0f);
        fsTopBar.addView(btnBack);

        // Channel Logo
        ImageView ivLogo = new ImageView(this);
        ivLogo.setLayoutParams(new LinearLayout.LayoutParams(px(dp, 30), px(dp, 30)));
        ivLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (currentChannel != null && currentChannel.logo != null && !currentChannel.logo.isEmpty()) {
            TvUtil.loadImage(ivLogo, currentChannel.logo);
        } else {
            ivLogo.setImageResource(R.drawable.home_logo);
        }
        fsTopBar.addView(ivLogo);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(currentChannel != null ? currentChannel.name : TvUtil.translate(this, "بث مباشر"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = px(dp, 15);
        tvTitle.setLayoutParams(titleLp);
        fsTopBar.addView(tvTitle);

        // Server Status
        LinearLayout serverBox = new LinearLayout(this);
        serverBox.setOrientation(LinearLayout.HORIZONTAL);
        serverBox.setGravity(Gravity.CENTER_VERTICAL);
        serverBox.setBackgroundColor(Color.parseColor("#22FFFFFF"));
        serverBox.setPadding(px(dp, 8), px(dp, 4), px(dp, 8), px(dp, 4));
        LinearLayout.LayoutParams serverBoxLp = new LinearLayout.LayoutParams(-2, -2);
        serverBoxLp.rightMargin = px(dp, 15);
        serverBox.setLayoutParams(serverBoxLp);

        View serverDot = new View(this);
        serverStatusDot = serverDot;
        GradientDrawable sDotBg = new GradientDrawable();
        sDotBg.setColor(Color.GREEN);
        sDotBg.setCornerRadius(px(dp, 6));
        serverDot.setBackground(sDotBg);
        serverBox.addView(serverDot, new LinearLayout.LayoutParams(px(dp, 8), px(dp, 8)));

        TextView tvServer = new TextView(this);
        tvServerStatus = tvServer;
        tvServer.setText(TvUtil.translate(this, "سيرفر نشط"));
        tvServer.setTextColor(Color.WHITE);
        tvServer.setTextSize(11);
        LinearLayout.LayoutParams tvServerLp = new LinearLayout.LayoutParams(-2, -2);
        tvServerLp.leftMargin = px(dp, 5);
        tvServer.setLayoutParams(tvServerLp);
        serverBox.addView(tvServer);
        fsTopBar.addView(serverBox);

        // Favorite Toggle Icon
        btnFsFav = new ImageView(this);
        btnFsFav.setLayoutParams(new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28)));
        btnFsFav.setPadding(px(dp, 2), px(dp, 2), px(dp, 2), px(dp, 2));
        updateFsFavIcon();
        btnFsFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLiveChannelFavorite();
            }
        });
        TvUtil.applyTvFocusHighlight(btnFsFav, 10.0f);
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        favLp.rightMargin = px(dp, 15);
        btnFsFav.setLayoutParams(favLp);
        fsTopBar.addView(btnFsFav);

        // Child Lock Icon
        btnFsLock = new ImageView(this);
        btnFsLock.setImageResource(R.drawable.ic_material_lock_open);
        btnFsLock.setColorFilter(Color.WHITE);
        btnFsLock.setPadding(px(dp, 2), px(dp, 2), px(dp, 2), px(dp, 2));
        btnFsLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLocked = !isLocked;
                updateFsLockIcon();
                if (isLocked) {
                    fsTopBar.setVisibility(View.GONE);
                    fsBottomBar.setVisibility(View.GONE);
                    fsCenterPlay.setVisibility(View.GONE);
                    if (fsCenterControls != null) fsCenterControls.setVisibility(View.GONE);
                    Toast.makeText(LiveActivity.this, "تم قفل الشاشة والتحكم", Toast.LENGTH_SHORT).show();
                } else {
                    fsTopBar.setVisibility(View.VISIBLE);
                    fsBottomBar.setVisibility(View.VISIBLE);
                    fsCenterPlay.setVisibility(View.VISIBLE);
                    if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                    if (TvUtil.isTvMode(LiveActivity.this) && fsCenterPlay != null) {
                        fsCenterPlay.requestFocus();
                    }
                    Toast.makeText(LiveActivity.this, "تم إلغاء القفل", Toast.LENGTH_SHORT).show();
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btnFsLock, 10.0f);
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        lockLp.rightMargin = px(dp, 15);
        btnFsLock.setLayoutParams(lockLp);
        fsTopBar.addView(btnFsLock);

        // Quick satellite-style channel selector button
        final ImageView btnFsSelector = new ImageView(this);
        localSelectorTemp = btnFsSelector;
        int selectorIconId = getResources().getIdentifier("ic_settings_player", "drawable", getPackageName());
        if (selectorIconId == 0) selectorIconId = getResources().getIdentifier("ic_settings_playlist", "drawable", getPackageName());
        if (selectorIconId == 0) selectorIconId = android.R.drawable.ic_menu_agenda;
        btnFsSelector.setImageResource(selectorIconId);
        btnFsSelector.setColorFilter(Color.WHITE);
        btnFsSelector.setClickable(true);
        btnFsSelector.setFocusable(true);
        btnFsSelector.setFocusableInTouchMode(true);
        btnFsSelector.setPadding(px(dp, 2), px(dp, 2), px(dp, 2), px(dp, 2));
        btnFsSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                showQuickChannelSelector(dp);
            }
        });
        TvUtil.applyTvFocusHighlight(btnFsSelector, 10.0f);
        LinearLayout.LayoutParams selectorLp = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        selectorLp.rightMargin = px(dp, 15);
        btnFsSelector.setLayoutParams(selectorLp);
        fsTopBar.addView(btnFsSelector);

        // Video Quality Selection Button
        ImageView btnQuality = new ImageView(this);
        localQualityTemp = btnQuality;
        btnQuality.setImageResource(R.drawable.ic_material_high_quality);
        btnQuality.setColorFilter(Color.WHITE);
        btnQuality.setFocusable(true);
        LinearLayout.LayoutParams lpQuality = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        lpQuality.rightMargin = px(dp, 15);
        btnQuality.setLayoutParams(lpQuality);
        btnQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                try {
                    int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                    final com.google.android.exoplayer2.ui.DefaultTrackNameProvider defaultProvider = 
                        new com.google.android.exoplayer2.ui.DefaultTrackNameProvider(getResources());
                    new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                        themeId != 0 ? new android.view.ContextThemeWrapper(LiveActivity.this, themeId) : LiveActivity.this,
                        "اختر جودة الفيديو",
                        exoPlayer,
                        2 // C.TRACK_TYPE_VIDEO
                    )
                    .setShowDisableOption(false)
                    .setAllowAdaptiveSelections(true)
                    .setTrackNameProvider(new com.google.android.exoplayer2.ui.TrackNameProvider() {
                        @Override
                        public String getTrackName(com.google.android.exoplayer2.Format format) {
                            int h = format.height;
                            if (h <= 0) {
                                return defaultProvider.getTrackName(format);
                            }
                            if (h >= 2160) {
                                return "4K (" + h + "p)";
                            }
                            if (h >= 1440) {
                                return "2K (" + h + "p)";
                            }
                            if (h >= 1080) {
                                return "1080p (FHD)";
                            }
                            if (h >= 720) {
                                return "720p (HD)";
                            }
                            if (h >= 480) {
                                return "480p (SD)";
                            }
                            if (h >= 360) {
                                return "360p";
                            }
                            if (h >= 240) {
                                return "240p";
                            }
                            return h + "p";
                        }
                    })
                    .build()
                    .show();
                } catch (Exception e) {
                    Toast.makeText(LiveActivity.this, "غير مدعوم للبث الحالي", Toast.LENGTH_SHORT).show();
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btnQuality, 10.0f);
        fsTopBar.addView(btnQuality);

        // Audio Selection Button
        ImageView btnAudio = new ImageView(this);
        localAudioTemp = btnAudio;
        btnAudio.setImageResource(R.drawable.ic_material_audio);
        btnAudio.setColorFilter(Color.WHITE);
        btnAudio.setFocusable(true);
        LinearLayout.LayoutParams lpAudio = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        lpAudio.rightMargin = px(dp, 15);
        btnAudio.setLayoutParams(lpAudio);
        btnAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                try {
                    int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                    java.util.List<com.google.android.exoplayer2.Tracks.Group> audioGroups = new java.util.ArrayList<>();
                    if (exoPlayer != null && exoPlayer.getCurrentTracks() != null) {
                        for (com.google.android.exoplayer2.Tracks.Group group : exoPlayer.getCurrentTracks().getGroups()) {
                            if (group.getType() == 1) { // 1 is C.TRACK_TYPE_AUDIO
                                audioGroups.add(group);
                            }
                        }
                    }
                    new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                        themeId != 0 ? new android.view.ContextThemeWrapper(LiveActivity.this, themeId) : LiveActivity.this,
                        "اختر لغة الصوت",
                        audioGroups,
                        new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder.DialogCallback() {
                            @Override
                            public void onTracksSelected(boolean isDisabled, java.util.Map<com.google.android.exoplayer2.source.TrackGroup, com.google.android.exoplayer2.trackselection.TrackSelectionOverride> overrides) {
                                if (exoPlayer == null) return;
                                com.google.android.exoplayer2.trackselection.TrackSelectionParameters.Builder builder = exoPlayer.getTrackSelectionParameters().buildUpon();
                                builder.setTrackTypeDisabled(1, isDisabled); // 1 is C.TRACK_TYPE_AUDIO
                                builder.clearOverridesOfType(1);
                                for (com.google.android.exoplayer2.trackselection.TrackSelectionOverride override : overrides.values()) {
                                    builder.addOverride(override);
                                }
                                com.google.android.exoplayer2.trackselection.TrackSelectionParameters newParams = builder.build();
                                exoPlayer.setTrackSelectionParameters(newParams);
                                if (trackSelector != null) {
                                    trackSelector.setParameters(newParams);
                                }
                                // Seek to current position to force reload the audio decoder immediately
                                long currentPos = exoPlayer.getCurrentPosition();
                                exoPlayer.seekTo(currentPos);
                            }
                        }
                    )
                    .setShowDisableOption(false)
                    .setAllowAdaptiveSelections(true)
                    .build()
                    .show();
                } catch (Exception e) {
                    Toast.makeText(LiveActivity.this, "غير مدعوم للبث الحالي", Toast.LENGTH_SHORT).show();
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btnAudio, 10.0f);
        fsTopBar.addView(btnAudio);

        // Subtitle Selection Button
        ImageView btnSub = new ImageView(this);
        localSubTemp = btnSub;
        btnSub.setImageResource(R.drawable.ic_material_subtitles);
        btnSub.setColorFilter(Color.WHITE);
        btnSub.setFocusable(true);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28));
        lpSub.rightMargin = px(dp, 15);
        btnSub.setLayoutParams(lpSub);
        btnSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                try {
                    int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                    new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                        themeId != 0 ? new android.view.ContextThemeWrapper(LiveActivity.this, themeId) : LiveActivity.this,
                        "اختر الترجمة",
                        exoPlayer,
                        3 // C.TRACK_TYPE_TEXT
                    )
                    .setShowDisableOption(true)
                    .setAllowAdaptiveSelections(false)
                    .build()
                    .show();
                } catch (Exception e) {
                    Toast.makeText(LiveActivity.this, "غير مدعوم للبث الحالي", Toast.LENGTH_SHORT).show();
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btnSub, 10.0f);
        fsTopBar.addView(btnSub);

        // Aspect Ratio Cycle
        ImageView btnFsAspect = new ImageView(this);
        btnFsAspect.setImageResource(R.drawable.ic_material_aspect_ratio);
        btnFsAspect.setColorFilter(Color.WHITE);
        btnFsAspect.setPadding(px(dp, 2), px(dp, 2), px(dp, 2), px(dp, 2));
        btnFsAspect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                int nextAspect = (currentAspectIndex + 1) % 3;
                applyAspectRatio(nextAspect, true);
            }
        });
        TvUtil.applyTvFocusHighlight(btnFsAspect, 10.0f);
        fsTopBar.addView(btnFsAspect, new LinearLayout.LayoutParams(px(dp, 28), px(dp, 28)));

        customOverlay.addView(fsTopBar);

        // Center Playback Container
        fsCenterControls = new LinearLayout(this);
        fsCenterControls.setOrientation(LinearLayout.HORIZONTAL);
        fsCenterControls.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerControlsParams = new FrameLayout.LayoutParams(-2, -2);
        centerControlsParams.gravity = Gravity.CENTER;
        fsCenterControls.setLayoutParams(centerControlsParams);

        // Center Rewind 10s Control
        ImageView fsCenterRew = new ImageView(this);
        fsCenterRew.setImageResource(getRewindIconId());
        fsCenterRew.setColorFilter(Color.WHITE);
        fsCenterRew.setBackgroundColor(Color.TRANSPARENT);
        fsCenterRew.setPadding(px(dp, 15), px(dp, 15), px(dp, 15), px(dp, 15));
        LinearLayout.LayoutParams centerRewParams = new LinearLayout.LayoutParams(px(dp, 60), px(dp, 60));
        centerRewParams.rightMargin = px(dp, 15);
        fsCenterRew.setLayoutParams(centerRewParams);
        fsCenterRew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                seekLiveBackward10s();
            }
        });
        TvUtil.applyTvFocusHighlight(fsCenterRew, 30.0f);
        fsCenterControls.addView(fsCenterRew);

        // Center Playback Control
        fsCenterPlay = new ImageView(this);
        fsCenterPlay.setImageResource(exoPlayer.getPlayWhenReady() ? R.drawable.ic_material_pause : R.drawable.ic_material_play_arrow);
        fsCenterPlay.setColorFilter(Color.WHITE);
        fsCenterPlay.setBackgroundColor(Color.TRANSPARENT);
        fsCenterPlay.setPadding(px(dp, 20), px(dp, 20), px(dp, 20), px(dp, 20));
        LinearLayout.LayoutParams centerPlayParams = new LinearLayout.LayoutParams(px(dp, 70), px(dp, 70));
        fsCenterPlay.setLayoutParams(centerPlayParams);
        fsCenterPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                boolean currentPlayState = exoPlayer.getPlayWhenReady();
                exoPlayer.setPlayWhenReady(!currentPlayState);
                fsCenterPlay.setImageResource(!currentPlayState ? R.drawable.ic_material_pause : R.drawable.ic_material_play_arrow);
            }
        });
        TvUtil.applyTvFocusHighlight(fsCenterPlay, 35.0f);
        fsCenterControls.addView(fsCenterPlay);

        // Center Forward 10s Control
        ImageView fsCenterFfwd = new ImageView(this);
        fsCenterFfwd.setImageResource(getForwardIconId());
        fsCenterFfwd.setColorFilter(Color.WHITE);
        fsCenterFfwd.setBackgroundColor(Color.TRANSPARENT);
        fsCenterFfwd.setPadding(px(dp, 15), px(dp, 15), px(dp, 15), px(dp, 15));
        LinearLayout.LayoutParams centerFfwdParams = new LinearLayout.LayoutParams(px(dp, 60), px(dp, 60));
        centerFfwdParams.leftMargin = px(dp, 15);
        fsCenterFfwd.setLayoutParams(centerFfwdParams);
        fsCenterFfwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) return;
                seekLiveForward10s();
            }
        });
        TvUtil.applyTvFocusHighlight(fsCenterFfwd, 30.0f);
        fsCenterControls.addView(fsCenterFfwd);

        customOverlay.addView(fsCenterControls);

        // Custom Bottom Bar
        fsBottomBar = new LinearLayout(this);
        fsBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        fsBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        fsBottomBar.setBackgroundResource(getResources().getIdentifier("player_bottom_gradient", "drawable", getPackageName()));
        fsBottomBar.setPadding(px(dp, 20), px(dp, 10), px(dp, 20), px(dp, 10));
        FrameLayout.LayoutParams bottomBarParams = new FrameLayout.LayoutParams(-1, -2);
        bottomBarParams.gravity = Gravity.BOTTOM;
        fsBottomBar.setLayoutParams(bottomBarParams);

        TextView tvFsChDetails = new TextView(this);
        tvFsChDetails.setText(currentChannel != null ? (currentChannel.num + " - " + currentChannel.name) : TvUtil.translate(this, "بث مباشر"));
        tvFsChDetails.setTextColor(Color.WHITE);
        tvFsChDetails.setTextSize(13);
        tvFsChDetails.setTypeface(null, Typeface.BOLD);
        fsBottomBar.addView(tvFsChDetails, new LinearLayout.LayoutParams(0, -2, 1));

        // LIVE Indicator
        fsTvLive = new TextView(this);
        fsTvLive.setText("🔴 LIVE");
        fsTvLive.setTextColor(Color.parseColor("#FF3D00"));
        fsTvLive.setTextSize(12);
        fsTvLive.setTypeface(null, Typeface.BOLD);
        fsBottomBar.addView(fsTvLive);

        customOverlay.addView(fsBottomBar);

        // Add custom overlay on top of playerView
        fullscreenOverlay.addView(customOverlay);

        // Touch listener for swipe gestures
        customOverlay.setOnTouchListener(new View.OnTouchListener() {
            private final Handler hideHandler = new Handler();
            private final Runnable hideRunnable = new Runnable() {
                @Override
                public void run() {
                    // Sliders remain visible with main controls
                }
            };

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        btnFsLock.setVisibility(View.VISIBLE);
                        new Handler().postDelayed(new Runnable() {
                            @Override public void run() { btnFsLock.setVisibility(View.VISIBLE); }
                        }, 2000);
                    }
                    return true;
                }

                float screenWidth = v.getWidth();
                float screenHeight = v.getHeight();

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialY = event.getY();
                        initialVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        initialBrightness = lp.screenBrightness;
                        if (initialBrightness < 0) {
                            try {
                                initialBrightness = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
                            } catch (Exception e) {
                                initialBrightness = 0.5f;
                            }
                        }

                        if (event.getX() > px(dp, 80) && event.getX() < screenWidth - px(dp, 80)) {
                            if (fsTopBar.getVisibility() == View.VISIBLE) {
                                fsTopBar.setVisibility(View.GONE);
                                fsBottomBar.setVisibility(View.GONE);
                                fsCenterPlay.setVisibility(View.GONE);
                                if (fsCenterControls != null) fsCenterControls.setVisibility(View.GONE);
                                fsLeftSliderContainer.setVisibility(View.GONE);
                                fsRightSliderContainer.setVisibility(View.GONE);
                            } else {
                                fsTopBar.setVisibility(View.VISIBLE);
                                fsBottomBar.setVisibility(View.VISIBLE);
                                fsCenterPlay.setVisibility(View.VISIBLE);
                                if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                                if (!TvUtil.isTvMode(LiveActivity.this)) {
                                    fsLeftSliderContainer.setVisibility(View.VISIBLE);
                                    fsRightSliderContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                        break;

                    case android.view.MotionEvent.ACTION_MOVE:
                        break;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        hideHandler.postDelayed(hideRunnable, 1500);
                        break;
                }
                return true;
            }
        });

        // Establish precise D-pad navigation routing between Top and Center controls in Fullscreen
        if (btnBack != null) btnBack.setId(9100);
        if (localQualityTemp != null) localQualityTemp.setId(9101);
        if (localAudioTemp != null) localAudioTemp.setId(9102);
        if (localSubTemp != null) localSubTemp.setId(9103);
        if (btnFsAspect != null) btnFsAspect.setId(9104);
        if (btnFsFav != null) btnFsFav.setId(9105);
        if (btnFsLock != null) btnFsLock.setId(9106);
        if (localSelectorTemp != null) localSelectorTemp.setId(9107);

        if (fsCenterPlay != null) fsCenterPlay.setId(View.generateViewId());
        if (fsCenterRew != null) fsCenterRew.setId(View.generateViewId());
        if (fsCenterFfwd != null) fsCenterFfwd.setId(View.generateViewId());

        if (btnBack != null) {
            btnBack.setNextFocusRightId(btnFsFav != null ? 9105 : (btnFsLock != null ? 9106 : (localSelectorTemp != null ? 9107 : (localQualityTemp != null ? 9101 : (localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : 9104))))));
            if (fsCenterPlay != null) btnBack.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (btnFsFav != null) {
            btnFsFav.setNextFocusLeftId(9100);
            btnFsFav.setNextFocusRightId(btnFsLock != null ? 9106 : (localSelectorTemp != null ? 9107 : (localQualityTemp != null ? 9101 : (localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : 9104)))));
            if (fsCenterPlay != null) btnFsFav.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (btnFsLock != null) {
            btnFsLock.setNextFocusLeftId(btnFsFav != null ? 9105 : 9100);
            btnFsLock.setNextFocusRightId(localSelectorTemp != null ? 9107 : (localQualityTemp != null ? 9101 : (localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : 9104))));
            if (fsCenterPlay != null) btnFsLock.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (localSelectorTemp != null) {
            localSelectorTemp.setNextFocusLeftId(btnFsLock != null ? 9106 : (btnFsFav != null ? 9105 : 9100));
            localSelectorTemp.setNextFocusRightId(localQualityTemp != null ? 9101 : (localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : 9104)));
            if (fsCenterPlay != null) localSelectorTemp.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (localQualityTemp != null) {
            localQualityTemp.setNextFocusLeftId(localSelectorTemp != null ? 9107 : (btnFsLock != null ? 9106 : (btnFsFav != null ? 9105 : 9100)));
            localQualityTemp.setNextFocusRightId(localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : 9104));
            if (fsCenterPlay != null) localQualityTemp.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (localAudioTemp != null) {
            localAudioTemp.setNextFocusLeftId(localQualityTemp != null ? 9101 : (localSelectorTemp != null ? 9107 : (btnFsLock != null ? 9106 : 9100)));
            localAudioTemp.setNextFocusRightId(localSubTemp != null ? 9103 : 9104);
            if (fsCenterPlay != null) localAudioTemp.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (localSubTemp != null) {
            localSubTemp.setNextFocusLeftId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : (localSelectorTemp != null ? 9107 : 9100)));
            localSubTemp.setNextFocusRightId(9104);
            if (fsCenterPlay != null) localSubTemp.setNextFocusDownId(fsCenterPlay.getId());
        }
        if (btnFsAspect != null) {
            btnFsAspect.setNextFocusLeftId(localSubTemp != null ? 9103 : (localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : (localSelectorTemp != null ? 9107 : 9100))));
            if (fsCenterPlay != null) btnFsAspect.setNextFocusDownId(fsCenterPlay.getId());
        }

        // Center Controls Next Focus Setup
        if (fsCenterPlay != null) {
            if (fsCenterRew != null) fsCenterPlay.setNextFocusLeftId(fsCenterRew.getId());
            if (fsCenterFfwd != null) fsCenterPlay.setNextFocusRightId(fsCenterFfwd.getId());
            fsCenterPlay.setNextFocusUpId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9106));
        }
        if (fsCenterRew != null) {
            if (fsCenterPlay != null) fsCenterRew.setNextFocusRightId(fsCenterPlay.getId());
            fsCenterRew.setNextFocusUpId(btnFsLock != null ? 9106 : 9100);
        }
        if (fsCenterFfwd != null) {
            if (fsCenterPlay != null) fsCenterFfwd.setNextFocusLeftId(fsCenterPlay.getId());
            fsCenterFfwd.setNextFocusUpId(localSubTemp != null ? 9103 : 9104);
        }

        triggerFsAutohide();
        fullscreenOverlay.setVisibility(View.VISIBLE);
        if (TvUtil.isTvMode(this) && fsCenterPlay != null) {
            fsCenterPlay.requestFocus();
        }
    }

    private void exitFullscreenPlayer(final float dp) {
        if (!isPlayerFullscreen) return;
        isPlayerFullscreen = false;

        // Detach player to prevent surface destruction issues when moving view hierarchy
        playerView.setPlayer(null);

        // Restore default screen orientation to landscape
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}

        // Restore brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);

        fullscreenOverlay.setVisibility(View.GONE);
        fullscreenOverlay.removeAllViews();

        if (mainLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
        }

        ViewGroup parent = (ViewGroup) playerWrapper.getParent();
        if (parent != null) {
            parent.removeView(playerWrapper);
        }

        // Restore preview layout
        playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));

        clickOverlay.setVisibility(View.VISIBLE);

        playerView.setUseController(false);
        playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);

        playerOriginalParent.addView(playerWrapper, 0);

        // Re-attach player once in new hierarchy so it binds to the new surface
        playerView.setPlayer(exoPlayer);

        if (TvUtil.isTvMode(this) && clickOverlay != null) {
            clickOverlay.requestFocus();
        }
    }

    private View buildPlayerPanel(final float dp) {
        playerOriginalParent = new LinearLayout(this);
        playerOriginalParent.setOrientation(LinearLayout.VERTICAL);
        playerOriginalParent.setBackgroundColor(Color.TRANSPARENT);
        int layoutId = getResources().getIdentifier("live_player_view", "layout", getPackageName());
        playerView = (layoutId != 0) ? (StyledPlayerView) getLayoutInflater().inflate(layoutId, null) : new StyledPlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setUseArtwork(false);

        playerWrapper = new FrameLayout(this);
        playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        playerWrapper.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        clickOverlay = new View(this);
        clickOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        clickOverlay.setBackgroundColor(Color.TRANSPARENT);
        clickOverlay.setClickable(true);
        clickOverlay.setFocusable(true);
        clickOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterFullscreenPlayer(dp);
            }
        });
        clickOverlay.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (rvChannels != null && rvChannels.getChildCount() > 0) {
                            int selectedIdx = 0;
                            if (currentChannel != null) {
                                for (int i = 0; i < displayedChannels.size(); i++) {
                                    if (displayedChannels.get(i).name.equals(currentChannel.name)) {
                                        selectedIdx = i;
                                        break;
                                    }
                                }
                            }
                            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(selectedIdx);
                            if (holder != null && holder.itemView instanceof ViewGroup) {
                                View chanRow = ((ViewGroup) holder.itemView).getChildAt(0);
                                if (chanRow != null) {
                                    chanRow.requestFocus();
                                    return true;
                                }
                            } else {
                                View first = rvChannels.getChildAt(0);
                                if (first != null && first instanceof ViewGroup) {
                                    View chanRow = ((ViewGroup) first).getChildAt(0);
                                    if (chanRow != null) {
                                        chanRow.requestFocus();
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            }
        });
        TvUtil.applyTvFocusHighlight(clickOverlay, 0.0f);
        playerWrapper.addView(clickOverlay);

        playerOriginalParent.addView(playerWrapper);

        com.google.android.exoplayer2.upstream.DefaultBandwidthMeter bandwidthMeter = 
            new com.google.android.exoplayer2.upstream.DefaultBandwidthMeter.Builder(this)
                .setInitialBitrateEstimate(250000) // 250 kbps initial estimate
                .build();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs: 15s buffer (ExoPlayer default minimum)
                50000, // maxBufferMs: 50s max buffer size (ExoPlayer default maximum)
                2500,  // bufferForPlaybackMs: starts playing after 2.5s of data to prevent instant stuttering
                5000   // bufferForPlaybackAfterRebufferMs: recovers smoothly with 5s of data after a drop
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();

        com.google.android.exoplayer2.mediacodec.MediaCodecSelector customMediaCodecSelector = new com.google.android.exoplayer2.mediacodec.MediaCodecSelector() {
            @Override
            public java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> getDecoderInfos(
                String mimeType, boolean requiresSecure, boolean requiresTunneling)
                throws com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException {
                
                if ("audio/mpeg-L2".equalsIgnoreCase(mimeType) || "audio/mpeg-L1".equalsIgnoreCase(mimeType)) {
                    java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> infos = 
                        com.google.android.exoplayer2.mediacodec.MediaCodecUtil.getDecoderInfos(
                            "audio/mpeg", requiresSecure, requiresTunneling);
                    if (!infos.isEmpty()) {
                        return infos;
                    }
                }
                return com.google.android.exoplayer2.mediacodec.MediaCodecUtil.getDecoderInfos(
                    mimeType, requiresSecure, requiresTunneling);
            }
        };

        com.google.android.exoplayer2.DefaultRenderersFactory renderersFactory = new com.google.android.exoplayer2.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(customMediaCodecSelector)
            .setExtensionRendererMode(com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setAllowMultipleAdaptiveSelections(true)
                .setPreferredAudioMimeTypes("audio/mp4a-latm", "audio/mpeg")
        );

        exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build();
            
        exoPlayer.setSeekParameters(com.google.android.exoplayer2.SeekParameters.CLOSEST_SYNC);

        playerView.setPlayer(exoPlayer);
        exoPlayer.addListener(new BasePlayerListener() {
            @Override
            public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize videoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    final String resStr = videoSize.width + "x" + videoSize.height;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (fsTvLive != null) {
                                fsTvLive.setText("🔴 LIVE | " + resStr);
                            }
                        }
                    });
                }
            }
            @Override 
            public void onPlaybackStateChanged(int state) {
                int progId = getResources().getIdentifier("progress", "id", getPackageName());
                View progress = playerView.findViewById(progId);
                if (progress != null) {
                    progress.setVisibility((state == Player.STATE_BUFFERING || isReconnecting) ? View.VISIBLE : View.GONE);
                }
                
                if (state == Player.STATE_BUFFERING || isReconnecting) {
                    if (tvServerStatus != null) tvServerStatus.setText(TvUtil.translate(LiveActivity.this, "جاري الاتصال..."));
                    if (serverStatusDot != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(Color.YELLOW);
                        gd.setCornerRadius(12);
                        serverStatusDot.setBackground(gd);
                    }
                } else if (state == Player.STATE_READY) {
                    retryCount = 0; // reset retries
                    isReconnecting = false;
                    
                    if (tvServerStatus != null) tvServerStatus.setText(TvUtil.translate(LiveActivity.this, "سيرفر نشط"));
                    if (serverStatusDot != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(Color.GREEN);
                        gd.setCornerRadius(12);
                        serverStatusDot.setBackground(gd);
                    }
                    
                    if (exoPlayer != null && exoPlayer.getVideoFormat() != null) {
                        int w = exoPlayer.getVideoFormat().width;
                        int h = exoPlayer.getVideoFormat().height;
                        if (w > 0 && h > 0) {
                            final String realRes = w + "x" + h;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (fsTvLive != null) fsTvLive.setText("🔴 LIVE | " + realRes);
                                }
                            });
                        }
                    }
                    
                    // Re-apply saved aspect ratio to ensure it persists on stream start
                    int savedAspect = getSharedPreferences("LivePrefs", MODE_PRIVATE).getInt("aspect_ratio", 1);
                    applyAspectRatio(savedAspect, false);
                }
            }
            @Override 
            public void onPlayerError(PlaybackException error) { 
                if (tvServerStatus != null) tvServerStatus.setText(TvUtil.translate(LiveActivity.this, "السيرفر غير متصل"));
                if (serverStatusDot != null) {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(Color.RED);
                    gd.setCornerRadius(12);
                    serverStatusDot.setBackground(gd);
                } 
                logToFile("Player Error: " + android.util.Log.getStackTraceString(error)); 
                if (error.getCause() != null) {
                    logToFile("Player Error Cause: " + android.util.Log.getStackTraceString(error.getCause()));
                }
                
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                if (!isConnected) {
                    Toast.makeText(LiveActivity.this, "لا يوجد اتصال بالإنترنت، بانتظار عودة الشبكة...", Toast.LENGTH_SHORT).show();
                    setProgressVisibility(View.VISIBLE);
                    return; 
                }

                // Format switching fallback on parsing/decoder error
                boolean isFormatError = false;
                if (error != null) {
                    int errorCode = error.errorCode;
                    if (errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        retryCount = 0;
                        retryPlayback(0);
                        return;
                    }
                    
                    String errorMsg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
                    Throwable cause = error.getCause();
                    String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
                    
                    if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                        errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        errorMsg.contains("unrecognized") || errorMsg.contains("extractor") || errorMsg.contains("parser") ||
                        causeMsg.contains("unrecognized") || causeMsg.contains("extractor") || causeMsg.contains("parser")) {
                        isFormatError = true;
                    }
                }
                
                if (isFormatError && retryCount < MAX_RETRIES) {
                    if (com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T.equals(attemptedMimeType)) {
                        attemptedMimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8;
                    } else if (com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8.equals(attemptedMimeType)) {
                        attemptedMimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T;
                    } else {
                        attemptedMimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8;
                    }
                }
                
                long currentPos = 0;
                if (exoPlayer != null) {
                    currentPos = exoPlayer.getCurrentPosition();
                }
                retryPlayback(currentPos);
            }
        });

        playerView.post(new Runnable() { @Override public void run() { setupCustomControls(); } });

        tvChannelTitle = new TextView(this);
        tvChannelTitle.setText(TvUtil.translate(this, "اختر قناة"));
        tvChannelTitle.setTextColor(Color.WHITE);
        tvChannelTitle.setTextSize(15);
        tvChannelTitle.setTypeface(null, Typeface.BOLD);
        tvChannelTitle.setPadding(px(dp, 16), px(dp, 8), px(dp, 16), px(dp, 8));
        playerOriginalParent.addView(tvChannelTitle);

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, -2);
        scrollLp.bottomMargin = px(dp, 16);
        scroll.setLayoutParams(scrollLp);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        controls.setPadding(px(dp, 12), px(dp, 6), px(dp, 12), px(dp, 6));
        controls.setLayoutParams(new FrameLayout.LayoutParams(-2, -2));

        controls.addView(makeCtrlBtn(dp, "تشغيل / إيقاف", 3));
        controls.addView(makeCtrlBtn(dp, "اضافة الى المفضلة", 2));
        controls.addView(makeCtrlBtn(dp, "بحث", 1));

        scroll.addView(controls);
        playerOriginalParent.addView(scroll);
        return playerOriginalParent;
    }

    private void setupCustomControls() {
        int settingsId = getResources().getIdentifier("settings", "id", getPackageName());
        View btnSettings = playerView.findViewById(settingsId);
        if (btnSettings != null) btnSettings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSpeedDialog(); } });
        int pipId = getResources().getIdentifier("pip", "id", getPackageName());
        View btnPip = playerView.findViewById(pipId);
        if (btnPip != null) btnPip.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT >= 26) try { enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().build()); } catch(Exception e){}
            }
        });
        int fsId = getResources().getIdentifier("bt_fullscreen", "id", getPackageName());
        View btnFs = playerView.findViewById(fsId);
        if (btnFs != null) btnFs.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleFullscreen(); } });
    }

    private void showSpeedDialog() {
        final String[] options = {"0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2.0x"};
        final float[] values = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
        android.app.AlertDialog.Builder b = (themeId != 0) ? 
            new android.app.AlertDialog.Builder(this, themeId) : 
            new android.app.AlertDialog.Builder(this);
        b.setTitle(TvUtil.translate(this, "سرعة التشغيل"));
        b.setItems(options, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                if (exoPlayer != null) exoPlayer.setPlaybackParameters(new PlaybackParameters(values[w], 1.0f));
            }
        });
        b.show();
    }
    
    private boolean isFullscreen = false;
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private View makeCtrlBtn(float dp, String label, final int action) {
        TextView btn = new TextView(this);
        btn.setText(TvUtil.translate(this, label));
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(px(dp, 18), px(dp, 8), px(dp, 18), px(dp, 8));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#E6070B19"));
        gd.setStroke((int)(dp * 1.5f), Color.parseColor("#4D00E5FF"));
        gd.setCornerRadius(px(dp, 10));
        btn.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(dp, 10);
        btn.setLayoutParams(lp);
        if (action == 2) {
            btnFavoriteCtrl = btn;
            updateFavButtonText();
        }

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (action == 3) {
                    if (exoPlayer != null) exoPlayer.setPlayWhenReady(!(exoPlayer.getPlayWhenReady() && exoPlayer.getPlaybackState() == Player.STATE_READY));
                } else if (action == 2 && currentChannel != null) {
                    android.content.SharedPreferences favSp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
                    boolean isFav = favSp.getBoolean("fav_" + currentChannel.name, false);
                    isFav = !isFav;
                    favSp.edit().putBoolean("fav_" + currentChannel.name, isFav).apply();
                    
                    if (isFav) {
                        if (!favoriteChannels.contains(currentChannel)) {
                            favoriteChannels.add(currentChannel);
                        }
                        Toast.makeText(LiveActivity.this, "تمت الإضافة للمفضلة", Toast.LENGTH_SHORT).show();
                    } else {
                        for (int i = favoriteChannels.size() - 1; i >= 0; i--) {
                            if (favoriteChannels.get(i).name.equals(currentChannel.name)) {
                                favoriteChannels.remove(i);
                            }
                        }
                        Toast.makeText(LiveActivity.this, "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show();
                    }
                    updateFavButtonText();
                    updateFsFavIcon();
                    
                    for (CategoryItem cat : categories) {
                        if (cat.name.equals(CAT_FAV)) {
                            cat.count = favoriteChannels.size();
                            break;
                        }
                    }
                    if (rvCategories != null) rvCategories.getAdapter().notifyDataSetChanged();
                    if (rvChannels != null) rvChannels.getAdapter().notifyDataSetChanged();
                } else if (action == 4) {
                    try {
                        int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                        java.util.List<com.google.android.exoplayer2.Tracks.Group> audioGroups = new java.util.ArrayList<>();
                        if (exoPlayer != null && exoPlayer.getCurrentTracks() != null) {
                            for (com.google.android.exoplayer2.Tracks.Group group : exoPlayer.getCurrentTracks().getGroups()) {
                                if (group.getType() == 1) { // 1 is C.TRACK_TYPE_AUDIO
                                    audioGroups.add(group);
                                }
                            }
                        }
                        new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                            themeId != 0 ? new android.view.ContextThemeWrapper(LiveActivity.this, themeId) : LiveActivity.this,
                            "اختر لغة الصوت",
                            audioGroups,
                            new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder.DialogCallback() {
                                @Override
                                public void onTracksSelected(boolean isDisabled, java.util.Map<com.google.android.exoplayer2.source.TrackGroup, com.google.android.exoplayer2.trackselection.TrackSelectionOverride> overrides) {
                                    if (exoPlayer == null) return;
                                    com.google.android.exoplayer2.trackselection.TrackSelectionParameters.Builder builder = exoPlayer.getTrackSelectionParameters().buildUpon();
                                    builder.setTrackTypeDisabled(1, isDisabled); // 1 is C.TRACK_TYPE_AUDIO
                                    builder.clearOverridesOfType(1);
                                    for (com.google.android.exoplayer2.trackselection.TrackSelectionOverride override : overrides.values()) {
                                        builder.addOverride(override);
                                    }
                                    com.google.android.exoplayer2.trackselection.TrackSelectionParameters newParams = builder.build();
                                    exoPlayer.setTrackSelectionParameters(newParams);
                                    if (trackSelector != null) {
                                        trackSelector.setParameters(newParams);
                                    }
                                    // Seek to current position to force reload the audio decoder immediately
                                    long currentPos = exoPlayer.getCurrentPosition();
                                    exoPlayer.seekTo(currentPos);
                                }
                            }
                        )
                        .setShowDisableOption(false)
                        .setAllowAdaptiveSelections(true)
                        .build()
                        .show();
                    } catch (Exception e) {
                        Toast.makeText(LiveActivity.this, "غير مدعوم للبث الحالي", Toast.LENGTH_SHORT).show();
                    }
                } else if (action == 5) {
                    try {
                        int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                        new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                            themeId != 0 ? new android.view.ContextThemeWrapper(LiveActivity.this, themeId) : LiveActivity.this,
                            "اختر الترجمة",
                            exoPlayer,
                            3 // C.TRACK_TYPE_TEXT
                        )
                        .setShowDisableOption(true)
                        .setAllowAdaptiveSelections(false)
                        .build()
                        .show();
                    } catch (Exception e) {
                        Toast.makeText(LiveActivity.this, "غير مدعوم للبث الحالي", Toast.LENGTH_SHORT).show();
                    }
                } else if (action == 6) {
                    int nextAspect = (currentAspectIndex + 1) % 3;
                    applyAspectRatio(nextAspect, true);
                } else if (action == 1) {
                    if (searchView != null) {
                        searchView.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(searchView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                } else if (action == 7) {
                    seekLiveBackward10s();
                } else if (action == 8) {
                    seekLiveForward10s();
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btn, 20.0f);
        return btn;
    }

    private void seekLiveForward10s() {
        if (exoPlayer == null) return;
        long currentPos = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();
        if (duration != com.google.android.exoplayer2.C.TIME_UNSET) {
            long liveOffset = duration - currentPos;
            if (liveOffset > 10000) {
                long targetPos = currentPos + 10000;
                if (targetPos > duration) {
                    targetPos = duration;
                }
                exoPlayer.seekTo(targetPos);
                Toast.makeText(this, "تقديم 10 ثوانٍ", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "البث مباشر بالفعل ولا يمكن تقديمه أكثر", Toast.LENGTH_SHORT).show();
            }
        } else {
            long targetPos = currentPos + 10000;
            exoPlayer.seekTo(targetPos);
        }
    }

    private void seekLiveBackward10s() {
        if (exoPlayer == null) return;
        long currentPos = exoPlayer.getCurrentPosition();
        long targetPos = currentPos - 10000;
        if (targetPos < 0) {
            targetPos = 0;
        }
        exoPlayer.seekTo(targetPos);
        Toast.makeText(this, "إرجاع 10 ثوانٍ", Toast.LENGTH_SHORT).show();
    }

    private int getRewindIconId() {
        int id = getResources().getIdentifier("ic_material_rewind", "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_rewind", "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_av_rewind", "drawable", getPackageName());
        if (id == 0) id = android.R.drawable.ic_media_rew;
        return id;
    }

    private int getForwardIconId() {
        int id = getResources().getIdentifier("ic_material_fast_forward", "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_fast_forward", "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_av_fast_forward", "drawable", getPackageName());
        if (id == 0) id = android.R.drawable.ic_media_ff;
        return id;
    }

    private void playNextChannel(boolean next) {
        if (filteredChannels == null || filteredChannels.isEmpty() || currentChannel == null) return;
        int currentIndex = -1;
        for (int i = 0; i < filteredChannels.size(); i++) {
            if (filteredChannels.get(i).name.equals(currentChannel.name)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1) return;
        
        int nextIndex;
        if (next) {
            nextIndex = (currentIndex + 1) % filteredChannels.size();
        } else {
            nextIndex = (currentIndex - 1 + filteredChannels.size()) % filteredChannels.size();
        }
        
        final ChannelItem target = filteredChannels.get(nextIndex);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                playChannel(target);
                if (rvChannels != null && rvChannels.getAdapter() != null) {
                    rvChannels.getAdapter().notifyDataSetChanged();
                }
                if (fsTopBar != null) {
                    fsTopBar.setVisibility(View.VISIBLE);
                    fsBottomBar.setVisibility(View.VISIBLE);
                    fsCenterPlay.setVisibility(View.VISIBLE);
                    if (fsCenterControls != null) fsCenterControls.setVisibility(View.VISIBLE);
                    if (!TvUtil.isTvMode(LiveActivity.this)) {
                        fsLeftSliderContainer.setVisibility(View.VISIBLE);
                        fsRightSliderContainer.setVisibility(View.VISIBLE);
                    }
                    if (TvUtil.isTvMode(LiveActivity.this) && fsCenterPlay != null) {
                        fsCenterPlay.requestFocus();
                    }
                }
            }
        });
    }

    private void playChannel(ChannelItem ch) {
        if (ch.url == null || ch.url.isEmpty()) {
            Toast.makeText(this, "رابط غير صالح", 0).show();
            return;
        }

        // Check if external player is activated
        android.content.SharedPreferences spSettings = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean useExternal = spSettings.getBoolean("use_external", false);
        int playerType = spSettings.getInt("player_type", 0);
        if (useExternal || playerType > 0) {
            launchExternalPlayer(this, ch.url, TvUtil.formatNameByLanguage(ch.name), null, null, playerType);
            return;
        }

        currentChannel = ch;
        attemptedMimeType = null;
        tvChannelTitle.setText(TvUtil.formatNameByLanguage(ch.name));
        updateFavButtonText();

        android.content.SharedPreferences sp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        sp.edit().putString("last_channel_name", ch.name)
                 .putString("last_category_name", selectedCategory)
                 .apply();

        for (int i = recentChannels.size() - 1; i >= 0; i--) {
            if (recentChannels.get(i).name.equals(ch.name)) {
                recentChannels.remove(i);
            }
        }
        recentChannels.add(0, ch);
        if (recentChannels.size() > 50) recentChannels.remove(recentChannels.size() - 1);
        saveRecentChannels();

        for (CategoryItem cat : categories) {
            if (cat.name.equals(CAT_RECENT)) { cat.count = recentChannels.size(); break; }
        }
        if (rvCategories != null) rvCategories.getAdapter().notifyDataSetChanged();
        if (rvChannels != null) rvChannels.getAdapter().notifyDataSetChanged();
        focusSelectedChannel();
        MediaSource src = buildMediaSource(ch.url);
        if (exoPlayer != null) { 
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaSource(src); 
            exoPlayer.prepare(); 
            exoPlayer.setPlayWhenReady(true); 
            
            // Restore saved aspect ratio on channel change
            int savedAspect = getSharedPreferences("LivePrefs", MODE_PRIVATE).getInt("aspect_ratio", 1);
            applyAspectRatio(savedAspect, false);
        }
    }

    private void applyAspectRatio(int aspectIndex, boolean showToast) {
        currentAspectIndex = aspectIndex;
        if (playerView != null) {
            if (currentAspectIndex == 0) {
                playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            } else if (currentAspectIndex == 1) {
                playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
            } else if (currentAspectIndex == 2) {
                playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            }
        }
        getSharedPreferences("LivePrefs", MODE_PRIVATE).edit().putInt("aspect_ratio", currentAspectIndex).apply();
    }

    private MediaSource buildMediaSource(String url) {
        return buildMediaSource(url, attemptedMimeType);
    }

    private MediaSource buildMediaSource(String url, String mimeType) {
        String cleanUrl = url.trim();
        android.net.Uri uri = android.net.Uri.parse(cleanUrl);
        String lowercaseUrl = cleanUrl.toLowerCase();
        
        // 1. HTTP Data Source configuration
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000) // Increase connection timeout to 15s for weak connections
            .setReadTimeoutMs(20000)    // Increase read timeout to 20s
            .setKeepPostFor302Redirects(true)
            .setAllowCrossProtocolRedirects(true);
            
        // Use a generic high-compatibility PC Chrome User-Agent to prevent 403 Forbidden responses
        httpDataSourceFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
        
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Connection", "Keep-Alive");
        httpDataSourceFactory.setDefaultRequestProperties(headers);
        
        // 2. Extractor settings for progressive media (including TS)
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        extractorsFactory.setTsExtractorFlags(1 | 8 | 64); // FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS | FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
        
        // 3. Disable caching for live streams in LiveActivity to prevent high disk I/O latency on weak hardware (like TV boxes)
        com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = httpDataSourceFactory;
        
        // 4. Force detection of format based on url or mimeType
        boolean isHls = false;
        boolean isDash = false;
        boolean isRtsp = false;
        
        if (mimeType != null) {
            isHls = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8.equals(mimeType);
            isDash = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_MPD.equals(mimeType);
            isRtsp = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_RTSP.equals(mimeType);
        } else {
            isHls = lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains("m3u8") || lowercaseUrl.contains("hls") || lowercaseUrl.contains("format=m3u8") || lowercaseUrl.contains("type=m3u8") || lowercaseUrl.contains("extension=m3u8") || lowercaseUrl.contains("output=m3u8");
            isDash = lowercaseUrl.contains(".mpd") || lowercaseUrl.contains("mpd") || lowercaseUrl.contains("dash") || lowercaseUrl.contains("format=mpd") || lowercaseUrl.contains("type=mpd") || lowercaseUrl.contains("extension=mpd");
            isRtsp = cleanUrl.startsWith("rtsp://");
        }
        
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(uri);
        
        // Create custom LoadErrorHandlingPolicy with aggressive retries for weak connections
        com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy errorHandlingPolicy = 
            new com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy(12) {
                @Override
                public long getRetryDelayMsFor(com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
                    if (loadErrorInfo.exception instanceof com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException) {
                        int responseCode = ((com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException) loadErrorInfo.exception).responseCode;
                        if (responseCode == 404) {
                            return com.google.android.exoplayer2.C.TIME_UNSET; // Don't retry on 404 Not Found
                        }
                        if (responseCode == 403 || responseCode == 401 || responseCode == 500) {
                            if (loadErrorInfo.errorCount > 3) {
                                return com.google.android.exoplayer2.C.TIME_UNSET; // Fail after 3 attempts in the background
                            }
                        }
                    }
                    return Math.min(500L * loadErrorInfo.errorCount, 3000L); // Fast retry delay
                }
                
                @Override
                public int getMinimumLoadableRetryCount(int dataType) {
                    return 10; // Aggressively retry up to 10 times
                }
            };
            
        if (isHls) {
            mediaItemBuilder.setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8);
            return new HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItemBuilder.build());
        } else if (isDash) {
            mediaItemBuilder.setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_MPD);
            return new DashMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItemBuilder.build());
        } else if (isRtsp) {
            mediaItemBuilder.setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_RTSP);
            try {
                return new com.google.android.exoplayer2.source.rtsp.RtspMediaSource.Factory()
                    .createMediaSource(mediaItemBuilder.build());
            } catch (Throwable t) {
                // Fallback to progressive/DefaultMediaSourceFactory if RTSP fails
            }
        }
        
        // ProgressiveMediaSource is used for MP4, TS, MKV, AVI, etc.
        return new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            .createMediaSource(mediaItemBuilder.build());
    }

    private void setProgressVisibility(int visibility) {
        if (playerView != null) {
            int progId = getResources().getIdentifier("progress", "id", getPackageName());
            View progress = playerView.findViewById(progId);
            if (progress != null) {
                progress.setVisibility(visibility);
            }
        }
    }

    private void retryPlayback(final long errorPosition) {
        if (exoPlayer == null || currentChannel == null) return;
        if (retryCount >= MAX_RETRIES) {
            Toast.makeText(this, "فشل الاتصال بالبث بعد عدة محاولات", Toast.LENGTH_SHORT).show();
            isReconnecting = false;
            setProgressVisibility(View.GONE);
            return;
        }
        
        isReconnecting = true;
        retryCount++;
        setProgressVisibility(View.VISIBLE);

        final String originalUrl = currentChannel.url;
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer == null || currentChannel == null) return;
                try {
                    String targetUrl = originalUrl;
                    if (attemptedMimeType != null) {
                        if (com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8.equals(attemptedMimeType)) {
                            if (targetUrl.contains(".ts")) {
                                targetUrl = targetUrl.replace(".ts", ".m3u8");
                            }
                        } else if (com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T.equals(attemptedMimeType)) {
                            if (targetUrl.contains(".m3u8")) {
                                targetUrl = targetUrl.replace(".m3u8", ".ts");
                            }
                        }
                    }
                    MediaSource src = buildMediaSource(targetUrl, attemptedMimeType);
                    exoPlayer.stop();
                    exoPlayer.clearMediaItems();
                    exoPlayer.setMediaSource(src);
                    exoPlayer.prepare();
                    boolean isLive = originalUrl.toLowerCase().contains("m3u8") || originalUrl.toLowerCase().contains("hls") || originalUrl.toLowerCase().contains(".mpd") || originalUrl.toLowerCase().contains("live");
                    if (!isLive && errorPosition > 0) {
                        exoPlayer.seekTo(errorPosition);
                    }
                    exoPlayer.setPlayWhenReady(true);
                    isReconnecting = false;
                } catch (Throwable t) {
                    android.util.Log.e("LiveActivity", "Retry failed: " + t.getMessage());
                }
            }
        }, retryCount == 1 ? 500 : 1500); // 500ms for first retry, 1500ms for subsequent ones
    }

    public static class StreamFormatDetector {
        public interface Callback {
            void onResult(String detectedMimeType, String resolvedUrl);
        }

        public static void detect(final String url, final String userAgent, final String referer, final Callback callback) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String currentUrl = url;
                    String mimeType = null;
                    java.net.HttpURLConnection conn = null;
                    try {
                        // 1. URL pattern check (fast path)
                        String lowercaseUrl = url.toLowerCase();
                        if (lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains("m3u8") || lowercaseUrl.contains("hls")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8;
                        } else if (lowercaseUrl.contains(".mpd") || lowercaseUrl.contains("mpd") || lowercaseUrl.contains("dash")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_MPD;
                        } else if (lowercaseUrl.contains(".ism") || lowercaseUrl.contains("smoothstreaming") || lowercaseUrl.contains("manifest")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_SS;
                        } else if (lowercaseUrl.contains(".ts") || lowercaseUrl.contains("mpegts") || lowercaseUrl.contains("/ts/") || lowercaseUrl.endsWith("ts")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T;
                        }

                        if (mimeType != null) {
                            final String finalMimeType = mimeType;
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResult(finalMimeType, url);
                                }
                            });
                            return;
                        }

                        // 2. HTTP headers check (slow path)
                        int redirects = 0;
                        while (redirects < 5) {
                            java.net.URL javaUrl = new java.net.URL(currentUrl);
                            conn = (java.net.HttpURLConnection) javaUrl.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(4000);
                            conn.setReadTimeout(4000);
                            conn.setInstanceFollowRedirects(false);

                            if (userAgent != null && !userAgent.isEmpty()) {
                                conn.setRequestProperty("User-Agent", userAgent);
                            } else {
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36");
                            }
                            if (referer != null && !referer.isEmpty()) {
                                conn.setRequestProperty("Referer", referer);
                            }

                            int status = conn.getResponseCode();
                            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                                status == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                                status == 307 || status == 308) {
                                String newUrl = conn.getHeaderField("Location");
                                if (newUrl != null) {
                                    currentUrl = newUrl;
                                    redirects++;
                                    conn.disconnect();
                                    continue;
                                }
                            }
                            
                            mimeType = conn.getContentType();
                            break;
                        }
                    } catch (Throwable e) {
                        android.util.Log.e("StreamFormatDetector", "Error probing stream: " + e.getMessage());
                    } finally {
                        if (conn != null) {
                            try { conn.disconnect(); } catch (Exception e) {}
                        }
                    }

                    if (mimeType == null || mimeType.contains("octet-stream") || mimeType.contains("text/html")) {
                        String lowercaseUrl = currentUrl.toLowerCase();
                        if (lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains("m3u8") || lowercaseUrl.contains("hls") || lowercaseUrl.contains("format=m3u8")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8;
                        } else if (lowercaseUrl.contains(".mpd") || lowercaseUrl.contains("mpd") || lowercaseUrl.contains("dash") || lowercaseUrl.contains("format=mpd")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_MPD;
                        } else if (lowercaseUrl.contains(".ism") || lowercaseUrl.contains("smoothstreaming") || lowercaseUrl.contains("manifest")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_SS;
                        } else if (lowercaseUrl.contains(".ts") || lowercaseUrl.contains("mpegts") || lowercaseUrl.contains("/ts/") || lowercaseUrl.endsWith("ts") || lowercaseUrl.contains("format=ts")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T;
                        } else if (lowercaseUrl.contains("/live/") || lowercaseUrl.contains("get.php")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T;
                        } else {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8; // default Live fallback
                        }
                    } else {
                        String mimeTypeLower = mimeType.toLowerCase();
                        if (mimeTypeLower.contains("mpegurl") || mimeTypeLower.contains("m3u8") || mimeTypeLower.contains("x-mpegurl")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8;
                        } else if (mimeTypeLower.contains("dash") || mimeTypeLower.contains("mpd")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_MPD;
                        } else if (mimeTypeLower.contains("smoothstreaming") || mimeTypeLower.contains("ms-sstr")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_SS;
                        } else if (mimeTypeLower.contains("mp2t") || mimeTypeLower.contains("mpegts") || mimeTypeLower.contains("video/ts")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP2T;
                        } else if (mimeTypeLower.contains("video/mp4")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;
                        } else if (mimeTypeLower.contains("video/webm")) {
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.VIDEO_WEBM;
                        }
                    }

                    final String finalMimeType = mimeType;
                    final String finalUrl = currentUrl;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(finalMimeType, finalUrl);
                        }
                    });
                }
            });
        }
    }

    class CatAdapter extends RecyclerView.Adapter<CatAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount; View root;
            VH(View v) {
                super(v); root = v;
                tvName = (TextView) v.findViewWithTag("name");
                tvCount = (TextView) v.findViewWithTag("count");
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            float dp = getResources().getDisplayMetrics().density;
            LinearLayout container = new LinearLayout(LiveActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = new LinearLayout(LiveActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(dp, 16), px(dp, 10), px(dp, 16), px(dp, 10));
            TextView tvN = new TextView(LiveActivity.this);
            tvN.setTag("name"); tvN.setTextColor(Color.WHITE); tvN.setTextSize(11);
            row.addView(tvN, new LinearLayout.LayoutParams(0, -2, 1));
            TextView tvC = new TextView(LiveActivity.this);
            tvC.setTag("count"); tvC.setTextColor(Color.parseColor(BLUE_ACTIVE)); tvC.setTextSize(11);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
            countLp.leftMargin = px(dp, 10);
            tvC.setLayoutParams(countLp);
            row.addView(tvC);
            container.addView(row);
            
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    // Focus highlight is handled automatically by TvUtil. D-pad scrolling is now 100% smooth.
                }
            });
            TvUtil.applyTvFocusHighlight(row, 0.0f);
            
            View div = new View(LiveActivity.this);
            div.setBackgroundColor(Color.parseColor("#222222"));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, 1);
            divLp.topMargin = px(dp, 2);
            container.addView(div, divLp);
            return new VH(container);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            final CategoryItem cat = categories.get(pos);
            h.tvName.setText(TvUtil.formatNameByLanguage(cat.name)); h.tvCount.setText(String.valueOf(cat.count));
            if (!cat.name.equals(CAT_ALL) && !cat.name.equals(CAT_FAV) && !cat.name.equals(CAT_RECENT)) {
                TvUtil.alignTextByLanguage(h.tvName, cat.name);
            } else {
                h.tvName.setGravity(android.view.Gravity.RIGHT);
            }
            boolean sel = cat.name.equals(selectedCategory);
            View row = ((ViewGroup)h.root).getChildAt(0);
            row.setBackgroundColor(sel ? Color.parseColor(BLUE_TRANS) : Color.TRANSPARENT);
            h.tvName.setTextColor(sel ? Color.parseColor(BLUE_ACTIVE) : Color.WHITE);
            h.tvName.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { 
                    selectedCategory = cat.name; 
                    getSharedPreferences("LivePrefs", MODE_PRIVATE).edit().putString("last_selected_category", cat.name).apply();
                    notifyDataSetChanged(); 
                    refreshChannels();
                    focusSelectedCategory();
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (rvChannels != null && displayedChannels.size() > 0) {
                                rvChannels.scrollToPosition(0);
                                rvChannels.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (rvChannels == null) return;
                                        RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(0);
                                        if (holder != null && holder.itemView instanceof ViewGroup) {
                                            View chanRow = ((ViewGroup) holder.itemView).getChildAt(0);
                                            if (chanRow != null) {
                                                chanRow.requestFocus();
                                            }
                                        } else {
                                            View first = rvChannels.getChildAt(0);
                                            if (first != null && first instanceof ViewGroup) {
                                                View chanRow = ((ViewGroup) first).getChildAt(0);
                                                if (chanRow != null) chanRow.requestFocus();
                                            }
                                        }
                                    }
                                }, 50);
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }
        @Override public int getItemCount() { return categories.size(); }
    }

    class ChanAdapter extends RecyclerView.Adapter<ChanAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvNum, tvName; ImageView ivLogo; View root;
            VH(View v) {
                super(v); root = v;
                tvNum = (TextView) v.findViewWithTag("num");
                tvName = (TextView) v.findViewWithTag("name");
                ivLogo = (ImageView) v.findViewWithTag("logo");
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            float dp = getResources().getDisplayMetrics().density;
            LinearLayout container = new LinearLayout(LiveActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = new LinearLayout(LiveActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(dp, 16), px(dp, 8), px(dp, 16), px(dp, 8));
            TextView tvN = new TextView(LiveActivity.this);
            tvN.setTag("num"); tvN.setTextColor(Color.WHITE); tvN.setTextSize(11);
            tvN.setMinWidth(px(dp, 35));
            row.addView(tvN);
            ImageView iv = new ImageView(LiveActivity.this);
            iv.setTag("logo");
            int sz = px(dp, 20);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(sz, sz);
            ivLp.rightMargin = px(dp, 12);
            iv.setLayoutParams(ivLp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(iv);
            TextView tvNm = new TextView(LiveActivity.this);
            tvNm.setTag("name"); tvNm.setTextColor(Color.WHITE); tvNm.setTextSize(11);
            row.addView(tvNm, new LinearLayout.LayoutParams(0, -2, 1));
            container.addView(row);
            TvUtil.applyTvFocusHighlight(row, 0.0f);
            View div = new View(LiveActivity.this);
            div.setBackgroundColor(Color.parseColor("#222222"));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, 1);
            divLp.topMargin = px(dp, 2);
            container.addView(div, divLp);
            return new VH(container);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            if (pos >= displayedChannels.size()) return;
            final ChannelItem ch = displayedChannels.get(pos);
            h.tvNum.setText(String.valueOf(ch.num)); h.tvName.setText(TvUtil.formatNameByLanguage(ch.name));
            TvUtil.alignTextByLanguage(h.tvName, ch.name);
            if (ch.logo != null && !ch.logo.isEmpty()) {
                TvUtil.loadImage(h.ivLogo, ch.logo);
            } else {
                h.ivLogo.setImageDrawable(null);
            }
            boolean sel = currentChannel != null && currentChannel.name.equals(ch.name);
            View row = ((ViewGroup)h.root).getChildAt(0);
            row.setBackgroundColor(sel ? Color.parseColor(BLUE_TRANS) : Color.TRANSPARENT);
            h.tvName.setTextColor(sel ? Color.parseColor(BLUE_ACTIVE) : Color.WHITE);
            h.tvName.setTypeface(null, Typeface.BOLD);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { 
                    if (currentChannel != null && currentChannel.name.equals(ch.name)) {
                        float dp = getResources().getDisplayMetrics().density;
                        enterFullscreenPlayer(dp);
                    } else {
                        playChannel(ch); 
                    }
                    v.requestFocus(); 
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                            focusSelectedCategory();
                            return true;
                        } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (clickOverlay != null) {
                                clickOverlay.requestFocus();
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }
        @Override public int getItemCount() { return displayedChannels.size(); }

        @Override public void onViewRecycled(VH h) {
            super.onViewRecycled(h);
            TvUtil.cancelLoad(h.ivLogo);
        }
    }

    private void refreshChannels() {
        filteredChannels.clear();
        if (selectedCategory.equals(CAT_ALL)) filteredChannels.addAll(allChannels);
        else if (selectedCategory.equals(CAT_FAV)) filteredChannels.addAll(favoriteChannels);
        else if (selectedCategory.equals(CAT_RECENT)) filteredChannels.addAll(recentChannels);
        else {
            for (ChannelItem ch : allChannels) {
                if (ch.category.equals(selectedCategory)) filteredChannels.add(ch);
            }
        }
        displayedChannels.clear();
        displayedChannels.addAll(filteredChannels);
        if (rvChannels != null) {
            rvChannels.getAdapter().notifyDataSetChanged();
            rvChannels.scrollToPosition(0);
        }
    }

    private final Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    private void setupSearchListener(EditText search) {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(final CharSequence s, int start, int before, int count) { 
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        filterChannelsInBackground(s.toString());
                    }
                };
                searchHandler.postDelayed(searchRunnable, 400); // 400ms debounce
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterChannelsInBackground(final String q) {
        final List<ChannelItem> tempFiltered = new ArrayList<>();
        List<ChannelItem> sourceList = allChannels;
        for (ChannelItem ch : sourceList) {
            boolean catOk = selectedCategory.equals(CAT_ALL) || ch.category.equals(selectedCategory) || selectedCategory.equals(CAT_FAV) || selectedCategory.equals(CAT_RECENT);
            boolean nameOk = q.isEmpty() || ch.name.toLowerCase().contains(q.toLowerCase());
            if (catOk && nameOk) tempFiltered.add(ch);
        }
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                filteredChannels = tempFiltered;
                displayedChannels.clear();
                displayedChannels.addAll(filteredChannels);
                if (rvChannels != null && rvChannels.getAdapter() != null) {
                    rvChannels.getAdapter().notifyDataSetChanged();
                }
            }
        });
    }

    private void filterChannels(String q) {
        filterChannelsInBackground(q);
    }

    private void saveRecentChannels() {
        android.content.SharedPreferences sp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (ChannelItem ch : recentChannels) {
            names.add(ch.name);
        }
        sp.edit().putString("recent_names", new com.google.gson.Gson().toJson(names)).apply();
    }

    private void loadRecentChannels() {
        recentChannels.clear();
        android.content.SharedPreferences sp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        String json = sp.getString("recent_names", "[]");
        java.util.ArrayList<String> names = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.ArrayList<String>>(){}.getType());
        if (names != null) {
            for (String name : names) {
                for (ChannelItem ch : allChannels) {
                    if (ch.name.equals(name)) {
                        recentChannels.add(ch);
                        break;
                    }
                }
            }
        }
    }

    private void autoPlayLastChannel() {
        android.content.SharedPreferences sp = getSharedPreferences("LivePrefs", MODE_PRIVATE);
        String lastChanName = sp.getString("last_channel_name", "");
        String lastCatName = sp.getString("last_category_name", "");

        ChannelItem targetChannel = null;
        if (!lastChanName.isEmpty() && !lastCatName.isEmpty()) {
            for (ChannelItem ch : allChannels) {
                if (ch.name.equals(lastChanName)) {
                    targetChannel = ch;
                    break;
                }
            }
        }

        if (targetChannel == null && !allChannels.isEmpty()) {
            targetChannel = allChannels.get(0);
        }

        if (targetChannel != null) {
            final ChannelItem finalChan = targetChannel;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    playChannel(finalChan);
                }
            }, 500);
        }
    }

    private void launchExternalPlayer(android.content.Context context, String url, String title, String userAgent, String referer, int playerType) {
        try {
            android.net.Uri uri = android.net.Uri.parse(url.trim());
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            if (title != null && !title.isEmpty()) {
                intent.putExtra("title", title);
                intent.putExtra("title_name", title);
            }

            android.os.Bundle headers = new android.os.Bundle();
            if (userAgent != null && !userAgent.isEmpty()) {
                headers.putString("User-Agent", userAgent);
                intent.putExtra("http-user-agent", userAgent);
            } else {
                headers.putString("User-Agent", "VLC/3.0.18 LibVLC/3.0.18");
                intent.putExtra("http-user-agent", "VLC/3.0.18 LibVLC/3.0.18");
            }
            if (referer != null && !referer.isEmpty()) {
                headers.putString("Referer", referer);
            }
            intent.putExtra("headers", headers);
            intent.putExtra("android.media.intent.extra.HTTP_HEADERS", headers);

            if (playerType == 1) { // VLC Player
                intent.setPackage("org.videolan.vlc");
            } else if (playerType == 2) { // MX Player
                try {
                    context.getPackageManager().getPackageInfo("com.mxtech.videoplayer.ad", 0);
                    intent.setPackage("com.mxtech.videoplayer.ad");
                } catch (Exception e) {
                    try {
                        context.getPackageManager().getPackageInfo("com.mxtech.videoplayer.pro", 0);
                        intent.setPackage("com.mxtech.videoplayer.pro");
                    } catch (Exception e2) {
                        // ignore and let it use chooser
                    }
                }
            }

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                android.content.Intent chooser = android.content.Intent.createChooser(intent, "اختر مشغل الفيديو");
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "خطأ في تشغيل المشغل الخارجي: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private int px(float dp, int val) { return (int)(dp * val); }

    static class CategoryItem {
        String name; int count;
        CategoryItem(String n, int c) { name = n; count = c; }
    }
    static class ChannelItem {
        int num; String name, logo, category, url;
        ChannelItem(int nu, String nm, String lg, String cat, String u) {
            num = nu; name = nm; logo = lg; category = cat; url = u;
        }
    }    private void showQuickChannelSelector(final float dp) {
        if (categories == null || categories.isEmpty()) {
            Toast.makeText(this, "قائمة التصنيفات غير متوفرة", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x88000000); // Sleek darkened overlay
        root.setFocusable(false);
        
        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        
        // Premium glassmorphic obsidian/blue receiver drawer background
        GradientDrawable drawerBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { Color.parseColor("#F90A0B0E"), Color.parseColor("#F2121319") }
        );
        float r = 28 * dp;
        drawerBg.setCornerRadii(new float[]{ 0, 0, r, r, r, r, 0, 0 }); // round right corners
        drawer.setBackground(drawerBg);
        
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams((int)(430 * dp), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        drawer.setLayoutParams(drawerLp);
        drawer.setPadding((int)(16 * dp), (int)(24 * dp), (int)(16 * dp), (int)(24 * dp));
        drawer.setFocusable(false);
        
        // GORGEOUS SATELLITE/TV HEADER WITH CLOCK
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding((int)(8 * dp), 0, (int)(8 * dp), (int)(12 * dp));
        headerLayout.setFocusable(false);
        
        FrameLayout iconContainer = new FrameLayout(this);
        int containerSize = (int)(38 * dp);
        LinearLayout.LayoutParams iconContainerLp = new LinearLayout.LayoutParams(containerSize, containerSize);
        iconContainerLp.rightMargin = (int)(10 * dp);
        iconContainer.setLayoutParams(iconContainerLp);
        
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#1C00E5FF")); // subtle cyber cyan tint
        iconBg.setCornerRadius(19 * dp);
        iconBg.setStroke((int)(1.5f * dp), Color.parseColor("#3300E5FF"));
        iconContainer.setBackground(iconBg);
        
        ImageView headerIcon = new ImageView(this);
        int selectorIconId = getResources().getIdentifier("ic_settings_player", "drawable", getPackageName());
        if (selectorIconId == 0) selectorIconId = getResources().getIdentifier("ic_settings_playlist", "drawable", getPackageName());
        if (selectorIconId != 0) {
            headerIcon.setImageResource(selectorIconId);
        } else {
            headerIcon.setImageResource(R.drawable.home_logo);
        }
        headerIcon.setColorFilter(Color.parseColor("#00E5FF"));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams((int)(20 * dp), (int)(20 * dp), Gravity.CENTER);
        iconContainer.addView(headerIcon, iconLp);
        
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setFocusable(false);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        textContainer.setLayoutParams(textLp);
        
        TextView titleTv = new TextView(this);
        titleTv.setText(TvUtil.translate(this, "دليل القنوات"));
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(19);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        
        TextView subtitleTv = new TextView(this);
        subtitleTv.setText(TvUtil.translate(this, "التنقل السريع بين قنوات البث المباشر"));
        subtitleTv.setTextColor(Color.parseColor("#7E828C"));
        subtitleTv.setTextSize(10.5f);
        subtitleTv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        
        textContainer.addView(titleTv);
        textContainer.addView(subtitleTv);
        
        TextView clockTv = new TextView(this);
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH);
            clockTv.setText(sdf.format(new java.util.Date()));
        } catch (Exception e) {
            clockTv.setText("");
        }
        clockTv.setTextColor(Color.parseColor("#00E5FF"));
        clockTv.setTextSize(13);
        clockTv.setTypeface(Typeface.create("sans-serif-thin", Typeface.BOLD));
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(-2, -2);
        clockLp.leftMargin = (int)(8 * dp);
        
        headerLayout.addView(iconContainer);
        headerLayout.addView(textContainer);
        headerLayout.addView(clockTv, clockLp);
        
        drawer.addView(headerLayout);
        
        // Sleek horizontal divider
        View headerDivider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, (int)(1.5f * dp));
        divLp.topMargin = (int)(4 * dp);
        divLp.bottomMargin = (int)(16 * dp);
        headerDivider.setLayoutParams(divLp);
        headerDivider.setBackgroundColor(Color.parseColor("#15FFFFFF"));
        drawer.addView(headerDivider);

        LinearLayout listsContainer = new LinearLayout(this);
        listsContainer.setOrientation(LinearLayout.HORIZONTAL);
        listsContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        listsContainer.setFocusable(false);
        
        final RecyclerView rvCats = new RecyclerView(this);
        rvCats.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams catsLp = new LinearLayout.LayoutParams((int)(140 * dp), -1);
        rvCats.setLayoutParams(catsLp);
        
        View divider = new View(this);
        LinearLayout.LayoutParams vDivLp = new LinearLayout.LayoutParams((int)(1.5f * dp), -1);
        vDivLp.leftMargin = (int)(8 * dp);
        vDivLp.rightMargin = (int)(8 * dp);
        divider.setLayoutParams(vDivLp);
        divider.setBackgroundColor(Color.parseColor("#15FFFFFF"));
        
        final RecyclerView rvChans = new RecyclerView(this);
        rvChans.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams chansLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        chansLp.leftMargin = (int)(10 * dp);
        rvChans.setLayoutParams(chansLp);
        
        listsContainer.addView(rvCats);
        listsContainer.addView(divider);
        listsContainer.addView(rvChans);
        drawer.addView(listsContainer, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        root.addView(drawer);
        d.setContentView(root);
        
        final List<ChannelItem> menuChannelsList = new ArrayList<>();
        final QuickMenuChanAdapter chanAdapter = new QuickMenuChanAdapter(menuChannelsList, dp, d);
        rvChans.setAdapter(chanAdapter);
        
        int targetCatIdx = 0;
        int targetChanIdx = -1;
        String currentCatName = CAT_ALL;
        
        if (currentChannel != null) {
            currentCatName = currentChannel.category;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).name.equals(currentCatName)) {
                    targetCatIdx = i;
                    break;
                }
            }
        }
        
        menuChannelsList.clear();
        if (currentCatName.equals(CAT_ALL)) {
            if (allChannels != null) menuChannelsList.addAll(allChannels);
        } else if (currentCatName.equals(CAT_FAV)) {
            if (favoriteChannels != null) menuChannelsList.addAll(favoriteChannels);
        } else if (currentCatName.equals(CAT_RECENT)) {
            if (recentChannels != null) menuChannelsList.addAll(recentChannels);
        } else {
            if (allChannels != null) {
                for (ChannelItem ch : allChannels) {
                    if (ch.category != null && ch.category.equals(currentCatName)) {
                        menuChannelsList.add(ch);
                    }
                }
            }
        }
        
        if (currentChannel != null) {
            for (int i = 0; i < menuChannelsList.size(); i++) {
                if (menuChannelsList.get(i).name.equals(currentChannel.name)) {
                    targetChanIdx = i;
                    break;
                }
            }
        }
        
        chanAdapter.selectedPos = targetChanIdx;
        chanAdapter.notifyDataSetChanged();
        
        final QuickMenuCatAdapter catAdapter = new QuickMenuCatAdapter(categories, dp, new QuickMenuCatAdapter.OnCategoryFocusedListener() {
            @Override
            public void onCategoryFocused(String categoryName) {
                menuChannelsList.clear();
                if (categoryName.equals(CAT_ALL)) {
                    if (allChannels != null) menuChannelsList.addAll(allChannels);
                } else if (categoryName.equals(CAT_FAV)) {
                    if (favoriteChannels != null) menuChannelsList.addAll(favoriteChannels);
                } else if (categoryName.equals(CAT_RECENT)) {
                    if (recentChannels != null) menuChannelsList.addAll(recentChannels);
                } else {
                    if (allChannels != null) {
                        for (ChannelItem ch : allChannels) {
                            if (ch.category != null && ch.category.equals(categoryName)) {
                                menuChannelsList.add(ch);
                            }
                        }
                    }
                }
                
                if (currentChannel != null && categoryName.equals(currentChannel.category)) {
                    int idx = -1;
                    for (int i = 0; i < menuChannelsList.size(); i++) {
                        if (menuChannelsList.get(i).name.equals(currentChannel.name)) {
                            idx = i;
                            break;
                        }
                    }
                    chanAdapter.selectedPos = idx;
                } else {
                    chanAdapter.selectedPos = -1;
                }
                
                chanAdapter.notifyDataSetChanged();
                rvChans.scrollToPosition(0);
            }
        });
        catAdapter.selectedPos = targetCatIdx;
        rvCats.setAdapter(catAdapter);
        
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        drawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });

        if (d.getWindow() != null) {
            d.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }

        d.show();

        final int finalCatIdx = targetCatIdx;
        final int finalChanIdx = targetChanIdx;
        rvCats.scrollToPosition(finalCatIdx);
        
        if (finalChanIdx != -1) {
            rvChans.scrollToPosition(finalChanIdx);
            rvChans.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.ViewHolder vh = rvChans.findViewHolderForAdapterPosition(finalChanIdx);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    } else {
                        rvChans.requestFocus();
                    }
                }
            }, 180);
        } else {
            rvCats.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.ViewHolder vh = rvCats.findViewHolderForAdapterPosition(finalCatIdx);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    } else {
                        rvCats.requestFocus();
                    }
                }
            }, 180);
        }
    }

    private static class QuickMenuCatAdapter extends RecyclerView.Adapter<QuickMenuCatAdapter.VH> {
        interface OnCategoryFocusedListener {
            void onCategoryFocused(String categoryName);
        }
        
        private final List<CategoryItem> items;
        private final float dp;
        private final OnCategoryFocusedListener listener;
        public int selectedPos = 0;
        
        QuickMenuCatAdapter(List<CategoryItem> items, float dp, OnCategoryFocusedListener listener) {
            this.items = items;
            this.dp = dp;
            this.listener = listener;
        }
        
        static class VH extends RecyclerView.ViewHolder {
            LinearLayout layout;
            View indicator;
            TextView tvName;
            TextView tvBadge;
            
            VH(View v) {
                super(v);
                layout = (LinearLayout) v;
                indicator = v.findViewWithTag("indicator");
                tvName = (TextView) v.findViewWithTag("name");
                tvBadge = (TextView) v.findViewWithTag("badge");
            }
        }
        
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(8 * dp), (int)(10 * dp), (int)(8 * dp), (int)(10 * dp));
            row.setFocusable(true);
            
            View ind = new View(parent.getContext());
            LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams((int)(3.5f * dp), (int)(18 * dp));
            ind.setLayoutParams(indLp);
            GradientDrawable indGd = new GradientDrawable();
            indGd.setColor(Color.parseColor("#FF00E5FF")); // cyan indicator
            indGd.setCornerRadius(2 * dp);
            ind.setBackground(indGd);
            ind.setTag("indicator");
            
            TextView tv = new TextView(parent.getContext());
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            tvLp.leftMargin = (int)(6 * dp);
            tv.setLayoutParams(tvLp);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(13);
            tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tv.setTag("name");
            
            TextView badge = new TextView(parent.getContext());
            badge.setPadding((int)(6 * dp), (int)(2 * dp), (int)(6 * dp), (int)(2 * dp));
            badge.setTextSize(9.5f);
            badge.setTag("badge");
            
            row.addView(ind);
            row.addView(tv);
            row.addView(badge);
            
            TvUtil.applyTvFocusHighlight(row, 8.0f);
            return new VH(row);
        }
        
        @Override
        public void onBindViewHolder(final VH holder, final int position) {
            final CategoryItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvBadge.setText(String.valueOf(item.count));
            
            boolean isActive = (position == selectedPos);
            
            if (isActive) {
                holder.indicator.setVisibility(View.VISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#FF00E5FF"));
                holder.tvName.setTypeface(null, Typeface.BOLD);
                
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setColor(Color.parseColor("#1A00E5FF")); // subtle cyan glass
                activeBg.setCornerRadius(8 * dp);
                holder.layout.setBackground(activeBg);
                
                holder.tvBadge.setTextColor(Color.parseColor("#FF00E5FF"));
                GradientDrawable badgeActiveBg = new GradientDrawable();
                badgeActiveBg.setColor(Color.parseColor("#2500E5FF"));
                badgeActiveBg.setCornerRadius(10 * dp);
                holder.tvBadge.setBackground(badgeActiveBg);
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#D0D0D5"));
                holder.tvName.setTypeface(null, Typeface.NORMAL);
                holder.layout.setBackground(null);
                
                holder.tvBadge.setTextColor(Color.parseColor("#8E919C"));
                GradientDrawable badgeNormalBg = new GradientDrawable();
                badgeNormalBg.setColor(Color.parseColor("#15FFFFFF"));
                badgeNormalBg.setCornerRadius(10 * dp);
                holder.tvBadge.setBackground(badgeNormalBg);
            }
            
            holder.layout.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        selectedPos = position;
                        notifyDataSetChanged();
                        if (listener != null) {
                            listener.onCategoryFocused(item.name);
                        }
                    }
                }
            });
            
            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedPos = position;
                    notifyDataSetChanged();
                    if (listener != null) {
                        listener.onCategoryFocused(item.name);
                    }
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class QuickMenuChanAdapter extends RecyclerView.Adapter<QuickMenuChanAdapter.VH> {
        private final List<ChannelItem> items;
        private final float dp;
        private final Dialog dialog;
        public int selectedPos = -1;
        
        QuickMenuChanAdapter(List<ChannelItem> items, float dp, Dialog dialog) {
            this.items = items;
            this.dp = dp;
            this.dialog = dialog;
        }
        
        class VH extends RecyclerView.ViewHolder {
            LinearLayout layout;
            View indicator;
            TextView tvNum;
            ImageView ivLogo;
            TextView tvName;
            
            VH(View v) {
                super(v);
                layout = (LinearLayout) v;
                indicator = v.findViewWithTag("indicator");
                tvNum = (TextView) v.findViewWithTag("num");
                ivLogo = (ImageView) v.findViewWithTag("logo");
                tvName = (TextView) v.findViewWithTag("name");
            }
        }
        
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            row.setPadding((int)(8 * dp), (int)(8 * dp), (int)(8 * dp), (int)(8 * dp));
            row.setFocusable(true);
            
            View ind = new View(parent.getContext());
            LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams((int)(3.5f * dp), (int)(22 * dp));
            indLp.rightMargin = (int)(6 * dp);
            ind.setLayoutParams(indLp);
            GradientDrawable indGd = new GradientDrawable();
            indGd.setColor(Color.parseColor("#FF00E5FF")); // active channel color
            indGd.setCornerRadius(2 * dp);
            ind.setBackground(indGd);
            ind.setTag("indicator");
            
            TextView tvNum = new TextView(parent.getContext());
            tvNum.setTag("num");
            tvNum.setTextSize(11);
            tvNum.setTextColor(Color.parseColor("#6B6D7A"));
            tvNum.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(-2, -2);
            numLp.rightMargin = (int)(6 * dp);
            tvNum.setLayoutParams(numLp);
            
            FrameLayout imgContainer = new FrameLayout(parent.getContext());
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams((int)(36 * dp), (int)(36 * dp));
            imgLp.rightMargin = (int)(8 * dp);
            imgContainer.setLayoutParams(imgLp);
            GradientDrawable imgBg = new GradientDrawable();
            imgBg.setColor(Color.parseColor("#12FFFFFF")); // elegant circular logo border
            imgBg.setCornerRadius(18 * dp);
            imgBg.setStroke((int)(1 * dp), Color.parseColor("#1BFFFFFF"));
            imgContainer.setBackground(imgBg);
            imgContainer.setPadding((int)(4 * dp), (int)(4 * dp), (int)(4 * dp), (int)(4 * dp));
            
            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setTag("logo");
            imgContainer.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            
            TextView tv = new TextView(parent.getContext());
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            tv.setLayoutParams(tvLp);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(13.5f);
            tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            tv.setTag("name");
            
            row.addView(ind);
            row.addView(tvNum);
            row.addView(imgContainer);
            row.addView(tv);
            
            TvUtil.applyTvFocusHighlight(row, 8.0f);
            return new VH(row);
        }
        
        @Override
        public void onBindViewHolder(final VH holder, final int position) {
            final ChannelItem ch = items.get(position);
            holder.tvName.setText(TvUtil.formatNameByLanguage(ch.name));
            
            if (holder.tvNum != null) {
                holder.tvNum.setText(String.format(java.util.Locale.US, "%02d", position + 1));
            }
            
            if (ch.logo != null && !ch.logo.isEmpty()) {
                TvUtil.loadImage(holder.ivLogo, ch.logo);
            } else {
                holder.ivLogo.setImageResource(R.drawable.home_logo);
            }
            
            boolean isActive = (position == selectedPos);
            if (isActive) {
                holder.indicator.setVisibility(View.VISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#FF00E5FF"));
                holder.tvName.setTypeface(null, Typeface.BOLD);
                if (holder.tvNum != null) {
                    holder.tvNum.setTextColor(Color.parseColor("#FF00E5FF"));
                }
                
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setColor(Color.parseColor("#1A00E5FF"));
                activeBg.setCornerRadius(8 * dp);
                holder.layout.setBackground(activeBg);
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvName.setTypeface(null, Typeface.NORMAL);
                if (holder.tvNum != null) {
                    holder.tvNum.setTextColor(Color.parseColor("#6B6D7A"));
                }
                holder.layout.setBackground(null);
            }
            
            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playChannel(ch);
                    dialog.dismiss();
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}
