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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaitingActivity extends Activity {

    private String dns, username, password, code;
    private TextView tvProgress;
    private int currentProgress = 0;
    private boolean isDownloadStarted = false;
    private boolean onlyLive = false;
    private static final String TAG = "WaitingActivity";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvUtil.enableTls12(this); // Fix SSL issues for older Android versions

        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        TvUtil.hideSystemUI(this);

        dns = normalizeDns(getIntent().getStringExtra("dns"));
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        code = getIntent().getStringExtra("code");
        onlyLive = getIntent().getBooleanExtra("only_live", false);

        boolean forceRefresh = getIntent().getBooleanExtra("force_refresh", false);
        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        java.io.File fileLive = new java.io.File(getExternalFilesDir(null), "xtream_live.json");

        if (!forceRefresh && fileLive.exists() && fileLive.length() > 500
                && dns.equals(sp.getString("last_cached_dns", ""))
                && username.equals(sp.getString("last_cached_username", ""))) {
            sp.edit().putString("active_dns", dns)
                     .putString("active_username", username)
                     .putString("active_password", password)
                     .putString("active_code", code)
                     .putBoolean("active_only_live", onlyLive)
                     .apply();

            Intent intent = new Intent(WaitingActivity.this, Ot2Activity.class);
            startActivity(intent);
            finish();
            return;
        }

        createUI();
        startDownload();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, Ot1Activity.class);
        startActivity(intent);
        finish();
    }

    private void createUI() {
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("bg_fix_v3", false)) {
            new java.io.File(getFilesDir(), "splash_bg.jpg").delete();
            new java.io.File(getFilesDir(), "splash_logo.png").delete();
            prefs.edit().remove("last_bg_url").remove("last_logo_url").putBoolean("bg_fix_v3", true).apply();
        }

        float scale = getResources().getDisplayMetrics().density;
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_sports);
        TvUtil.loadCachedBackground(root);

        View overlay = new View(this);
        overlay.setBackgroundColor(Color.parseColor("#55000000"));
        root.addView(overlay);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(-1, -1);
        containerParams.gravity = Gravity.CENTER;
        container.setLayoutParams(containerParams);
        
        final ImageView logo = new ImageView(this);
        TvUtil.loadCachedLogo(logo, R.drawable.home_logo);
        int logoSize = (int)(120 * scale);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoParams.gravity = Gravity.CENTER;
        logo.setLayoutParams(logoParams);
        container.addView(logo);

        fetchSettingsAsync(root, logo);

        tvProgress = new TextView(this);
        tvProgress.setText("0%");
        tvProgress.setTextColor(Color.WHITE);
        tvProgress.setTextSize(22);
        tvProgress.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(-1, -2);
        tvParams.topMargin = (int)(20 * scale);
        tvProgress.setLayoutParams(tvParams);
        container.addView(tvProgress);

        try {
            com.airbnb.lottie.LottieAnimationView lottie = new com.airbnb.lottie.LottieAnimationView(this);
            lottie.setAnimation("hhuu.json");
            lottie.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
            lottie.playAnimation();
            LinearLayout.LayoutParams lottieParams = new LinearLayout.LayoutParams((int)(50 * scale), (int)(50 * scale));
            lottieParams.gravity = Gravity.CENTER;
            lottieParams.topMargin = (int)(5 * scale);
            lottie.setLayoutParams(lottieParams);
            container.addView(lottie);
        } catch (Exception e) {
            Log.e(TAG, "Lottie error: " + e.getMessage());
        }

        root.addView(container);

        // Horizontal Bottom News Ticker (TV-like Marquee)
        LinearLayout tickerContainer = new LinearLayout(this);
        tickerContainer.setOrientation(LinearLayout.HORIZONTAL);
        tickerContainer.setBackgroundColor(Color.parseColor("#E6080808"));
        tickerContainer.setGravity(Gravity.CENTER_VERTICAL);
        tickerContainer.setPadding((int)(10 * scale), 0, (int)(10 * scale), 0);

        TextView badge = new TextView(this);
        badge.setText("  الأخبار  ");
        badge.setTextColor(Color.BLACK);
        badge.setBackgroundColor(Color.parseColor("#FFC107"));
        badge.setTextSize(10);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, (int)(16 * scale));
        badgeLp.rightMargin = (int)(8 * scale);
        badge.setLayoutParams(badgeLp);
        tickerContainer.addView(badge);

        MarqueeTextView tickerTv = new MarqueeTextView(this);
        tickerTv.setSingleLine(true);
        tickerTv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tickerTv.setMarqueeRepeatLimit(-1);
        tickerTv.setHorizontallyScrolling(true);
        tickerTv.setTextColor(Color.WHITE);
        tickerTv.setTextSize(11);
        tickerTv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        tickerContainer.addView(tickerTv);

        TvUtil.setupDualLanguageTicker(this, badge, tickerTv);

        FrameLayout.LayoutParams tickerLp = new FrameLayout.LayoutParams(-1, (int)(22 * scale));
        tickerLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tickerContainer.setLayoutParams(tickerLp);
        root.addView(tickerContainer);

        setContentView(root);
    }

    private void startDownload() {
        if (dns == null || username == null || password == null) {
            android.widget.Toast.makeText(this, TvUtil.translate(this, "بيانات الدخول غير مكتملة!"), android.widget.Toast.LENGTH_SHORT).show();
            getSharedPreferences("Playlists", MODE_PRIVATE).edit().putBoolean("auto_login", false).apply();
            Intent intent = new Intent(this, Ot1Activity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Check for updates first on a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL configUrl = new java.net.URL("https://blueplus-auz.pages.dev/settings.json");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) configUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
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
                        org.json.JSONObject json = new org.json.JSONObject(decrypted);
                         
                         // Parse news ticker texts (Dual language: AR & EN)
                         if (json.has("ticker_texts_ar")) {
                             org.json.JSONArray tickerArr = json.getJSONArray("ticker_texts_ar");
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
                         if (json.has("ticker_texts_en")) {
                             org.json.JSONArray tickerArr = json.getJSONArray("ticker_texts_en");
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
                         if (json.has("ticker_texts")) {
                             org.json.JSONArray tickerArr = json.getJSONArray("ticker_texts");
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

                         final String version = json.optString("app_version", "");
                        final String updateFeatures = json.optString("update_features", "");
                        final int updateRequired = json.optInt("update_required", 0);
                        final String updateLink = json.optString("update_link", "");

                        String localVersion = "1.0.0";
                        try {
                            localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        } catch (Exception e) {}

                        if (!version.isEmpty() && !updateLink.isEmpty() && isServerVersionNewer(version, localVersion)) {
                            // Update available! Show dialog and stop/postpone download progression
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showUpdateDialog(version, updateFeatures, updateLink, updateRequired == 1);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update check failed in WaitingActivity: " + e.getMessage());
                }

                // No updates, proceed to load IPTV data
                continueDownload();
            }
        }).start();
    }

    private void continueDownload() {
        synchronized (this) {
            if (isDownloadStarted) return;
            isDownloadStarted = true;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Verify code expiration first, even if local cache exists
                    if (code != null && !code.trim().isEmpty() && !code.equalsIgnoreCase("VIP")) {
                        // Check server expiration immediately without starting progress counter
                        if (isCodeExpired(code)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    android.widget.Toast.makeText(WaitingActivity.this, TvUtil.translate(WaitingActivity.this, "هذا الكود منتهي الصلاحية!"), android.widget.Toast.LENGTH_LONG).show();
                                    
                                    try {
                                        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                                        String existing = sp.getString("list", "[]");
                                        java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(
                                            existing, 
                                            new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
                                        );
                                        
                                        if (list != null) {
                                            for (int i = list.size() - 1; i >= 0; i--) {
                                                java.util.Map<String, Object> item = list.get(i);
                                                if (code.equals(item.get("code"))) {
                                                    list.remove(i);
                                                }
                                            }
                                            sp.edit().putString("list", new com.google.gson.Gson().toJson(list))
                                                     .putBoolean("auto_login", false)
                                                     .apply();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to remove expired playlist: " + e.getMessage());
                                    }

                                    Intent intent = new Intent(WaitingActivity.this, Ot1Activity.class);
                                    startActivity(intent);
                                    finish();
                                }
                            });
                            return;
                        }
                    }

                    // 2. Initialize progress text
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvProgress.setText("0%");
                        }
                    });

                    // 3. Fetch real expiration date from Xtream Codes login API (only if it is a VIP/direct login or code is empty)
                    if (code == null || code.trim().isEmpty() || code.equalsIgnoreCase("VIP")) {
                        try {
                            java.net.URL loginUrl = new java.net.URL(dns + "/player_api.php?username=" + username + "&password=" + password);
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
                            Log.e(TAG, "Failed to fetch expiry date: " + e.getMessage());
                        }
                    }

                    // Always perform a fresh download of IPTV data when loading
                    final java.io.File fileLive = new java.io.File(getExternalFilesDir(null), "xtream_live.json");
                    final java.io.File fileVod = new java.io.File(getExternalFilesDir(null), "xtream_vod.json");
                    final java.io.File fileSeries = new java.io.File(getExternalFilesDir(null), "xtream_series.json");
                    android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);

                    // Otherwise, save the credentials as cached and start download
                    sp.edit().putString("last_cached_dns", dns)
                             .putString("last_cached_username", username)
                             .putString("last_cached_password", password)
                             .putString("active_dns", dns)
                             .putString("active_username", username)
                             .putString("active_password", password)
                             .putString("active_code", code)
                             .putBoolean("active_only_live", onlyLive)
                             .apply();

                    // ═══════════════════════════════════════════════════════
                    // SEQUENTIAL DOWNLOAD: تحميل البيانات بالتتابع لمنع الحظر
                    // ═══════════════════════════════════════════════════════
                    final java.util.concurrent.atomic.AtomicLong totalBytesAtom = new java.util.concurrent.atomic.AtomicLong(0);
                    final java.util.concurrent.atomic.AtomicLong totalContentLength = new java.util.concurrent.atomic.AtomicLong(0);
                    final boolean[] isDownloading = {true};

                    // Progress updater every 150ms for smooth real percentage loading
                    final android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    final Runnable progressUpdater = new Runnable() {
                        @Override public void run() {
                            if (!isDownloading[0]) return;
                            long done = totalBytesAtom.get();
                            long total = totalContentLength.get();
                            if (total <= 0) {
                                total = onlyLive ? (800L * 1024L) : (4L * 1024L * 1024L); // 800KB for live only, 4MB for full connection
                            }
                            if (done > total) {
                                total = done + (1024L * 1024L); // auto-expand estimate if exceeded
                            }
                            int pct = (int) Math.min(99, (done * 100L) / total);
                            tvProgress.setText(pct + "%");
                            progressHandler.postDelayed(this, 150);
                        }
                    };
                    progressHandler.post(progressUpdater);

                    final String finalDnsP = dns, finalUserP = username, finalPassP = password;

                    try {
                        // ── Phase 1: Live channels ──────────────────────────────
                        FileOutputStream outLive = new FileOutputStream(fileLive);
                        outLive.write("{\"data\":{".getBytes());
                        downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_live_categories&username=" + finalUserP + "&password=" + finalPassP, outLive, "get_live_categories", totalBytesAtom, totalContentLength);
                        outLive.write(",".getBytes());
                        downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_live_streams&username=" + finalUserP + "&password=" + finalPassP, outLive, "get_live_streams", totalBytesAtom, totalContentLength);
                        outLive.write("}}".getBytes());
                        outLive.flush(); outLive.close();

                        // ── Phase 2: VOD / أفلام ────────────────────────────────
                        FileOutputStream outVod = new FileOutputStream(fileVod);
                        if (onlyLive) {
                            outVod.write("{\"data\":{\"get_vod_categories\":[],\"get_vod_streams\":[]}}".getBytes());
                        } else {
                            outVod.write("{\"data\":{".getBytes());
                            downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_vod_categories&username=" + finalUserP + "&password=" + finalPassP, outVod, "get_vod_categories", totalBytesAtom, totalContentLength);
                            outVod.write(",".getBytes());
                            downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_vod_streams&username=" + finalUserP + "&password=" + finalPassP, outVod, "get_vod_streams", totalBytesAtom, totalContentLength);
                            outVod.write("}}".getBytes());
                        }
                        outVod.flush(); outVod.close();

                        // ── Phase 3: Series / مسلسلات ──────────────────────────
                        FileOutputStream outSeries = new FileOutputStream(fileSeries);
                        if (onlyLive) {
                            outSeries.write("{\"data\":{\"get_series_categories\":[],\"get_series\":[]}}".getBytes());
                        } else {
                            outSeries.write("{\"data\":{".getBytes());
                            downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_series_categories&username=" + finalUserP + "&password=" + finalPassP, outSeries, "get_series_categories", totalBytesAtom, totalContentLength);
                            outSeries.write(",".getBytes());
                            downloadToStreamParallel(finalDnsP + "/player_api.php?action=get_series&username=" + finalUserP + "&password=" + finalPassP, outSeries, "get_series", totalBytesAtom, totalContentLength);
                            outSeries.write("}}".getBytes());
                        }
                        outSeries.flush(); outSeries.close();

                    } catch (Exception e) {
                        Log.e(TAG, "Sequential download failed: " + e.getMessage());
                        throw e;
                    }

                    isDownloading[0] = false;
                    progressHandler.removeCallbacks(progressUpdater);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            tvProgress.setText("100%");
                        }
                    });

                    // Clear static memory caches to ensure the newly downloaded data is loaded
                    try {
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
                        Log.e(TAG, "Failed to clear static caches: " + t.getMessage());
                    }

                    // Fetch and pre-download ad images in a separate background thread to prevent UI delays
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                java.net.URL configUrl = new java.net.URL("https://blueplus-auz.pages.dev/settings.json");
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) configUrl.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setRequestProperty("User-Agent", USER_AGENT);
                                conn.setConnectTimeout(8000);
                                conn.setReadTimeout(8000);
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
                                    org.json.JSONObject obj = new org.json.JSONObject(decrypted);
                                    
                                    // PRE-CACHE IMAGES SYSTEM (Background)
                                    final org.json.JSONArray adArr = obj.optJSONArray("ad_images");
                                    if (adArr != null) {
                                        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("ad_urls_json", adArr.toString()).apply();
                                    }
                                    // preCacheImages removed, handled dynamically by Glide
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Ad pre-download failed: " + e.getMessage());
                            }
                        }
                    }).start();

                    // START MOVIE/CHANNEL PRE-CACHE (Background)
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(2000); // Give data saving some time
                                startBackgroundPreCache();
                            } catch (Exception e) {}
                        }
                    }).start();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentProgress = 100;
                            tvProgress.setText("100%");
                            startActivity(new Intent(WaitingActivity.this, Ot2Activity.class));
                            finish();
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            android.widget.Toast.makeText(WaitingActivity.this, TvUtil.translate(WaitingActivity.this, "فشل الاتصال: يرجى التحقق من الشبكة أو صلاحية الاشتراك!"), android.widget.Toast.LENGTH_LONG).show();
                            android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                            sp.edit().putBoolean("auto_login", false).apply();
                            Intent intent = new Intent(WaitingActivity.this, Ot1Activity.class);
                            startActivity(intent);
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    private void downloadToStream(String urlStr, FileOutputStream output, String key, final int index, final int totalSteps) throws Exception {
        output.write(("\"" + key + "\":").getBytes());
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();

        if (connection.getResponseCode() != 200) {
            output.write("[]".getBytes());
            return;
        }

        int contentLength = connection.getContentLength();
        InputStream rawInput = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        if (encoding != null && "gzip".equalsIgnoreCase(encoding)) {
            rawInput = new java.util.zip.GZIPInputStream(rawInput);
        }
        InputStream input = new java.io.BufferedInputStream(rawInput);
        byte data[] = new byte[32768];
        int count;
        int bytesRead = 0;
        
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
            bytesRead += count;
            
            if (contentLength > 0) {
                float stepProgress = (float) bytesRead / contentLength;
                final int currentPercent = (int) (((float) index / totalSteps) * 100 + (stepProgress * (100f / totalSteps)));
                currentProgress = currentPercent;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPercent >= 0 && currentPercent <= 100) {
                            tvProgress.setText(currentPercent + "%");
                        }
                    }
                });
            } else {
                float estimatedStepProgress = Math.min((float) bytesRead / (500 * 1024), 0.99f); // assume 500kb avg
                final int currentPercent = (int) (((float) index / totalSteps) * 100 + (estimatedStepProgress * (100f / totalSteps)));
                currentProgress = currentPercent;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPercent >= 0 && currentPercent <= 100) {
                            tvProgress.setText(currentPercent + "%");
                        }
                    }
                });
            }
        }
        input.close();
    }

    // ── تحميل متوازٍ - بدون progress index ──────────────────────────────
    private void downloadToStreamParallel(String urlStr, FileOutputStream output, String key, java.util.concurrent.atomic.AtomicLong totalBytesAtom, java.util.concurrent.atomic.AtomicLong totalContentLength) throws Exception {
        output.write(("\"" + key + "\":").getBytes());
        java.io.BufferedOutputStream bufferedOut = new java.io.BufferedOutputStream(output, 131072); // 128KB output buffer to minimize disk write overhead
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();

        if (connection.getResponseCode() != 200) {
            throw new java.io.IOException("HTTP error: " + connection.getResponseCode() + " for " + key);
        }

        int length = connection.getContentLength();
        if (length > 0) {
            totalContentLength.addAndGet(length);
        }

        InputStream rawInput = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        if (encoding != null && "gzip".equalsIgnoreCase(encoding)) {
            rawInput = new java.util.zip.GZIPInputStream(rawInput);
        }

        InputStream input = new java.io.BufferedInputStream(rawInput, 65536); // 64KB read buffer
        byte[] data = new byte[65536]; // 64KB data chunks
        int count;
        while ((count = input.read(data)) != -1) {
            bufferedOut.write(data, 0, count);
            totalBytesAtom.addAndGet(count);
        }
        bufferedOut.flush(); // Ensure all buffered data is written to the FileOutputStream
        input.close();
    }

    private boolean isCodeExpired(String activationCode) {
        try {
            java.net.URL url = new java.net.URL("https://blueplus-auz.pages.dev/codes.json");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
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
                java.util.ArrayList<java.util.Map<String, Object>> codesList = new com.google.gson.Gson().fromJson(
                    decrypted, 
                    new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
                );
                
                java.util.Map<String, Object> foundCode = null;
                if (codesList != null) {
                    for (java.util.Map<String, Object> c : codesList) {
                        if (activationCode.equalsIgnoreCase(String.valueOf(c.get("code")))) {
                            foundCode = c;
                            break;
                        }
                    }
                }

                if (foundCode != null) {
                    String statusStr = foundCode.containsKey("status") ? String.valueOf(foundCode.get("status")) : "";
                    if ("disabled".equalsIgnoreCase(statusStr)) {
                        return true;
                    }
                    
                    if (foundCode.containsKey("expires_at") && foundCode.get("expires_at") != null) {
                        String expiresAtStr = String.valueOf(foundCode.get("expires_at")).trim();
                        if (!expiresAtStr.isEmpty() && !"null".equalsIgnoreCase(expiresAtStr)) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                                java.util.Date expiryDate = sdf.parse(expiresAtStr);
                                
                                // Add 2 days grace period (2 days * 24 hours * 60 mins * 60 secs * 1000 ms)
                                long gracePeriodMs = 2L * 24L * 60L * 60L * 1000L;
                                java.util.Date expiryWithGrace = new java.util.Date(expiryDate.getTime() + gracePeriodMs);
                                
                                java.util.Date currentDate = new java.util.Date();
                                if (currentDate.after(expiryWithGrace)) {
                                    return true;
                                }
                                
                                java.text.SimpleDateFormat outSdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US);
                                String formatted = outSdf.format(expiryDate);
                                getSharedPreferences("Playlists", MODE_PRIVATE).edit().putString("expiry_date", formatted).apply();
                            } catch (Exception e) {
                                Log.e(TAG, "Failed parsing expires_at in isCodeExpired: " + e.getMessage());
                            }
                        } else {
                            getSharedPreferences("Playlists", MODE_PRIVATE).edit().putString("expiry_date", "غير محدود ♾️").apply();
                        }
                    } else {
                        getSharedPreferences("Playlists", MODE_PRIVATE).edit().putString("expiry_date", "غير محدود ♾️").apply();
                    }
                    return false;
                } else {
                    return true; // Code not found
                }
            } else {
                return true; // Server error
            }
        } catch (Exception e) {
            Log.e(TAG, "isCodeExpired check failed: " + e.getMessage());
        }
        return false;
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
        } catch (Exception ex) { return "E1:AA:63:DE:99:AC"; }
    }

    private String formatExpiryDate(String expDateStr) {
        if (expDateStr == null || expDateStr.isEmpty() || "null".equals(expDateStr) || "0".equals(expDateStr)) {
            return "غير محدود ♾️";
        }
        try {
            long seconds = Long.parseLong(expDateStr);
            if (seconds <= 0) {
                return "غير محدود ♾️";
            }
            java.util.Date date = new java.util.Date(seconds * 1000L);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH);
            return sdf.format(date);
        } catch (Exception e) {
            return "غير محدود ♾️";
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
    private void showUpdateDialog(final String serverVersion, final String updateFeatures, final String updateLink, final boolean isRequired) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final android.app.Dialog dialog = new android.app.Dialog(WaitingActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                
                FrameLayout root = new FrameLayout(WaitingActivity.this);
                root.setBackgroundColor(Color.TRANSPARENT);

                View dimView = new View(WaitingActivity.this);
                dimView.setBackgroundColor(Color.parseColor("#E00A0E1A")); // Very deep dark translucent blue
                root.addView(dimView);

                float scale = getResources().getDisplayMetrics().density;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int cardWidth = Math.min((int)(290 * scale), (int)(screenWidth * 0.82f));

                // Master ScrollView wraps the card to support small screens / landscape orientation
                android.widget.ScrollView masterScroll = new android.widget.ScrollView(WaitingActivity.this);
                FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(cardWidth, -2);
                scrollParams.gravity = Gravity.CENTER;
                scrollParams.topMargin = (int)(20 * scale);
                scrollParams.bottomMargin = (int)(20 * scale);
                masterScroll.setLayoutParams(scrollParams);
                masterScroll.setVerticalScrollBarEnabled(false);
                masterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

                LinearLayout card = new LinearLayout(WaitingActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding((int)(12*scale), (int)(12*scale), (int)(12*scale), (int)(12*scale));
                card.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));

                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.parseColor("#E60A0F24"), Color.parseColor("#E61A2C4D")}
                );
                gd.setCornerRadius(20 * scale);
                gd.setStroke((int)(1.5 * scale), Color.parseColor("#8000E5FF")); // Glow cyan/neon border
                card.setBackground(gd);

                ImageView icon = new ImageView(WaitingActivity.this);
                icon.setImageResource(android.R.drawable.stat_sys_download);
                icon.setColorFilter(Color.parseColor("#00E5FF"));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int)(30*scale), (int)(30*scale));
                iconParams.bottomMargin = (int)(8*scale);
                icon.setLayoutParams(iconParams);
                card.addView(icon);

                TextView title = new TextView(WaitingActivity.this);
                title.setText(TvUtil.translate(WaitingActivity.this, "تحديث جديد متوفر! v") + serverVersion);
                title.setTextColor(Color.WHITE);
                title.setTextSize(13);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
                titleParams.bottomMargin = (int)(5*scale);
                title.setLayoutParams(titleParams);
                card.addView(title);

                TextView sub = new TextView(WaitingActivity.this);
                sub.setText(TvUtil.translate(WaitingActivity.this, "يرجى تحديث التطبيق للحصول على أفضل تجربة وأحدث الميزات."));
                sub.setTextColor(Color.parseColor("#B0BEC5"));
                sub.setTextSize(10);
                sub.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
                subParams.bottomMargin = (int)(10*scale);
                sub.setLayoutParams(subParams);
                card.addView(sub);

                if (updateFeatures != null && !updateFeatures.trim().isEmpty()) {
                    boolean isAr = "ar".equals(TvUtil.getAppLanguage(WaitingActivity.this));
                    TextView changeTitle = new TextView(WaitingActivity.this);
                    changeTitle.setText(TvUtil.translate(WaitingActivity.this, "ما الجديد في هذا الإصدار:"));
                    changeTitle.setTextColor(Color.parseColor("#00E5FF"));
                    changeTitle.setTextSize(10);
                    changeTitle.setGravity(isAr ? Gravity.RIGHT : Gravity.LEFT);
                    changeTitle.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams ctParams = new LinearLayout.LayoutParams(-1, -2);
                    ctParams.bottomMargin = (int)(4*scale);
                    changeTitle.setLayoutParams(ctParams);
                    card.addView(changeTitle);

                    android.widget.ScrollView scrollView = new android.widget.ScrollView(WaitingActivity.this);
                    LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(-1, (int)(55*scale));
                    svParams.bottomMargin = (int)(12*scale);
                    scrollView.setLayoutParams(svParams);

                    TextView features = new TextView(WaitingActivity.this);
                    features.setText(updateFeatures);
                    features.setTextColor(Color.WHITE);
                    features.setTextSize(11);
                    features.setGravity(isAr ? Gravity.RIGHT : Gravity.LEFT);
                    features.setLineSpacing(0, 1.3f);
                    scrollView.addView(features);
                    card.addView(scrollView);
                }

                LinearLayout btnRow = new LinearLayout(WaitingActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                btnRow.setLayoutParams(rowParams);

                if (!isRequired) {
                    TextView btnCancel = new TextView(WaitingActivity.this);
                    btnCancel.setText(TvUtil.translate(WaitingActivity.this, "لاحقاً"));
                    btnCancel.setTextColor(Color.parseColor("#B0BEC5"));
                    btnCancel.setGravity(Gravity.CENTER);
                    btnCancel.setTextSize(13);
                    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(38*scale), 1.0f);
                    cancelParams.rightMargin = (int)(10*scale);
                    btnCancel.setLayoutParams(cancelParams);

                    android.graphics.drawable.GradientDrawable cancelGd = new android.graphics.drawable.GradientDrawable();
                    cancelGd.setCornerRadius(14 * scale);
                    cancelGd.setColor(Color.parseColor("#1AFFFFFF"));
                    cancelGd.setStroke((int)(1.5 * scale), Color.parseColor("#33FFFFFF"));
                    btnCancel.setBackground(cancelGd);

                    btnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            continueDownload(); // Resume download
                        }
                    });
                    TvUtil.applyTvFocusHighlight(btnCancel, 14.0f);
                    btnRow.addView(btnCancel);
                }

                TextView btnUpdate = new TextView(WaitingActivity.this);
                btnUpdate.setText(TvUtil.translate(WaitingActivity.this, "تحديث الآن"));
                btnUpdate.setTextColor(Color.WHITE);
                btnUpdate.setGravity(Gravity.CENTER);
                btnUpdate.setTextSize(14);
                btnUpdate.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(0, (int)(38*scale), 1.2f);
                btnUpdate.setLayoutParams(updateParams);

                android.graphics.drawable.GradientDrawable updateGd = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#FF0072FF"), Color.parseColor("#FF00F2FE")}
                );
                updateGd.setCornerRadius(14 * scale);
                btnUpdate.setBackground(updateGd);

                btnUpdate.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(android.net.Uri.parse(updateLink));
                            startActivity(i);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(WaitingActivity.this, TvUtil.translate(WaitingActivity.this, "فشل فتح رابط التحديث!"), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                TvUtil.applyTvFocusHighlight(btnUpdate, 14.0f);
                btnRow.addView(btnUpdate);

                card.addView(btnRow);
                masterScroll.addView(card);
                root.addView(masterScroll);
                dialog.setContentView(root);
                
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                }

                if (!isRequired) {
                    dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(android.content.DialogInterface dialogInterface) {
                            continueDownload();
                        }
                    });
                }

                dialog.setCancelable(!isRequired);
                dialog.setCanceledOnTouchOutside(!isRequired);
                dialog.show();
            }
        });
    }

    private void preCacheImages(org.json.JSONArray arr) {
        if (arr == null || arr.length() == 0) return;
        try {
            java.io.File adsDir = new java.io.File(getFilesDir(), "ads");
            if (!adsDir.exists()) adsDir.mkdirs();
            int count = 0;
            for (int j = 0; j < arr.length(); j++) {
                String adUrlStr = arr.optString(j);
                if (adUrlStr == null || adUrlStr.isEmpty()) continue;
                if (!adUrlStr.startsWith("http")) adUrlStr = "http://" + adUrlStr;
                try {
                    java.net.URL adUrl = new java.net.URL(adUrlStr);
                    java.net.HttpURLConnection adConn = (java.net.HttpURLConnection) adUrl.openConnection();
                    adConn.setRequestProperty("User-Agent", USER_AGENT);
                    adConn.setConnectTimeout(5000);
                    adConn.setReadTimeout(5000);
                    adConn.connect();
                    if (adConn.getResponseCode() == 200) {
                        java.io.InputStream adInput = adConn.getInputStream();
                        java.io.File adFile = new java.io.File(adsDir, "ad_" + j + ".png");
                        java.io.FileOutputStream adOutput = new java.io.FileOutputStream(adFile);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = adInput.read(buffer)) != -1) {
                            adOutput.write(buffer, 0, bytesRead);
                        }
                        adOutput.flush();
                        adOutput.close();
                        adInput.close();
                        count++;
                    }
                } catch (Exception ex) {
                    Log.e("WaitingActivity", "Failed downloading ad image: " + ex.getMessage());
                }
            }
            getSharedPreferences("Playlists", MODE_PRIVATE).edit().putInt("cached_ads_count", count).apply();
        } catch (Exception e) {
            Log.e("WaitingActivity", "preCacheImages error: " + e.getMessage());
        }
    }

    private void startBackgroundPreCache() {
        try {
            // 1. Pre-cache Ads specifically as files for the Home Carousel
            java.io.File splashFile = new java.io.File(getFilesDir(), "split_live.json");
            if (!splashFile.exists()) return;

            // 2. We don't want to parse giant JSONs here again, but we can pre-fetch 
            // the most common logos if they are available.
            // For now, Glide will handle the on-demand caching with the improved TvUtil.loadImage
            // However, we can at least ensure TLS fix is applied globally.
        } catch (Exception e) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }

    private void fetchSettingsAsync(final FrameLayout root, final ImageView logoView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String API_URL = "https://blueplus-auz.pages.dev/settings.json";
                    HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
                    c.setRequestMethod("GET");
                    c.setRequestProperty("User-Agent", USER_AGENT);
                    c.setConnectTimeout(6000); 
                    c.setReadTimeout(6000);
                    
                    int resCode = c.getResponseCode();
                    if (resCode != 200) return;

                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    String decrypted = TvUtil.decryptAES(sb.toString());
                    final org.json.JSONObject json = new org.json.JSONObject(decrypted);
                    final String bgUrl   = json.optString("bg_image",    "");
                    final String logoUrl = json.optString("logo_image",  "");

                    String lastBgUrl = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("last_bg_url", "");
                    String lastLogoUrl = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("last_logo_url", "");

                    // Background cache updates for background/logo images
                    java.io.File bgFile = new java.io.File(getFilesDir(), "splash_bg.jpg");
                    if (!bgUrl.isEmpty() && (!bgUrl.equals(lastBgUrl) || !bgFile.exists())) {
                        if (downloadFile(bgUrl, bgFile)) {
                            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("last_bg_url", bgUrl).apply();
                            final Bitmap bmp = BitmapFactory.decodeFile(bgFile.getAbsolutePath());
                            if (bmp != null) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (root != null) {
                                            android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                                            drawable.setGravity(android.view.Gravity.FILL);
                                            root.setBackground(drawable);
                                        }
                                    }
                                });
                            }
                        }
                    }

                    java.io.File logoFile = new java.io.File(getFilesDir(), "splash_logo.png");
                    if (!logoUrl.isEmpty() && (!logoUrl.equals(lastLogoUrl) || !logoFile.exists())) {
                        if (downloadFile(logoUrl, logoFile)) {
                            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("last_logo_url", logoUrl).apply();
                            final Bitmap bmp = BitmapFactory.decodeFile(logoFile.getAbsolutePath());
                            if (bmp != null) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (logoView != null) {
                                            logoView.setImageBitmap(bmp);
                                        }
                                    }
                                });
                            }
                        }
                    }

                } catch (Exception e) {
                    Log.e("WaitingActivity", "fetchSettingsAsync error: " + e.getMessage());
                }
            }
        }).start();
    }

    private boolean downloadFile(String urlStr, java.io.File destFile) {
        String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        java.io.InputStream is = null;
        java.io.FileOutputStream fos = null;
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            c = (java.net.HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.connect();
            
            if (c.getResponseCode() == 200) {
                java.io.File tempFile = new java.io.File(destFile.getParent(), destFile.getName() + ".tmp");
                is = c.getInputStream();
                fos = new java.io.FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
                fos.close();
                fos = null;
                is.close();
                is = null;
                
                if (tempFile.exists() && tempFile.length() > 100) {
                    if (destFile.exists()) destFile.delete();
                    tempFile.renameTo(destFile);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("WaitingActivity", "downloadFile error: " + e.getMessage());
            if (urlStr.startsWith("https://")) {
                return downloadFile(urlStr.replace("https://", "http://"), destFile);
            }
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (fos != null) fos.close(); } catch (Exception e) {}
            try { if (c != null) c.disconnect(); } catch (Exception e) {}
        }
        return false;
    }

    private boolean isServerVersionNewer(String serverVer, String localVer) {
        if (serverVer == null || localVer == null) return false;
        try {
            String[] sParts = serverVer.replaceAll("[^0-9.]", "").split("\\.");
            String[] lParts = localVer.replaceAll("[^0-9.]", "").split("\\.");
            int length = Math.max(sParts.length, lParts.length);
            for (int i = 0; i < length; i++) {
                int sNum = i < sParts.length && !sParts[i].isEmpty() ? Integer.parseInt(sParts[i]) : 0;
                int lNum = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;
                if (sNum > lNum) return true;
                if (sNum < lNum) return false;
            }
        } catch (Exception e) {}
        return false;
    }

    private static class MarqueeTextView extends android.widget.TextView {
        public MarqueeTextView(android.content.Context context) {
            super(context);
        }
        @Override
        public boolean isFocused() {
            return true;
        }
    }
}