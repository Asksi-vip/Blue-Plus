package com.blue.plus;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.content.Intent;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;

public class MainActivity extends Activity {

    private LinearLayout linear1;
    private ImageView    imageview1;
    private TextView     tvVersion;

    private static final String TAG       = "SplashDebug";
    private static final String FILE_BG   = "splash_bg.jpg";
    private static final String FILE_LOGO = "splash_logo.png";
    private static final String API_URL   = "https://blueplus-auz.pages.dev/settings.json";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_BG_URL = "last_bg_url";
    private static final String KEY_LOGO_URL = "last_logo_url";

    private boolean hasNavigated = false;

    private synchronized void proceedToNextActivity() {
        if (hasNavigated) return;
        hasNavigated = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
                boolean autoLogin = sp.getBoolean("auto_login", false);
                
                Intent intent;
                if (autoLogin) {
                    String activeDns = sp.getString("active_dns", "");
                    String activeUser = sp.getString("active_username", "");
                    String activePass = sp.getString("active_password", "");
                    String activeCode = sp.getString("active_code", "");

                    if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                        String listJson = sp.getString("list", "[]");
                        java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(
                            listJson, 
                            new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
                        );
                        if (list != null && !list.isEmpty()) {
                            java.util.Map<String, Object> lastItem = list.get(list.size() - 1);
                            activeDns = (String) lastItem.get("dns");
                            activeUser = (String) lastItem.get("username");
                            activePass = (String) lastItem.get("password");
                            activeCode = lastItem.containsKey("code") ? (String) lastItem.get("code") : "";
                        }
                    }

                    if (!activeDns.isEmpty() && !activeUser.isEmpty() && !activePass.isEmpty()) {
                        intent = new Intent(MainActivity.this, WaitingActivity.class);
                        intent.putExtra("dns", activeDns);
                        intent.putExtra("username", activeUser);
                        intent.putExtra("password", activePass);
                        intent.putExtra("code", activeCode);
                        intent.putExtra("only_live", sp.getBoolean("active_only_live", false));
                    } else {
                        intent = new Intent(MainActivity.this, Ot1Activity.class);
                    }
                } else {
                    intent = new Intent(MainActivity.this, Ot1Activity.class);
                }
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        boolean autoLogin = sp.getBoolean("auto_login", false);
        
        if (autoLogin) {
            String activeDns = sp.getString("active_dns", "");
            String activeUser = sp.getString("active_username", "");
            String activePass = sp.getString("active_password", "");
            String activeCode = sp.getString("active_code", "");

            if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                String listJson = sp.getString("list", "[]");
                java.util.ArrayList<java.util.Map<String, Object>> list = new com.google.gson.Gson().fromJson(
                    listJson, 
                    new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
                );
                if (list != null && !list.isEmpty()) {
                    java.util.Map<String, Object> lastItem = list.get(list.size() - 1);
                    activeDns = (String) lastItem.get("dns");
                    activeUser = (String) lastItem.get("username");
                    activePass = (String) lastItem.get("password");
                    activeCode = lastItem.containsKey("code") ? (String) lastItem.get("code") : "";
                }
            }

            if (!activeDns.isEmpty() && !activeUser.isEmpty() && !activePass.isEmpty()) {
                hasNavigated = true;
                Intent intent = new Intent(MainActivity.this, WaitingActivity.class);
                intent.putExtra("dns", activeDns);
                intent.putExtra("username", activeUser);
                intent.putExtra("password", activePass);
                intent.putExtra("code", activeCode);
                intent.putExtra("only_live", sp.getBoolean("active_only_live", false));
                startActivity(intent);
                finish();
                return;
            }
        }

        CastManager.startServer(getApplicationContext());
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}



        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);
        setContentView(R.layout.main);

        linear1      = (LinearLayout) findViewById(R.id.linear1);
        imageview1   = (ImageView)    findViewById(R.id.imageview1);
        tvVersion    = (TextView)     findViewById(R.id.tvVersion);

        loadCachedImages();
        runEntranceAnimations();
        
        // Start network request to fetch configurations
        fetchSettingsAsync();

        // Register installation using the phone's MAC address
        trackInstall();

        // Guaranteed fallback transition after 2000ms in case the server is extremely slow or fails
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                proceedToNextActivity();
            }
        }, 2000);
    }

    private void trackInstall() {
        final android.content.SharedPreferences sp = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isTracked = sp.getBoolean("is_installed_tracked_v2", false);
        if (!isTracked) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String mac = getMacAddress().toLowerCase();
                        String encodedMac = java.net.URLEncoder.encode(mac, "UTF-8");
                        String urlStr = "https://camillecyrm.serv00.net/api/install.php?device_id=" + encodedMac;
                        
                        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
                        c.setRequestMethod("GET");
                        c.setRequestProperty("User-Agent", USER_AGENT);
                        c.setConnectTimeout(6000);
                        c.setReadTimeout(6000);
                        int resCode = c.getResponseCode();
                        if (resCode == 200) {
                            sp.edit().putBoolean("is_installed_tracked_v2", true).apply();
                            Log.d(TAG, "Install registered successfully for MAC: " + mac);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed registering install: " + e.getMessage());
                    }
                }
            }).start();
        }
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

    private void loadCachedImages() {
        Bitmap bg   = readCache(FILE_BG);
        Bitmap logo = readCache(FILE_LOGO);
        if (bg   != null) linear1.setBackground(new BitmapDrawable(getResources(), bg));
        if (logo != null) imageview1.setImageBitmap(logo);
    }

    private Bitmap readCache(String name) {
        try {
            File f = new File(getFilesDir(), name);
            if (f.exists()) return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) { Log.e(TAG, "readCache error: " + e.getMessage()); }
        return null;
    }

    private void saveCache(Bitmap bmp, String name) {
        try {
            File f = new File(getFilesDir(), name);
            FileOutputStream o = new FileOutputStream(f);
            bmp.compress(name.endsWith(".png")
                ? Bitmap.CompressFormat.PNG
                : Bitmap.CompressFormat.JPEG, 90, o);
            o.flush(); o.close();
            Log.d(TAG, "Saved to cache: " + name);
        } catch (Exception e) { Log.e(TAG, "saveCache error: " + e.getMessage()); }
    }

    private void runEntranceAnimations() {
        imageview1.setAlpha(0f);
        imageview1.setScaleX(0.7f);
        imageview1.setScaleY(0.7f);
        imageview1.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(700)
            .setInterpolator(new OvershootInterpolator(1.1f))
            .setStartDelay(200).start();
    }

    private void fetchSettingsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Fetching JSON from: " + API_URL);
                    HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
                    c.setRequestMethod("GET");
                    c.setRequestProperty("User-Agent", USER_AGENT);
                    c.setConnectTimeout(6000); 
                    c.setReadTimeout(6000);
                    
                    int resCode = c.getResponseCode();
                    if (resCode != 200) {
                        proceedToNextActivity();
                        return;
                    }

                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    String decryptedResponse = TvUtil.decryptAES(sb.toString());
                    final JSONObject json = new JSONObject(decryptedResponse);
                    final String bgUrl   = json.optString("bg_image",    "");
                    final String logoUrl = json.optString("logo_image",  "");
                    final String version = json.optString("app_version", "");
                    final String updateFeatures = json.optString("update_features", "");
                    final int updateRequired = json.optInt("update_required", 0);
                    final String updateLink = json.optString("update_link", "");

                    String lastBgUrl = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_BG_URL, "");
                    String lastLogoUrl = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_LOGO_URL, "");

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!version.isEmpty() && tvVersion  != null) tvVersion.setText("V" + version);
                        }
                    });

                    // Get current app version name
                    String localVersion = "1.0.0";
                    try {
                        localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception e) {}

                    // Compare versions
                    if (!version.isEmpty() && !updateLink.isEmpty() && isServerVersionNewer(version, localVersion)) {
                        showUpdateDialog(version, updateFeatures, updateLink, updateRequired == 1);
                    } else {
                        // Trigger navigation immediately on successful config fetch if no update
                        proceedToNextActivity();
                    }

                    // Background cache updates for background/logo images
                    File bgFile = new File(getFilesDir(), FILE_BG);
                    if (!bgUrl.isEmpty() && (!bgUrl.equals(lastBgUrl) || !bgFile.exists())) {
                        final Bitmap bmp = downloadBitmap(bgUrl);
                        if (bmp != null) {
                            saveCache(bmp, FILE_BG);
                            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(KEY_BG_URL, bgUrl).apply();
                            runOnUiThread(new Runnable() {
                                @Override public void run() { fadeBackground(bmp); }
                            });
                        }
                    }

                    File logoFile = new File(getFilesDir(), FILE_LOGO);
                    if (!logoUrl.isEmpty() && (!logoUrl.equals(lastLogoUrl) || !logoFile.exists())) {
                        final Bitmap bmp = downloadBitmap(logoUrl);
                        if (bmp != null) {
                            saveCache(bmp, FILE_LOGO);
                            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(KEY_LOGO_URL, logoUrl).apply();
                            runOnUiThread(new Runnable() {
                                @Override public void run() { fadeImage(imageview1, bmp); }
                            });
                        }
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "Global Error: " + e.getMessage());
                    proceedToNextActivity();
                }
            }
        }).start();
    }

    private Bitmap downloadBitmap(String urlStr) {
        try {
            return downloadBitmapHelper(urlStr);
        } catch (Exception e) {
            if (urlStr.startsWith("https://")) {
                try {
                    String httpUrl = urlStr.replace("https://", "http://");
                    return downloadBitmapHelper(httpUrl);
                } catch (Exception ex) {}
            }
        }
        return null;
    }

    private Bitmap downloadBitmapHelper(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setConnectTimeout(15000); 
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.connect();
        
        InputStream is = c.getInputStream();
        Bitmap bmp = BitmapFactory.decodeStream(is);
        is.close();
        return bmp;
    }

    private void fadeBackground(final Bitmap bmp) {
        linear1.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override public void run() {
                linear1.setBackground(new BitmapDrawable(getResources(), bmp));
                linear1.animate().alpha(1f).setDuration(500).start();
            }
        }).start();
    }

    private void fadeImage(final ImageView iv, final Bitmap bmp) {
        iv.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override public void run() {
                iv.setImageBitmap(bmp);
                iv.animate().alpha(1f).setDuration(500).start();
            }
        }).start();
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showUpdateDialog(final String serverVersion, final String updateFeatures, final String updateLink, final boolean isRequired) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final android.app.Dialog dialog = new android.app.Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                
                FrameLayout root = new FrameLayout(MainActivity.this);
                root.setBackgroundColor(Color.TRANSPARENT);

                View dimView = new View(MainActivity.this);
                dimView.setBackgroundColor(Color.parseColor("#E00A0E1A")); // Very deep dark translucent blue
                root.addView(dimView);

                float scale = getResources().getDisplayMetrics().density;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int cardWidth = Math.min((int)(290 * scale), (int)(screenWidth * 0.82f));

                // Master ScrollView wraps the card to support small screens / landscape orientation
                android.widget.ScrollView masterScroll = new android.widget.ScrollView(MainActivity.this);
                FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(cardWidth, -2);
                scrollParams.gravity = Gravity.CENTER;
                scrollParams.topMargin = (int)(20 * scale);
                scrollParams.bottomMargin = (int)(20 * scale);
                masterScroll.setLayoutParams(scrollParams);
                masterScroll.setVerticalScrollBarEnabled(false);
                masterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding((int)(16*scale), (int)(16*scale), (int)(16*scale), (int)(16*scale));
                card.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));

                GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.parseColor("#E60A0F24"), Color.parseColor("#E61A2C4D")}
                );
                gd.setCornerRadius(24 * scale);
                gd.setStroke((int)(2 * scale), Color.parseColor("#8000E5FF")); // Glow cyan/neon border
                card.setBackground(gd);

                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageResource(android.R.drawable.stat_sys_download);
                icon.setColorFilter(Color.parseColor("#00E5FF"));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int)(38*scale), (int)(38*scale));
                iconParams.bottomMargin = (int)(10*scale);
                icon.setLayoutParams(iconParams);
                card.addView(icon);

                TextView title = new TextView(MainActivity.this);
                title.setText("تحديث جديد متوفر! v" + serverVersion);
                title.setTextColor(Color.WHITE);
                title.setTextSize(15);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
                titleParams.bottomMargin = (int)(6*scale);
                title.setLayoutParams(titleParams);
                card.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setText("يرجى تحديث التطبيق للحصول على أفضل تجربة وأحدث الميزات.");
                sub.setTextColor(Color.parseColor("#B0BEC5"));
                sub.setTextSize(11);
                sub.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
                subParams.bottomMargin = (int)(12*scale);
                sub.setLayoutParams(subParams);
                card.addView(sub);

                if (updateFeatures != null && !updateFeatures.trim().isEmpty()) {
                    TextView changeTitle = new TextView(MainActivity.this);
                    changeTitle.setText("ما الجديد في هذا الإصدار:");
                    changeTitle.setTextColor(Color.parseColor("#00E5FF"));
                    changeTitle.setTextSize(11);
                    changeTitle.setGravity(Gravity.RIGHT);
                    changeTitle.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams ctParams = new LinearLayout.LayoutParams(-1, -2);
                    ctParams.bottomMargin = (int)(5*scale);
                    changeTitle.setLayoutParams(ctParams);
                    card.addView(changeTitle);

                    android.widget.ScrollView scrollView = new android.widget.ScrollView(MainActivity.this);
                    LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(-1, (int)(80*scale));
                    svParams.bottomMargin = (int)(15*scale);
                    scrollView.setLayoutParams(svParams);

                    TextView features = new TextView(MainActivity.this);
                    features.setText(updateFeatures);
                    features.setTextColor(Color.WHITE);
                    features.setTextSize(11);
                    features.setGravity(Gravity.RIGHT);
                    features.setLineSpacing(0, 1.3f);
                    scrollView.addView(features);
                    card.addView(scrollView);
                }

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                btnRow.setLayoutParams(rowParams);

                if (!isRequired) {
                    TextView btnCancel = new TextView(MainActivity.this);
                    btnCancel.setText("لاحقاً");
                    btnCancel.setTextColor(Color.parseColor("#B0BEC5"));
                    btnCancel.setGravity(Gravity.CENTER);
                    btnCancel.setTextSize(13);
                    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(38*scale), 1.0f);
                    cancelParams.rightMargin = (int)(10*scale);
                    btnCancel.setLayoutParams(cancelParams);

                    GradientDrawable cancelGd = new GradientDrawable();
                    cancelGd.setCornerRadius(14 * scale);
                    cancelGd.setColor(Color.parseColor("#1AFFFFFF"));
                    cancelGd.setStroke((int)(1.5 * scale), Color.parseColor("#33FFFFFF"));
                    btnCancel.setBackground(cancelGd);

                    btnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            proceedToNextActivity();
                        }
                    });
                    TvUtil.applyTvFocusHighlight(btnCancel, 14.0f);
                    btnRow.addView(btnCancel);
                }

                TextView btnUpdate = new TextView(MainActivity.this);
                btnUpdate.setText("تحديث الآن");
                btnUpdate.setTextColor(Color.WHITE);
                btnUpdate.setGravity(Gravity.CENTER);
                btnUpdate.setTextSize(14);
                btnUpdate.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(0, (int)(38*scale), 1.2f);
                btnUpdate.setLayoutParams(updateParams);

                GradientDrawable updateGd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
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
                            Toast.makeText(MainActivity.this, "فشل فتح رابط التحديث!", Toast.LENGTH_SHORT).show();
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

                dialog.setCancelable(!isRequired);
                dialog.setCanceledOnTouchOutside(!isRequired);

                hasNavigated = true; // Prevents splash redirect from firing!

                dialog.show();
            }
        });
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}