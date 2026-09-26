package com.blue.plus;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import org.json.JSONObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Ot2Activity extends Activity {

    private FrameLayout rootLayout;
    private static final String TAG = "Ot2Activity";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private int liveCount = 0, movieCount = 0, seriesCount = 0;
    private final String THEME_COLOR = "#2196F3";
    private final String BG_BLUE_TRANS = "#222196F3";
    private final String STROKE_BLUE = "#552196F3";
    private String currentLang;
    
    private FrameLayout loadingOverlay;
    private RequestNetwork requestNetwork;
    private TextView tickerTv;

    private java.util.List<String> adUrls = new java.util.ArrayList<>();
    private int currentAdIndex = 0;
    private android.os.Handler adHandler = new android.os.Handler();
    private Runnable adRunnable;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adRunnable != null) {
            adHandler.removeCallbacks(adRunnable);
        }
    }

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
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        TvUtil.hideSystemUI(this);
        
        android.content.SharedPreferences spCount = getSharedPreferences("Playlists", MODE_PRIVATE);
        liveCount = spCount.getInt("cached_live_count", 0);
        movieCount = spCount.getInt("cached_movie_count", 0);
        seriesCount = spCount.getInt("cached_series_count", 0);

        requestNetwork = new RequestNetwork(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                parseDownloadedData();
            }
        }).start();
        createUI();
        loadCachedBackground();
        setupLoadingOverlay();
        registerCodeActivationIfNeeded();
    }

    private void setupLoadingOverlay() {
        float scale = getResources().getDisplayMetrics().density;
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
        
        FrameLayout.LayoutParams lottieParams = new FrameLayout.LayoutParams((int)(200 * scale), (int)(200 * scale));
        lottieParams.gravity = Gravity.CENTER;
        lottie.setLayoutParams(lottieParams);
        
        loadingOverlay.addView(lottie);
        rootLayout.addView(loadingOverlay);
    }

    private void refreshData() {
        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        String activeDns = sp.getString("active_dns", "");
        String activeUser = sp.getString("active_username", "");
        String activePass = sp.getString("active_password", "");
        String activeCode = sp.getString("active_code", "");
        final boolean onlyLive = sp.getBoolean("active_only_live", false);

        if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
            String json = sp.getString("list", "[]");
            java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
            if (list == null || list.isEmpty()) return;
            java.util.Map<String, Object> lastItem = list.get(list.size() - 1);
            activeDns = (String) lastItem.get("dns");
            activeUser = (String) lastItem.get("username");
            activePass = (String) lastItem.get("password");
            activeCode = lastItem.containsKey("code") ? (String) lastItem.get("code") : "";
        }

        final String dns = normalizeDns(activeDns);
        final String user = activeUser;
        final String pass = activePass;
        final String code = activeCode;

        loadingOverlay.setVisibility(View.VISIBLE);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Fetch and update real expiration date (only if not an activation code)
                    if (code == null || code.trim().isEmpty() || code.equalsIgnoreCase("VIP")) {
                        try {
                            java.net.URL loginUrl = new java.net.URL(dns + "/player_api.php?username=" + user + "&password=" + pass);
                            java.net.HttpURLConnection loginConn = (java.net.HttpURLConnection) loginUrl.openConnection();
                            loginConn.setRequestProperty("User-Agent", USER_AGENT);
                            loginConn.setConnectTimeout(10000);
                            loginConn.setReadTimeout(10000);
                            loginConn.connect();
                            if (loginConn.getResponseCode() == 200) {
                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(loginConn.getInputStream()));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                reader.close();
                                
                                org.json.JSONObject loginJson = new org.json.JSONObject(sb.toString());
                                if (loginJson.has("user_info")) {
                                    org.json.JSONObject userInfo = loginJson.getJSONObject("user_info");
                                    String expDateStr = userInfo.optString("exp_date", "");
                                    String formattedDate = formatExpiryDate(expDateStr);
                                    getSharedPreferences("Playlists", MODE_PRIVATE).edit().putString("expiry_date", formattedDate).apply();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to update expiry date: " + e.getMessage());
                        }
                    }

                    final java.io.File fileLive = new java.io.File(getExternalFilesDir(null), "xtream_live.json");
                    final java.io.File fileVod = new java.io.File(getExternalFilesDir(null), "xtream_vod.json");
                    final java.io.File fileSeries = new java.io.File(getExternalFilesDir(null), "xtream_series.json");

                    try {
                        // 1. Live channels
                        java.io.FileOutputStream outLive = new java.io.FileOutputStream(fileLive);
                        outLive.write("{\"data\":{".getBytes());
                        downloadStream(dns + "/player_api.php?action=get_live_categories&username=" + user + "&password=" + pass, outLive, "get_live_categories");
                        outLive.write(",".getBytes());
                        downloadStream(dns + "/player_api.php?action=get_live_streams&username=" + user + "&password=" + pass, outLive, "get_live_streams");
                        outLive.write("}}".getBytes());
                        outLive.flush(); outLive.close();

                        // 2. VOD / movies
                        java.io.FileOutputStream outVod = new java.io.FileOutputStream(fileVod);
                        if (onlyLive) {
                            outVod.write("{\"data\":{\"get_vod_categories\":[],\"get_vod_streams\":[]}}".getBytes());
                        } else {
                            outVod.write("{\"data\":{".getBytes());
                            downloadStream(dns + "/player_api.php?action=get_vod_categories&username=" + user + "&password=" + pass, outVod, "get_vod_categories");
                            outVod.write(",".getBytes());
                            downloadStream(dns + "/player_api.php?action=get_vod_streams&username=" + user + "&password=" + pass, outVod, "get_vod_streams");
                            outVod.write("}}".getBytes());
                        }
                        outVod.flush(); outVod.close();

                        // 3. Series
                        java.io.FileOutputStream outSeries = new java.io.FileOutputStream(fileSeries);
                        if (onlyLive) {
                            outSeries.write("{\"data\":{\"get_series_categories\":[],\"get_series\":[]}}".getBytes());
                        } else {
                            outSeries.write("{\"data\":{".getBytes());
                            downloadStream(dns + "/player_api.php?action=get_series_categories&username=" + user + "&password=" + pass, outSeries, "get_series_categories");
                            outSeries.write(",".getBytes());
                            downloadStream(dns + "/player_api.php?action=get_series&username=" + user + "&password=" + pass, outSeries, "get_series");
                            outSeries.write("}}".getBytes());
                        }
                        outSeries.flush(); outSeries.close();

                    } catch (Exception e) {
                        Log.e(TAG, "Refresh download failed: " + e.getMessage());
                        throw e;
                    }

                    try {
                        // Clear static memory caches to ensure the newly downloaded data is loaded
                        SeriesActivity.cachedMovies = null;
                        SeriesActivity.cachedSeries = null;
                        SeriesActivity.cachedMoviesCounts = null;
                        SeriesActivity.cachedSeriesCounts = null;
                        SeriesActivity.cachedMoviesIdToName = null;
                        SeriesActivity.cachedSeriesIdToName = null;
                        LiveActivity.cachedChannels = null;
                        LiveActivity.cachedCategoryCounts = null;
                        LiveActivity.cachedCategoryIdToName = null;
                    } catch (Throwable t) {
                        Log.e(TAG, "Static cache eviction failed: " + t.getMessage());
                    }

                    // Fetch and pre-download ad images synchronously inside the background thread of refreshData
                    try {
                        java.net.URL configUrl = new java.net.URL("https://blueplus-auz.pages.dev/settings.json");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) configUrl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.connect();
                        if (conn.getResponseCode() == 200) {
                            java.io.InputStream is = conn.getInputStream();
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            is.close();

                            String decrypted = TvUtil.decryptAES(sb.toString());
                            final org.json.JSONObject obj = new org.json.JSONObject(decrypted);
                             
                             // Parse news ticker texts (Dual language: AR & EN)
                             if (obj.has("ticker_texts_ar")) {
                                 org.json.JSONArray tickerArr = obj.getJSONArray("ticker_texts_ar");
                                 StringBuilder tickerSb = new StringBuilder();
                                 for (int idx = 0; idx < tickerArr.length(); idx++) {
                                     String txt = tickerArr.getString(idx).trim();
                                     if (!txt.isEmpty()) {
                                         if (tickerSb.length() > 0) tickerSb.append("   ✦   ");
                                         tickerSb.append(txt);
                                     }
                                 }
                                 getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("ticker_text_ar", tickerSb.toString()).apply();
                             }
                             if (obj.has("ticker_texts_en")) {
                                 org.json.JSONArray tickerArr = obj.getJSONArray("ticker_texts_en");
                                 StringBuilder tickerSb = new StringBuilder();
                                 for (int idx = 0; idx < tickerArr.length(); idx++) {
                                     String txt = tickerArr.getString(idx).trim();
                                     if (!txt.isEmpty()) {
                                         if (tickerSb.length() > 0) tickerSb.append("   ✦   ");
                                         tickerSb.append(txt);
                                     }
                                 }
                                 getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("ticker_text_en", tickerSb.toString()).apply();
                             }
                             if (obj.has("ticker_texts")) {
                                 org.json.JSONArray tickerArr = obj.getJSONArray("ticker_texts");
                                 StringBuilder tickerSb = new StringBuilder();
                                 for (int idx = 0; idx < tickerArr.length(); idx++) {
                                     String txt = tickerArr.getString(idx).trim();
                                     if (!txt.isEmpty()) {
                                         if (tickerSb.length() > 0) tickerSb.append("   ✦   ");
                                         tickerSb.append(txt);
                                     }
                                 }
                                 getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("ticker_text", tickerSb.toString()).apply();
                             }
                             
                             runOnUiThread(new Runnable() {
                                 @Override
                                 public void run() {
                                     if (tickerTv != null) {
                                         TvUtil.setupDualLanguageTicker(Ot2Activity.this, null, tickerTv);
                                     }
                                 }
                             });
                             
                             // Ad images are now loaded dynamically via Glide
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ad pre-download failed: " + e.getMessage());
                    }

                    parseDownloadedData();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loadingOverlay.setVisibility(View.GONE);
                            recreate(); // تحديث الواجهة بالأرقام الجديدة
                        }
                    });
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loadingOverlay.setVisibility(View.GONE);
                            android.widget.Toast.makeText(Ot2Activity.this, "فشل التحديث: " + errorMsg, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void downloadStream(String urlStr, java.io.FileOutputStream output, String key) throws Exception {
        output.write(("\"" + key + "\":").getBytes());
        java.io.BufferedOutputStream bufferedOut = new java.io.BufferedOutputStream(output, 131072); // 128KB output buffer to minimize disk write overhead
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();
        if (conn.getResponseCode() == 200) {
            java.io.InputStream rawInput = conn.getInputStream();
            String encoding = conn.getContentEncoding();
            if (encoding != null && "gzip".equalsIgnoreCase(encoding)) {
                rawInput = new java.util.zip.GZIPInputStream(rawInput);
            }
            java.io.InputStream is = new java.io.BufferedInputStream(rawInput, 65536); // 64KB read buffer
            byte[] buffer = new byte[65536]; // 64KB chunks
            int count;
            while ((count = is.read(buffer)) != -1) {
                bufferedOut.write(buffer, 0, count);
            }
            bufferedOut.flush(); // Ensure all buffered data is written to the FileOutputStream
            is.close();
        } else {
            throw new java.io.IOException("Server returned code " + conn.getResponseCode() + " for " + key);
        }
    }

    private void parseDownloadedData() {
        liveCount = 0;
        movieCount = 0;
        seriesCount = 0;

        // Parse live count
        File fileLive = new File(getExternalFilesDir(null), "xtream_live.json");
        if (fileLive.exists()) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(fileLive), "UTF-8"))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("data")) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            if (key.equals("get_live_streams")) {
                                liveCount = countArrayElements(reader);
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
                Log.e(TAG, "Live parse error: " + e.getMessage());
            }
        }

        // Parse movies count
        File fileVod = new File(getExternalFilesDir(null), "xtream_vod.json");
        if (fileVod.exists()) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(fileVod), "UTF-8"))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("data")) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            if (key.equals("get_vod_streams")) {
                                movieCount = countArrayElements(reader);
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
                Log.e(TAG, "Vod parse error: " + e.getMessage());
            }
        }

        // Parse series count
        File fileSeries = new File(getExternalFilesDir(null), "xtream_series.json");
        if (fileSeries.exists()) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(fileSeries), "UTF-8"))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("data")) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            if (key.equals("get_series")) {
                                seriesCount = countArrayElements(reader);
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
                Log.e(TAG, "Series parse error: " + e.getMessage());
            }
        }

        android.content.SharedPreferences spCount = getSharedPreferences("Playlists", MODE_PRIVATE);
        int oldLive = spCount.getInt("cached_live_count", -1);
        int oldMovie = spCount.getInt("cached_movie_count", -1);
        int oldSeries = spCount.getInt("cached_series_count", -1);

        spCount.edit()
                .putInt("cached_live_count", liveCount)
                .putInt("cached_movie_count", movieCount)
                .putInt("cached_series_count", seriesCount)
                .apply();

        if (oldLive != -1 && (oldLive != liveCount || oldMovie != movieCount || oldSeries != seriesCount)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recreate();
                }
            });
        }
    }

    private int countArrayElements(JsonReader reader) throws java.io.IOException {
        int count = 0;
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.skipValue();
                count++;
            }
            reader.endArray();
        } else {
            reader.skipValue();
        }
        return count;
    }

    private void createUI() {
        float scale = getResources().getDisplayMetrics().density;
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Apple Atmospheric Midnight Canvas Overlay
        View overlay = new View(this);
        overlay.setBackgroundColor(Color.parseColor("#7305070B"));
        rootLayout.addView(overlay);

        // Apple Header - Center Brand Logo
        ImageView logo = new ImageView(this);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams((int)(88 * scale), (int)(44 * scale));
        logoParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        logoParams.topMargin = (int)(12 * scale);
        logo.setLayoutParams(logoParams);
        logo.setImageResource(R.drawable.home_logo);
        loadCachedLogo(logo);
        rootLayout.addView(logo);

        // Apple Status Capsule (Expiry & Account Status Pill)
        final LinearLayout expiryPill = new LinearLayout(this);
        expiryPill.setOrientation(LinearLayout.HORIZONTAL);
        expiryPill.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams expiryParams = new FrameLayout.LayoutParams(-2, -2);
        expiryParams.gravity = Gravity.TOP | Gravity.RIGHT;
        expiryParams.topMargin = (int)(14 * scale);
        expiryParams.rightMargin = (int)(28 * scale);
        expiryPill.setLayoutParams(expiryParams);

        GradientDrawable expBg = new GradientDrawable();
        expBg.setColor(Color.parseColor("#E60B101C"));
        expBg.setCornerRadius(999 * scale);
        expBg.setStroke((int)(1.2f * scale), Color.parseColor("#3380B4FF"));
        expiryPill.setBackground(expBg);
        expiryPill.setPadding((int)(14 * scale), (int)(6 * scale), (int)(14 * scale), (int)(6 * scale));

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams((int)(8 * scale), (int)(8 * scale));
        dotParams.leftMargin = (int)(8 * scale);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#30D158")); // iOS Green
        dot.setBackground(dotBg);
        expiryPill.addView(dot);

        final TextView tvExpiry = new TextView(this);
        tvExpiry.setTextColor(Color.parseColor("#EBEBF5"));
        tvExpiry.setTextSize(12);
        tvExpiry.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        expiryPill.addView(tvExpiry);
        rootLayout.addView(expiryPill);

        String expiryCached = getSharedPreferences("Playlists", MODE_PRIVATE).getString("expiry_date", "");
        if (expiryCached.isEmpty()) {
            tvExpiry.setText(TvUtil.translate(this, "قائمة التشغيل: ") + TvUtil.translate(this, "جاري التحميل..."));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                        String activeDns = sp.getString("active_dns", "");
                        String activeUser = sp.getString("active_username", "");
                        String activePass = sp.getString("active_password", "");
                        String activeCode = sp.getString("active_code", "");

                        if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                            String json = sp.getString("list", "[]");
                            java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
                            if (list != null && !list.isEmpty()) {
                                java.util.Map<String, Object> lastItem = list.get(list.size() - 1);
                                activeDns = (String) lastItem.get("dns");
                                activeUser = (String) lastItem.get("username");
                                activePass = (String) lastItem.get("password");
                                activeCode = lastItem.containsKey("code") ? (String) lastItem.get("code") : "";
                            }
                        }

                        String dns = normalizeDns(activeDns);
                        String user = activeUser;
                        String pass = activePass;
                        String code = activeCode;
                            
                        if (code == null || code.trim().isEmpty() || code.equalsIgnoreCase("VIP")) {
                            java.net.URL loginUrl = new java.net.URL(dns + "/player_api.php?username=" + user + "&password=" + pass);
                            java.net.HttpURLConnection loginConn = (java.net.HttpURLConnection) loginUrl.openConnection();
                            loginConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                            loginConn.setConnectTimeout(8000);
                            loginConn.setReadTimeout(8000);
                            loginConn.connect();
                            if (loginConn.getResponseCode() == 200) {
                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(loginConn.getInputStream()));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) sb.append(line);
                                reader.close();
                                
                                org.json.JSONObject loginJson = new org.json.JSONObject(sb.toString());
                                if (loginJson.has("user_info")) {
                                    org.json.JSONObject userInfo = loginJson.getJSONObject("user_info");
                                    String expDateStr = userInfo.optString("exp_date", "");
                                    final String formattedDate = formatExpiryDate(expDateStr);
                                    getSharedPreferences("Playlists", MODE_PRIVATE).edit().putString("expiry_date", formattedDate).apply();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            tvExpiry.setText(TvUtil.translate(Ot2Activity.this, "قائمة التشغيل: ") + formattedDate);
                                        }
                                    });
                                }
                            }
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvExpiry.setText(TvUtil.translate(Ot2Activity.this, "قائمة التشغيل: ") + TvUtil.translate(Ot2Activity.this, "غير محدود ♾️"));
                                }
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvExpiry.setText(TvUtil.translate(Ot2Activity.this, "قائمة التشغيل: ") + TvUtil.translate(Ot2Activity.this, "غير محدود ♾️"));
                            }
                        });
                    }
                }
            }).start();
        } else {
            String displayExpiry = expiryCached;
            if ("غير محدود ♾️".equals(expiryCached) || "Unlimited ♾️".equals(expiryCached)) {
                displayExpiry = TvUtil.translate(this, "غير محدود ♾️");
            }
            tvExpiry.setText(TvUtil.translate(this, "قائمة التشغيل: ") + displayExpiry);
        }

        // Apple Horizontal Navigation Grid
        LinearLayout mainHContainer = new LinearLayout(this);
        mainHContainer.setOrientation(LinearLayout.HORIZONTAL);
        mainHContainer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mainParams = new FrameLayout.LayoutParams(-2, -1);
        mainParams.setMargins((int)(20 * scale), (int)(32 * scale), (int)(20 * scale), (int)(50 * scale));
        mainHContainer.setLayoutParams(mainParams);

        // 1. Hero Live TV Card (iOS Blue)
        final View liveTvCard = addIosGridCard(mainHContainer, TvUtil.translate(this, "بث مباشر"), "قنوات لايف", R.drawable.picsart_26_05_20_21_51_20_597, "#0A84FF", true, new View.OnClickListener() {
            @Override public void onClick(View v) { 
                startActivity(new Intent(Ot2Activity.this, LiveActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // 2. Col 2: Movies & Sports
        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setGravity(Gravity.CENTER);
        addIosGridCard(col2, TvUtil.translate(this, "أفلام"), "أحدث الأفلام", R.drawable.picsart_26_05_20_21_50_41_231, "#BF5AF2", false, new View.OnClickListener() {
            @Override public void onClick(View v) { 
                Intent intent = new Intent(Ot2Activity.this, SeriesActivity.class);
                intent.putExtra("type", "movies");
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
        addIosGridCard(col2, TvUtil.translate(this, "رياضة"), "مباريات اليوم", R.drawable.picsart_26_05_20_21_48_13_028, "#30D158", false, new View.OnClickListener() {
            @Override public void onClick(View v) { 
                startActivity(new Intent(Ot2Activity.this, SportsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
        mainHContainer.addView(col2);

        // 3. Col 3: Series & Server Switch
        LinearLayout col3 = new LinearLayout(this);
        col3.setOrientation(LinearLayout.VERTICAL);
        col3.setGravity(Gravity.CENTER);
        addIosGridCard(col3, TvUtil.translate(this, "مسلسلات"), "مسلسلات حصرية", R.drawable.picsart_26_05_20_21_50_20_637, "#40C8E0", false, new View.OnClickListener() {
            @Override public void onClick(View v) { 
                Intent intent = new Intent(Ot2Activity.this, SeriesActivity.class);
                intent.putExtra("type", "series");
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
        addIosGridCard(col3, TvUtil.translate(this, "تغيير السيرفر"), "قوائم التشغيل", R.drawable.picsart_26_05_20_21_48_52_267, "#FF9F0A", false, new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        mainHContainer.addView(col3);

        // 4. Col 4: Ad Banner & Floating Capsule Actions
        LinearLayout col4 = new LinearLayout(this);
        col4.setOrientation(LinearLayout.VERTICAL);
        col4.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams col4Params = new LinearLayout.LayoutParams((int)(260 * scale), -1);
        col4Params.leftMargin = (int)(10 * scale);
        col4.setLayoutParams(col4Params);

        addBanner(col4);

        // Apple Floating Capsule Toolbar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bottomBarParams = new LinearLayout.LayoutParams(-2, -2);
        bottomBarParams.topMargin = (int)(12 * scale);
        bottomBarParams.gravity = Gravity.CENTER_HORIZONTAL;
        bottomBar.setLayoutParams(bottomBarParams);

        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.parseColor("#E60B101C"));
        barBg.setCornerRadius(18 * scale);
        barBg.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        bottomBar.setBackground(barBg);
        bottomBar.setPadding((int)(8 * scale), (int)(6 * scale), (int)(8 * scale), (int)(6 * scale));

        addSmallButton(bottomBar, R.drawable.picsart_26_05_20_21_49_58_854, "#EBEBF5", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Ot2Activity.this, SettingsActivity.class));
            }
        });
        addSmallButton(bottomBar, R.drawable.picsart_26_05_20_21_51_00_695, "#0A84FF", new View.OnClickListener() {
            @Override public void onClick(View v) { refreshData(); }
        });
        addSmallButton(bottomBar, R.drawable.picsart_26_05_20_21_49_34_776, "#FF453A", new View.OnClickListener() {
            @Override public void onClick(View v) { finishAffinity(); }
        });
        addSmallButton(bottomBar, R.drawable.tele, "#70D7FF", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/match_sportss"));
                    startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(Ot2Activity.this, "فشل فتح رابط التليجرام", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        col4.addView(bottomBar);

        mainHContainer.addView(col4);

        // Safe Horizontal Scrolling for small landscape phone displays
        android.widget.HorizontalScrollView hzScroll = new android.widget.HorizontalScrollView(this);
        FrameLayout.LayoutParams hzLp = new FrameLayout.LayoutParams(-1, -1);
        hzScroll.setLayoutParams(hzLp);
        hzScroll.setHorizontalScrollBarEnabled(false);
        hzScroll.setFillViewport(true);
        hzScroll.addView(mainHContainer);

        rootLayout.addView(hzScroll);

        // Version badge (SF Style Subdued Footnote)
        TextView tvVersion = new TextView(this);
        tvVersion.setText("Blue+ Pro • v1.3");
        tvVersion.setTextColor(Color.parseColor("#66FFFFFF"));
        tvVersion.setTextSize(10.5f);
        tvVersion.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams verParams = new FrameLayout.LayoutParams(-2, -2);
        verParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        verParams.bottomMargin = (int)(16 * scale);
        verParams.rightMargin = (int)(24 * scale);
        tvVersion.setLayoutParams(verParams);
        rootLayout.addView(tvVersion);

        setContentView(rootLayout);

        liveTvCard.post(new Runnable() {
            @Override
            public void run() {
                liveTvCard.requestFocus();
            }
        });
    }

    private View addIosGridCard(LinearLayout container, String title, String subtitle, int iconRes, String accentHex, boolean isLarge, View.OnClickListener listener) {
        float scale = getResources().getDisplayMetrics().density;
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        
        int width = (int)(152 * scale);
        int height = isLarge ? (int)(236 * scale) : (int)(112 * scale);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins((int)(6 * scale), (int)(5 * scale), (int)(6 * scale), (int)(5 * scale));
        card.setLayoutParams(params);
        card.setPadding((int)(10 * scale), (int)(10 * scale), (int)(10 * scale), (int)(10 * scale));
        
        int accentColor = Color.parseColor(accentHex);
        
        // Apple Continuous Squircle Frosted Glass container
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#E60D1526")); // Deep midnight translucent glass
        gd.setCornerRadius((isLarge ? 22 : 18) * scale);
        gd.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF")); // Specular reflection border
        card.setBackground(gd);
        
        // Squircle Icon Tile Container (Apple Control Center / Settings style)
        FrameLayout iconTile = new FrameLayout(this);
        int tileDim = isLarge ? (int)(74 * scale) : (int)(46 * scale);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(tileDim, tileDim);
        tileParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconTile.setLayoutParams(tileParams);
        
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius((isLarge ? 18 : 14) * scale);
        int bgAlpha = Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        int strokeAlpha = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        tileBg.setColor(bgAlpha);
        tileBg.setStroke((int)(1.2f * scale), strokeAlpha);
        iconTile.setBackground(tileBg);
        
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int iconSize = isLarge ? (int)(44 * scale) : (int)(26 * scale);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        iconTile.addView(icon);
        
        card.addView(iconTile);
        
        // Title Text
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(isLarge ? 17 : 14);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, (int)(8 * scale), 0, 0);
        card.addView(tvTitle);
        
        // Subtitle badge
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(subtitle);
            tvSub.setTextColor(Color.parseColor("#80FFFFFF"));
            tvSub.setTextSize(10);
            tvSub.setGravity(Gravity.CENTER);
            tvSub.setPadding(0, (int)(2 * scale), 0, 0);
            card.addView(tvSub);
        }
        
        card.setOnClickListener(listener);
        TvUtil.applyTvFocusHighlight(card, (isLarge ? 22.0f : 18.0f));
        container.addView(card);
        return card;
    }

    private View addGridCard(LinearLayout container, String title, int iconRes, boolean isLarge, View.OnClickListener listener) {
        return addIosGridCard(container, title, null, iconRes, "#0A84FF", isLarge, listener);
    }

    private void addBanner(LinearLayout container) {
        final float scale = getResources().getDisplayMetrics().density;
        FrameLayout banner = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)(260 * scale), (int)(160 * scale));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = (int)(5 * scale);
        banner.setLayoutParams(params);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#E60D1526")); // Midnight frosted glass
        gd.setCornerRadius((int)(18 * scale)); // iOS continuous squircle corners
        gd.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF"));
        banner.setBackground(gd);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            banner.setClipToOutline(true);
        }

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        banner.addView(img);

        container.addView(banner);

        loadLocalAds(img);
    }

    private void loadLocalAds(final ImageView bannerImageView) {
        adUrls.clear();
        String adsJson = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("ad_urls_json", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(adsJson);
            for (int i = 0; i < arr.length(); i++) {
                String url = arr.optString(i);
                if (url != null && !url.isEmpty()) {
                    adUrls.add(url);
                }
            }
        } catch (Exception e) {}

        if (!adUrls.isEmpty()) {
            Glide.with(this).load(adUrls.get(0)).diskCacheStrategy(DiskCacheStrategy.ALL).into(bannerImageView);
            startAdCarousel(bannerImageView);
        } else {
            int bannerRes = getResources().getIdentifier("tv_banner", "drawable", getPackageName());
            if (bannerRes == 0) bannerRes = getResources().getIdentifier("default_image", "drawable", getPackageName());
            if (bannerRes != 0) bannerImageView.setImageResource(bannerRes);
        }
    }

    private void startAdCarousel(final ImageView bannerImageView) {
        if (adRunnable != null) return;
        
        adRunnable = new Runnable() {
            @Override
            public void run() {
                if (adUrls.size() > 1) {
                    currentAdIndex = (currentAdIndex + 1) % adUrls.size();
                    animateBanner(bannerImageView, adUrls.get(currentAdIndex));
                }
                adHandler.postDelayed(this, 15000);
            }
        };
        adHandler.postDelayed(adRunnable, 15000);
    }

    private void animateBanner(final ImageView img, final String nextUrl) {
        android.view.animation.AlphaAnimation fadeOut = new android.view.animation.AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(400);
        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {}
            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {}
            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                if (!isDestroyed()) {
                    Glide.with(Ot2Activity.this).load(nextUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(img);
                    android.view.animation.AlphaAnimation fadeIn = new android.view.animation.AlphaAnimation(0.0f, 1.0f);
                    fadeIn.setDuration(400);
                    img.startAnimation(fadeIn);
                }
            }
        });
        img.startAnimation(fadeOut);
    }

    private void addSmallButton(LinearLayout container, int iconRes, String accentHex, View.OnClickListener listener) {
        float scale = getResources().getDisplayMetrics().density;
        
        FrameLayout btn = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)(44 * scale), (int)(44 * scale));
        params.setMargins((int)(5 * scale), 0, (int)(5 * scale), 0);
        btn.setLayoutParams(params);
        
        int accentColor = Color.parseColor(accentHex);
        int bgAlpha = Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        int strokeAlpha = Color.argb(100, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgAlpha);
        gd.setCornerRadius(13 * scale);
        gd.setStroke((int)(1.2f * scale), strokeAlpha);
        btn.setBackground(gd);
        
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int iconSize = (int)(22 * scale);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        btn.addView(icon);
        
        btn.setOnClickListener(listener);
        TvUtil.applyTvFocusHighlight(btn, 13.0f);
        container.addView(btn);
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
        } catch (Exception e) { }
    }

    private void loadCachedLogo(ImageView iv) {
        try {
            File f = new File(getFilesDir(), "splash_logo.png");
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) iv.setImageBitmap(bmp);
            }
        } catch (Exception e) { }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, Ot1Activity.class);
        startActivity(intent);
        finish();
    }

    private String formatExpiryDate(String expDateStr) {
        if (expDateStr == null || expDateStr.isEmpty() || "null".equals(expDateStr) || "0".equals(expDateStr)) {
            return TvUtil.translate(this, "غير محدود ♾️");
        }
        try {
            long seconds = Long.parseLong(expDateStr);
            if (seconds <= 0) {
                return TvUtil.translate(this, "غير محدود ♾️");
            }
            java.util.Date date = new java.util.Date(seconds * 1000L);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH);
            return sdf.format(date);
        } catch (Exception e) {
            return TvUtil.translate(this, "غير محدود ♾️");
        }
    }

    private String normalizeDns(String dns) {
        if (dns == null) return "";
        dns = dns.trim();
        if (dns.isEmpty()) return "";
        if (!dns.startsWith("http://") && !dns.startsWith("https://")) {
            dns = "http://" + dns;
        }
        if (dns.endsWith("/")) {
            dns = dns.substring(0, dns.length() - 1);
        }
        return dns;
    }

    private void registerCodeActivationIfNeeded() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                    String activeCode = sp.getString("active_code", "");
                    if (activeCode.isEmpty()) {
                        String json = sp.getString("list", "[]");
                        java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
                        if (list != null && !list.isEmpty()) {
                            java.util.Map<String, Object> lastItem = list.get(list.size() - 1);
                            activeCode = lastItem.containsKey("code") ? (String) lastItem.get("code") : "";
                        }
                    }
                    final String currentCode = activeCode;
                    if (currentCode != null && !currentCode.trim().isEmpty() && !currentCode.equalsIgnoreCase("VIP")) {
                        String lastRegCode = sp.getString("last_registered_code", "");
                        if (!currentCode.equalsIgnoreCase(lastRegCode)) {
                            String mac = getMacAddress();
                            String urlStr = "https://camillecyrm.serv00.net/api/code.php?action=activate&code=" 
                                + java.net.URLEncoder.encode(currentCode, "UTF-8") 
                                + "&mac=" + java.net.URLEncoder.encode(mac, "UTF-8");
                            java.net.URL url = new java.net.URL(urlStr);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("User-Agent", USER_AGENT);
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(8000);
                            conn.connect();
                            if (conn.getResponseCode() == 200) {
                                sp.edit().putString("last_registered_code", currentCode).apply();
                            }
                            conn.disconnect();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Server activation registration failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private String getMacAddress() {
        try {
            java.util.List<java.net.NetworkInterface> interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface intf : interfaces) {
                if (intf.getName().equalsIgnoreCase("wlan0") || intf.getName().equalsIgnoreCase("eth0")) {
                    byte[] mac = intf.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder buf = new StringBuilder();
                        for (byte b : mac) buf.append(String.format("%02X:", b));
                        if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                        String realMac = buf.toString();
                        if (!realMac.equals("02:00:00:00:00:00")) return realMac;
                    }
                }
            }
            String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            if (androidId == null) androidId = "DEADC0DE0000";
            if (androidId.length() < 12) androidId = (androidId + "000000000000").substring(0, 12);
            StringBuilder formattedMac = new StringBuilder();
            for (int i = 0; i < 12; i += 2) {
                if (i > 0) formattedMac.append(":");
                formattedMac.append(androidId.substring(i, i + 2).toUpperCase(java.util.Locale.ENGLISH));
            }
            return formattedMac.toString();
        } catch (Exception ex) { return "E1:AA:63:DE:99:AC22"; }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}

