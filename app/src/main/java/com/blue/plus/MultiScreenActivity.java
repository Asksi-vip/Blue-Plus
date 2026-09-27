package com.blue.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MultiScreenActivity extends Activity {

    private static final int MAX_SCREENS = 4;
    private int currentScreenCount = 2; // 2 or 4
    private int activeAudioIndex = 0;
    private int fullscreenIndex = -1; // -1 means grid mode

    private FrameLayout rootLayout;
    private LinearLayout gridContainer;
    private TextView tvActiveAudio;
    private TextView btnToggleLayout;

    private ScreenSlot[] screens = new ScreenSlot[MAX_SCREENS];
    private List<LiveActivity.ChannelItem> availableChannels = new ArrayList<>();

    private static class ScreenSlot {
        FrameLayout container;
        StyledPlayerView playerView;
        ExoPlayer player;
        TextView tvChannelName;
        TextView btnPickChannel;
        TextView btnAudio;
        TextView btnExpand;
        View highlightBorder;
        String channelUrl = "";
        String channelName = "";
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUtil.hideSystemUI(this);

        SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        currentScreenCount = sp.getInt("multiscreen_count", 2);
        int countExtra = getIntent().getIntExtra("screens_count", -1);
        if (countExtra == 2 || countExtra == 4) {
            currentScreenCount = countExtra;
        } else if (currentScreenCount != 2 && currentScreenCount != 4) {
            currentScreenCount = 2;
        }

        loadAvailableChannels();
        buildUI();

        // If launched with an initial channel from LiveActivity
        String initialUrl = getIntent().getStringExtra("initial_url");
        String initialName = getIntent().getStringExtra("initial_name");
        if (initialName == null || initialName.isEmpty()) {
            initialName = getIntent().getStringExtra("initial_title");
        }
        if (initialUrl != null && !initialUrl.isEmpty()) {
            playOnScreen(0, initialUrl, initialName != null ? initialName : "قناة 1");
        } else if (!availableChannels.isEmpty()) {
            playOnScreen(0, availableChannels.get(0).url, availableChannels.get(0).name);
            if (availableChannels.size() > 1) {
                playOnScreen(1, availableChannels.get(1).url, availableChannels.get(1).name);
            }
        }
    }

    private void buildUI() {
        float scale = getResources().getDisplayMetrics().density;
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#06080C"));

        // Header Bar (Apple Liquid Glass)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, (int)(48 * scale));
        header.setLayoutParams(headerLp);
        header.setPadding((int)(16 * scale), 0, (int)(16 * scale), 0);

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.parseColor("#E60B101C"));
        headerBg.setStroke((int)(1 * scale), Color.parseColor("#2680B4FF"));
        header.setBackground(headerBg);

        // Back Button
        TextView btnBack = new TextView(this);
        btnBack.setText("❮ " + TvUtil.translate(this, "رجوع"));
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(13);
        btnBack.setTypeface(null, Typeface.BOLD);
        btnBack.setPadding((int)(12 * scale), (int)(6 * scale), (int)(12 * scale), (int)(6 * scale));
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Color.parseColor("#2280B4FF"));
        backBg.setCornerRadius(14 * scale);
        btnBack.setBackground(backBg);
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "الشاشات المتعددة (Multi-Screen)"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding((int)(16 * scale), 0, (int)(16 * scale), 0);
        header.addView(tvTitle);

        // Active Audio Indicator Pill
        tvActiveAudio = new TextView(this);
        tvActiveAudio.setText("🔊 " + TvUtil.translate(this, "الصوت: شاشة 1"));
        tvActiveAudio.setTextColor(Color.parseColor("#30D158"));
        tvActiveAudio.setTextSize(12);
        tvActiveAudio.setTypeface(null, Typeface.BOLD);
        tvActiveAudio.setPadding((int)(12 * scale), (int)(5 * scale), (int)(12 * scale), (int)(5 * scale));
        GradientDrawable audioBg = new GradientDrawable();
        audioBg.setColor(Color.parseColor("#2630D158"));
        audioBg.setCornerRadius(14 * scale);
        audioBg.setStroke((int)(1 * scale), Color.parseColor("#6630D158"));
        tvActiveAudio.setBackground(audioBg);
        LinearLayout.LayoutParams audioLp = new LinearLayout.LayoutParams(-2, -2);
        audioLp.leftMargin = (int)(12 * scale);
        audioLp.rightMargin = (int)(12 * scale);
        header.addView(tvActiveAudio, audioLp);

        // Flexible Spacer
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        // Layout Toggle Button (2 Screens / 4 Screens)
        btnToggleLayout = new TextView(this);
        btnToggleLayout.setText(currentScreenCount == 2 ? "⊞ " + TvUtil.translate(this, "تبديل إلى 4 شاشات") : "⊟ " + TvUtil.translate(this, "تبديل إلى شاشتين"));
        btnToggleLayout.setTextColor(Color.WHITE);
        btnToggleLayout.setTextSize(12);
        btnToggleLayout.setTypeface(null, Typeface.BOLD);
        btnToggleLayout.setPadding((int)(14 * scale), (int)(6 * scale), (int)(14 * scale), (int)(6 * scale));
        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(Color.parseColor("#0A84FF"));
        toggleBg.setCornerRadius(14 * scale);
        btnToggleLayout.setBackground(toggleBg);
        btnToggleLayout.setOnClickListener(v -> toggleLayoutMode());
        header.addView(btnToggleLayout);

        rootLayout.addView(header);

        // Grid Container below header
        gridContainer = new LinearLayout(this);
        FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(-1, -1);
        gridLp.topMargin = (int)(48 * scale);
        gridContainer.setLayoutParams(gridLp);
        rootLayout.addView(gridContainer);

        setupScreensLayout();
        setContentView(rootLayout);
    }

    private void setupScreensLayout() {
        gridContainer.removeAllViews();
        fullscreenIndex = -1;

        if (currentScreenCount == 2) {
            gridContainer.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 2; i++) {
                if (screens[i] == null) screens[i] = createScreenSlot(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1.0f);
                lp.setMargins(2, 2, 2, 2);
                screens[i].container.setLayoutParams(lp);
                screens[i].container.setVisibility(View.VISIBLE);
                gridContainer.addView(screens[i].container);
            }
            for (int i = 2; i < MAX_SCREENS; i++) {
                if (screens[i] != null && screens[i].player != null) {
                    screens[i].player.pause();
                }
            }
        } else {
            // 4 Screens: 2 vertical rows, each with 2 horizontal screens
            gridContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
            row1.setLayoutParams(row1Lp);

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
            row2.setLayoutParams(row2Lp);

            for (int i = 0; i < 4; i++) {
                if (screens[i] == null) screens[i] = createScreenSlot(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1.0f);
                lp.setMargins(2, 2, 2, 2);
                screens[i].container.setLayoutParams(lp);
                screens[i].container.setVisibility(View.VISIBLE);
                if (i < 2) row1.addView(screens[i].container);
                else row2.addView(screens[i].container);
            }
            gridContainer.addView(row1);
            gridContainer.addView(row2);
        }

        updateAudioState();
    }

    private ScreenSlot createScreenSlot(final int index) {
        float scale = getResources().getDisplayMetrics().density;
        ScreenSlot slot = new ScreenSlot();

        slot.container = new FrameLayout(this);
        slot.container.setBackgroundColor(Color.BLACK);

        // PlayerView
        slot.playerView = new StyledPlayerView(this);
        slot.playerView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        slot.playerView.setUseController(false);

        // Initialize ExoPlayer with stable synchronous MediaCodec & fallback
        DefaultRenderersFactory rf = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(4000, 15000, 800, 1200)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        slot.player = new ExoPlayer.Builder(this, rf)
                .setLoadControl(lc)
                .build();

        slot.playerView.setPlayer(slot.player);
        slot.container.addView(slot.playerView);

        // Highlight selection border
        slot.highlightBorder = new View(this);
        slot.highlightBorder.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        slot.container.addView(slot.highlightBorder);

        // Frosted Control Overlay at Top of Screen
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams overLp = new FrameLayout.LayoutParams(-1, (int)(36 * scale));
        overLp.gravity = Gravity.TOP;
        overlay.setLayoutParams(overLp);
        overlay.setPadding((int)(8 * scale), (int)(4 * scale), (int)(8 * scale), (int)(4 * scale));

        GradientDrawable overBg = new GradientDrawable();
        overBg.setColor(Color.parseColor("#CC0B101C"));
        overlay.setBackground(overBg);

        // Channel Tag / Name
        slot.tvChannelName = new TextView(this);
        slot.tvChannelName.setText(TvUtil.translate(this, "شاشة ") + (index + 1));
        slot.tvChannelName.setTextColor(Color.WHITE);
        slot.tvChannelName.setTextSize(11);
        slot.tvChannelName.setTypeface(null, Typeface.BOLD);
        slot.tvChannelName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        overlay.addView(slot.tvChannelName);

        // Audio Button
        slot.btnAudio = new TextView(this);
        slot.btnAudio.setText(index == activeAudioIndex ? "🔊" : "🔇");
        slot.btnAudio.setTextSize(13);
        slot.btnAudio.setPadding((int)(8 * scale), (int)(2 * scale), (int)(8 * scale), (int)(2 * scale));
        slot.btnAudio.setOnClickListener(v -> setActiveAudio(index));
        overlay.addView(slot.btnAudio);

        // Channel Picker Button
        slot.btnPickChannel = new TextView(this);
        slot.btnPickChannel.setText("📺 " + TvUtil.translate(this, "قناة"));
        slot.btnPickChannel.setTextColor(Color.WHITE);
        slot.btnPickChannel.setTextSize(10.5f);
        slot.btnPickChannel.setTypeface(null, Typeface.BOLD);
        slot.btnPickChannel.setPadding((int)(8 * scale), (int)(4 * scale), (int)(8 * scale), (int)(4 * scale));
        GradientDrawable pickBg = new GradientDrawable();
        pickBg.setColor(Color.parseColor("#3380B4FF"));
        pickBg.setCornerRadius(10 * scale);
        slot.btnPickChannel.setBackground(pickBg);
        slot.btnPickChannel.setOnClickListener(v -> showChannelPickerForScreen(index));
        overlay.addView(slot.btnPickChannel);

        // Fullscreen Expand / Restore Button
        slot.btnExpand = new TextView(this);
        slot.btnExpand.setText(" ⛶ ");
        slot.btnExpand.setTextColor(Color.WHITE);
        slot.btnExpand.setTextSize(13);
        slot.btnExpand.setPadding((int)(6 * scale), (int)(2 * scale), (int)(6 * scale), (int)(2 * scale));
        slot.btnExpand.setOnClickListener(v -> toggleFullscreenScreen(index));
        overlay.addView(slot.btnExpand);

        slot.container.addView(overlay);

        // Clicking screen selects its audio
        slot.container.setOnClickListener(v -> setActiveAudio(index));

        // Error recovery listener
        slot.player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // Auto-fallback TS <-> M3U8 on error
                if (slot.channelUrl != null && !slot.channelUrl.isEmpty()) {
                    String fallbackUrl = slot.channelUrl;
                    if (fallbackUrl.endsWith(".ts")) fallbackUrl = fallbackUrl.replace(".ts", ".m3u8");
                    else if (fallbackUrl.endsWith(".m3u8")) fallbackUrl = fallbackUrl.replace(".m3u8", ".ts");
                    if (!fallbackUrl.equals(slot.channelUrl)) {
                        playOnScreen(index, fallbackUrl, slot.channelName);
                    }
                }
            }
        });

        return slot;
    }

    private void setActiveAudio(int index) {
        activeAudioIndex = index;
        updateAudioState();
    }

    private void updateAudioState() {
        float scale = getResources().getDisplayMetrics().density;
        tvActiveAudio.setText("🔊 " + TvUtil.translate(this, "الصوت: شاشة ") + (activeAudioIndex + 1));

        for (int i = 0; i < currentScreenCount; i++) {
            ScreenSlot slot = screens[i];
            if (slot == null) continue;

            boolean isActive = (i == activeAudioIndex);
            if (slot.player != null) {
                slot.player.setVolume(isActive ? 1.0f : 0.0f);
            }
            if (slot.btnAudio != null) {
                slot.btnAudio.setText(isActive ? "🔊" : "🔇");
            }
            if (slot.highlightBorder != null) {
                GradientDrawable border = new GradientDrawable();
                if (isActive) {
                    border.setStroke((int)(2.5f * scale), Color.parseColor("#0A84FF"));
                } else {
                    border.setStroke((int)(1 * scale), Color.parseColor("#26FFFFFF"));
                }
                slot.highlightBorder.setBackground(border);
            }
        }
    }

    private void toggleFullscreenScreen(int index) {
        if (fullscreenIndex == index) {
            // Restore grid layout
            fullscreenIndex = -1;
            setupScreensLayout();
        } else {
            // Expand this screen full
            fullscreenIndex = index;
            gridContainer.removeAllViews();
            for (int i = 0; i < currentScreenCount; i++) {
                if (screens[i] != null) {
                    if (i == index) {
                        screens[i].container.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
                        screens[i].container.setVisibility(View.VISIBLE);
                        gridContainer.addView(screens[i].container);
                    } else {
                        screens[i].container.setVisibility(View.GONE);
                    }
                }
            }
            setActiveAudio(index);
        }
    }

    private void toggleLayoutMode() {
        currentScreenCount = (currentScreenCount == 2) ? 4 : 2;
        getSharedPreferences("Settings", MODE_PRIVATE).edit().putInt("multiscreen_count", currentScreenCount).apply();
        btnToggleLayout.setText(currentScreenCount == 2 ? "⊞ " + TvUtil.translate(this, "تبديل إلى 4 شاشات") : "⊟ " + TvUtil.translate(this, "تبديل إلى شاشتين"));
        setupScreensLayout();
        Toast.makeText(this, TvUtil.translate(this, "تم تفعيل نمط: ") + currentScreenCount + TvUtil.translate(this, " شاشات"), Toast.LENGTH_SHORT).show();
    }

    private void playOnScreen(int index, String url, String name) {
        if (index < 0 || index >= MAX_SCREENS) return;
        ScreenSlot slot = screens[index];
        if (slot == null || slot.player == null) return;

        slot.channelUrl = resolveStreamUrl(url);
        slot.channelName = name;
        slot.tvChannelName.setText("شاشة " + (index + 1) + ": " + TvUtil.formatNameByLanguage(name));

        MediaSource src = buildMediaSource(slot.channelUrl);
        slot.player.stop();
        slot.player.clearMediaItems();
        slot.player.setMediaSource(src);
        slot.player.prepare();
        slot.player.setPlayWhenReady(true);
        slot.player.setVolume(index == activeAudioIndex ? 1.0f : 0.0f);
    }

    private String resolveStreamUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return rawUrl;
        String formatSetting = getSharedPreferences("Settings", MODE_PRIVATE).getString("stream_format", "auto");
        if ("m3u8".equalsIgnoreCase(formatSetting)) {
            if (rawUrl.contains(".ts")) return rawUrl.replace(".ts", ".m3u8");
        } else if ("ts".equalsIgnoreCase(formatSetting)) {
            if (rawUrl.contains(".m3u8")) return rawUrl.replace(".m3u8", ".ts");
        } else if ("auto".equalsIgnoreCase(formatSetting)) {
            if (rawUrl.contains(".ts")) return rawUrl.replace(".ts", ".m3u8");
        }
        return rawUrl;
    }

    private MediaSource buildMediaSource(String streamUrl) {
        Uri uri = Uri.parse(streamUrl.trim());
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        httpFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");

        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        extractorsFactory.setTsExtractorFlags(1 | 8 | 64);
        extractorsFactory.setConstantBitrateSeekingEnabled(true);

        String lowercaseUrl = streamUrl.toLowerCase();
        if (lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains("hls")) {
            return new HlsMediaSource.Factory(httpFactory).createMediaSource(MediaItem.fromUri(uri));
        } else if (lowercaseUrl.contains(".mpd") || lowercaseUrl.contains("dash")) {
            return new DashMediaSource.Factory(httpFactory).createMediaSource(MediaItem.fromUri(uri));
        } else {
            return new ProgressiveMediaSource.Factory(httpFactory, extractorsFactory).createMediaSource(MediaItem.fromUri(uri));
        }
    }

    private void showChannelPickerForScreen(final int screenIndex) {
        if (availableChannels.isEmpty()) {
            Toast.makeText(this, TvUtil.translate(this, "لا توجد قنوات متاحة"), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] channelNames = new String[availableChannels.size()];
        for (int i = 0; i < availableChannels.size(); i++) {
            channelNames[i] = TvUtil.formatNameByLanguage(availableChannels.get(i).name);
        }

        int themeId = getResources().getIdentifier("PremiumDialogTheme", "style", getPackageName());
        AlertDialog.Builder b = (themeId != 0) ? new AlertDialog.Builder(this, themeId) : new AlertDialog.Builder(this);
        b.setTitle(TvUtil.translate(this, "اختر قناة للشاشة ") + (screenIndex + 1));
        b.setItems(channelNames, (dialog, which) -> {
            LiveActivity.ChannelItem selected = availableChannels.get(which);
            playOnScreen(screenIndex, selected.url, selected.name);
            setActiveAudio(screenIndex);
        });
        b.setNegativeButton(TvUtil.translate(this, "إلغاء"), null);
        b.show();
    }

    private void loadAvailableChannels() {
        if (LiveActivity.cachedChannels != null && !LiveActivity.cachedChannels.isEmpty()) {
            availableChannels = new ArrayList<>(LiveActivity.cachedChannels);
            return;
        }

        File cacheFile = new File(getCacheDir(), "live_data.json");
        if (!cacheFile.exists()) {
            cacheFile = new File(getExternalFilesDir(null), "xtream_live.json");
        }
        if (cacheFile.exists()) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(cacheFile), "UTF-8"))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("data")) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            if (key.equals("get_live_streams")) {
                                reader.beginArray();
                                int count = 0;
                                SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                                String dns = sp.getString("active_dns", "");
                                String user = sp.getString("active_username", "");
                                String pass = sp.getString("active_password", "");

                                while (reader.hasNext() && count < 300) {
                                    reader.beginObject();
                                    String cname = "", streamId = "";
                                    while (reader.hasNext()) {
                                        String k = reader.nextName();
                                        if (k.equals("name")) cname = reader.nextString();
                                        else if (k.equals("stream_id")) streamId = reader.nextString();
                                        else reader.skipValue();
                                    }
                                    reader.endObject();
                                    if (!streamId.isEmpty()) {
                                        String url = dns + "/live/" + user + "/" + pass + "/" + streamId + ".m3u8";
                                        availableChannels.add(new LiveActivity.ChannelItem(count + 1, cname, "", "General", url));
                                        count++;
                                    }
                                }
                                reader.endArray();
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
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (int i = 0; i < currentScreenCount; i++) {
            if (screens[i] != null && screens[i].player != null && screens[i].channelUrl != null && !screens[i].channelUrl.isEmpty()) {
                screens[i].player.setPlayWhenReady(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i] != null && screens[i].player != null) {
                screens[i].player.setPlayWhenReady(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i] != null && screens[i].player != null) {
                screens[i].player.stop();
                screens[i].player.release();
                screens[i].player = null;
            }
        }
    }
}
