package com.blue.plus;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import java.net.NetworkInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SportsActivity extends Activity {

    private FrameLayout rootLayout;
    private static final String TAG = "SportsActivity";
    private final String THEME_BLUE_DARK = "#CC0A0E1A"; // Deep space translucent midnight blue-black
    private final String THEME_BLUE_LIGHT = "#AA1E293B"; // Slate blue-grey
    private final String STROKE_BLUE = "#4D38BDF8"; // Semi-transparent Electric sky blue
    private final String ICON_COLOR = "#38BDF8"; // Sky blue

    private LinearLayout matchesContainer;
    private FrameLayout loadingOverlay;
    private List<MatchFixture> allMatches = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, LIVE, UPCOMING, FINISHED
    private boolean isInitialLoad = true;
    private String currentLang;

    private static final String YACINE_EVENTS_URL = "https://a2.apk-api.com/api/events";
    private static final String YACINE_EVENT_DETAIL_URL = "https://a2.apk-api.com/api/event/";
    private static final String OVERRIDES_URL = "https://blueplus.pages.dev/sports_overrides.json";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLang != null && !currentLang.equals(TvUtil.getAppLanguage(this))) {
            recreate();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = TvUtil.getAppLanguage(this);
        TvUtil.enableTls12(this); // Fix SSL issues for older Android versions
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Semi-transparent overlay
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#55000000"));
        rootLayout.addView(overlay);

        buildUI();
        loadCachedBackground();
        fetchFixturesAsync();
        setContentView(rootLayout);
    }

    private void buildUI() {
        float scale = getResources().getDisplayMetrics().density;

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        mainLayout.setPadding((int) (24 * scale), (int) (16 * scale), (int) (24 * scale), (int) (16 * scale));

        // Top Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.bottomMargin = (int) (12 * scale);
        header.setLayoutParams(headerLp);

        // Back Arrow
        LinearLayout btnBack = new LinearLayout(this);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setClickable(true);
        btnBack.setFocusable(true);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (40 * scale), (int) (40 * scale));
        btnBack.setLayoutParams(backLp);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#22FFFFFF"));
        backBg.setStroke((int) (1.5 * scale), Color.parseColor("#55FFFFFF"));
        btnBack.setBackground(backBg);
        TvUtil.applyTvFocusHighlight(btnBack, 20.0f);

        ImageView ivBack = new ImageView(this);
        android.graphics.drawable.Drawable backIcon = getResources().getDrawable(android.R.drawable.ic_menu_revert);
        if (backIcon != null) {
            backIcon.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            ivBack.setImageDrawable(backIcon);
        }
        btnBack.addView(ivBack, new LinearLayout.LayoutParams((int) (22 * scale), (int) (22 * scale)));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        header.addView(btnBack);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "جدول مباريات اليوم"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        titleLp.rightMargin = (int) (40 * scale);
        tvTitle.setLayoutParams(titleLp);
        header.addView(tvTitle);

        mainLayout.addView(header);

        // Filter Bar (ALL, LIVE, UPCOMING, FINISHED)
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(-1, -2);
        filterLp.bottomMargin = (int) (14 * scale);
        filterBar.setLayoutParams(filterLp);

        addFilterButton(filterBar, "الكل", "ALL", scale);
        addFilterButton(filterBar, "مباشر الآن 🔴", "LIVE", scale);
        addFilterButton(filterBar, "بانتظار البدء ⏱️", "UPCOMING", scale);
        addFilterButton(filterBar, "انتهت 🏁", "FINISHED", scale);

        mainLayout.addView(filterBar);

        // Scrollview of matches
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        scrollView.setVerticalScrollBarEnabled(false);

        matchesContainer = new LinearLayout(this);
        matchesContainer.setOrientation(LinearLayout.VERTICAL);
        matchesContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        matchesContainer.setLayoutParams(new ScrollView.LayoutParams(-1, -2));
        scrollView.addView(matchesContainer);

        mainLayout.addView(scrollView);
        rootLayout.addView(mainLayout);

        // Setup loading overlay
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        loadingOverlay.setBackgroundColor(Color.parseColor("#55000000"));
        loadingOverlay.setVisibility(View.GONE);
        loadingOverlay.setClickable(true);
        loadingOverlay.setFocusable(true);

        com.airbnb.lottie.LottieAnimationView lottie = new com.airbnb.lottie.LottieAnimationView(this);
        lottie.setAnimation("loading.json");
        lottie.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        lottie.playAnimation();
        FrameLayout.LayoutParams lottieParams = new FrameLayout.LayoutParams((int)(160 * scale), (int)(160 * scale));
        lottieParams.gravity = Gravity.CENTER;
        lottie.setLayoutParams(lottieParams);
        loadingOverlay.addView(lottie);

        rootLayout.addView(loadingOverlay);
    }

    private void addFilterButton(final LinearLayout container, String title, final String filterTag, final float scale) {
        final TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (16 * scale), (int) (8 * scale), (int) (16 * scale), (int) (8 * scale));
        tv.setClickable(true);
        tv.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = (int) (6 * scale);
        lp.rightMargin = (int) (6 * scale);
        tv.setLayoutParams(lp);

        updateFilterButtonBg(tv, filterTag.equals(currentFilter), scale);
        TvUtil.applyTvFocusHighlight(tv, 15.0f);

        if (filterTag.equals("ALL")) {
            tv.post(new Runnable() {
                @Override
                public void run() {
                    tv.requestFocus();
                }
            });
        }

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = filterTag;
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    if (child instanceof TextView) {
                        updateFilterButtonBg((TextView) child, child == tv, scale);
                    }
                }
                filterFixtures(currentFilter);
            }
        });
        container.addView(tv);
    }

    private void updateFilterButtonBg(TextView tv, boolean active, float scale) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(15 * scale);
        if (active) {
            gd.setColor(Color.parseColor("#4D2196F3")); // Active theme blue tint
            gd.setStroke((int) (1.5 * scale), Color.parseColor("#FF2196F3"));
            tv.setTextColor(Color.parseColor(ICON_COLOR));
        } else {
            gd.setColor(Color.parseColor("#1AFFFFFF")); // Glass transparent white
            gd.setStroke((int) (1.5 * scale), Color.parseColor("#33FFFFFF"));
            tv.setTextColor(Color.WHITE);
        }
        tv.setBackground(gd);
    }

    private static JSONObject fetchAndDecryptYacine(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "okhttp/4.12.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();
            
            if (conn.getResponseCode() == 200) {
                String tHeader = conn.getHeaderField("t");
                if (tHeader == null) {
                    for (String key : conn.getHeaderFields().keySet()) {
                        if (key != null && key.equalsIgnoreCase("t")) {
                            tHeader = conn.getHeaderField(key);
                            break;
                        }
                    }
                }
                
                if (tHeader != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    
                    String body = sb.toString();
                    byte[] decodedBytes = android.util.Base64.decode(body, android.util.Base64.DEFAULT);
                    
                    String keyStr = "c!xZj+N9&G@Ev@vw" + tHeader.trim();
                    byte[] keyBytes = keyStr.getBytes("UTF-8");
                    byte[] out = new byte[decodedBytes.length];
                    for (int i = 0; i < decodedBytes.length; i++) {
                        out[i] = (byte) (decodedBytes[i] ^ keyBytes[i % keyBytes.length]);
                    }
                    
                    String decryptedJson = new String(out, "UTF-8");
                    return new JSONObject(decryptedJson);
                }
            }
        } catch (Exception e) {
            Log.e("YacineDecrypt", "Error: " + e.getMessage());
        }
        return null;
    }

    private void fetchFixturesAsync() {
        loadingOverlay.setVisibility(View.VISIBLE);
        final SharedPreferences spSettings = getSharedPreferences("Settings", MODE_PRIVATE);
        final String timeFormat = spSettings.getString("time_format", "12");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<MatchFixture> fetched = new ArrayList<>();
                try {
                    // 1. Fetch overrides from GitHub Pages
                    JSONObject overridesObj = new JSONObject();
                    try {
                        URL overridesUrl = new URL(OVERRIDES_URL + "?t=" + System.currentTimeMillis());
                        HttpURLConnection connOver = (HttpURLConnection) overridesUrl.openConnection();
                        connOver.setRequestMethod("GET");
                        connOver.setConnectTimeout(8000);
                        connOver.setReadTimeout(8000);
                        connOver.setRequestProperty("User-Agent", "Mozilla/5.0");
                        if (connOver.getResponseCode() == 200) {
                            BufferedReader rOver = new BufferedReader(new InputStreamReader(connOver.getInputStream(), "UTF-8"));
                            StringBuilder sbOver = new StringBuilder();
                            String lineOver;
                            while ((lineOver = rOver.readLine()) != null) sbOver.append(lineOver);
                            rOver.close();
                            String responseBody = sbOver.toString();
                            String decrypted = TvUtil.decryptAES(responseBody);
                            if (decrypted != null && !decrypted.isEmpty()) {
                                overridesObj = new JSONObject(decrypted);
                            }
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Overrides fetch failed: " + ex.getMessage());
                    }

                    List<String> processedIds = new ArrayList<>();

                    // 2. Fetch and decrypt matches list directly from Yacine API
                    JSONObject eventsJson = fetchAndDecryptYacine(YACINE_EVENTS_URL);
                    if (eventsJson != null && eventsJson.has("data")) {
                        JSONArray eventsArr = eventsJson.getJSONArray("data");
                        for (int i = 0; i < eventsArr.length(); i++) {
                            JSONObject ev = eventsArr.getJSONObject(i);
                            String matchId = ev.optString("id", "");
                            if (matchId.isEmpty()) continue;

                            processedIds.add(matchId);

                            JSONObject team1 = ev.optJSONObject("team_1");
                            JSONObject team2 = ev.optJSONObject("team_2");
                            
                            String team1Name = (team1 != null) ? team1.optString("name", "") : ev.optString("team_1_name", "");
                            String team2Name = (team2 != null) ? team2.optString("name", "") : ev.optString("team_2_name", "");
                            String team1Logo = (team1 != null) ? team1.optString("logo", "") : ev.optString("team_1_logo", "");
                            String team2Logo = (team2 != null) ? team2.optString("logo", "") : ev.optString("team_2_logo", "");
                            
                            String champions = ev.optString("champions", "");
                            String commentary = ev.optString("commentary", "");
                            String channel = ev.optString("channel", "");
                            
                            long startTime = ev.optLong("start_time", 0);
                            long endTime = ev.optLong("end_time", 0);

                            // Apply custom overrides early for team name, start/end time, etc.
                            JSONObject override = null;
                            if (overridesObj.has(matchId)) {
                                override = overridesObj.optJSONObject(matchId);
                            }
                            if (override != null) {
                                team1Name = override.optString("team_1_name", override.optString("team1", team1Name));
                                team2Name = override.optString("team_2_name", override.optString("team2", team2Name));
                                team1Logo = override.optString("team_1_logo", override.optString("team1Logo", team1Logo));
                                team2Logo = override.optString("team_2_logo", override.optString("team2Logo", team2Logo));
                                champions = override.optString("champions", override.optString("league", champions));
                                commentary = override.optString("commentary", override.optString("commentator", commentary));
                                channel = override.optString("channel", channel);
                                startTime = override.optLong("start_time", override.optLong("startTime", startTime));
                                endTime = override.optLong("end_time", override.optLong("endTime", endTime));
                            }

                            // Calculate status with improved logic for missing end_times
                            long now = System.currentTimeMillis() / 1000;
                            
                            // If endTime is 0 or less than startTime, assume 2.5 hours duration
                            long effectiveEndTime = endTime;
                            if (effectiveEndTime <= startTime) {
                                effectiveEndTime = startTime + (150 * 60); // 150 minutes
                            }

                            String mappedStatus = "UPCOMING";
                            if (now < startTime) {
                                mappedStatus = "UPCOMING";
                            } else if (now >= startTime && now <= effectiveEndTime) {
                                mappedStatus = "LIVE";
                            } else {
                                mappedStatus = "FINISHED";
                            }

                            // Format start time
                            String formattedTime = "";
                            if ("24".equals(timeFormat)) {
                                java.text.SimpleDateFormat sdf24 = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH);
                                sdf24.setTimeZone(java.util.TimeZone.getTimeZone("GMT+3"));
                                if (startTime > 0) {
                                    try {
                                        java.util.Date date = new java.util.Date(startTime * 1000L);
                                        formattedTime = sdf24.format(date);
                                    } catch (Exception e) {}
                                }
                            } else {
                                java.text.SimpleDateFormat sdf12 = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH);
                                sdf12.setTimeZone(java.util.TimeZone.getTimeZone("GMT+3"));
                                if (startTime > 0) {
                                    try {
                                        java.util.Date date = new java.util.Date(startTime * 1000L);
                                        formattedTime = sdf12.format(date);
                                    } catch (Exception e) {}
                                }
                            }

                            MatchFixture fixture = new MatchFixture();
                            fixture.id = matchId;
                            fixture.team1 = team1Name;
                            fixture.team2 = team2Name;
                            fixture.team1Logo = team1Logo;
                            fixture.team2Logo = team2Logo;
                            fixture.league = champions;
                            fixture.time = formattedTime;
                            fixture.channel = channel;
                            fixture.commentator = commentary;
                            fixture.status = mappedStatus;

                            // Set score
                            if (mappedStatus.equals("UPCOMING")) {
                                fixture.score = "VS";
                            } else if (mappedStatus.equals("FINISHED")) {
                                fixture.score = "FT";
                            } else {
                                fixture.score = "LIVE";
                            }

                            // 3. Fetch event details containing original streams list
                            JSONArray origStreamArr = null;
                            JSONObject eventDetail = fetchAndDecryptYacine(YACINE_EVENT_DETAIL_URL + matchId);
                            if (eventDetail != null) {
                                if (eventDetail.has("data")) {
                                    origStreamArr = eventDetail.optJSONArray("data");
                                } else if (eventDetail.has("التدفقات")) {
                                    origStreamArr = eventDetail.optJSONArray("التدفقات");
                                } else if (eventDetail.has("streams")) {
                                    origStreamArr = eventDetail.optJSONArray("streams");
                                }
                            }

                            // 4. Parse custom overrides streams first
                            if (override != null) {
                                try {
                                    JSONArray customStreamArr = override.optJSONArray("streams");
                                    if (customStreamArr == null) {
                                        customStreamArr = override.optJSONArray("التدفقات");
                                    }
                                    if (customStreamArr != null) {
                                        for (int j = 0; j < customStreamArr.length(); j++) {
                                            JSONObject sobj = customStreamArr.optJSONObject(j);
                                            if (sobj != null) {
                                                MatchStream stream = new MatchStream();
                                                stream.name = sobj.has("الاسم") ? sobj.optString("الاسم", "بث") : (sobj.has("name") ? sobj.optString("name", "بث") : "بث");
                                                stream.url = sobj.optString("url", "").trim();
                                                stream.userAgent = sobj.has("user_agent") ? sobj.optString("user_agent", "") : (sobj.has("user-agent") ? sobj.optString("user-agent", "") : "");
                                                if (stream.userAgent.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                                    JSONObject headers = sobj.optJSONObject("headers");
                                                    if (headers == null) headers = sobj.optJSONObject("العناوين");
                                                    if (headers != null) {
                                                        stream.userAgent = headers.optString("User-Agent", headers.optString("user_agent", headers.optString("user-agent", "")));
                                                    }
                                                }
                                                stream.referer = sobj.has("المُحيل") ? sobj.optString("المُحيل", "") : (sobj.has("referer") ? sobj.optString("referer", "") : "");
                                                if (stream.referer.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                                    JSONObject headers = sobj.optJSONObject("headers");
                                                    if (headers == null) headers = sobj.optJSONObject("العناوين");
                                                    if (headers != null) {
                                                        stream.referer = headers.optString("Referer", headers.optString("referer", headers.optString("المُحيل", "")));
                                                    }
                                                }
                                                fixture.streams.add(stream);
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Failed parsing overrides streams: " + ex.getMessage());
                                }
                            }

                            // 5. Parse original streams from Yacine TV and append them only if no custom streams are present
                            if (origStreamArr != null && fixture.streams.isEmpty()) {
                                for (int j = 0; j < origStreamArr.length(); j++) {
                                    JSONObject sobj = origStreamArr.optJSONObject(j);
                                    if (sobj != null) {
                                        String origUrl = sobj.optString("url", "").trim();
                                        boolean exists = false;
                                        for (MatchStream s : fixture.streams) {
                                            if (s.url != null && s.url.equals(origUrl)) {
                                                exists = true;
                                                break;
                                            }
                                        }
                                        if (!exists) {
                                            MatchStream stream = new MatchStream();
                                            stream.name = sobj.has("الاسم") ? sobj.optString("الاسم", "بث") : (sobj.has("name") ? sobj.optString("name", "بث") : "بث");
                                            stream.url = origUrl;
                                            stream.userAgent = sobj.has("user_agent") ? sobj.optString("user_agent", "") : (sobj.has("user-agent") ? sobj.optString("user-agent", "") : "");
                                            if (stream.userAgent.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                                JSONObject headers = sobj.optJSONObject("headers");
                                                if (headers == null) headers = sobj.optJSONObject("العناوين");
                                                if (headers != null) {
                                                    stream.userAgent = headers.optString("User-Agent", headers.optString("user_agent", headers.optString("user-agent", "")));
                                                }
                                            }
                                            stream.referer = sobj.has("المُحيل") ? sobj.optString("المُحيل", "") : (sobj.has("referer") ? sobj.optString("referer", "") : "");
                                            if (stream.referer.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                                JSONObject headers = sobj.optJSONObject("headers");
                                                if (headers == null) headers = sobj.optJSONObject("العناوين");
                                                if (headers != null) {
                                                    stream.referer = headers.optString("Referer", headers.optString("referer", headers.optString("المُحيل", "")));
                                                }
                                            }
                                            fixture.streams.add(stream);
                                        }
                                    }
                                }
                            }

                            if (!fetched.contains(fixture)) {
                                fetched.add(fixture);
                            }
                        }
                    }

                    // 6. Process custom matches defined ONLY in overrides
                    java.util.Iterator<String> keys = overridesObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (processedIds.contains(key)) continue;

                        JSONObject override = overridesObj.optJSONObject(key);
                        if (override == null) continue;

                        String team1Name = override.optString("team_1_name", override.optString("team1", ""));
                        String team2Name = override.optString("team_2_name", override.optString("team2", ""));
                        if (team1Name.isEmpty() && team2Name.isEmpty()) continue; // Not a custom match structure

                        String team1Logo = override.optString("team_1_logo", override.optString("team1Logo", ""));
                        String team2Logo = override.optString("team_2_logo", override.optString("team2Logo", ""));
                        String champions = override.optString("champions", override.optString("league", ""));
                        String commentary = override.optString("commentary", override.optString("commentator", ""));
                        String channel = override.optString("channel", "");
                        
                        long startTime = override.optLong("start_time", override.optLong("startTime", 0));
                        long endTime = override.optLong("end_time", override.optLong("endTime", 0));
                        if (endTime <= startTime) {
                            endTime = startTime + (150 * 60);
                        }

                        long now = System.currentTimeMillis() / 1000;
                        String mappedStatus = "UPCOMING";
                        if (now < startTime) {
                            mappedStatus = "UPCOMING";
                        } else if (now >= startTime && now <= endTime) {
                            mappedStatus = "LIVE";
                        } else {
                            mappedStatus = "FINISHED";
                        }

                        String formattedTime = "";
                        if ("24".equals(timeFormat)) {
                            java.text.SimpleDateFormat sdf24 = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH);
                            sdf24.setTimeZone(java.util.TimeZone.getTimeZone("GMT+3"));
                            if (startTime > 0) {
                                try {
                                    java.util.Date date = new java.util.Date(startTime * 1000L);
                                    formattedTime = sdf24.format(date);
                                } catch (Exception e) {}
                            }
                        } else {
                            java.text.SimpleDateFormat sdf12 = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH);
                            sdf12.setTimeZone(java.util.TimeZone.getTimeZone("GMT+3"));
                            if (startTime > 0) {
                                try {
                                    java.util.Date date = new java.util.Date(startTime * 1000L);
                                    formattedTime = sdf12.format(date);
                                } catch (Exception e) {}
                            }
                        }

                        MatchFixture fixture = new MatchFixture();
                        fixture.id = key;
                        fixture.team1 = team1Name;
                        fixture.team2 = team2Name;
                        fixture.team1Logo = team1Logo;
                        fixture.team2Logo = team2Logo;
                        fixture.league = champions;
                        fixture.time = formattedTime;
                        fixture.channel = channel;
                        fixture.commentator = commentary;
                        fixture.status = mappedStatus;

                        if (mappedStatus.equals("UPCOMING")) {
                            fixture.score = "VS";
                        } else if (mappedStatus.equals("FINISHED")) {
                            fixture.score = "FT";
                        } else {
                            fixture.score = "LIVE";
                        }

                        JSONArray customStreamArr = override.optJSONArray("streams");
                        if (customStreamArr == null) {
                            customStreamArr = override.optJSONArray("التدفقات");
                        }
                        if (customStreamArr != null) {
                            for (int j = 0; j < customStreamArr.length(); j++) {
                                JSONObject sobj = customStreamArr.optJSONObject(j);
                                if (sobj != null) {
                                    MatchStream stream = new MatchStream();
                                    stream.name = sobj.has("الاسم") ? sobj.optString("الاسم", "بث") : (sobj.has("name") ? sobj.optString("name", "بث") : "بث");
                                    stream.url = sobj.optString("url", "").trim();
                                    stream.userAgent = sobj.has("user_agent") ? sobj.optString("user_agent", "") : (sobj.has("user-agent") ? sobj.optString("user-agent", "") : "");
                                    if (stream.userAgent.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                        JSONObject headers = sobj.optJSONObject("headers");
                                        if (headers == null) headers = sobj.optJSONObject("العناوين");
                                        if (headers != null) {
                                            stream.userAgent = headers.optString("User-Agent", headers.optString("user_agent", headers.optString("user-agent", "")));
                                        }
                                    }
                                    stream.referer = sobj.has("المُحيل") ? sobj.optString("المُحيل", "") : (sobj.has("referer") ? sobj.optString("referer", "") : "");
                                    if (stream.referer.isEmpty() && (sobj.has("headers") || sobj.has("العناوين"))) {
                                        JSONObject headers = sobj.optJSONObject("headers");
                                        if (headers == null) headers = sobj.optJSONObject("العناوين");
                                        if (headers != null) {
                                            stream.referer = headers.optString("Referer", headers.optString("referer", headers.optString("المُحيل", "")));
                                        }
                                    }
                                    fixture.streams.add(stream);
                                }
                            }
                        }

                        if (!fetched.contains(fixture)) {
                            fetched.add(fixture);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fixtures fetch failed: " + e.getMessage());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingOverlay.setVisibility(View.GONE);
                        allMatches.clear();
                        if (!fetched.isEmpty()) {
                            allMatches.addAll(fetched);
                        } else {
                            generateFixtures();
                        }
                        filterFixtures(currentFilter);
                    }
                });
            }
        }).start();
    }

    private void generateFixtures() {
        allMatches.add(new MatchFixture(
                "ريال مدريد", "برشلونة", 
                "دوري أبطال أوروبا", "21:00", 
                "beIN Sports 1 HD", "عصام الشوالي", 
                "LIVE", "2 - 1"
        ));
        allMatches.add(new MatchFixture(
                "مانشستر سيتي", "ليفربول", 
                "الدوري الإنجليزي", "19:30", 
                "beIN Sports 2 HD", "خليل البلوشي", 
                "FINISHED", "3 - 2"
        ));
        allMatches.add(new MatchFixture(
                "الهلال", "النصر", 
                "دوري روشن السعودي", "20:00", 
                "SSC 1 HD", "فهد العتيبي", 
                "UPCOMING", "VS"
        ));
    }

    private void filterFixtures(String filter) {
        matchesContainer.removeAllViews();
        float scale = getResources().getDisplayMetrics().density;

        List<MatchFixture> filtered = new ArrayList<>();
        for (final MatchFixture match : allMatches) {
            if (filter.equals("ALL") || match.status.equals(filter)) {
                filtered.add(match);
            }
        }

        for (int i = 0; i < filtered.size(); i++) {
            final MatchFixture match = filtered.get(i);

            View matchView = createMatchCard(match, scale);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (560 * scale), (int) (76 * scale));
            lp.bottomMargin = (int) (10 * scale);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            matchView.setLayoutParams(lp);
            matchesContainer.addView(matchView);
        }

        if (filtered.isEmpty()) {
            TextView noMatches = new TextView(this);
            noMatches.setText(TvUtil.translate(this, "لا توجد مباريات في هذا القسم حالياً!"));
            noMatches.setTextColor(Color.parseColor("#B0BEC5"));
            noMatches.setTextSize(14);
            noMatches.setGravity(Gravity.CENTER);
            noMatches.setPadding(0, (int)(40 * scale), 0, 0);
            matchesContainer.addView(noMatches);
        }

        if (isInitialLoad && !filtered.isEmpty()) {
            isInitialLoad = false;
            final View firstMatch = matchesContainer.getChildAt(0);
            if (firstMatch != null) {
                firstMatch.post(new Runnable() {
                    @Override
                    public void run() {
                        firstMatch.requestFocus();
                    }
                });
            }
        }
    }

    private View createMatchCard(final MatchFixture match, float scale) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (12 * scale), 0, (int) (12 * scale), 0);

        // Premium Translucent Space Blue-Black Gradient Background
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(THEME_BLUE_DARK), Color.parseColor(THEME_BLUE_LIGHT)}
        );
        gd.setCornerRadius(14 * scale);
        
        // Red glowing border for live matches, cyan for others
        if (match.status.equals("LIVE")) {
            gd.setStroke((int) (2 * scale), Color.parseColor("#FF4444"));
        } else {
            gd.setStroke((int) (1.5 * scale), Color.parseColor(STROKE_BLUE));
        }
        card.setBackground(gd);

        // --- Left Section: Watch / Stream Info ---
        LinearLayout leftSec = new LinearLayout(this);
        leftSec.setOrientation(LinearLayout.VERTICAL);
        leftSec.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -1, 1.3f);
        leftSec.setLayoutParams(leftLp);

        TextView tvWatch = new TextView(this);
        tvWatch.setTextSize(10);
        tvWatch.setTypeface(null, Typeface.BOLD);
        tvWatch.setGravity(Gravity.CENTER);
        tvWatch.setPadding((int) (8 * scale), (int) (4 * scale), (int) (8 * scale), (int) (4 * scale));

        GradientDrawable watchBg = new GradientDrawable();
        watchBg.setCornerRadius(8 * scale);

        if (match.status.equals("LIVE")) {
            tvWatch.setVisibility(View.VISIBLE);
            tvWatch.setText(TvUtil.translate(this, "شاهد الآن 🔴"));
            tvWatch.setTextColor(Color.WHITE);
            watchBg.setColor(Color.parseColor("#CC1100")); 
            watchBg.setStroke((int) (1 * scale), Color.parseColor("#FF2211"));
        } else {
            tvWatch.setVisibility(View.GONE);
        }
        tvWatch.setBackground(watchBg);
        leftSec.addView(tvWatch);

        TextView tvChannel = new TextView(this);
        tvChannel.setText(match.channel);
        tvChannel.setTextColor(Color.parseColor("#818CF8")); // Indigo
        tvChannel.setTextSize(9);
        tvChannel.setTypeface(null, Typeface.BOLD);
        tvChannel.setPadding(0, (int) (2 * scale), 0, 0);
        leftSec.addView(tvChannel);

        TextView tvComment = new TextView(this);
        tvComment.setText("🎤 " + match.commentator);
        tvComment.setTextColor(Color.parseColor("#94A3B8")); // Soft Slate
        tvComment.setTextSize(8);
        tvComment.setPadding(0, (int) (1 * scale), 0, 0);
        leftSec.addView(tvComment);

        card.addView(leftSec);

        // --- Center Section: Teams & Score ---
        LinearLayout centerSec = new LinearLayout(this);
        centerSec.setOrientation(LinearLayout.HORIZONTAL);
        centerSec.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, -1, 2.2f);
        centerSec.setLayoutParams(centerLp);

        // Team 1 Name
        TextView tvTeam1 = new TextView(this);
        tvTeam1.setText(match.team1);
        tvTeam1.setTextColor(Color.WHITE);
        tvTeam1.setTextSize(11);
        tvTeam1.setTypeface(null, Typeface.BOLD);
        tvTeam1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tvTeam1.setLayoutParams(t1Lp);
        centerSec.addView(tvTeam1);

        // Team 1 Logo
        ImageView ivLogo1 = new ImageView(this);
        ivLogo1.setLayoutParams(new LinearLayout.LayoutParams((int) (26 * scale), (int) (26 * scale)));
        ivLogo1.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!match.team1Logo.isEmpty()) {
            TvUtil.loadImage(ivLogo1, match.team1Logo);
        } else {
            ivLogo1.setImageDrawable(null);
        }
        centerSec.addView(ivLogo1);

        // Score / Time Box
        LinearLayout scoreBox = new LinearLayout(this);
        scoreBox.setOrientation(LinearLayout.VERTICAL);
        scoreBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams((int)(60 * scale), -2);
        scoreLp.leftMargin = (int)(6 * scale);
        scoreLp.rightMargin = (int)(6 * scale);
        scoreBox.setLayoutParams(scoreLp);

        TextView tvScore = new TextView(this);
        String displayScore = match.score;
        if (displayScore == null || displayScore.isEmpty() || displayScore.contains("انتهت")) {
            displayScore = match.status.equals("FINISHED") ? "FT" : "VS";
        }
        tvScore.setText(displayScore);
        tvScore.setTextColor(match.status.equals("LIVE") ? Color.parseColor("#FF4444") : Color.WHITE);
        tvScore.setTextSize(14);
        tvScore.setTypeface(null, Typeface.BOLD);
        tvScore.setGravity(Gravity.CENTER);
        scoreBox.addView(tvScore);

        TextView tvTime = new TextView(this);
        if (match.status.equals("FINISHED") && (match.time == null || match.time.isEmpty() || match.time.contains("انتهت"))) {
            tvTime.setVisibility(View.GONE);
        } else {
            tvTime.setText(match.time);
            tvTime.setVisibility(View.VISIBLE);
        }
        tvTime.setTextColor(Color.parseColor("#94A3B8"));
        tvTime.setTextSize(8);
        tvTime.setGravity(Gravity.CENTER);
        scoreBox.addView(tvTime);

        centerSec.addView(scoreBox);

        // Team 2 Logo
        ImageView ivLogo2 = new ImageView(this);
        ivLogo2.setLayoutParams(new LinearLayout.LayoutParams((int) (26 * scale), (int) (26 * scale)));
        ivLogo2.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!match.team2Logo.isEmpty()) {
            TvUtil.loadImage(ivLogo2, match.team2Logo);
        } else {
            ivLogo2.setImageDrawable(null);
        }
        centerSec.addView(ivLogo2);

        // Team 2 Name
        TextView tvTeam2 = new TextView(this);
        tvTeam2.setText(match.team2);
        tvTeam2.setTextColor(Color.WHITE);
        tvTeam2.setTextSize(11);
        tvTeam2.setTypeface(null, Typeface.BOLD);
        tvTeam2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t2Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tvTeam2.setLayoutParams(t2Lp);
        centerSec.addView(tvTeam2);

        card.addView(centerSec);

        // --- Right Section: League & Status Info ---
        LinearLayout rightSec = new LinearLayout(this);
        rightSec.setOrientation(LinearLayout.VERTICAL);
        rightSec.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1.3f);
        rightSec.setLayoutParams(rightLp);

        TextView tvLeague = new TextView(this);
        tvLeague.setText(match.league);
        tvLeague.setTextColor(Color.WHITE);
        tvLeague.setTextSize(11);
        tvLeague.setTypeface(null, Typeface.BOLD);
        tvLeague.setGravity(Gravity.RIGHT);
        rightSec.addView(tvLeague);

        TextView tvStatus = new TextView(this);
        if (match.status.equals("LIVE")) {
            tvStatus.setText(TvUtil.translate(this, "مباراة جارية ⚽"));
            tvStatus.setTextColor(Color.parseColor("#FF4444"));
        } else if (match.status.equals("FINISHED")) {
            tvStatus.setText(TvUtil.translate(this, "انتهت 🏁"));
            tvStatus.setTextColor(Color.parseColor("#94A3B8"));
        } else {
            tvStatus.setText(TvUtil.translate(this, "تبدأ قريباً ⏱️"));
            tvStatus.setTextColor(Color.parseColor(ICON_COLOR));
        }
        tvStatus.setTextSize(9);
        tvStatus.setPadding(0, (int) (2 * scale), 0, 0);
        rightSec.addView(tvStatus);

        card.addView(rightSec);

        // Click Action: Launch streams dialog if available, otherwise search local channel
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!match.streams.isEmpty()) {
                    showStreamsQualityDialog(match);
                } else {
                    playMatchChannel(match.channel);
                }
            }
        });
        TvUtil.applyTvFocusHighlight(card);
        return card;
    }

    private void showStreamsQualityDialog(final MatchFixture match) {
        float scale = getResources().getDisplayMetrics().density;
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        
        TextView intro = new TextView(this);
        intro.setText(TvUtil.translate(this, "اختر جودة البث والتشغيل المفضلة لديك:"));
        intro.setTextColor(Color.WHITE);
        intro.setTextSize(13);
        intro.setGravity(Gravity.RIGHT);
        intro.setPadding(0, 0, 0, (int)(10 * scale));
        container.addView(intro);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        for (final MatchStream stream : match.streams) {
            TextView btnStream = new TextView(this);
            btnStream.setText("▶ " + stream.name);
            btnStream.setTextColor(Color.WHITE);
            btnStream.setTextSize(13);
            btnStream.setTypeface(null, Typeface.BOLD);
            btnStream.setGravity(Gravity.CENTER);
            btnStream.setPadding((int)(16 * scale), (int)(10 * scale), (int)(16 * scale), (int)(10 * scale));
            btnStream.setClickable(true);
            btnStream.setFocusable(true);
            
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(Color.parseColor("#4D2196F3"));
            btnBg.setCornerRadius(10 * scale);
            btnBg.setStroke((int)(1.5 * scale), Color.parseColor("#802196F3"));
            btnStream.setBackground(btnBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(8 * scale);
            btnStream.setLayoutParams(lp);

            btnStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    playDirectStream(stream, match.team1 + " vs " + match.team2);
                }
            });
            TvUtil.applyTvFocusHighlight(btnStream);
            container.addView(btnStream);
        }

        // Custom premium floating dialog styling
        LinearLayout cardContainer = new LinearLayout(this);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setPadding((int)(20 * scale), (int)(16 * scale), (int)(20 * scale), (int)(16 * scale));
        
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(THEME_BLUE_DARK), Color.parseColor(THEME_BLUE_LIGHT)}
        );
        gd.setCornerRadius(16 * scale);
        gd.setStroke((int)(2 * scale), Color.parseColor(STROKE_BLUE));
        cardContainer.setBackground(gd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "اختر جودة البث المباشر"));
        tvTitle.setTextColor(Color.parseColor(ICON_COLOR));
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, (int)(12 * scale));
        cardContainer.addView(tvTitle);
        cardContainer.addView(container);

        TextView btnCancel = new TextView(this);
        btnCancel.setText(TvUtil.translate(this, "إلغاء"));
        btnCancel.setTextColor(Color.parseColor("#B0BEC5"));
        btnCancel.setTextSize(13);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding((int)(16 * scale), (int)(8 * scale), (int)(16 * scale), (int)(8 * scale));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.parseColor("#1AFFFFFF"));
        cancelBg.setCornerRadius(8 * scale);
        cancelBg.setStroke((int)(1.5 * scale), Color.parseColor("#33FFFFFF"));
        btnCancel.setBackground(cancelBg);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams((int)(120 * scale), -2);
        cancelLp.gravity = Gravity.CENTER_HORIZONTAL;
        cancelLp.topMargin = (int)(8 * scale);
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        TvUtil.applyTvFocusHighlight(btnCancel, 8.0f);
        cardContainer.addView(btnCancel);

        dialog.setContentView(cardContainer);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int)(360 * scale), -2);
        }
        dialog.show();
    }

    private void playDirectStream(MatchStream stream, String title) {
        Toast.makeText(this, "جاري فتح البث المباشر المخصص بجودة عالية...", Toast.LENGTH_SHORT).show();
        
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("url", stream.url);
        intent.putExtra("title", title);
        
        // Pass customized headers if present
        if (!stream.userAgent.isEmpty()) {
            intent.putExtra("user_agent", stream.userAgent);
        }
        if (!stream.referer.isEmpty()) {
            intent.putExtra("referer", stream.referer);
        }
        
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void playMatchChannel(final String channelName) {
        Toast.makeText(this, "جاري البحث عن القناة الناقلة: " + channelName + "...", Toast.LENGTH_LONG).show();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(getExternalFilesDir(null), "xtream_live.json");
                if (!file.exists()) {
                    showToastOnUI("لا توجد قنوات محملة في الذاكرة لتشغيلها!");
                    return;
                }

                String cleanChannelName = channelName.replace(" HD", "").trim();
                String targetStreamId = "";
                String targetName = "";
                
                try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (name.equals("data")) {
                            reader.beginObject();
                            while (reader.hasNext()) {
                                String key = reader.nextName();
                                if (key.equals("get_live_streams")) {
                                    reader.beginArray();
                                    while (reader.hasNext()) {
                                        reader.beginObject();
                                        String streamName = "";
                                        String streamId = "";
                                        while (reader.hasNext()) {
                                            String subKey = reader.nextName();
                                            if (subKey.equals("name")) {
                                                streamName = reader.nextString();
                                            } else if (subKey.equals("stream_id")) {
                                                streamId = reader.nextString();
                                            } else {
                                                reader.skipValue();
                                            }
                                        }
                                        reader.endObject();
                                        
                                        if (!streamName.isEmpty() && streamName.toLowerCase().contains(cleanChannelName.toLowerCase())) {
                                            targetStreamId = streamId;
                                            targetName = streamName;
                                            break;
                                        }
                                    }
                                    if (!targetStreamId.isEmpty()) break;
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
                } catch (Exception e) {
                    Log.e(TAG, "Search channels failed: " + e.getMessage());
                }

                if (!targetStreamId.isEmpty()) {
                    final String finalId = targetStreamId;
                    final String finalName = targetName;
                    
                    SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                    String activeDns = sp.getString("active_dns", "");
                    String activeUser = sp.getString("active_username", "");
                    String activePass = sp.getString("active_password", "");

                     if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                         String listJson = sp.getString("list", "[]");
                         ArrayList<Map<String, Object>> list = new Gson().fromJson(listJson, new TypeToken<ArrayList<Map<String, Object>>>(){}.getType());
                         if (list != null && !list.isEmpty()) {
                             Map<String, Object> lastItem = list.get(list.size() - 1);
                             activeDns = (String) lastItem.get("dns");
                             activeUser = (String) lastItem.get("username");
                             activePass = (String) lastItem.get("password");
                         }
                     }

                     final String dns = activeDns;
                     final String user = activeUser;
                     final String pass = activePass;
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String streamFormat = getSharedPreferences("Settings", MODE_PRIVATE).getString("stream_format", "ts");
                                if ("auto".equals(streamFormat)) {
                                    streamFormat = "ts";
                                }
                                String playUrl = dns + "/live/" + user + "/" + pass + "/" + finalId + "." + streamFormat;
                                
                                Intent intent = new Intent(SportsActivity.this, PlayerActivity.class);
                                intent.putExtra("url", playUrl);
                                intent.putExtra("title", finalName);
                                startActivity(intent);
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            }
                        });
                } else {
                    showToastOnUI("عذراً، لم نتمكن من العثور على البث النشط لـ " + channelName + " في قائمتك حالياً!");
                }
            }
        }).start();
    }

    private void showToastOnUI(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SportsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void loadCachedBackground() {
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
                    rootLayout.setBackground(drawable);
                } else {
                    f.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed loading sports background: " + e.getMessage());
        }
    }

    private static class MatchFixture {
        String id;
        String team1;
        String team2;
        String team1Logo;
        String team2Logo;
        String league;
        String time;
        String channel;
        String commentator;
        String status; // LIVE, UPCOMING, FINISHED
        String score;
        List<MatchStream> streams = new ArrayList<>();

        MatchFixture() {}

        MatchFixture(String team1, String team2, String league, String time, String channel, String commentator, String status, String score) {
            this.id = "";
            this.team1 = team1;
            this.team2 = team2;
            this.team1Logo = "";
            this.team2Logo = "";
            this.league = league;
            this.time = time;
            this.channel = channel;
            this.commentator = commentator;
            this.status = status;
            this.score = score;
        }
    }

    private static class MatchStream {
        String name;
        String url;
        String userAgent;
        String referer;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}
