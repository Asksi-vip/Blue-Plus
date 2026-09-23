package com.blue.plus;

import android.view.WindowManager;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.provider.Settings;
import java.io.File;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends Activity {
    private FrameLayout rootLayout;
    private LinearLayout loginCard;
    private EditText etUser, etPass, etActivation;
    private LinearLayout layoutUserPass, layoutActivation;
    private CheckBox cbActivation;

    private String defaultDnsFromPanel = "";
    private String defaultNameFromPanel = "";
    private ArrayList<HashMap<String, String>> dnsServersList = new ArrayList<>();

    private static final String THEME_COLOR = "#2196F3";
    private static final String FILE_BG = "splash_bg.jpg";
    private static final String API_ACTIVATION_URL = "https://blueplus-auz.pages.dev/codes.json";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (Exception e) {}
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        createLoginUI();

        setContentView(rootLayout);
        loadCachedBackground();
        fetchDefaultConfig();
    }

    private void createLoginUI() {
        float scale = getResources().getDisplayMetrics().density;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        boolean isTv = TvUtil.isAndroidTV(this);
        
        loginCard = new LinearLayout(this);
        
        FrameLayout.LayoutParams cardParams;
        if (isTv) {
            cardParams = new FrameLayout.LayoutParams((int)(370 * scale), -2);
        } else {
            // For mobile: 92% of screen width, max 330dp in landscape
            int targetWidth = (int)(screenWidth * 0.92f);
            int maxWidth = (int)(330 * scale);
            cardParams = new FrameLayout.LayoutParams(Math.min(targetWidth, maxWidth), -2);
        }
        cardParams.gravity = Gravity.CENTER;
        loginCard.setLayoutParams(cardParams);
        loginCard.setOrientation(LinearLayout.VERTICAL);

        // iOS Frosted Glassmorphism Container
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setCornerRadius(isTv ? 20 * scale : 16 * scale);
        cardShape.setColor(Color.parseColor("#E60F1422")); // Deep midnight translucent glass
        cardShape.setStroke((int)(1.5f * scale), Color.parseColor("#3380B4FF")); // Specular light reflection border
        loginCard.setBackground(cardShape);

        // Header Section with App Monogram & SF-style typography
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        int headerPadding = (int)((isTv ? 14 : 10) * scale);
        header.setPadding(0, headerPadding, 0, (int)(4 * scale));

        // Blue+ Badge Monogram
        TextView tvBrand = new TextView(this);
        tvBrand.setText("★ BLUE + ★");
        tvBrand.setTextColor(Color.parseColor("#38B6FF"));
        tvBrand.setTextSize(isTv ? 12 : 10);
        tvBrand.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBrand.setGravity(Gravity.CENTER);
        int badgePadH = (int)(10 * scale);
        int badgePadV = (int)(3 * scale);
        tvBrand.setPadding(badgePadH, badgePadV, badgePadH, badgePadV);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#220A84FF"));
        badgeBg.setCornerRadius(10 * scale);
        badgeBg.setStroke((int)(1 * scale), Color.parseColor("#4438B6FF"));
        tvBrand.setBackground(badgeBg);
        header.addView(tvBrand);

        TextView tvWelcome = new TextView(this);
        tvWelcome.setText(TvUtil.translate(this, "Welcome"));
        tvWelcome.setTextColor(Color.WHITE);
        tvWelcome.setTextSize(isTv ? 20 : 16);
        tvWelcome.setTypeface(null, android.graphics.Typeface.BOLD);
        tvWelcome.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams welcomeLp = new LinearLayout.LayoutParams(-2, -2);
        welcomeLp.topMargin = (int)(6 * scale);
        tvWelcome.setLayoutParams(welcomeLp);
        header.addView(tvWelcome);

        TextView tvSub = new TextView(this);
        tvSub.setText(TvUtil.translate(this, "Enter activation code"));
        tvSub.setTextColor(Color.parseColor("#90FFFFFF"));
        tvSub.setTextSize(isTv ? 13 : 11);
        tvSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = (int)(2 * scale);
        tvSub.setLayoutParams(subLp);
        header.addView(tvSub);

        loginCard.addView(header);

        LinearLayout inputArea = new LinearLayout(this);
        inputArea.setOrientation(LinearLayout.VERTICAL);
        int areaPaddingH = (int)((isTv ? 18 : 14) * scale);
        int areaPaddingB = (int)((isTv ? 16 : 12) * scale);
        inputArea.setPadding(areaPaddingH, (int)(8 * scale), areaPaddingH, areaPaddingB);

        // iOS Segmented Control (Capsule Switcher)
        final LinearLayout segmentedContainer = new LinearLayout(this);
        segmentedContainer.setOrientation(LinearLayout.HORIZONTAL);
        segmentedContainer.setGravity(Gravity.CENTER);
        int segHeight = (int)((isTv ? 40 : 34) * scale);
        LinearLayout.LayoutParams segParams = new LinearLayout.LayoutParams(-1, segHeight);
        segParams.topMargin = (int)(4 * scale);
        segParams.bottomMargin = (int)(10 * scale);
        segmentedContainer.setLayoutParams(segParams);
        segmentedContainer.setPadding((int)(3 * scale), (int)(3 * scale), (int)(3 * scale), (int)(3 * scale));

        final GradientDrawable segBg = new GradientDrawable();
        segBg.setColor(Color.parseColor("#1CFFFFFF"));
        segBg.setCornerRadius(12 * scale);
        segBg.setStroke((int)(1 * scale), Color.parseColor("#25FFFFFF"));
        segmentedContainer.setBackground(segBg);

        final TextView tabUserPass = new TextView(this);
        tabUserPass.setText(TvUtil.translate(this, "username"));
        tabUserPass.setGravity(Gravity.CENTER);
        tabUserPass.setTextSize(isTv ? 13 : 11);
        tabUserPass.setFocusable(true);
        LinearLayout.LayoutParams tab1Lp = new LinearLayout.LayoutParams(0, -1, 1f);
        tabUserPass.setLayoutParams(tab1Lp);

        final TextView tabActivation = new TextView(this);
        tabActivation.setText(TvUtil.translate(this, "Activation code"));
        tabActivation.setGravity(Gravity.CENTER);
        tabActivation.setTextSize(isTv ? 13 : 11);
        tabActivation.setFocusable(true);
        LinearLayout.LayoutParams tab2Lp = new LinearLayout.LayoutParams(0, -1, 1f);
        tabActivation.setLayoutParams(tab2Lp);

        // Helper to update iOS Segmented Tab aesthetics
        final Runnable updateSegmentStyles = new Runnable() {
            @Override
            public void run() {
                boolean isActivation = (layoutActivation != null && layoutActivation.getVisibility() == View.VISIBLE);
                
                GradientDrawable activeTabBg = new GradientDrawable();
                activeTabBg.setColor(Color.parseColor("#0A84FF")); // iOS System Blue
                activeTabBg.setCornerRadius(9 * getResources().getDisplayMetrics().density);

                GradientDrawable inactiveTabBg = new GradientDrawable();
                inactiveTabBg.setColor(Color.TRANSPARENT);

                if (!isActivation) {
                    tabUserPass.setBackground(activeTabBg);
                    tabUserPass.setTextColor(Color.WHITE);
                    tabUserPass.setTypeface(null, android.graphics.Typeface.BOLD);

                    tabActivation.setBackground(inactiveTabBg);
                    tabActivation.setTextColor(Color.parseColor("#99FFFFFF"));
                    tabActivation.setTypeface(null, android.graphics.Typeface.NORMAL);
                } else {
                    tabActivation.setBackground(activeTabBg);
                    tabActivation.setTextColor(Color.WHITE);
                    tabActivation.setTypeface(null, android.graphics.Typeface.BOLD);

                    tabUserPass.setBackground(inactiveTabBg);
                    tabUserPass.setTextColor(Color.parseColor("#99FFFFFF"));
                    tabUserPass.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
        };

        TvUtil.applyTvFocusHighlight(tabUserPass, 9.0f);
        TvUtil.applyTvFocusHighlight(tabActivation, 9.0f);

        segmentedContainer.addView(tabUserPass);
        segmentedContainer.addView(tabActivation);
        inputArea.addView(segmentedContainer);

        layoutUserPass = new LinearLayout(this);
        layoutUserPass.setOrientation(LinearLayout.VERTICAL);

        etUser = createInput(layoutUserPass, "username", android.R.drawable.ic_menu_myplaces);
        etPass = createInput(layoutUserPass, "password", android.R.drawable.ic_lock_idle_lock);

        inputArea.addView(layoutUserPass);

        layoutActivation = new LinearLayout(this);
        layoutActivation.setOrientation(LinearLayout.VERTICAL);
        layoutActivation.setVisibility(View.GONE);

        etActivation = createInput(layoutActivation, "Activation code", android.R.drawable.ic_menu_set_as);

        inputArea.addView(layoutActivation);

        // Hidden dummy CheckBox for 100% backward compatibility with existing listeners
        cbActivation = new CheckBox(this);
        cbActivation.setVisibility(View.GONE);
        inputArea.addView(cbActivation);

        // Set Tab Click Listeners
        tabUserPass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutUserPass.setVisibility(View.VISIBLE);
                layoutActivation.setVisibility(View.GONE);
                cbActivation.setChecked(false);
                updateSegmentStyles.run();
                if (etUser != null) etUser.requestFocus();
            }
        });

        tabActivation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutUserPass.setVisibility(View.GONE);
                layoutActivation.setVisibility(View.VISIBLE);
                cbActivation.setChecked(true);
                updateSegmentStyles.run();
                if (etActivation != null) etActivation.requestFocus();
            }
        });

        updateSegmentStyles.run();

        // Modern iOS Primary Action Button
        TextView btnLogin = new TextView(this);
        btnLogin.setText(TvUtil.translate(this, "LOGIN") + "  ➔");
        btnLogin.setTextColor(Color.WHITE);
        btnLogin.setTypeface(null, android.graphics.Typeface.BOLD);
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setTextSize(isTv ? 16 : 14);
        int btnPadding = (int)((isTv ? 12 : 9) * scale);
        btnLogin.setPadding(0, btnPadding, 0, btnPadding);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, -2);
        btnParams.topMargin = (int)((isTv ? 14 : 10) * scale);
        btnLogin.setLayoutParams(btnParams);

        // iOS Vibrant Blue Gradient Button
        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor("#0A84FF"), Color.parseColor("#0055D4")}
        );
        btnBg.setCornerRadius(12 * scale);
        btnLogin.setBackground(btnBg);
        
        TvUtil.applyTvFocusHighlight(btnLogin, 12.0f);
        
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLogin();
            }
        });
        inputArea.addView(btnLogin);

        // Clean iOS Hardware MAC Chip (Footer)
        LinearLayout macContainer = new LinearLayout(this);
        macContainer.setOrientation(LinearLayout.HORIZONTAL);
        macContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams macParams = new LinearLayout.LayoutParams(-2, -2);
        macParams.gravity = Gravity.CENTER_HORIZONTAL;
        macParams.topMargin = (int)((isTv ? 12 : 8) * scale);
        macContainer.setLayoutParams(macParams);
        macContainer.setPadding((int)(10 * scale), (int)(3 * scale), (int)(10 * scale), (int)(3 * scale));

        GradientDrawable macBg = new GradientDrawable();
        macBg.setColor(Color.parseColor("#14FFFFFF"));
        macBg.setCornerRadius(10 * scale);
        macBg.setStroke((int)(1 * scale), Color.parseColor("#20FFFFFF"));
        macContainer.setBackground(macBg);

        TextView tvMacIcon = new TextView(this);
        tvMacIcon.setText("🖥️ ");
        tvMacIcon.setTextSize(isTv ? 11 : 9);
        macContainer.addView(tvMacIcon);

        TextView tvMac = new TextView(this);
        tvMac.setText("MAC: " + getMacAddress().toLowerCase());
        tvMac.setTextColor(Color.parseColor("#90FFFFFF"));
        tvMac.setTextSize(isTv ? 11 : 9);
        macContainer.addView(tvMac);

        inputArea.addView(macContainer);

        loginCard.addView(inputArea);

        // Wrap loginCard in a centered FrameLayout inside ScrollView to prevent vertical stretching!
        FrameLayout cardWrapper = new FrameLayout(this);
        cardWrapper.setLayoutParams(new android.widget.ScrollView.LayoutParams(-1, -1));
        cardWrapper.addView(loginCard);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFillViewport(true);
        scrollView.addView(cardWrapper);

        rootLayout.addView(scrollView);
    }

    private void handleLogin() {
        if (layoutActivation.getVisibility() == View.VISIBLE) {
            String code = etActivation.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, TvUtil.translate(this, "يرجى إدخال الكود"), Toast.LENGTH_SHORT).show();
                return;
            }
            validateActivationCode(code);
        } else {
            final String username = etUser.getText().toString().trim();
            final String password = etPass.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, TvUtil.translate(this, "يرجى إدخال اسم المستخدم وكلمة المرور"), Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (dnsServersList.isEmpty()) {
                if (!defaultDnsFromPanel.isEmpty()) {
                    HashMap<String, String> defaultSrv = new HashMap<>();
                    defaultSrv.put("url", defaultDnsFromPanel);
                    defaultSrv.put("name", defaultNameFromPanel.isEmpty() ? "VIP Playlist" : defaultNameFromPanel);
                    dnsServersList.add(defaultSrv);
                } else {
                    Toast.makeText(this, TvUtil.translate(this, "سيرفرات الاتصال غير متوفرة. جاري استرجاعها..."), Toast.LENGTH_SHORT).show();
                    fetchDefaultConfig();
                    return;
                }
            }
            
            final ProgressDialog pd = new ProgressDialog(LoginActivity.this);
            pd.setMessage(TvUtil.translate(LoginActivity.this, "جاري التحقق من الحساب..."));
            pd.setCancelable(false);
            pd.show();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    String workingDns = null;
                    String workingName = null;
                    
                    for (HashMap<String, String> srv : dnsServersList) {
                        final String testDns = srv.get("url");
                        final String testName = srv.get("name");
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.setMessage("فحص: " + testName + "...");
                            }
                        });
                        
                        if (checkDnsCredentials(testDns, username, password)) {
                            workingDns = testDns;
                            workingName = testName;
                            break;
                        }
                    }
                    
                    final String finalDns = workingDns;
                    final String finalName = workingName;
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                            if (finalDns != null) {
                                String playlistName = finalName;
                                if (playlistName == null || playlistName.trim().isEmpty()) {
                                    playlistName = "VIP Playlist";
                                }
                                
                                Map<String, Object> apiData = new HashMap<>();
                                apiData.put("dns", finalDns);
                                apiData.put("username", username);
                                apiData.put("password", password);
                                apiData.put("code", "VIP");
                                
                                savePlaylist(playlistName, apiData);
                                
                                Intent intent = new Intent(LoginActivity.this, WaitingActivity.class);
                                intent.putExtra("dns", finalDns);
                                intent.putExtra("username", username);
                                intent.putExtra("password", password);
                                intent.putExtra("code", "VIP");
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "الحساب غير صحيح أو منتهي الصلاحية على جميع السيرفرات!"), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private void fetchDefaultConfig() {
        RequestNetwork rn = new RequestNetwork(this);
        String configUrl = "https://blueplus-auz.pages.dev/settings.json";
        rn.startRequestNetwork("GET", configUrl, "config", new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    String decrypted = TvUtil.decryptAES(response);
                    org.json.JSONObject obj = new org.json.JSONObject(decrypted);
                    defaultDnsFromPanel = normalizeDns(obj.optString("default_dns", obj.optString("dns", "")));
                    defaultNameFromPanel = obj.optString("name", "IPTV Panel Pro");
                    
                    dnsServersList.clear();
                    
                    if (!defaultDnsFromPanel.isEmpty()) {
                        HashMap<String, String> defaultSrv = new HashMap<>();
                        defaultSrv.put("url", defaultDnsFromPanel);
                        defaultSrv.put("name", defaultNameFromPanel.isEmpty() ? "VIP Playlist" : defaultNameFromPanel);
                        dnsServersList.add(defaultSrv);
                    }
                    
                    if (obj.has("vip_servers")) {
                        org.json.JSONArray servers = obj.getJSONArray("vip_servers");
                        for (int i = 0; i < servers.length(); i++) {
                            org.json.JSONObject serverObj = servers.getJSONObject(i);
                            String u = normalizeDns(serverObj.optString("url", ""));
                            String n = serverObj.optString("name", "VIP Playlist");
                            if (!u.isEmpty()) {
                                boolean exists = false;
                                for (HashMap<String, String> existing : dnsServersList) {
                                    if (existing.get("url").equalsIgnoreCase(u)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    HashMap<String, String> srv = new HashMap<>();
                                    srv.put("url", u);
                                    srv.put("name", n);
                                    dnsServersList.add(srv);
                                }
                            }
                        }
                    }
                    
                    if (dnsServersList.isEmpty() && !defaultDnsFromPanel.isEmpty()) {
                        HashMap<String, String> defaultSrv = new HashMap<>();
                        defaultSrv.put("url", defaultDnsFromPanel);
                        defaultSrv.put("name", defaultNameFromPanel.isEmpty() ? "VIP Playlist" : defaultNameFromPanel);
                        dnsServersList.add(defaultSrv);
                    }
                } catch (Exception e) {
                    Log.e("LoginActivity", "Error parsing config: " + e.getMessage());
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                Log.e("LoginActivity", "Config fetch failed: " + message);
            }
        });
    }

    private boolean checkDnsCredentials(String dns, String username, String password) {
        try {
            java.net.URL loginUrl = new java.net.URL(dns + "/player_api.php?username=" + username + "&password=" + password);
            java.net.HttpURLConnection loginConn = (java.net.HttpURLConnection) loginUrl.openConnection();
            loginConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            loginConn.setConnectTimeout(6000);
            loginConn.setReadTimeout(6000);
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
                    int auth = userInfo.optInt("auth", 0);
                    String authStr = userInfo.optString("auth", "");
                    String status = userInfo.optString("status", "");
                    
                    if (auth == 1 || authStr.equals("1") || status.equalsIgnoreCase("Active")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LoginActivity", "DNS check failed for " + dns + ": " + e.getMessage());
        }
        return false;
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

    private void validateActivationCode(final String code) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(TvUtil.translate(this, "جاري التحقق من الكود..."));
        pd.setCancelable(false);
        pd.show();

        RequestNetwork rn = new RequestNetwork(this);
        rn.startRequestNetwork("GET", API_ACTIVATION_URL, "activation", new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    String decrypted = TvUtil.decryptAES(response);
                    ArrayList<Map<String, Object>> codesList = new Gson().fromJson(decrypted, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
                    Map<String, Object> foundCode = null;
                    if (codesList != null) {
                        for (Map<String, Object> c : codesList) {
                            if (code.equalsIgnoreCase(String.valueOf(c.get("code")))) {
                                foundCode = c;
                                break;
                            }
                        }
                    }

                    if (foundCode != null) {
                        String statusStr = foundCode.containsKey("status") ? String.valueOf(foundCode.get("status")) : "";
                        if ("disabled".equalsIgnoreCase(statusStr)) {
                            pd.dismiss();
                            Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "تم تعطيل هذا الكود من قبل المسؤول"), Toast.LENGTH_LONG).show();
                            return;
                        }

                        boolean isExpired = false;
                        if (foundCode.containsKey("expires_at") && foundCode.get("expires_at") != null) {
                            String expiresAtStr = String.valueOf(foundCode.get("expires_at")).trim();
                            if (!expiresAtStr.isEmpty() && !"null".equalsIgnoreCase(expiresAtStr)) {
                                try {
                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                                    java.util.Date expiryDate = sdf.parse(expiresAtStr);
                                    java.util.Date currentDate = new java.util.Date();
                                    if (currentDate.after(expiryDate)) {
                                        isExpired = true;
                                    }
                                } catch (Exception e) {
                                    Log.e("LoginActivity", "Failed parsing expires_at expiry: " + e.getMessage());
                                }
                            }
                        }

                        if (isExpired) {
                            pd.dismiss();
                            Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "هذا الكود منتهي الصلاحية!"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        final Map<String, Object> finalFoundCode = foundCode;
                        Map<String, Object> map = new HashMap<>();
                        map.put("status", true);
                        map.put("code", code);
                        map.put("dns", finalFoundCode.get("dns"));
                        map.put("username", finalFoundCode.get("username"));
                        map.put("password", finalFoundCode.get("password"));
                        map.put("name", finalFoundCode.containsKey("name") ? finalFoundCode.get("name") : "VIP Playlist");
                        
                        pd.dismiss();
                        showPlaylistDialog(map);
                    } else {
                        pd.dismiss();
                        Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "الكود غير صحيح"), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    pd.dismiss();
                    Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "حدث خطأ في معالجة البيانات"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                pd.dismiss();
                Toast.makeText(LoginActivity.this, TvUtil.translate(LoginActivity.this, "فشل الاتصال بالسيرفر"), Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void showPlaylistDialog(final Map<String, Object> apiData) {
        float scale = getResources().getDisplayMetrics().density;

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog);
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding((int)(20*scale), (int)(20*scale), (int)(20*scale), (int)(20*scale));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#332196F3"));
        gd.setCornerRadius(15 * scale);
        card.setBackground(gd);

        TextView title = new TextView(this);
        title.setText(TvUtil.translate(this, "Enter Playlist Name"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText(TvUtil.translate(this, "If you don't choose a name for the playlist, a name will be added automatically"));
        sub.setTextColor(Color.parseColor("#BBFFFFFF"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int)(10*scale), 0, (int)(20*scale));
        card.addView(sub);

        final LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        final GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.WHITE);
        rowBg.setCornerRadius(10 * scale);
        inputRow.setBackground(rowBg);
        inputRow.setPadding((int)(10*scale), (int)(10*scale), (int)(10*scale), (int)(10*scale));
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView userIcon = new ImageView(this);
        userIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
        userIcon.setLayoutParams(new LinearLayout.LayoutParams((int)(30*scale), (int)(30*scale)));
        inputRow.addView(userIcon);

        final EditText etName = new EditText(this);
        etName.setHint("Ex : MOODTV");
        etName.setBackgroundColor(Color.TRANSPARENT);
        etName.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        etName.setPadding((int)(10*scale), 0, 0, 0);

        etName.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = v.getContext().getResources().getDisplayMetrics().density;
                if (hasFocus) {
                    GradientDrawable focusBg = new GradientDrawable();
                    focusBg.setColor(Color.parseColor("#2200E5FF"));
                    focusBg.setCornerRadius(10 * sc);
                    focusBg.setStroke((int)(2 * sc), Color.parseColor("#00E5FF"));
                    inputRow.setBackground(focusBg);
                } else {
                    inputRow.setBackground(rowBg);
                }
            }
        });

        inputRow.addView(etName);
        card.addView(inputRow);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, (int)(20*scale), 0, 0);

        TextView btnExit = new TextView(this);
        btnExit.setText(TvUtil.translate(this, "EXIT"));
        btnExit.setTextColor(Color.WHITE);
        btnExit.setGravity(Gravity.CENTER);
        btnExit.setPadding((int)(10*scale), (int)(10*scale), (int)(10*scale), (int)(10*scale));
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(0, -2, 1);
        exitLp.rightMargin = (int)(10*scale);
        btnExit.setLayoutParams(exitLp);

        GradientDrawable exitBg = new GradientDrawable();
        exitBg.setColor(Color.parseColor("#1AFFFFFF"));
        exitBg.setCornerRadius(10 * scale);
        exitBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
        btnExit.setBackground(exitBg);

        TextView btnSave = new TextView(this);
        btnSave.setText(TvUtil.translate(this, "SAVE"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding((int)(10*scale), (int)(10*scale), (int)(10*scale), (int)(10*scale));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, -2, 1);
        btnSave.setLayoutParams(saveLp);

        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(Color.parseColor("#332196F3"));
        saveBg.setCornerRadius(10 * scale);
        saveBg.setStroke((int)(1 * scale), Color.parseColor("#AA2196F3"));
        btnSave.setBackground(saveBg);

        TvUtil.applyTvFocusHighlight(btnExit, 10.0f);
        TvUtil.applyTvFocusHighlight(btnSave, 10.0f);

        btnRow.addView(btnExit);
        btnRow.addView(btnSave);
        card.addView(btnRow);

        final AlertDialog dialog = builder.setView(card).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String playlistName = etName.getText().toString().trim();
                if (playlistName.isEmpty()) {
                    String actCode = apiData != null && apiData.containsKey("code") && apiData.get("code") != null ? String.valueOf(apiData.get("code")).trim() : "";
                    if (!actCode.isEmpty() && !actCode.equalsIgnoreCase("VIP")) {
                        playlistName = actCode;
                    } else if (apiData != null && apiData.containsKey("name") && apiData.get("name") != null) {
                        playlistName = String.valueOf(apiData.get("name"));
                    } else {
                        playlistName = "VIP Playlist";
                    }
                }
                savePlaylist(playlistName, apiData);
                dialog.dismiss();
                Intent intent = new Intent(LoginActivity.this, WaitingActivity.class);
                intent.putExtra("dns", (String)apiData.get("dns"));
                intent.putExtra("username", (String)apiData.get("username"));
                intent.putExtra("password", (String)apiData.get("password"));
                intent.putExtra("code", (String)apiData.get("code"));
                startActivity(intent);
                finish();
            }
        });
    }

    private void savePlaylist(String name, Map<String, Object> data) {
        SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        String existing = sp.getString("list", "[]");
        ArrayList<Map<String, Object>> list = new Gson().fromJson(existing, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());

        String finalName = name;
        if (finalName == null || finalName.trim().isEmpty() || finalName.toLowerCase().contains("iptv panel pro") || finalName.equalsIgnoreCase("VIP Playlist")) {
            int count = (list != null ? list.size() : 0) + 1;
            finalName = "B" + count;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("name", finalName);
        item.put("dns", data.get("dns"));
        item.put("username", data.get("username"));
        item.put("password", data.get("password"));
        item.put("code", data.get("code"));

        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(0, item);
        sp.edit().putString("list", new Gson().toJson(list))
                 .putBoolean("auto_login", true)
                 .apply();
        Toast.makeText(this, TvUtil.translate(this, "تم حفظ قائمة التشغيل بنجاح"), Toast.LENGTH_SHORT).show();
    }

    private EditText createInput(LinearLayout container, final String hint, int iconRes) {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isAndroidTV(this);
        final boolean isPassword = "password".equalsIgnoreCase(hint);
        
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#1AFFFFFF")); // iOS ultra-thin frosted dark surface
        rowBg.setCornerRadius(12 * scale);
        rowBg.setStroke((int)(1 * scale), Color.parseColor("#26FFFFFF")); // Subtle specular border
        row.setBackground(rowBg);

        int rowHeight = (int)((isTv ? 44 : 36) * scale);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, rowHeight);
        rowParams.topMargin = (int)((isTv ? 10 : 7) * scale);
        row.setLayoutParams(rowParams);
        int paddingSide = (int)((isTv ? 14 : 11) * scale);
        row.setPadding(paddingSide, 0, paddingSide, 0);

        ImageView icon = new ImageView(this);
        int iconSize = (int)((isTv ? 20 : 17) * scale);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.parseColor("#38B6FF")); // Vibrant iOS System Blue accent
        row.addView(icon);

        final EditText et = new EditText(this);
        et.setHint(TvUtil.translate(this, hint));
        et.setHintTextColor(Color.parseColor("#80FFFFFF"));
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setTextSize(isTv ? 14 : 12);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, -1, 1f);
        etParams.leftMargin = (int)(10 * scale);
        etParams.rightMargin = (int)(6 * scale);
        et.setLayoutParams(etParams);
        et.setSingleLine(true);
        
        if (isPassword) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            et.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        // Highlight the entire row wrapper when the EditText gains focus on smart TVs
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = v.getContext().getResources().getDisplayMetrics().density;
                if (hasFocus) {
                    GradientDrawable focusBg = new GradientDrawable();
                    focusBg.setColor(Color.parseColor("#24007AFF")); // iOS System Blue tint
                    focusBg.setCornerRadius(12 * sc);
                    focusBg.setStroke((int)(2 * sc), Color.parseColor("#0A84FF")); // Glowing active border
                    row.setBackground(focusBg);
                } else {
                    row.setBackground(rowBg);
                }
            }
        });

        row.addView(et);

        // Password show/hide eye toggle button
        if (isPassword) {
            final ImageView eyeToggle = new ImageView(this);
            int eyeSize = (int)((isTv ? 22 : 18) * scale);
            eyeToggle.setLayoutParams(new LinearLayout.LayoutParams(eyeSize, eyeSize));
            eyeToggle.setImageResource(android.R.drawable.ic_menu_view);
            eyeToggle.setColorFilter(Color.parseColor("#80FFFFFF"));
            eyeToggle.setFocusable(true);
            TvUtil.applyTvFocusHighlight(eyeToggle, 6.0f);
            
            final boolean[] isVisible = {false};
            eyeToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isVisible[0] = !isVisible[0];
                    if (isVisible[0]) {
                        et.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        eyeToggle.setColorFilter(Color.parseColor("#0A84FF"));
                    } else {
                        et.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        eyeToggle.setColorFilter(Color.parseColor("#80FFFFFF"));
                    }
                    et.setSelection(et.getText().length());
                }
            });
            row.addView(eyeToggle);
        }

        container.addView(row);
        return et;
    }

    private String getMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
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
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null) androidId = "DEADC0DE0000";
            if (androidId.length() < 12) androidId = (androidId + "000000000000").substring(0, 12);
            StringBuilder formattedMac = new StringBuilder();
            for (int i = 0; i < 12; i += 2) {
                if (i > 0) formattedMac.append(":");
                formattedMac.append(androidId.substring(i, i + 2).toUpperCase(Locale.ENGLISH));
            }
            return formattedMac.toString();
        } catch (Exception ex) { return "E1:AA:63:DE:99:AC"; }
    }

    private void loadCachedBackground() {
        try {
            File f = new File(getFilesDir(), FILE_BG);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}
