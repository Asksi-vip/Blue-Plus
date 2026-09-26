package com.blue.plus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

public class PlayerActivity extends Activity {

    public String playbackSessionId = "";
    
    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }
    public long getDuration() {
        return exoPlayer != null ? exoPlayer.getDuration() : 0;
    }
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.getPlayWhenReady() && exoPlayer.getPlaybackState() == Player.STATE_READY;
    }
    public String getVideoUrl() {
        return videoUrl;
    }
    public String getVideoTitle() {
        return title;
    }

    private FrameLayout castOverlayLayout;
    private TextView tvCastDeviceName;
    private TextView tvCastTitle;
    private android.widget.SeekBar castSeekBar;
    private TextView tvCastPosition;
    private TextView tvCastDuration;
    private ImageView btnCastPlayPause;
    private ImageView btnCastRew, btnCastFfwd;
    private android.widget.Button btnDisconnectCast;
    private boolean isUserSeekingCast = false;
    private long lastReceivedCastPosition = 0;
    private long lastReceivedCastDuration = 0;
    private boolean isTvPlaying = false;

    private StyledPlayerView playerView;
    private ExoPlayer exoPlayer;
    private String videoUrl, title;
    private String userAgent, referer;
    private int retryCount = 0;
    private String attemptedMimeType = null;
    private static final int MAX_RETRIES = 5;
    private final Handler reconnectHandler = new Handler();
    private boolean isReconnecting = false;
    private static com.google.android.exoplayer2.upstream.cache.SimpleCache simpleCache;
    private static synchronized com.google.android.exoplayer2.upstream.cache.SimpleCache getSimpleCache(android.content.Context context) {
        if (simpleCache == null) {
            java.io.File baseDir = context.getExternalCacheDir();
            if (baseDir == null) {
                baseDir = context.getCacheDir();
            }
            java.io.File cacheDir = new java.io.File(baseDir, "media_cache");
            com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor evictor = 
                new com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024); // 250MB cache size
            com.google.android.exoplayer2.database.StandaloneDatabaseProvider databaseProvider = 
                new com.google.android.exoplayer2.database.StandaloneDatabaseProvider(context);
            simpleCache = new com.google.android.exoplayer2.upstream.cache.SimpleCache(cacheDir, evictor, databaseProvider);
        }
        return simpleCache;
    }

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
                        android.util.Log.d("PlayerWatchdog", "Buffering watchdog fired, retrying VOD...");
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
                                android.util.Log.d("PlayerWatchdog", "Video freeze detected (stuck at " + currentRenderedFrames + " frames), retrying VOD...");
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
                } else {
                    bufferingDurationSeconds = 0;
                    videoFrozenSeconds = 0;
                    lastRenderedFrames = -1;
                }
            }
            watchdogHandler.postDelayed(this, 1000);
        }
    };

    private android.content.BroadcastReceiver networkReceiver = new android.content.BroadcastReceiver() {
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
    private final Handler progressSaveHandler = new Handler();
    private final Runnable progressSaveRunnable = new Runnable() {
        @Override
        public void run() {
            savePlaybackPosition();
            progressSaveHandler.postDelayed(this, 5000);
        }
    };

    private LinearLayout leftTapIndicator;
    private LinearLayout rightTapIndicator;
    private TextView tvLeftTapSec;
    private TextView tvRightTapSec;
    private TextView tvSpeed2xBadge;
    private ImageView localSelectorTemp;

    // Premium Live-like Player Customizations
    private boolean isLocked = false;
    private boolean isControllerVisible = true;
    private int currentAspectIndex = 0;
    private android.media.AudioManager audioManager;
    private int maxVolume;
    private FrameLayout fsLeftSliderContainer;
    private FrameLayout fsRightSliderContainer;
    private android.widget.SeekBar fsLeftSlider;
    private android.widget.SeekBar fsRightSlider;
    private ImageView btnFsLock;
    private View btnPlay, btnPause;
    private View btnBack;
    private TextView tvPlayerTitle;
    private DefaultTrackSelector trackSelector;
    private View btnFs;
    private LinearLayout seekOverlay;
    private android.widget.SeekBar seekProgress;
    private TextView tvSeekTime;
    private TextView tvSeekDuration;
    private boolean isSeekingMode = false;
    private boolean showedControlsOnDown = false;
    private long tempSeekPosition = 0;
    private Handler autoHideHandler = new Handler();
    private Runnable autoHideRunnable;
    private Handler seekHideHandler = new Handler();
    private Runnable seekHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (isSeekingMode) {
                isSeekingMode = false;
                if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);
                if (exoPlayer != null) {
                    exoPlayer.seekTo(tempSeekPosition);
                    exoPlayer.setPlayWhenReady(true);
                }
                if (playerView != null) {
                    playerView.showController();
                }
            }
        }
    };

    private void resetSeekAutoHideTimer() {
        seekHideHandler.removeCallbacks(seekHideRunnable);
        seekHideHandler.postDelayed(seekHideRunnable, 4000);
    }

    private String formatTime(long ms) {
        if (ms < 0) return "00:00";
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hrs = totalSec / 3600;
        if (hrs > 0) {
            return String.format("%02d:%02d:%02d", hrs, min, sec);
        } else {
            return String.format("%02d:%02d", min, sec);
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playbackSessionId = String.valueOf(System.currentTimeMillis());
        CastManager.loadCastingState(this);
        try {
            java.net.CookieManager cookieManager = new java.net.CookieManager();
            cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            java.net.CookieHandler.setDefault(cookieManager);
        } catch (Exception e) {}
        
        videoUrl = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        if (title != null) {
            title = TvUtil.formatNameByLanguage(title);
        }
        userAgent = getIntent().getStringExtra("user_agent");
        referer = getIntent().getStringExtra("referer");

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "رابط البث غير صالح", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Check if external player is activated
        android.content.SharedPreferences spSettings = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean useExternal = spSettings.getBoolean("use_external", false);
        int playerType = spSettings.getInt("player_type", 0);
        if (useExternal || playerType > 0) {
            launchExternalPlayer(this, videoUrl, title, userAgent, referer, playerType);
            finish();
            return;
        }

        // Force Fullscreen and Landscape orientation
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}

        // Hide bottom navigation bar and status bar, make it sticky immersive
        hideSystemUI();

        // Enable draw under notch (Selfie Cutout) for Android 9.0+ (API 28+)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Initialize Audio Manager for gesture control
        audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);

        CastManager.activePlayer = this;
        buildUI();
        initializePlayer();
        watchdogHandler.post(watchdogRunnable);
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.setBackgroundColor(Color.BLACK);

        // Try to inflate the Live Player View (which uses custom player_controls.xml)
        int layoutId = getResources().getIdentifier("live_player_view", "layout", getPackageName());
        playerView = (layoutId != 0) ? (StyledPlayerView) getLayoutInflater().inflate(layoutId, null) : new StyledPlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(true); // smooth switching
        playerView.setUseArtwork(false);
        try {
            if (playerView.getSubtitleView() != null) {
                android.content.SharedPreferences spSettings = getSharedPreferences("Settings", MODE_PRIVATE);
                String subSize = spSettings.getString("subtitle_size", "medium");
                float sizeSp = 18f;
                if ("small".equals(subSize)) sizeSp = 14f;
                else if ("large".equals(subSize)) sizeSp = 24f;
                playerView.getSubtitleView().setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
            }
        } catch (Exception ignored) {}
        root.addView(playerView);

        final float scale = getResources().getDisplayMetrics().density;

        // ── Double-Tap seeking overlay (YouTube Style) ──
        LinearLayout doubleTapOverlay = new LinearLayout(this);
        doubleTapOverlay.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams doubleTapLp = new FrameLayout.LayoutParams(-1, -1);
        doubleTapLp.topMargin = (int)(80 * scale);
        doubleTapLp.bottomMargin = (int)(100 * scale);
        doubleTapOverlay.setLayoutParams(doubleTapLp);
        
        FrameLayout leftTap = new FrameLayout(this);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        leftTap.setLayoutParams(leftLp);
        
        final FrameLayout rightTap = new FrameLayout(this);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        rightTap.setLayoutParams(rightLp);
        
        // Left Indicator
        leftTapIndicator = new LinearLayout(this);
        leftTapIndicator.setOrientation(LinearLayout.VERTICAL);
        leftTapIndicator.setGravity(Gravity.CENTER);
        leftTapIndicator.setVisibility(View.GONE);
        
        android.graphics.drawable.GradientDrawable leftBg = new android.graphics.drawable.GradientDrawable();
        leftBg.setColor(Color.parseColor("#4D00E5FF")); // 30% neon cyan glow
        leftBg.setCornerRadii(new float[]{0, 0, 300 * scale, 300 * scale, 300 * scale, 300 * scale, 0, 0}); // semicircle facing right
        leftTapIndicator.setBackground(leftBg);
        
        tvLeftTapSec = new TextView(this);
        tvLeftTapSec.setTextColor(Color.WHITE);
        tvLeftTapSec.setTextSize(16);
        tvLeftTapSec.setGravity(Gravity.CENTER);
        tvLeftTapSec.setTypeface(null, android.graphics.Typeface.BOLD);
        leftTapIndicator.addView(tvLeftTapSec);
        
        leftTap.addView(leftTapIndicator, new FrameLayout.LayoutParams((int)(140 * scale), -1, Gravity.LEFT));
        
        // Right Indicator
        rightTapIndicator = new LinearLayout(this);
        rightTapIndicator.setOrientation(LinearLayout.VERTICAL);
        rightTapIndicator.setGravity(Gravity.CENTER);
        rightTapIndicator.setVisibility(View.GONE);
        
        android.graphics.drawable.GradientDrawable rightBg = new android.graphics.drawable.GradientDrawable();
        rightBg.setColor(Color.parseColor("#4D00E5FF"));
        rightBg.setCornerRadii(new float[]{300 * scale, 300 * scale, 0, 0, 0, 0, 300 * scale, 300 * scale}); // semicircle facing left
        rightTapIndicator.setBackground(rightBg);
        
        tvRightTapSec = new TextView(this);
        tvRightTapSec.setTextColor(Color.WHITE);
        tvRightTapSec.setTextSize(16);
        tvRightTapSec.setGravity(Gravity.CENTER);
        tvRightTapSec.setTypeface(null, android.graphics.Typeface.BOLD);
        rightTapIndicator.addView(tvRightTapSec);
        
        rightTap.addView(rightTapIndicator, new FrameLayout.LayoutParams((int)(140 * scale), -1, Gravity.RIGHT));
        
        doubleTapOverlay.addView(leftTap);
        doubleTapOverlay.addView(rightTap);
        root.addView(doubleTapOverlay);
        
        tvSpeed2xBadge = new TextView(this);
        tvSpeed2xBadge.setText("⏩ 2.0x");
        tvSpeed2xBadge.setTextColor(Color.WHITE);
        tvSpeed2xBadge.setTextSize(14);
        tvSpeed2xBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSpeed2xBadge.setPadding((int)(16 * scale), (int)(8 * scale), (int)(16 * scale), (int)(8 * scale));
        android.graphics.drawable.GradientDrawable speedBg = new android.graphics.drawable.GradientDrawable();
        speedBg.setColor(Color.parseColor("#B3000000"));
        speedBg.setCornerRadius(20 * scale);
        tvSpeed2xBadge.setBackground(speedBg);
        tvSpeed2xBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams speedLp = new FrameLayout.LayoutParams(-2, -2);
        speedLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        speedLp.topMargin = (int)(25 * scale);
        root.addView(tvSpeed2xBadge, speedLp);
        
        // Touch Listeners for Double Tap Gestures
        leftTap.setOnTouchListener(new View.OnTouchListener() {
            private long lastTapTime = 0;
            private int tapCount = 0;
            private final Runnable resetTap = new Runnable() {
                @Override
                public void run() {
                    tapCount = 0;
                    if (leftTapIndicator != null) leftTapIndicator.setVisibility(View.GONE);
                }
            };
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        handleLockTap();
                    }
                    return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    long now = System.currentTimeMillis();
                    if (now - lastTapTime < 350) {
                        tapCount++;
                        long accumulatedSeek = tapCount * 10000;
                        seekVideo(false, 10000, accumulatedSeek);
                        lastTapTime = now;
                        v.removeCallbacks(resetTap);
                        v.postDelayed(resetTap, 1000);
                    } else {
                        lastTapTime = now;
                        final long currentTapTime = now;
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (lastTapTime == currentTapTime) {
                                    toggleController();
                                }
                            }
                        }, 350);
                    }
                    return true;
                }
                return false;
            }
        });
        
        rightTap.setOnTouchListener(new View.OnTouchListener() {
            private long lastTapTime = 0;
            private int tapCount = 0;
            private boolean isHold2xActive = false;
            private Runnable singleTapRunnable = null;
            private final Handler holdHandler = new Handler();
            private final Runnable holdRunnable = new Runnable() {
                @Override
                public void run() {
                    if (exoPlayer != null && exoPlayer.isPlaying()) {
                        isHold2xActive = true;
                        if (singleTapRunnable != null) {
                            rightTap.removeCallbacks(singleTapRunnable);
                            singleTapRunnable = null;
                        }
                        exoPlayer.setPlaybackParameters(new com.google.android.exoplayer2.PlaybackParameters(2.0f, 1.0f));
                        if (tvSpeed2xBadge != null) tvSpeed2xBadge.setVisibility(View.VISIBLE);
                    }
                }
            };

            private final Runnable resetTap = new Runnable() {
                @Override
                public void run() {
                    tapCount = 0;
                    if (rightTapIndicator != null) rightTapIndicator.setVisibility(View.GONE);
                }
            };

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        handleLockTap();
                    }
                    return true;
                }

                int action = event.getAction();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    isHold2xActive = false;
                    holdHandler.postDelayed(holdRunnable, 300);

                    long now = System.currentTimeMillis();
                    if (now - lastTapTime < 350) {
                        holdHandler.removeCallbacks(holdRunnable);
                        tapCount++;
                        long accumulatedSeek = tapCount * 10000;
                        seekVideo(true, 10000, accumulatedSeek);
                        lastTapTime = now;
                        v.removeCallbacks(resetTap);
                        v.postDelayed(resetTap, 1000);
                    } else {
                        lastTapTime = now;
                        final long currentTapTime = now;
                        singleTapRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (lastTapTime == currentTapTime && !isHold2xActive) {
                                    toggleController();
                                }
                            }
                        };
                        v.postDelayed(singleTapRunnable, 350);
                    }
                    return true;
                } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                    holdHandler.removeCallbacks(holdRunnable);
                    if (isHold2xActive) {
                        isHold2xActive = false;
                        if (exoPlayer != null) {
                            exoPlayer.setPlaybackParameters(new com.google.android.exoplayer2.PlaybackParameters(1.0f, 1.0f));
                        }
                        if (tvSpeed2xBadge != null) tvSpeed2xBadge.setVisibility(View.GONE);
                        return true;
                    }
                }
                return false;
            }
        });

        // 1. Build Left Brightness Slider Container (Matches LiveActivity)
        fsLeftSliderContainer = new FrameLayout(this);
        fsLeftSliderContainer.setFocusable(false);
        fsLeftSliderContainer.setFocusableInTouchMode(false);
        fsLeftSliderContainer.setVisibility(View.GONE);
        fsLeftSliderContainer.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams leftSliderParams = new FrameLayout.LayoutParams((int)(48 * scale), (int)(260 * scale));
        leftSliderParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        leftSliderParams.leftMargin = (int)(20 * scale);
        fsLeftSliderContainer.setLayoutParams(leftSliderParams);

        ImageView ivBright = new ImageView(this);
        ivBright.setImageResource(R.drawable.ic_material_brightness);
        ivBright.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams ivBrightLp = new FrameLayout.LayoutParams((int)(24 * scale), (int)(24 * scale));
        ivBrightLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ivBrightLp.topMargin = (int)(10 * scale);
        fsLeftSliderContainer.addView(ivBright, ivBrightLp);

        fsLeftSlider = new android.widget.SeekBar(this);
        fsLeftSlider.setFocusable(false);
        fsLeftSlider.setFocusableInTouchMode(false);
        fsLeftSlider.setMax(100);
        
        // Load initial screen brightness
        float curBright = 0.5f;
        WindowManager.LayoutParams wlp = getWindow().getAttributes();
        if (wlp.screenBrightness >= 0) {
            curBright = wlp.screenBrightness;
        } else {
            try {
                curBright = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
            } catch (Exception e) {}
        }
        fsLeftSlider.setProgress((int)(curBright * 100));
        fsLeftSlider.setPadding(0, 0, 0, 0);
        
        FrameLayout.LayoutParams sliderLp = new FrameLayout.LayoutParams((int)(180 * scale), (int)(48 * scale));
        sliderLp.gravity = Gravity.CENTER;
        fsLeftSlider.setLayoutParams(sliderLp);
        fsLeftSlider.setRotation(270f);

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
        if (!TvUtil.isTvMode(PlayerActivity.this)) {
            TvUtil.applyTvFocusHighlight(fsLeftSlider);
        }

        // Customize Seekbar track colors (Premium transparent look)
        try {
            android.graphics.drawable.Drawable progressDrawableLeft = fsLeftSlider.getProgressDrawable();
            if (progressDrawableLeft instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progressDrawableLeft;
                android.graphics.drawable.Drawable bg = ld.findDrawableByLayerId(android.R.id.background);
                if (bg != null) bg.setColorFilter(Color.parseColor("#4DFFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
                android.graphics.drawable.Drawable prog = ld.findDrawableByLayerId(android.R.id.progress);
                if (prog != null) prog.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                progressDrawableLeft.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                android.graphics.drawable.Drawable thumb = fsLeftSlider.getThumb();
                if (thumb != null) thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) {}
        fsLeftSliderContainer.addView(fsLeftSlider);

        View.OnTouchListener brightTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) return true;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        int[] loc = new int[2];
                        fsLeftSliderContainer.getLocationOnScreen(loc);
                        float containerTop = loc[1];
                        float containerHeight = fsLeftSliderContainer.getHeight();
                        float startY = containerTop + (40 * scale);
                        float endY = containerTop + containerHeight - (40 * scale);
                        float trackLength = endY - startY;
                        float relativeY = event.getRawY() - startY;
                        float percentage = 1.0f - (relativeY / trackLength);
                        if (percentage < 0.0f) percentage = 0.0f;
                        if (percentage > 1.0f) percentage = 1.0f;
                        int progress = (int) (percentage * 100);
                        fsLeftSlider.setProgress(progress);
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = progress / 100f == 0 ? 0.01f : progress / 100f;
                        getWindow().setAttributes(lp);
                        resetAutoHideTimer();
                        break;
                }
                return true;
            }
        };
        fsLeftSliderContainer.setOnTouchListener(brightTouchListener);
        fsLeftSlider.setOnTouchListener(brightTouchListener);
        root.addView(fsLeftSliderContainer);

        // 2. Build Right Volume Slider Container (Matches LiveActivity)
        fsRightSliderContainer = new FrameLayout(this);
        fsRightSliderContainer.setFocusable(false);
        fsRightSliderContainer.setFocusableInTouchMode(false);
        fsRightSliderContainer.setVisibility(View.GONE);
        fsRightSliderContainer.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams rightSliderParams = new FrameLayout.LayoutParams((int)(48 * scale), (int)(260 * scale));
        rightSliderParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        rightSliderParams.rightMargin = (int)(20 * scale);
        fsRightSliderContainer.setLayoutParams(rightSliderParams);

        ImageView ivVol = new ImageView(this);
        ivVol.setImageResource(R.drawable.ic_material_volume);
        ivVol.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams ivVolLp = new FrameLayout.LayoutParams((int)(24 * scale), (int)(24 * scale));
        ivVolLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ivVolLp.topMargin = (int)(10 * scale);
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
        if (!TvUtil.isTvMode(PlayerActivity.this)) {
            TvUtil.applyTvFocusHighlight(fsRightSlider);
        }

        try {
            android.graphics.drawable.Drawable progressDrawableRight = fsRightSlider.getProgressDrawable();
            if (progressDrawableRight instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progressDrawableRight;
                android.graphics.drawable.Drawable bg = ld.findDrawableByLayerId(android.R.id.background);
                if (bg != null) bg.setColorFilter(Color.parseColor("#4DFFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
                android.graphics.drawable.Drawable prog = ld.findDrawableByLayerId(android.R.id.progress);
                if (prog != null) prog.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                progressDrawableRight.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                android.graphics.drawable.Drawable thumb = fsRightSlider.getThumb();
                if (thumb != null) thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) {}
        fsRightSliderContainer.addView(fsRightSlider);

        View.OnTouchListener volTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) return true;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        int[] loc = new int[2];
                        fsRightSliderContainer.getLocationOnScreen(loc);
                        float containerTop = loc[1];
                        float containerHeight = fsRightSliderContainer.getHeight();
                        float startY = containerTop + (40 * scale);
                        float endY = containerTop + containerHeight - (40 * scale);
                        float trackLength = endY - startY;
                        float relativeY = event.getRawY() - startY;
                        float percentage = 1.0f - (relativeY / trackLength);
                        if (percentage < 0.0f) percentage = 0.0f;
                        if (percentage > 1.0f) percentage = 1.0f;
                        int newVolume = (int) (percentage * maxVolume);
                        fsRightSlider.setProgress(newVolume);
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0);
                        resetAutoHideTimer();
                        break;
                }
                return true;
            }
        };
        fsRightSliderContainer.setOnTouchListener(volTouchListener);
        fsRightSlider.setOnTouchListener(volTouchListener);
        root.addView(fsRightSliderContainer);

        // 3. Build Floating circular Child Lock Button at the TOP-LEFT
        btnFsLock = new ImageView(this);
        btnFsLock.setImageResource(R.drawable.ic_material_lock_open);
        btnFsLock.setColorFilter(Color.WHITE);
        btnFsLock.setPadding((int)(2 * scale), (int)(2 * scale), (int)(2 * scale), (int)(2 * scale));
        btnFsLock.setVisibility(View.GONE);

        android.graphics.drawable.GradientDrawable lockBg = new android.graphics.drawable.GradientDrawable();
        lockBg.setColor(Color.parseColor("#88000000"));
        lockBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        btnFsLock.setBackground(lockBg);

        FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
        lockParams.gravity = Gravity.RIGHT | Gravity.TOP;
        lockParams.rightMargin = (int)(265 * scale);
        lockParams.topMargin = (int)(18 * scale);
        btnFsLock.setLayoutParams(lockParams);
        TvUtil.applyTvFocusHighlight(btnFsLock);
        btnFsLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLocked = !isLocked;
                if (isLocked) {
                    btnFsLock.setImageResource(R.drawable.ic_material_lock);
                    btnFsLock.setColorFilter(Color.RED);
                    playerView.setUseController(false);
                    playerView.hideController();
                    fsLeftSliderContainer.setVisibility(View.GONE);
                    fsRightSliderContainer.setVisibility(View.GONE);
                    Toast.makeText(PlayerActivity.this, "تم قفل الشاشة والتحكم", Toast.LENGTH_SHORT).show();
                } else {
                    btnFsLock.setImageResource(R.drawable.ic_material_lock_open);
                    btnFsLock.setColorFilter(Color.WHITE);
                    playerView.setUseController(true);
                    playerView.showController();
                    if (!TvUtil.isTvMode(PlayerActivity.this)) {
                        fsLeftSliderContainer.setVisibility(View.VISIBLE);
                        fsRightSliderContainer.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(PlayerActivity.this, "تم إلغاء القفل", Toast.LENGTH_SHORT).show();
                }
                resetAutoHideTimer();
            }
        });
        root.addView(btnFsLock);

        // 4. Connect Player Controller visibility with custom sliders and Lock button
        playerView.setControllerVisibilityListener(new StyledPlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                isControllerVisible = (visibility == View.VISIBLE);
                if (isLocked) {
                    playerView.hideController();
                    return;
                }
                if (TvUtil.isTvMode(PlayerActivity.this)) {
                    fsLeftSliderContainer.setVisibility(View.GONE);
                    fsRightSliderContainer.setVisibility(View.GONE);
                } else {
                    fsLeftSliderContainer.setVisibility(visibility);
                    fsRightSliderContainer.setVisibility(visibility);
                }
                btnFsLock.setVisibility(visibility);
                if (visibility == View.VISIBLE) {
                    resetAutoHideTimer();
                    playerView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (btnPlay != null && btnPlay.getVisibility() == View.VISIBLE) {
                                btnPlay.requestFocus();
                            } else if (btnPause != null && btnPause.getVisibility() == View.VISIBLE) {
                                btnPause.requestFocus();
                            }
                        }
                    });
                } else {
                    cancelAutoHideTimer();
                }
            }
        });

        // 5. Autohide runnable initialization
        autoHideRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLocked) {
                    btnFsLock.setVisibility(View.GONE);
                } else {
                    playerView.hideController();
                }
            }
        };

        // Tap screen while locked to show/hide the Floating Lock button
        View.OnTouchListener lockTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        handleLockTap();
                    }
                    return true;
                }
                return false;
            }
        };
        root.setOnTouchListener(lockTouchListener);
        playerView.setOnTouchListener(lockTouchListener);

        playerView.post(new Runnable() {
            @Override
            public void run() {
                setupCustomControls();
            }
        });

        // Setup premium glassmorphic seek overlay (YouTube style)
        seekOverlay = new LinearLayout(this);
        seekOverlay.setOrientation(LinearLayout.VERTICAL);
        seekOverlay.setGravity(Gravity.CENTER);
        seekOverlay.setPadding((int)(20 * scale), (int)(15 * scale), (int)(20 * scale), (int)(15 * scale));
        
        FrameLayout.LayoutParams seekLp = new FrameLayout.LayoutParams((int)(500 * scale), (int)(110 * scale));
        seekLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        seekLp.bottomMargin = (int)(60 * scale);
        seekOverlay.setLayoutParams(seekLp);

        android.graphics.drawable.GradientDrawable seekBg = new android.graphics.drawable.GradientDrawable();
        seekBg.setColor(Color.parseColor("#E60A0E1A"));
        seekBg.setStroke(2, Color.parseColor("#FF00E5FF")); // Glow cyan outline
        seekBg.setCornerRadius(15 * scale);
        seekOverlay.setBackground(seekBg);
        seekOverlay.setVisibility(View.GONE);

        tvSeekTime = new TextView(this);
        tvSeekTime.setTextColor(Color.parseColor("#FF00E5FF"));
        tvSeekTime.setTextSize(18);
        tvSeekTime.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSeekTime.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-1, -2);
        timeLp.bottomMargin = (int)(8 * scale);
        tvSeekTime.setLayoutParams(timeLp);
        seekOverlay.addView(tvSeekTime);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        
        seekProgress = new android.widget.SeekBar(this);
        seekProgress.setMax(100);
        seekProgress.setFocusable(false);
        seekProgress.setFocusableInTouchMode(false);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(0, -2, 1);
        seekProgress.setLayoutParams(progressLp);
        
        try {
            android.graphics.drawable.Drawable progDr = seekProgress.getProgressDrawable();
            if (progDr instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progDr;
                ld.findDrawableByLayerId(android.R.id.background).setColorFilter(Color.parseColor("#33FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
                ld.findDrawableByLayerId(android.R.id.progress).setColorFilter(Color.parseColor("#FF00E5FF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                seekProgress.getThumb().setColorFilter(Color.parseColor("#FF00E5FF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) {}
        progressRow.addView(seekProgress);

        tvSeekDuration = new TextView(this);
        tvSeekDuration.setTextColor(Color.WHITE);
        tvSeekDuration.setTextSize(13);
        LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(-2, -2);
        durLp.leftMargin = (int)(12 * scale);
        tvSeekDuration.setLayoutParams(durLp);
        progressRow.addView(tvSeekDuration);

        seekOverlay.addView(progressRow);
        root.addView(seekOverlay);

        initCastOverlay(root);

        setContentView(root);
    }

    private void resetAutoHideTimer() {
        cancelAutoHideTimer();
        autoHideHandler.postDelayed(autoHideRunnable, 3000);
    }

    private void cancelAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (isLocked) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    finish();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (isSeekingMode) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || 
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    
                    if (exoPlayer != null) {
                        long duration = exoPlayer.getDuration();
                        if (duration > 0) {
                            long seekStep = 10000; // 10 seconds per press
                            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                tempSeekPosition = Math.max(0, tempSeekPosition - seekStep);
                            } else {
                                tempSeekPosition = Math.min(duration, tempSeekPosition + seekStep);
                            }
                            int progressPercent = (int) ((tempSeekPosition * 100) / duration);
                            if (seekProgress != null) seekProgress.setProgress(progressPercent);
                            if (tvSeekTime != null) tvSeekTime.setText(formatTime(tempSeekPosition));
                            if (tvSeekDuration != null) tvSeekDuration.setText(formatTime(duration));
                            resetSeekAutoHideTimer();
                        }
                    }
                    return true;
                }
                
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    
                    isSeekingMode = false;
                    seekHideHandler.removeCallbacks(seekHideRunnable);
                    if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(tempSeekPosition);
                        exoPlayer.setPlayWhenReady(true);
                    }
                    if (playerView != null) {
                        playerView.showController();
                    }
                    return true;
                }
                
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    isSeekingMode = false;
                    seekHideHandler.removeCallbacks(seekHideRunnable);
                    if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);
                    if (exoPlayer != null) {
                        exoPlayer.setPlayWhenReady(true);
                    }
                    return true;
                }
            }
            return true;
        }

        // DPAD LEFT / RIGHT: Enter seek mode (except when top row controls are focused)
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || 
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
            
            boolean isTopRowFocused = (btnBack != null && btnBack.hasFocus()) 
                || (btnFsLock != null && btnFsLock.hasFocus()) 
                || (btnFs != null && btnFs.hasFocus());

            if (!isTopRowFocused) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (exoPlayer != null) {
                        long duration = exoPlayer.getDuration();
                        if (duration > 0) {
                            isSeekingMode = true;
                            tempSeekPosition = exoPlayer.getCurrentPosition();
                            exoPlayer.setPlayWhenReady(false);
                            if (playerView != null) {
                                playerView.hideController();
                            }
                            if (seekOverlay != null) seekOverlay.setVisibility(View.VISIBLE);

                            long seekStep = 10000;
                            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                tempSeekPosition = Math.max(0, tempSeekPosition - seekStep);
                            } else {
                                tempSeekPosition = Math.min(duration, tempSeekPosition + seekStep);
                            }
                            int progressPercent = (int) ((tempSeekPosition * 100) / duration);
                            if (seekProgress != null) seekProgress.setProgress(progressPercent);
                            if (tvSeekTime != null) tvSeekTime.setText(formatTime(tempSeekPosition));
                            if (tvSeekDuration != null) tvSeekDuration.setText(formatTime(duration));
                            resetSeekAutoHideTimer();
                        }
                    }
                }
                return true;
            }
        }

        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (playerView != null && isControllerVisible) {
                    playerView.hideController();
                    return true;
                } else {
                    finish();
                    return true;
                }
            }
            return true;
        }

        // DPAD CENTER / ENTER / PLAY_PAUSE / SPACE: Toggle Play/Pause
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
            keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (!isControllerVisible) {
                    showedControlsOnDown = true;
                    if (playerView != null) {
                        playerView.showController();
                    }
                    resetAutoHideTimer();
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
            
            if (isControllerVisible) {
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
                    if (playerView != null) {
                        playerView.showController();
                    }
                    resetAutoHideTimer();
                }
            }
            return true;
        }

        // Volume Keys handling (only volume buttons, NOT DPAD UP/DOWN which are for navigation)
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
                    
                    if (playerView != null) {
                        playerView.showController();
                    }
                    resetAutoHideTimer();
                }
            }
            return true;
        }

        if (playerView != null && !isControllerVisible) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                playerView.showController();
            }
            return true;
        }

        resetAutoHideTimer();
        return super.dispatchKeyEvent(event);
    }

    private void setupCustomControls() {
        final float scale = getResources().getDisplayMetrics().density;
        ImageView localQualityTemp = null;
        ImageView localAudioTemp = null;
        ImageView localSubTemp = null;

        // Setup Back Button & Title in linear14 with left-padding to prevent overlap with lock button
        int linear14Id = getResources().getIdentifier("linear14", "id", getPackageName());
        LinearLayout linear14 = playerView.findViewById(linear14Id);
        if (linear14 != null) {
            linear14.setGravity(Gravity.CENTER_VERTICAL);
            linear14.setPadding((int)(15 * scale), 0, (int)(15 * scale), 0);
            
            ImageView ivBack = new ImageView(this);
            ivBack.setImageResource(R.drawable.ic_back_arrow);
            ivBack.setColorFilter(Color.WHITE);
            ivBack.setFocusable(true);
            LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams((int)(26 * scale), (int)(26 * scale));
            lpBack.rightMargin = (int)(10 * scale);
            ivBack.setLayoutParams(lpBack);
            ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            TvUtil.applyTvFocusHighlight(ivBack, 15.0f);
            btnBack = ivBack;
            linear14.addView(btnBack);

            tvPlayerTitle = new TextView(this);
            tvPlayerTitle.setText(title != null ? title : "مشغل الفيديو");
            tvPlayerTitle.setTextColor(Color.WHITE);
            tvPlayerTitle.setTextSize(16);
            tvPlayerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPlayerTitle.setGravity(Gravity.CENTER_VERTICAL);
            linear14.addView(tvPlayerTitle);
        }

        // Dynamically move the aspect ratio toggle button (bt_fullscreen) to the top right (linear15)
        int fsId = getResources().getIdentifier("bt_fullscreen", "id", getPackageName());
        btnFs = playerView.findViewById(fsId);
        if (btnFs != null) {
            android.view.ViewGroup parent = (android.view.ViewGroup) btnFs.getParent();
            if (parent != null) {
                parent.removeView(btnFs);
            }
            
            int linear15Id = getResources().getIdentifier("linear15", "id", getPackageName());
            final LinearLayout linear15 = playerView.findViewById(linear15Id);
            if (linear15 != null) {
                linear15.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                // Add Video Quality Selection Button
                ImageView btnQuality = new ImageView(this);
                localQualityTemp = btnQuality;
                btnQuality.setImageResource(R.drawable.ic_material_high_quality);
                btnQuality.setColorFilter(Color.WHITE);
                btnQuality.setFocusable(true);
                LinearLayout.LayoutParams lpQuality = new LinearLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
                lpQuality.rightMargin = (int)(15 * scale);
                btnQuality.setLayoutParams(lpQuality);
                btnQuality.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                            final com.google.android.exoplayer2.ui.DefaultTrackNameProvider defaultProvider = 
                                new com.google.android.exoplayer2.ui.DefaultTrackNameProvider(getResources());
                            new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                                themeId != 0 ? new android.view.ContextThemeWrapper(PlayerActivity.this, themeId) : PlayerActivity.this,
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
                            Toast.makeText(PlayerActivity.this, "غير مدعوم للفيديو الحالي", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                TvUtil.applyTvFocusHighlight(btnQuality, 15.0f);
                linear15.addView(btnQuality);

                // Add Audio Selection Button
                ImageView btnAudio = new ImageView(this);
                localAudioTemp = btnAudio;
                btnAudio.setImageResource(R.drawable.ic_material_audio);
                btnAudio.setColorFilter(Color.WHITE);
                btnAudio.setFocusable(true);
                LinearLayout.LayoutParams lpAudio = new LinearLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
                lpAudio.rightMargin = (int)(15 * scale);
                btnAudio.setLayoutParams(lpAudio);
                btnAudio.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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
                                themeId != 0 ? new android.view.ContextThemeWrapper(PlayerActivity.this, themeId) : PlayerActivity.this,
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
                            Toast.makeText(PlayerActivity.this, "غير مدعوم للفيديو الحالي", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                TvUtil.applyTvFocusHighlight(btnAudio, 15.0f);
                linear15.addView(btnAudio);

                // Add Subtitle Selection Button
                ImageView btnSub = new ImageView(this);
                localSubTemp = btnSub;
                btnSub.setImageResource(R.drawable.ic_material_subtitles);
                btnSub.setColorFilter(Color.WHITE);
                btnSub.setFocusable(true);
                LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
                lpSub.rightMargin = (int)(15 * scale);
                btnSub.setLayoutParams(lpSub);
                btnSub.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
                            new com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder(
                                themeId != 0 ? new android.view.ContextThemeWrapper(PlayerActivity.this, themeId) : PlayerActivity.this,
                                "اختر الترجمة",
                                exoPlayer,
                                3 // C.TRACK_TYPE_TEXT
                            )
                            .setShowDisableOption(true)
                            .setAllowAdaptiveSelections(false)
                            .build()
                            .show();
                        } catch (Exception e) {
                            Toast.makeText(PlayerActivity.this, "غير مدعوم للفيديو الحالي", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                TvUtil.applyTvFocusHighlight(btnSub, 15.0f);
                linear15.addView(btnSub);

                // Add Quick Menu/Selector Button
                ImageView btnFsSelector = new ImageView(this);
                localSelectorTemp = btnFsSelector;
                int selectorIconId = getResources().getIdentifier("ic_settings_player", "drawable", getPackageName());
                if (selectorIconId == 0) selectorIconId = getResources().getIdentifier("ic_settings_playlist", "drawable", getPackageName());
                if (selectorIconId == 0) selectorIconId = android.R.drawable.ic_menu_agenda;
                btnFsSelector.setImageResource(selectorIconId);
                btnFsSelector.setColorFilter(Color.WHITE);
                btnFsSelector.setClickable(true);
                btnFsSelector.setFocusable(true);
                btnFsSelector.setFocusableInTouchMode(true);
                LinearLayout.LayoutParams lpSelector = new LinearLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
                lpSelector.rightMargin = (int)(15 * scale);
                btnFsSelector.setLayoutParams(lpSelector);
                btnFsSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showQuickMediaSelector(scale);
                    }
                });
                TvUtil.applyTvFocusHighlight(btnFsSelector, 15.0f);
                linear15.addView(btnFsSelector);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(30 * scale), (int)(30 * scale));
                lp.rightMargin = (int)(20 * scale);
                btnFs.setLayoutParams(lp);
                linear15.addView(btnFs);
            }

            btnFs.setOnClickListener(new View.OnClickListener() { 
                @Override 
                public void onClick(View v) { 
                    currentAspectIndex = (currentAspectIndex + 1) % 3;
                    applyAspectRatio(currentAspectIndex, true);
                } 
            });
            TvUtil.applyTvFocusHighlight(btnFs);
        }

        // Keep Play, Pause, Rewind, Fast Forward, and Settings inside the bottom bar (linear20)
        // Retrieve views from playerView
        int playId = getResources().getIdentifier("exo_play", "id", getPackageName());
        int pauseId = getResources().getIdentifier("exo_pause", "id", getPackageName());
        int rewId = getResources().getIdentifier("exo_rew", "id", getPackageName());
        int ffwdId = getResources().getIdentifier("exo_ffwd", "id", getPackageName());
        int settingsId = getResources().getIdentifier("settings", "id", getPackageName());
        int pipId = getResources().getIdentifier("pip", "id", getPackageName());
        int progressId = getResources().getIdentifier("exo_progress", "id", getPackageName());

        btnPlay = playerView.findViewById(playId);
        btnPause = playerView.findViewById(pauseId);
        View btnRew = playerView.findViewById(rewId);
        View btnFfwd = playerView.findViewById(ffwdId);
        View btnSettings = playerView.findViewById(settingsId);
        View btnPip = playerView.findViewById(pipId);
        View timeBar = playerView.findViewById(progressId);

        // Make all controls focusable for TV/D-pad navigation and set custom click listeners
        if (btnPlay != null) {
            TvUtil.applyTvFocusHighlight(btnPlay);
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (exoPlayer != null) {
                        exoPlayer.play();
                    }
                }
            });
        }
        if (btnPause != null) {
            TvUtil.applyTvFocusHighlight(btnPause);
            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (exoPlayer != null) {
                        exoPlayer.pause();
                    }
                }
            });
        }
        if (btnRew != null) {
            TvUtil.applyTvFocusHighlight(btnRew);
            btnRew.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(Math.max(0, exoPlayer.getCurrentPosition() - 10000));
                    }
                }
            });
        }
        if (btnFfwd != null) {
            TvUtil.applyTvFocusHighlight(btnFfwd);
            btnFfwd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 10000));
                    }
                }
            });
        }

        // Set initial visibility of play/pause buttons based on current playback state
        if (btnPlay != null && btnPause != null) {
            boolean isPlaying = exoPlayer != null && exoPlayer.getPlayWhenReady();
            btnPlay.setVisibility(isPlaying ? View.GONE : View.VISIBLE);
            btnPause.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
        }
        
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSpeedDialog();
                }
            });
            TvUtil.applyTvFocusHighlight(btnSettings);
        }

        if (btnPip != null) {
            btnPip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        try {
                            enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().build());
                        } catch (Exception e) {}
                    }
                }
            });
            TvUtil.applyTvFocusHighlight(btnPip);
        }

        if (timeBar != null) {
            timeBar.setFocusable(true);
            timeBar.setFocusableInTouchMode(true);
            TvUtil.applyTvFocusHighlight(timeBar, 10.0f);
        }

        // Set left/right side slider focus navigation
        if (fsLeftSlider != null && btnPlay != null) {
            fsLeftSlider.setId(View.generateViewId());
            fsLeftSlider.setNextFocusRightId(btnPlay.getId());
            btnPlay.setNextFocusLeftId(fsLeftSlider.getId());
        }
        if (fsRightSlider != null && btnPlay != null) {
            fsRightSlider.setId(View.generateViewId());
            fsRightSlider.setNextFocusLeftId(btnPlay.getId());
            btnPlay.setNextFocusRightId(fsRightSlider.getId());
        }
        // Setup Cast button click listener
        int castId = getResources().getIdentifier("cast", "id", getPackageName());
        View btnCast = playerView.findViewById(castId);
        if (btnCast != null) {
            btnCast.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCastDevicesDialog();
                }
            });
            TvUtil.applyTvFocusHighlight(btnCast);
        }

        // Establish precise D-pad navigation routing between Top and Bottom controls
        if (btnBack != null) btnBack.setId(9100);
        if (localQualityTemp != null) localQualityTemp.setId(9101);
        if (localAudioTemp != null) localAudioTemp.setId(9102);
        if (localSubTemp != null) localSubTemp.setId(9103);
        if (btnFs != null) btnFs.setId(9104);
        if (localSelectorTemp != null) localSelectorTemp.setId(9107);

        if (btnBack != null) {
            btnBack.setNextFocusRightId(localQualityTemp != null ? 9101 : (localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : (localSelectorTemp != null ? 9107 : 9104))));
            btnBack.setNextFocusDownId(playId);
        }
        if (localQualityTemp != null) {
            localQualityTemp.setNextFocusLeftId(9100);
            localQualityTemp.setNextFocusRightId(localAudioTemp != null ? 9102 : (localSubTemp != null ? 9103 : (localSelectorTemp != null ? 9107 : 9104)));
            localQualityTemp.setNextFocusDownId(playId);
        }
        if (localAudioTemp != null) {
            localAudioTemp.setNextFocusLeftId(localQualityTemp != null ? 9101 : 9100);
            localAudioTemp.setNextFocusRightId(localSubTemp != null ? 9103 : (localSelectorTemp != null ? 9107 : 9104));
            localAudioTemp.setNextFocusDownId(playId);
        }
        if (localSubTemp != null) {
            localSubTemp.setNextFocusLeftId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100));
            localSubTemp.setNextFocusRightId(localSelectorTemp != null ? 9107 : 9104);
            localSubTemp.setNextFocusDownId(playId);
        }
        if (localSelectorTemp != null) {
            localSelectorTemp.setNextFocusLeftId(localSubTemp != null ? 9103 : (localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100)));
            localSelectorTemp.setNextFocusRightId(9104);
            localSelectorTemp.setNextFocusDownId(playId);
        }
        if (btnFs != null) {
            btnFs.setNextFocusLeftId(localSelectorTemp != null ? 9107 : (localSubTemp != null ? 9103 : (localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100))));
            btnFs.setNextFocusDownId(playId);
        }

        // Bottom Controls "Up" Routing
        if (btnPlay != null) btnPlay.setNextFocusUpId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100));
        if (btnPause != null) btnPause.setNextFocusUpId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100));
        if (btnRew != null) btnRew.setNextFocusUpId(localQualityTemp != null ? 9101 : 9100);
        if (btnFfwd != null) btnFfwd.setNextFocusUpId(localSubTemp != null ? 9103 : 9104);
        if (timeBar != null) timeBar.setNextFocusUpId(localAudioTemp != null ? 9102 : (localQualityTemp != null ? 9101 : 9100));
        if (btnCast != null) btnCast.setNextFocusUpId(localAudioTemp != null ? 9102 : 9104);
        if (btnPip != null) btnPip.setNextFocusUpId(localSubTemp != null ? 9103 : 9104);
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
        getSharedPreferences("VODPrefs", MODE_PRIVATE).edit().putInt("aspect_ratio", currentAspectIndex).apply();
    }

    private void initializePlayer() {
        try {
            com.google.android.exoplayer2.upstream.DefaultBandwidthMeter bandwidthMeter = 
                new com.google.android.exoplayer2.upstream.DefaultBandwidthMeter.Builder(this)
                    .setInitialBitrateEstimate(4000000) // 4.0 Mbps initial estimate for VOD movies
                    .build();

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    8000,  // minBufferMs: 8s buffer
                    25000, // maxBufferMs: 25s max buffer
                    1000,  // bufferForPlaybackMs: starts playing after 1.0s
                    1500   // bufferForPlaybackAfterRebufferMs: recovers in 1.5s
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(5000, true) // retain 5s back buffer for instant backward seeking
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
                                if (tvPlayerTitle != null) {
                                    tvPlayerTitle.setText((title != null ? title : "مشغل الفيديو") + " (" + resStr + ")");
                                }
                            }
                        });
                    }
                }
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (btnPlay != null) btnPlay.setVisibility(isPlaying ? View.GONE : View.VISIBLE);
                    if (btnPause != null) btnPause.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                    if (CastManager.isCasting) {
                        CastManager.sendCommand(isPlaying ? "RESUME" : "PAUSE");
                    }
                }
                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        if (CastManager.isCasting && exoPlayer != null) {
                            CastManager.sendCommand("SEEK|" + newPosition.positionMs);
                        }
                    }
                }
                @Override 
                public void onPlaybackStateChanged(int state) {
                    int progId = getResources().getIdentifier("progress", "id", getPackageName());
                    View progress = playerView.findViewById(progId);
                    if (progress != null) {
                        progress.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                    }
                    if (state == Player.STATE_READY) {
                        retryCount = 0; // Reset retry count upon successful loading
                        
                        // Restore saved aspect ratio on stream ready
                        int savedAspect = getSharedPreferences("VODPrefs", MODE_PRIVATE).getInt("aspect_ratio", 1);
                        applyAspectRatio(savedAspect, false);
                    }
                }
                @Override 
                public void onPlayerError(PlaybackException error) {
                    android.util.Log.e("PlayerActivity", "Playback error: " + error.getMessage(), error);
                    try {
                        java.io.File logFile = new java.io.File("/storage/emulated/0/Download/player_log.txt");
                        java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                        fw.write(sdf.format(new java.util.Date()) + " : PlayerActivity Error: " + android.util.Log.getStackTraceString(error) + "\n");
                        if (error.getCause() != null) {
                            fw.write(sdf.format(new java.util.Date()) + " : PlayerActivity Error Cause: " + android.util.Log.getStackTraceString(error.getCause()) + "\n");
                        }
                        fw.close();
                    } catch (Exception e) {}
                    
                    if (error != null && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        retryCount = 0;
                        retryPlayback(0);
                        return;
                    }
                    
                    boolean isFormatError = false;
                    if (error != null) {
                        int errorCode = error.errorCode;
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

            checkResumePosition();
        } catch (Throwable t) {
            android.util.Log.e("PlayerActivity", "Error initializing player: " + t.getMessage(), t);
            Toast.makeText(this, "خطأ في تشغيل المشغل: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void checkResumePosition() {
        if (videoUrl == null) {
            startPlayback(0);
            return;
        }
        
        android.content.SharedPreferences sp = getSharedPreferences("ContinueWatchingPrefs", MODE_PRIVATE);
        final long savedPos = sp.getLong(videoUrl, 0);
        
        if (savedPos > 10000) { // Only prompt if progress is more than 10 seconds
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PlayerActivity.this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                    builder.setTitle(TvUtil.translate(PlayerActivity.this, "متابعة المشاهدة"));
                    builder.setMessage(TvUtil.translate(PlayerActivity.this, "هل تريد إكمال المشاهدة من حيث توقفت عند (") + formatTime(savedPos) + TvUtil.translate(PlayerActivity.this, ")؟"));
                    builder.setCancelable(false);
                    
                    builder.setPositiveButton(TvUtil.translate(PlayerActivity.this, "متابعة المشاهدة"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            startPlayback(savedPos);
                        }
                    });
                    
                    builder.setNegativeButton(TvUtil.translate(PlayerActivity.this, "البدء من البداية"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            getSharedPreferences("ContinueWatchingPrefs", MODE_PRIVATE).edit().remove(videoUrl).apply();
                            startPlayback(0);
                        }
                    });
                    
                    android.app.AlertDialog dialog = builder.create();
                    dialog.show();
                    
                    try {
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).requestFocus();
                    } catch (Exception e) {}
                }
            });
        } else {
            startPlayback(0);
        }
    }

    private void startPlayback(long position) {
        if (exoPlayer != null) {
            attemptedMimeType = null;
            MediaSource src = buildMediaSource(videoUrl);
            exoPlayer.setMediaSource(src);
            if (position > 0) {
                exoPlayer.seekTo(position);
            }
            exoPlayer.prepare();
            if (CastManager.isCasting) {
                exoPlayer.setPlayWhenReady(false);
                CastManager.sendCommand("PLAY|" + videoUrl + "|" + (title != null ? title : "فيديو") + "|" + (userAgent != null ? userAgent : "") + "|" + (referer != null ? referer : ""));
                if (position > 0) {
                    CastManager.sendCommand("SEEK|" + position);
                }
                Toast.makeText(this, "جاري العرض على التلفاز...", Toast.LENGTH_SHORT).show();
            } else {
                exoPlayer.setPlayWhenReady(true);
            }
            progressSaveHandler.removeCallbacks(progressSaveRunnable);
            progressSaveHandler.postDelayed(progressSaveRunnable, 5000);
        }
    }

    private void savePlaybackPosition() {
        if (exoPlayer != null && videoUrl != null) {
            long currentPos = exoPlayer.getCurrentPosition();
            long duration = exoPlayer.getDuration();
            if (duration > 0) {
                android.content.SharedPreferences sp = getSharedPreferences("ContinueWatchingPrefs", MODE_PRIVATE);
                if (currentPos > duration * 0.95 || (duration - currentPos) < 60000) {
                    sp.edit().remove(videoUrl).apply();
                } else if (currentPos > 5000) {
                    sp.edit().putLong(videoUrl, currentPos).apply();
                }
            }
        }
    }

    private void seekVideo(boolean forward, long ms, long accumulatedMs) {
        if (CastManager.isCasting) {
            long duration = lastReceivedCastDuration;
            long currentPos = lastReceivedCastPosition;
            long targetPos;
            if (forward) {
                targetPos = Math.min(duration, currentPos + ms);
                showDoubleTapIndicator(true, accumulatedMs);
            } else {
                targetPos = Math.max(0, currentPos - ms);
                showDoubleTapIndicator(false, accumulatedMs);
            }
            CastManager.sendCommand("SEEK|" + targetPos);
            return;
        }
        if (exoPlayer != null) {
            long duration = exoPlayer.getDuration();
            long currentPos = exoPlayer.getCurrentPosition();
            long targetPos;
            if (forward) {
                targetPos = Math.min(duration, currentPos + ms);
                showDoubleTapIndicator(true, accumulatedMs);
            } else {
                targetPos = Math.max(0, currentPos - ms);
                showDoubleTapIndicator(false, accumulatedMs);
            }
            exoPlayer.seekTo(targetPos);
            
            if (CastManager.isCasting) {
                CastManager.sendCommand("SEEK|" + targetPos);
            }
        }
    }

    private void showDoubleTapIndicator(boolean isRight, long accumulatedMs) {
        final View indicator = isRight ? rightTapIndicator : leftTapIndicator;
        final TextView textView = isRight ? tvRightTapSec : tvLeftTapSec;
        
        if (indicator == null || textView == null) return;
        
        String prefix = isRight ? "▶▶\n+" : "◀◀\n-";
        textView.setText(prefix + (accumulatedMs / 1000) + " ثانية");
        
        indicator.setVisibility(View.VISIBLE);
        indicator.setAlpha(1.0f);
        indicator.animate().cancel();
        
        indicator.setScaleX(1.0f);
        indicator.setScaleY(1.0f);
        indicator.animate()
            .alpha(0.0f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(800)
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    indicator.setVisibility(View.GONE);
                }
            });
    }

    private void handleLockTap() {
        if (btnFsLock != null) {
            if (btnFsLock.getVisibility() != View.VISIBLE) {
                btnFsLock.setVisibility(View.VISIBLE);
                resetAutoHideTimer();
            } else {
                btnFsLock.setVisibility(View.GONE);
                cancelAutoHideTimer();
            }
        }
    }

    private void toggleController() {
        if (playerView != null) {
            if (playerView.isControllerFullyVisible()) {
                playerView.hideController();
            } else {
                playerView.showController();
            }
        }
    }

    private MediaSource buildMediaSource(String url) {
        return buildMediaSource(url, attemptedMimeType);
    }

    private MediaSource buildMediaSource(String url, String mimeType) {
        String cleanUrl = url.trim();
        android.net.Uri uri = android.net.Uri.parse(cleanUrl);
        String lowercaseUrl = cleanUrl.toLowerCase();
        
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
        
        // 1. HTTP Data Source configuration
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000) // Increase connection timeout to 15s for weak connections
            .setReadTimeoutMs(20000)    // Increase read timeout to 20s
            .setKeepPostFor302Redirects(true)
            .setAllowCrossProtocolRedirects(true);
            
        String actualUserAgent = (userAgent != null && !userAgent.isEmpty()) ? userAgent : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";
        httpDataSourceFactory.setUserAgent(actualUserAgent);
        
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        }
        headers.put("Accept", "*/*");
        headers.put("Connection", "Keep-Alive");
        httpDataSourceFactory.setDefaultRequestProperties(headers);
        
        // 2. Extractor settings for progressive media (including TS)
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        extractorsFactory.setTsExtractorFlags(1 | 8 | 64); // FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS | FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
        
        // 3. Cache Data Source configuration
        com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory;
        if (isHls || isDash || isRtsp || lowercaseUrl.contains("live")) {
            // Disable disk cache for HLS, DASH, RTSP, and live streams to prevent disk write latency (highly problematic on cheap TV boxes)
            dataSourceFactory = httpDataSourceFactory;
        } else {
            try {
                com.google.android.exoplayer2.upstream.cache.SimpleCache cache = getSimpleCache(this);
                dataSourceFactory = new com.google.android.exoplayer2.upstream.cache.CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                    .setCacheReadDataSourceFactory(new com.google.android.exoplayer2.upstream.FileDataSource.Factory())
                    .setCacheWriteDataSinkFactory(
                        new com.google.android.exoplayer2.upstream.cache.CacheDataSink.Factory()
                            .setCache(cache)
                            .setFragmentSize(5 * 1024 * 1024)
                    )
                    .setFlags(com.google.android.exoplayer2.upstream.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            } catch (Throwable t) {
                android.util.Log.e("PlayerActivity", "Failed to initialize CacheDataSource, falling back to HTTP: " + t.getMessage());
                dataSourceFactory = httpDataSourceFactory;
            }
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

    private void retryPlayback(final long errorPosition) {
        if (exoPlayer == null || videoUrl == null) return;
        if (retryCount >= MAX_RETRIES) {
            Toast.makeText(this, "فشل الاتصال بالبث بعد عدة محاولات", Toast.LENGTH_SHORT).show();
            isReconnecting = false;
            return;
        }
        
        isReconnecting = true;
        retryCount++;

        final String originalUrl = videoUrl;
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer == null) return;
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
                    exoPlayer.setMediaSource(src);
                    exoPlayer.prepare();
                    boolean isLive = originalUrl.toLowerCase().contains("m3u8") || originalUrl.toLowerCase().contains("hls") || originalUrl.toLowerCase().contains(".mpd") || originalUrl.toLowerCase().contains("live");
                    if (!isLive && errorPosition > 0) {
                        exoPlayer.seekTo(errorPosition);
                    }
                    exoPlayer.setPlayWhenReady(true);
                    isReconnecting = false;
                } catch (Throwable t) {
                    android.util.Log.e("PlayerActivity", "Retry failed: " + t.getMessage());
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
                            mimeType = com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8; // default fallback
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

    @Override
    protected void onPause() {
        super.onPause();
        progressSaveHandler.removeCallbacks(progressSaveRunnable);
        savePlaybackPosition();
        if (CastManager.isCasting) {
            CastManager.setCastStateListener(null);
        } else {
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(false);
            }
        }
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception e) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (CastManager.isCasting) {
            CastManager.setCastStateListener(castStateListener);
            CastManager.startClientConnection(this);
            showCastOverlay();
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(false);
            }
        } else {
            hideCastOverlay();
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(true);
                progressSaveHandler.removeCallbacks(progressSaveRunnable);
                progressSaveHandler.postDelayed(progressSaveRunnable, 5000);
            }
        }
        try {
            registerReceiver(networkReceiver, new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception e) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            final View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );
                    }
                }
            });
        }
    }

    private void launchExternalPlayer(android.content.Context context, String url, String title, String userAgent, String referer, int playerType) {
        try {
            android.net.Uri uri = android.net.Uri.parse(url.trim());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

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
                Intent chooser = Intent.createChooser(intent, "اختر مشغل الفيديو");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            }
        } catch (Exception e) {
            Toast.makeText(context, "خطأ في تشغيل المشغل الخارجي: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

    public void pauseVideo() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    exoPlayer.pause();
                }
            }
        });
    }

    public void resumeVideo() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    exoPlayer.play();
                }
            }
        });
    }

    public void seekToPosition(final long pos) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    exoPlayer.seekTo(pos);
                }
            }
        });
    }

    public void setVolume(final int val) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (audioManager != null) {
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, val, android.media.AudioManager.FLAG_SHOW_UI);
                    if (fsRightSlider != null) {
                        fsRightSlider.setProgress(val);
                    }
                }
            }
        });
    }

    public void setBrightness(final float val) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = val;
                getWindow().setAttributes(lp);
                if (fsLeftSlider != null) {
                    fsLeftSlider.setProgress((int)(val * 100));
                }
            }
        });
    }

    private final CastManager.CastStateListener castStateListener = new CastManager.CastStateListener() {
        @Override
        public void onStatusReceived(final long position, final long duration, final boolean isPlaying, final String sessionId, final String title, final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateCastUI(position, duration, isPlaying, title);
                }
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tvCastDeviceName != null) {
                        tvCastDeviceName.setText("جاري إعادة الاتصال بالتلفاز...");
                    }
                }
            });
        }
    };

    private void showCastDevicesDialog() {
        int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
        final android.app.AlertDialog.Builder builder = (themeId != 0) ?
            new android.app.AlertDialog.Builder(this, themeId) :
            new android.app.AlertDialog.Builder(this);

        if (CastManager.isCasting) {
            builder.setTitle(TvUtil.translate(this, "الاتصال اللاسلكي"));
            builder.setMessage(TvUtil.translate(this, "أنت متصل حالياً بالتلفاز. هل تريد قطع الاتصال؟"));
            builder.setPositiveButton(TvUtil.translate(this, "قطع الاتصال وتوقف التشغيل"), new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    CastManager.sendCommand("EXIT");
                    CastManager.disconnectFromDevice(PlayerActivity.this);
                    hideCastOverlay();
                    if (exoPlayer != null) {
                         exoPlayer.play();
                    }
                    Toast.makeText(PlayerActivity.this, TvUtil.translate(PlayerActivity.this, "تم قطع الاتصال وتوقف العرض"), Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNeutralButton(TvUtil.translate(this, "قطع الاتصال والاستئناف هنا"), new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    disconnectAndResumeLocally();
                }
            });
            builder.setNegativeButton(TvUtil.translate(this, "إلغاء"), null);
            builder.show();
            return;
        }

        final float scale = getResources().getDisplayMetrics().density;
        builder.setTitle(TvUtil.translate(this, "البحث عن أجهزة التلفاز..."));

        // Parent container for dialog custom view
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding((int)(18 * scale), (int)(10 * scale), (int)(18 * scale), (int)(10 * scale));

        // Searching Loader row
        final android.widget.LinearLayout statusLayout = new android.widget.LinearLayout(this);
        statusLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        statusLayout.setGravity(Gravity.CENTER_VERTICAL);
        statusLayout.setPadding(0, 0, 0, (int)(12 * scale));

        final android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progressBar.setIndeterminate(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF00E5FF")));
        }
        android.widget.LinearLayout.LayoutParams pbLp = new android.widget.LinearLayout.LayoutParams((int)(20 * scale), (int)(20 * scale));
        pbLp.rightMargin = (int)(10 * scale);
        statusLayout.addView(progressBar, pbLp);

        final android.widget.TextView tvStatus = new android.widget.TextView(this);
        tvStatus.setText("جاري البحث عن أجهزة تلفاز نشطة على الشبكة...");
        tvStatus.setTextColor(Color.parseColor("#FF00E5FF"));
        tvStatus.setTextSize(13);
        statusLayout.addView(tvStatus);

        mainLayout.addView(statusLayout);

        // Discovered devices ListView
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setDividerHeight(1);
        listView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, (int)(180 * scale)));
        mainLayout.addView(listView);

        builder.setView(mainLayout);

        final ArrayList<String> deviceList = new ArrayList<>();
        final ArrayList<String> deviceIps = new ArrayList<>();
        final ArrayList<String> deviceNames = new ArrayList<>();
        final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            deviceList
        ) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(14);
                }
                return view;
            }
        };
        listView.setAdapter(adapter);

        final android.app.AlertDialog dialog = builder.create();
        dialog.show();

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= deviceIps.size()) return;
                String targetIp = deviceIps.get(position);
                String targetName = deviceNames.get(position);
                
                CastManager.connectToDevice(PlayerActivity.this, targetIp, targetName);
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(false);
                }
                
                showCastOverlay();
                
                CastManager.sendCommand("PLAY|" + videoUrl + "|" + (title != null ? title : "فيديو") + "|" + (userAgent != null ? userAgent : "") + "|" + (referer != null ? referer : ""));
                
                Toast.makeText(PlayerActivity.this, "تم الاتصال! جاري العرض على " + targetName, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        CastManager.scanLocalNetwork(this, new CastManager.ScanListener() {
            @Override
            public void onDeviceFound(String name, String ip) {
                String entry = name + " (" + ip + ")";
                if (!deviceIps.contains(ip)) {
                    deviceIps.add(ip);
                    deviceNames.add(name);
                    deviceList.add(entry);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScanFinished(List<String> devices) {
                statusLayout.setVisibility(View.GONE);
                if (deviceList.isEmpty()) {
                    statusLayout.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("لم يتم العثور على أجهزة تلفاز متصلة بنفس الشبكة.");
                    tvStatus.setTextColor(Color.parseColor("#E50914"));
                }
            }
        });
    }

    private void initCastOverlay(FrameLayout root) {
        final float scale = getResources().getDisplayMetrics().density;
        
        castOverlayLayout = new FrameLayout(this);
        castOverlayLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        castOverlayLayout.setBackgroundColor(Color.parseColor("#0F172A"));
        castOverlayLayout.setVisibility(View.GONE);

        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        containerLp.leftMargin = (int)(40 * scale);
        containerLp.rightMargin = (int)(40 * scale);
        contentContainer.setLayoutParams(containerLp);

        ImageView ivCastIcon = new ImageView(this);
        int castResId = getResources().getIdentifier("ic_material_cast", "drawable", getPackageName());
        if (castResId != 0) {
            ivCastIcon.setImageResource(castResId);
        }
        ivCastIcon.setColorFilter(Color.parseColor("#FF00E5FF"));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int)(64 * scale), (int)(64 * scale));
        iconLp.bottomMargin = (int)(16 * scale);
        ivCastIcon.setLayoutParams(iconLp);
        contentContainer.addView(ivCastIcon);

        tvCastDeviceName = new TextView(this);
        tvCastDeviceName.setTextColor(Color.parseColor("#FF00E5FF"));
        tvCastDeviceName.setTextSize(18);
        tvCastDeviceName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCastDeviceName.setGravity(Gravity.CENTER);
        tvCastDeviceName.setText("جاري الاتصال بالتلفاز...");
        LinearLayout.LayoutParams tvNameLp = new LinearLayout.LayoutParams(-1, -2);
        tvNameLp.bottomMargin = (int)(8 * scale);
        tvCastDeviceName.setLayoutParams(tvNameLp);
        contentContainer.addView(tvCastDeviceName);

        tvCastTitle = new TextView(this);
        tvCastTitle.setTextColor(Color.WHITE);
        tvCastTitle.setTextSize(22);
        tvCastTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCastTitle.setGravity(Gravity.CENTER);
        tvCastTitle.setText(title != null ? title : "فيديو");
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = (int)(24 * scale);
        tvCastTitle.setLayoutParams(titleLp);
        contentContainer.addView(tvCastTitle);

        castSeekBar = new android.widget.SeekBar(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-1, -2);
        sbLp.bottomMargin = (int)(4 * scale);
        castSeekBar.setLayoutParams(sbLp);
        try {
            android.graphics.drawable.Drawable progDr = castSeekBar.getProgressDrawable();
            if (progDr instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progDr;
                ld.findDrawableByLayerId(android.R.id.background).setColorFilter(Color.parseColor("#33FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
                ld.findDrawableByLayerId(android.R.id.progress).setColorFilter(Color.parseColor("#FF00E5FF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                castSeekBar.getThumb().setColorFilter(Color.parseColor("#FF00E5FF"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) {}
        contentContainer.addView(castSeekBar);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        
        tvCastPosition = new TextView(this);
        tvCastPosition.setTextColor(Color.parseColor("#CBCDC8"));
        tvCastPosition.setTextSize(13);
        tvCastPosition.setText("00:00");
        timeRow.addView(tvCastPosition);
        
        View spacer = new View(this);
        timeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));
        
        tvCastDuration = new TextView(this);
        tvCastDuration.setTextColor(Color.parseColor("#CBCDC8"));
        tvCastDuration.setTextSize(13);
        tvCastDuration.setText("00:00");
        timeRow.addView(tvCastDuration);
        
        contentContainer.addView(timeRow);

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.topMargin = (int)(24 * scale);
        controlsLp.bottomMargin = (int)(24 * scale);
        controlsRow.setLayoutParams(controlsLp);

        btnCastRew = new ImageView(this);
        int rewId = getResources().getIdentifier("ic_material_fast_rewind", "drawable", getPackageName());
        if (rewId != 0) btnCastRew.setImageResource(rewId);
        btnCastRew.setColorFilter(Color.WHITE);
        btnCastRew.setFocusable(true);
        btnCastRew.setBackground(getSelectableBackground());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams((int)(48 * scale), (int)(48 * scale));
        btnLp.rightMargin = (int)(24 * scale);
        btnCastRew.setLayoutParams(btnLp);
        controlsRow.addView(btnCastRew);

        btnCastPlayPause = new ImageView(this);
        int playId = getResources().getIdentifier("ic_material_play_arrow", "drawable", getPackageName());
        if (playId != 0) btnCastPlayPause.setImageResource(playId);
        btnCastPlayPause.setColorFilter(Color.WHITE);
        btnCastPlayPause.setFocusable(true);
        btnCastPlayPause.setBackground(getSelectableBackground());
        controlsRow.addView(btnCastPlayPause, new LinearLayout.LayoutParams((int)(64 * scale), (int)(64 * scale)));

        btnCastFfwd = new ImageView(this);
        int ffwdId = getResources().getIdentifier("ic_material_fast_forward", "drawable", getPackageName());
        if (ffwdId != 0) btnCastFfwd.setImageResource(ffwdId);
        btnCastFfwd.setColorFilter(Color.WHITE);
        btnCastFfwd.setFocusable(true);
        btnCastFfwd.setBackground(getSelectableBackground());
        LinearLayout.LayoutParams ffLp = new LinearLayout.LayoutParams((int)(48 * scale), (int)(48 * scale));
        ffLp.leftMargin = (int)(24 * scale);
        btnCastFfwd.setLayoutParams(ffLp);
        controlsRow.addView(btnCastFfwd);

        contentContainer.addView(controlsRow);

        btnDisconnectCast = new android.widget.Button(this);
        btnDisconnectCast.setText("قطع الاتصال والاستئناف هنا");
        btnDisconnectCast.setTextColor(Color.WHITE);
        btnDisconnectCast.setAllCaps(false);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(Color.parseColor("#E50914"));
        btnBg.setCornerRadius(6 * scale);
        btnDisconnectCast.setBackground(btnBg);
        btnDisconnectCast.setPadding((int)(16 * scale), (int)(8 * scale), (int)(16 * scale), (int)(8 * scale));
        LinearLayout.LayoutParams discLp = new LinearLayout.LayoutParams(-2, -2);
        btnDisconnectCast.setLayoutParams(discLp);
        contentContainer.addView(btnDisconnectCast);

        castOverlayLayout.addView(contentContainer);
        root.addView(castOverlayLayout);

        setupCastListeners();
    }

    private android.graphics.drawable.Drawable getSelectableBackground() {
        int[] attrs = new int[]{android.R.attr.selectableItemBackgroundBorderless};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable drawable = ta.getDrawable(0);
        ta.recycle();
        return drawable;
    }

    private void setupCastListeners() {
        btnCastPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CastManager.sendCommand(isTvPlaying ? "PAUSE" : "RESUME");
            }
        });

        btnCastRew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long target = Math.max(0, lastReceivedCastPosition - 10000);
                CastManager.sendCommand("SEEK|" + target);
            }
        });

        btnCastFfwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long target = Math.min(lastReceivedCastDuration, lastReceivedCastPosition + 10000);
                CastManager.sendCommand("SEEK|" + target);
            }
        });

        castSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCastPosition.setText(formatTime(progress));
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                isUserSeekingCast = true;
            }
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                isUserSeekingCast = false;
                CastManager.sendCommand("SEEK|" + seekBar.getProgress());
            }
        });

        btnDisconnectCast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectAndResumeLocally();
            }
        });
    }

    private void showCastOverlay() {
        if (castOverlayLayout != null) {
            castOverlayLayout.setVisibility(View.VISIBLE);
        }
        if (playerView != null) {
            playerView.setVisibility(View.GONE);
        }
        if (tvCastDeviceName != null) {
            tvCastDeviceName.setText("جاري العرض على " + (CastManager.connectedDeviceName != null ? CastManager.connectedDeviceName : CastManager.connectedDeviceIp));
        }
    }

    private void hideCastOverlay() {
        if (castOverlayLayout != null) {
            castOverlayLayout.setVisibility(View.GONE);
        }
        if (playerView != null) {
            playerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateCastUI(long position, long duration, boolean isPlaying, String tvTitle) {
        if (castOverlayLayout == null || castOverlayLayout.getVisibility() != View.VISIBLE) {
            return;
        }
        isTvPlaying = isPlaying;
        lastReceivedCastPosition = position;
        lastReceivedCastDuration = duration;

        if (tvCastDeviceName != null) {
            tvCastDeviceName.setText("جاري العرض على " + (CastManager.connectedDeviceName != null ? CastManager.connectedDeviceName : CastManager.connectedDeviceIp));
        }

        if (tvTitle != null && !tvTitle.isEmpty() && tvCastTitle != null) {
            tvCastTitle.setText(tvTitle);
        }

        if (!isUserSeekingCast) {
            castSeekBar.setMax((int) duration);
            castSeekBar.setProgress((int) position);
            tvCastPosition.setText(formatTime(position));
        }
        tvCastDuration.setText(formatTime(duration));

        int playIconId = getResources().getIdentifier(
            isPlaying ? "ic_material_pause" : "ic_material_play_arrow",
            "drawable",
            getPackageName()
        );
        if (playIconId != 0 && btnCastPlayPause != null) {
            btnCastPlayPause.setImageResource(playIconId);
        }
    }

    private void disconnectAndResumeLocally() {
        long resumePos = lastReceivedCastPosition;
        CastManager.disconnectFromDevice(PlayerActivity.this);
        
        hideCastOverlay();
        
        if (exoPlayer != null) {
            exoPlayer.seekTo(resumePos);
            exoPlayer.setPlayWhenReady(true);
            exoPlayer.play();
        }
        Toast.makeText(this, "تم قطع الاتصال واستئناف التشغيل محلياً", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        watchdogHandler.removeCallbacks(watchdogRunnable);
        if (CastManager.activePlayer == this) {
            CastManager.activePlayer = null;
        }
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private String[] getCredentials() {
        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        String dns = sp.getString("active_dns", "");
        String username = sp.getString("active_username", "");
        String password = sp.getString("active_password", "");
        if (dns.isEmpty() || username.isEmpty() || password.isEmpty()) {
            try {
                String json = sp.getString("list", "[]");
                java.util.ArrayList<java.util.HashMap<String,Object>> list =
                    new com.google.gson.Gson().fromJson(json,
                        new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String,Object>>>(){}.getType());
                if (list != null && !list.isEmpty()) {
                    java.util.Map<String,Object> last = list.get(list.size()-1);
                    dns = last.get("dns") != null ? (String)last.get("dns") : "";
                    username = last.get("username") != null ? (String)last.get("username") : "";
                    password = last.get("password") != null ? (String)last.get("password") : "";
                }
            } catch (Exception e) {}
        }
        return new String[] { dns, username, password };
    }

    private void showQuickMediaSelector(final float scale) {
        final List<String[]> categoriesList = new ArrayList<>();
        
        final boolean isSeries = (videoUrl != null && videoUrl.contains("/series/"));

        // 1. Current Series Episodes
        if (isSeries && SeriesepisodesActivity.cachedEpisodes != null && !SeriesepisodesActivity.cachedEpisodes.isEmpty()) {
            categoriesList.add(new String[] { 
                "EPISODES", 
                "حلقات: " + SeriesepisodesActivity.cachedSeriesName, 
                String.valueOf(SeriesepisodesActivity.cachedEpisodes.size()) 
            });
        }
        
        // 2. Content Categories (Only for Movies!)
        if (!isSeries) {
            List<SeriesActivity.SeriesItem> sourceItems = SeriesActivity.cachedMovies;
            Map<String, String> sourceIdToName = SeriesActivity.cachedMoviesIdToName;
            
            if (sourceItems != null) {
                if (sourceIdToName != null) {
                    for (Map.Entry<String, String> entry : sourceIdToName.entrySet()) {
                        String catId = entry.getKey();
                        int count = 0;
                        for (SeriesActivity.SeriesItem m : sourceItems) {
                            if (catId.equals(m.category)) count++;
                        }
                        categoriesList.add(new String[] { catId, entry.getValue(), String.valueOf(count) });
                    }
                } else {
                    java.util.Map<String, Integer> countMap = new java.util.HashMap<>();
                    for (SeriesActivity.SeriesItem m : sourceItems) {
                        if (m.category != null) {
                            int c = countMap.containsKey(m.category) ? countMap.get(m.category) : 0;
                            countMap.put(m.category, c + 1);
                        }
                    }
                    for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
                        categoriesList.add(new String[] { entry.getKey(), entry.getKey(), String.valueOf(entry.getValue()) });
                    }
                }
            }
        }

        if (categoriesList.isEmpty()) {
            Toast.makeText(this, "لا توجد أفلام أو حلقات لعرضها", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x88000000); // Sleek darkened overlay
        root.setFocusable(false);
        
        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        
        // Premium glassmorphic obsidian/blue receiver drawer background
        android.graphics.drawable.GradientDrawable drawerBg = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { Color.parseColor("#F90A0B0E"), Color.parseColor("#F2121319") }
        );
        float r = 28 * scale;
        drawerBg.setCornerRadii(new float[]{ 0, 0, r, r, r, r, 0, 0 }); // round right corners
        drawer.setBackground(drawerBg);
        
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams((int)(430 * scale), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        drawer.setLayoutParams(drawerLp);
        drawer.setPadding((int)(16 * scale), (int)(24 * scale), (int)(16 * scale), (int)(24 * scale));
        drawer.setFocusable(false);
        
        // GORGEOUS SATELLITE/TV HEADER WITH CLOCK
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding((int)(8 * scale), 0, (int)(8 * scale), (int)(12 * scale));
        headerLayout.setFocusable(false);
        
        FrameLayout iconContainer = new FrameLayout(this);
        int containerSize = (int)(38 * scale);
        LinearLayout.LayoutParams iconContainerLp = new LinearLayout.LayoutParams(containerSize, containerSize);
        iconContainerLp.rightMargin = (int)(10 * scale);
        iconContainer.setLayoutParams(iconContainerLp);
        
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setColor(Color.parseColor("#1C00E5FF")); // subtle cyber cyan tint
        iconBg.setCornerRadius(19 * scale);
        iconBg.setStroke((int)(1.5f * scale), Color.parseColor("#3300E5FF"));
        iconContainer.setBackground(iconBg);
        
        ImageView headerIcon = new ImageView(this);
        int selectorIconId = getResources().getIdentifier("ic_settings_player", "drawable", getPackageName());
        if (selectorIconId == 0) selectorIconId = getResources().getIdentifier("ic_settings_playlist", "drawable", getPackageName());
        if (selectorIconId == 0) selectorIconId = android.R.drawable.ic_menu_agenda;
        headerIcon.setImageResource(selectorIconId);
        headerIcon.setColorFilter(Color.parseColor("#00E5FF"));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams((int)(20 * scale), (int)(20 * scale), Gravity.CENTER);
        iconContainer.addView(headerIcon, iconLp);
        
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setFocusable(false);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        textContainer.setLayoutParams(textLp);
        
        TextView titleTv = new TextView(this);
        titleTv.setText("سينما التلفاز");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(19);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        
        TextView subtitleTv = new TextView(this);
        subtitleTv.setText("تصفح الحلقات والأفلام الحالية");
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
        clockLp.leftMargin = (int)(8 * scale);
        
        headerLayout.addView(iconContainer);
        headerLayout.addView(textContainer);
        headerLayout.addView(clockTv, clockLp);
        
        drawer.addView(headerLayout);
        
        // Sleek horizontal divider
        View headerDivider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, (int)(1.5f * scale));
        divLp.topMargin = (int)(4 * scale);
        divLp.bottomMargin = (int)(16 * scale);
        headerDivider.setLayoutParams(divLp);
        headerDivider.setBackgroundColor(Color.parseColor("#15FFFFFF"));
        drawer.addView(headerDivider);

        LinearLayout listsContainer = new LinearLayout(this);
        listsContainer.setOrientation(LinearLayout.HORIZONTAL);
        listsContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        listsContainer.setFocusable(false);
        
        final RecyclerView rvCats = new RecyclerView(this);
        rvCats.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams catsLp = new LinearLayout.LayoutParams((int)(140 * scale), -1);
        rvCats.setLayoutParams(catsLp);
        
        View divider = new View(this);
        LinearLayout.LayoutParams vDivLp = new LinearLayout.LayoutParams((int)(1.5f * scale), -1);
        vDivLp.leftMargin = (int)(8 * scale);
        vDivLp.rightMargin = (int)(8 * scale);
        divider.setLayoutParams(vDivLp);
        divider.setBackgroundColor(Color.parseColor("#15FFFFFF"));
        
        final RecyclerView rvItems = new RecyclerView(this);
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams itemsLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        itemsLp.leftMargin = (int)(10 * scale);
        rvItems.setLayoutParams(itemsLp);
        
        listsContainer.addView(rvCats);
        listsContainer.addView(divider);
        listsContainer.addView(rvItems);
        drawer.addView(listsContainer, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        root.addView(drawer);
        d.setContentView(root);
        
        final List<QuickMediaItem> menuMediaList = new ArrayList<>();
        final QuickMediaItemAdapter itemAdapter = new QuickMediaItemAdapter(menuMediaList, scale, d);
        rvItems.setAdapter(itemAdapter);
        
        int targetCatIdx = 0;
        int targetItemIdx = -1;
        String targetCatId = "";
        
        if (SeriesepisodesActivity.cachedEpisodes != null && !SeriesepisodesActivity.cachedEpisodes.isEmpty()) {
            boolean isEpisode = false;
            for (int i = 0; i < SeriesepisodesActivity.cachedEpisodes.size(); i++) {
                SeriesepisodesActivity.EpisodeItem ep = SeriesepisodesActivity.cachedEpisodes.get(i);
                if (videoUrl != null && videoUrl.contains("/" + ep.id + ".")) {
                    targetItemIdx = i;
                    isEpisode = true;
                    break;
                }
            }
            if (isEpisode) {
                targetCatId = "EPISODES";
            }
        }
        
        if (targetCatId.isEmpty() && SeriesActivity.cachedMovies != null) {
            for (SeriesActivity.SeriesItem m : SeriesActivity.cachedMovies) {
                if (videoUrl != null && videoUrl.contains("/" + m.seriesId + ".")) {
                    targetCatId = m.category;
                    break;
                }
            }
            
            if (!targetCatId.isEmpty()) {
                for (int i = 0; i < categoriesList.size(); i++) {
                    if (categoriesList.get(i)[0].equals(targetCatId)) {
                        targetCatIdx = i;
                        break;
                    }
                }
            }
        }
        
        if (targetCatId.isEmpty() && !categoriesList.isEmpty()) {
            targetCatId = categoriesList.get(0)[0];
        }
        
        menuMediaList.clear();
        if (targetCatId.equals("EPISODES")) {
            if (SeriesepisodesActivity.cachedEpisodes != null) {
                for (SeriesepisodesActivity.EpisodeItem ep : SeriesepisodesActivity.cachedEpisodes) {
                    String epTitle = "الحلقة " + ep.episodeNum + " - " + ep.title;
                    menuMediaList.add(new QuickMediaItem(epTitle, ep.id, ep.containerExtension, null, true));
                }
            }
        } else {
            List<SeriesActivity.SeriesItem> sourceItems = isSeries ? SeriesActivity.cachedSeries : SeriesActivity.cachedMovies;
            if (sourceItems != null) {
                for (SeriesActivity.SeriesItem m : sourceItems) {
                    if (m.category != null && m.category.equals(targetCatId)) {
                        menuMediaList.add(new QuickMediaItem(m.name, m.seriesId, m.containerExtension, m.cover, false));
                    }
                }
            }
            
            for (int i = 0; i < menuMediaList.size(); i++) {
                if (videoUrl != null && videoUrl.contains("/" + menuMediaList.get(i).id + ".")) {
                    targetItemIdx = i;
                    break;
                }
            }
        }
        
        itemAdapter.selectedPos = targetItemIdx;
        itemAdapter.notifyDataSetChanged();
        
        final QuickMediaCatAdapter catAdapter = new QuickMediaCatAdapter(categoriesList, scale, new QuickMediaCatAdapter.OnCategoryFocusedListener() {
            @Override
            public void onCategoryFocused(String catId) {
                menuMediaList.clear();
                if (catId.equals("EPISODES")) {
                    if (SeriesepisodesActivity.cachedEpisodes != null) {
                        for (SeriesepisodesActivity.EpisodeItem ep : SeriesepisodesActivity.cachedEpisodes) {
                            String epTitle = "الحلقة " + ep.episodeNum + " - " + ep.title;
                            menuMediaList.add(new QuickMediaItem(epTitle, ep.id, ep.containerExtension, null, true));
                        }
                    }
                } else {
                    List<SeriesActivity.SeriesItem> sourceItems = isSeries ? SeriesActivity.cachedSeries : SeriesActivity.cachedMovies;
                    if (sourceItems != null) {
                        for (SeriesActivity.SeriesItem m : sourceItems) {
                            if (m.category != null && m.category.equals(catId)) {
                                menuMediaList.add(new QuickMediaItem(m.name, m.seriesId, m.containerExtension, m.cover, false));
                            }
                        }
                    }
                }
                
                int activeIdx = -1;
                for (int i = 0; i < menuMediaList.size(); i++) {
                    if (videoUrl != null && videoUrl.contains("/" + menuMediaList.get(i).id + ".")) {
                        activeIdx = i;
                        break;
                    }
                }
                itemAdapter.selectedPos = activeIdx;
                itemAdapter.notifyDataSetChanged();
                rvItems.scrollToPosition(0);
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
        final int finalItemIdx = targetItemIdx;
        rvCats.scrollToPosition(finalCatIdx);
        
        if (finalItemIdx != -1) {
            rvItems.scrollToPosition(finalItemIdx);
            rvItems.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.ViewHolder vh = rvItems.findViewHolderForAdapterPosition(finalItemIdx);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    } else {
                        rvItems.requestFocus();
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

    private static class QuickMediaItem {
        boolean isEpisode;
        String title;
        String id;
        String ext;
        String cover;
        
        QuickMediaItem(String title, String id, String ext, String cover, boolean isEpisode) {
            this.title = title;
            this.id = id;
            this.ext = ext;
            this.cover = cover;
            this.isEpisode = isEpisode;
        }
    }

    private static class QuickMediaCatAdapter extends RecyclerView.Adapter<QuickMediaCatAdapter.VH> {
        interface OnCategoryFocusedListener {
            void onCategoryFocused(String categoryId);
        }
        
        private final List<String[]> items;
        private final float scale;
        private final OnCategoryFocusedListener listener;
        public int selectedPos = 0;
        
        QuickMediaCatAdapter(List<String[]> items, float scale, OnCategoryFocusedListener listener) {
            this.items = items;
            this.scale = scale;
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
            row.setPadding((int)(8 * scale), (int)(10 * scale), (int)(8 * scale), (int)(10 * scale));
            row.setFocusable(true);
            
            View ind = new View(parent.getContext());
            LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams((int)(3.5f * scale), (int)(18 * scale));
            ind.setLayoutParams(indLp);
            android.graphics.drawable.GradientDrawable indGd = new android.graphics.drawable.GradientDrawable();
            indGd.setColor(Color.parseColor("#FF00E5FF")); // cyan indicator
            indGd.setCornerRadius(2 * scale);
            ind.setBackground(indGd);
            ind.setTag("indicator");
            
            TextView tv = new TextView(parent.getContext());
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            tvLp.leftMargin = (int)(6 * scale);
            tv.setLayoutParams(tvLp);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(13);
            tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tv.setTag("name");
            
            TextView badge = new TextView(parent.getContext());
            badge.setPadding((int)(6 * scale), (int)(2 * scale), (int)(6 * scale), (int)(2 * scale));
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
            final String[] item = items.get(position);
            holder.tvName.setText(item[1]);
            holder.tvBadge.setText(item[2]);
            
            boolean isActive = (position == selectedPos);
            
            if (isActive) {
                holder.indicator.setVisibility(View.VISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#FF00E5FF"));
                holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                
                android.graphics.drawable.GradientDrawable activeBg = new android.graphics.drawable.GradientDrawable();
                activeBg.setColor(Color.parseColor("#1A00E5FF")); // subtle cyan glass
                activeBg.setCornerRadius(8 * scale);
                holder.layout.setBackground(activeBg);
                
                holder.tvBadge.setTextColor(Color.parseColor("#FF00E5FF"));
                android.graphics.drawable.GradientDrawable badgeActiveBg = new android.graphics.drawable.GradientDrawable();
                badgeActiveBg.setColor(Color.parseColor("#2500E5FF"));
                badgeActiveBg.setCornerRadius(10 * scale);
                holder.tvBadge.setBackground(badgeActiveBg);
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#D0D0D5"));
                holder.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.layout.setBackground(null);
                
                holder.tvBadge.setTextColor(Color.parseColor("#8E919C"));
                android.graphics.drawable.GradientDrawable badgeNormalBg = new android.graphics.drawable.GradientDrawable();
                badgeNormalBg.setColor(Color.parseColor("#15FFFFFF"));
                badgeNormalBg.setCornerRadius(10 * scale);
                holder.tvBadge.setBackground(badgeNormalBg);
            }
            
            holder.layout.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        selectedPos = position;
                        notifyDataSetChanged();
                        if (listener != null) {
                            listener.onCategoryFocused(item[0]);
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
                        listener.onCategoryFocused(item[0]);
                    }
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class QuickMediaItemAdapter extends RecyclerView.Adapter<QuickMediaItemAdapter.VH> {
        private final List<QuickMediaItem> items;
        private final float scale;
        private final Dialog dialog;
        public int selectedPos = -1;
        
        QuickMediaItemAdapter(List<QuickMediaItem> items, float scale, Dialog dialog) {
            this.items = items;
            this.scale = scale;
            this.dialog = dialog;
        }
        
        class VH extends RecyclerView.ViewHolder {
            LinearLayout layout;
            View indicator;
            TextView tvNum;
            ImageView ivCover;
            TextView tvName;
            
            VH(View v) {
                super(v);
                layout = (LinearLayout) v;
                indicator = v.findViewWithTag("indicator");
                tvNum = (TextView) v.findViewWithTag("num");
                ivCover = (ImageView) v.findViewWithTag("cover");
                tvName = (TextView) v.findViewWithTag("name");
            }
        }
        
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            row.setPadding((int)(8 * scale), (int)(6 * scale), (int)(8 * scale), (int)(6 * scale));
            row.setFocusable(true);
            
            View ind = new View(parent.getContext());
            LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams((int)(3.5f * scale), (int)(24 * scale));
            indLp.rightMargin = (int)(6 * scale);
            ind.setLayoutParams(indLp);
            android.graphics.drawable.GradientDrawable indGd = new android.graphics.drawable.GradientDrawable();
            indGd.setColor(Color.parseColor("#FF00E5FF")); // active channel/movie color
            indGd.setCornerRadius(2 * scale);
            ind.setBackground(indGd);
            ind.setTag("indicator");
            
            TextView tvNum = new TextView(parent.getContext());
            tvNum.setTag("num");
            tvNum.setTextSize(11);
            tvNum.setTextColor(Color.parseColor("#6B6D7A"));
            tvNum.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(-2, -2);
            numLp.rightMargin = (int)(6 * scale);
            tvNum.setLayoutParams(numLp);
            
            // Beautiful rounded vertical cover container for movies/episodes
            FrameLayout imgContainer = new FrameLayout(parent.getContext());
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams((int)(34 * scale), (int)(50 * scale));
            imgLp.rightMargin = (int)(8 * scale);
            imgContainer.setLayoutParams(imgLp);
            
            android.graphics.drawable.GradientDrawable imgBg = new android.graphics.drawable.GradientDrawable();
            imgBg.setColor(Color.parseColor("#12FFFFFF")); // subtle glass shape
            imgBg.setCornerRadius(6 * scale);
            imgBg.setStroke((int)(1 * scale), Color.parseColor("#1BFFFFFF"));
            imgContainer.setBackground(imgBg);
            imgContainer.setPadding((int)(3 * scale), (int)(3 * scale), (int)(3 * scale), (int)(3 * scale));
            
            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setTag("cover");
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
            final QuickMediaItem m = items.get(position);
            holder.tvName.setText(TvUtil.formatNameByLanguage(m.title));
            
            if (holder.tvNum != null) {
                holder.tvNum.setText(String.format(java.util.Locale.US, "%02d", position + 1));
            }
            
            if (m.cover != null && !m.cover.isEmpty()) {
                TvUtil.loadImage(holder.ivCover, m.cover);
            } else {
                holder.ivCover.setImageResource(R.drawable.home_logo);
            }
            
            boolean isActive = (position == selectedPos);
            if (isActive) {
                holder.indicator.setVisibility(View.VISIBLE);
                holder.tvName.setTextColor(Color.parseColor("#FF00E5FF"));
                holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                if (holder.tvNum != null) {
                    holder.tvNum.setTextColor(Color.parseColor("#FF00E5FF"));
                }
                
                android.graphics.drawable.GradientDrawable activeBg = new android.graphics.drawable.GradientDrawable();
                activeBg.setColor(Color.parseColor("#1A00E5FF"));
                activeBg.setCornerRadius(8 * scale);
                holder.layout.setBackground(activeBg);
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                if (holder.tvNum != null) {
                    holder.tvNum.setTextColor(Color.parseColor("#6B6D7A"));
                }
                holder.layout.setBackground(null);
            }
            
            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    
                    String[] creds = getCredentials();
                    String dns = creds[0];
                    String username = creds[1];
                    String password = creds[2];
                    
                    if (m.isEpisode) {
                        String streamUrl = dns + "/series/" + username + "/" + password + "/" + m.id + "." + m.ext;
                        videoUrl = streamUrl;
                        title = (SeriesepisodesActivity.cachedSeriesName != null ? SeriesepisodesActivity.cachedSeriesName : "") + " - " + m.title;
                        if (tvPlayerTitle != null) {
                            tvPlayerTitle.setText(title != null ? title : "مشغل الفيديو");
                        }
                        startPlayback(0);
                    } else {
                        boolean isSeriesMode = (videoUrl != null && videoUrl.contains("/series/"));
                        if (isSeriesMode) {
                            SeriesActivity.SeriesItem matchedSeries = null;
                            if (SeriesActivity.cachedSeries != null) {
                                for (SeriesActivity.SeriesItem s : SeriesActivity.cachedSeries) {
                                    if (s.seriesId != null && s.seriesId.equals(m.id)) {
                                        matchedSeries = s;
                                        break;
                                    }
                                }
                            }
                            Intent intent = new Intent(PlayerActivity.this, SeriesdetailActivity.class);
                            intent.putExtra("series_id", m.id);
                            intent.putExtra("name", m.title);
                            intent.putExtra("cover", m.cover);
                            intent.putExtra("type", "series");
                            if (matchedSeries != null) {
                                intent.putExtra("plot", matchedSeries.plot);
                                intent.putExtra("cast", matchedSeries.cast);
                                intent.putExtra("director", matchedSeries.director);
                                intent.putExtra("genre", matchedSeries.genre);
                                intent.putExtra("releaseDate", matchedSeries.releaseDate);
                                intent.putExtra("rating", matchedSeries.rating);
                                intent.putExtra("category", matchedSeries.category);
                            }
                            startActivity(intent);
                            finish();
                        } else {
                            String streamUrl = dns + "/movie/" + username + "/" + password + "/" + m.id + "." + m.ext;
                            videoUrl = streamUrl;
                            title = m.title;
                            if (tvPlayerTitle != null) {
                                tvPlayerTitle.setText(title != null ? title : "مشغل الفيديو");
                            }
                            startPlayback(0);
                        }
                    }
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
