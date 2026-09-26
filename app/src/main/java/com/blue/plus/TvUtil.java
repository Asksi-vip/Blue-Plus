package com.blue.plus;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import java.io.File;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

public class TvUtil {

    // Enable TLS 1.2 for older Android versions (4.4 - 7.0) safely
    public static void enableTls12(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 16 && android.os.Build.VERSION.SDK_INT <= 20) {
            try {
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
            } catch (Exception e) {
                android.util.Log.e("TvUtil", "Error enabling TLS 1.2", e);
            }
        }
    }

    // Global robust image loader
    private static final java.util.concurrent.ExecutorService sExecutor = 
        java.util.concurrent.Executors.newFixedThreadPool(6);

    private static android.util.LruCache<String, Bitmap> sMemoryCache = null;

    private static synchronized android.util.LruCache<String, Bitmap> getMemoryCache() {
        if (sMemoryCache == null) {
            int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
            sMemoryCache = new android.util.LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }
        return sMemoryCache;
    }

    private static okhttp3.OkHttpClient sUnsafeImageClient = null;

    private static synchronized okhttp3.OkHttpClient getUnsafeImageClient() {
        if (sUnsafeImageClient == null) {
            try {
                final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }
                };

                final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
                builder.sslSocketFactory(sslSocketFactory, (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                        return true;
                    }
                });
                builder.connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS);
                builder.readTimeout(8, java.util.concurrent.TimeUnit.SECONDS);
                builder.connectionPool(new okhttp3.ConnectionPool(15, 30, java.util.concurrent.TimeUnit.SECONDS));
                sUnsafeImageClient = builder.build();
            } catch (Exception e) {
                sUnsafeImageClient = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            }
        }
        return sUnsafeImageClient;
    }

    private static String getMd5(String s) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static void loadImage(final ImageView imageView, final String url) {
        if (url == null || url.isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }

        final String finalUrl = url.trim().startsWith("http") ? url.trim() : "http://" + url.trim();

        // Tag the ImageView with the URL to check for reuse when binding
        imageView.setTag(com.blue.plus.R.id.glide_tag_url, finalUrl);

        // 1. Check memory cache first
        Bitmap cached = getMemoryCache().get(finalUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set placeholder
        imageView.setImageDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1A2035")));

        // 2. Fetch asynchronously
        sExecutor.submit(new Runnable() {
            @Override
            public void run() {
                // Check if view has already been recycled before we start
                if (!finalUrl.equals(imageView.getTag(com.blue.plus.R.id.glide_tag_url))) {
                    return;
                }

                // Check disk cache
                final Context context = imageView.getContext();
                File cacheDir = new File(context.getCacheDir(), "iptv_covers");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                final File cachedFile = new File(cacheDir, getMd5(finalUrl));

                Bitmap bmp = null;
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    try {
                        bmp = decodeSampledBitmapFromFile(cachedFile.getAbsolutePath(), 400, 400);
                    } catch (OutOfMemoryError oom) {
                        getMemoryCache().evictAll();
                        System.gc();
                    } catch (Exception ignored) {}
                }

                if (bmp == null) {
                    // Download via OkHttp
                    okhttp3.Response response = null;
                    try {
                        okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(finalUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build();
                        response = getUnsafeImageClient().newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            java.io.InputStream is = response.body().byteStream();
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(cachedFile);
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                            fos.close();
                            is.close();

                            bmp = decodeSampledBitmapFromFile(cachedFile.getAbsolutePath(), 400, 400);
                        }
                    } catch (OutOfMemoryError oom) {
                        getMemoryCache().evictAll();
                        System.gc();
                    } catch (Exception e) {
                        android.util.Log.e("TvUtil", "Failed downloading " + finalUrl, e);
                    } finally {
                        if (response != null) {
                            try { response.close(); } catch (Exception ignored) {}
                        }
                    }
                }

                if (bmp != null) {
                    getMemoryCache().put(finalUrl, bmp);
                    final Bitmap finalBmp = bmp;
                    // Post to UI thread
                    imageView.post(new Runnable() {
                        @Override
                        public void run() {
                            // Verify tag matches to prevent placing wrong image in recycled views
                            if (finalUrl.equals(imageView.getTag(com.blue.plus.R.id.glide_tag_url))) {
                                imageView.setImageBitmap(finalBmp);
                            }
                        }
                    });
                } else {
                    // Set error placeholder
                    imageView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalUrl.equals(imageView.getTag(com.blue.plus.R.id.glide_tag_url))) {
                                imageView.setImageDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#111827")));
                            }
                        }
                    });
                }
            }
        });
    }

    // Call this from onViewRecycled to cancel pending loads
    public static void cancelLoad(final ImageView imageView) {
        if (imageView != null) {
            imageView.setTag(com.blue.plus.R.id.glide_tag_url, null);
            imageView.setImageDrawable(null);
        }
    }

    // Custom SocketFactory for TLS 1.2 support
    private static class Tls12SocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate;
        public Tls12SocketFactory(javax.net.ssl.SSLSocketFactory base) { this.delegate = base; }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException { return patch(delegate.createSocket(s, host, port, autoClose)); }
        @Override public java.net.Socket createSocket(String host, int port) throws java.io.IOException { return patch(delegate.createSocket(host, port)); }
        @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException { return patch(delegate.createSocket(host, port, localHost, localPort)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException { return patch(delegate.createSocket(host, port)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException { return patch(delegate.createSocket(address, port, localAddress, localPort)); }
        private java.net.Socket patch(java.net.Socket s) { if (s instanceof javax.net.ssl.SSLSocket) { ((javax.net.ssl.SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2"}); } return s; }
    }

    /**
     * Auto-detects if the current device is an Android TV / Smart TV / TV Box
     */
    public static boolean isAndroidTV(Context context) {
        try {
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true;
            }
            if (context.getPackageManager().hasSystemFeature("android.software.leanback")) {
                return true;
            }
            String model = Build.MODEL.toLowerCase();
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if (model.contains("firetv") || model.contains("fire tv") || model.contains("mibox") 
                || model.contains("tv box") || model.contains("tvbox") || model.contains("leanback")) {
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    /**
     * Checks if TV Mode layout/handling is active (either manual preference or auto-detected)
     */
    public static boolean isTvMode(Context context) {
        SharedPreferences sp = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        if (!sp.contains("device_mode")) {
            boolean detectedTv = isAndroidTV(context);
            sp.edit().putInt("device_mode", detectedTv ? 1 : 0).apply();
            return detectedTv;
        }
        return sp.getInt("device_mode", 0) == 1;
    }

    /**
     * Enforces an active glowing highlight when views gain focus via TV Remote / D-Pad
     */
    public static void applyTvFocusHighlight(final View view) {
        applyTvFocusHighlight(view, 12.0f); // Default 12dp corner radius
    }

    /**
     * Enforces an active glowing highlight when views gain focus via TV Remote / D-Pad with custom corner radius
     */
    public static void applyTvFocusHighlight(final View view, final float cornerRadiusDp) {
        if (view == null) return;
        
        view.setFocusable(true);
        // Important: setFocusableInTouchMode(false) ensures standard touch users don't get 
        // stuck with glowing highlights, and highlights ONLY trigger for TV D-Pad/remote/mouse focus!
        // HOWEVER, for EditText, we MUST set it to true so users can click/select to type!
        if (view instanceof android.widget.EditText || isTvMode(view.getContext())) {
            view.setFocusableInTouchMode(true);
        } else {
            view.setFocusableInTouchMode(false);
        }

        final View.OnFocusChangeListener originalFocusListener = view.getOnFocusChangeListener();

        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (originalFocusListener != null) {
                    originalFocusListener.onFocusChange(v, hasFocus);
                }
                updateViewVisualState(v, hasFocus, v.isHovered(), cornerRadiusDp);
            }
        });

        view.setOnHoverListener(new View.OnHoverListener() {
            @Override
            public boolean onHover(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_HOVER_ENTER:
                        v.setHovered(true);
                        updateViewVisualState(v, v.hasFocus(), true, cornerRadiusDp);
                        break;
                    case android.view.MotionEvent.ACTION_HOVER_EXIT:
                        v.setHovered(false);
                        updateViewVisualState(v, v.hasFocus(), false, cornerRadiusDp);
                        break;
                }
                return false;
            }
        });
    }

    private static void updateViewVisualState(View v, boolean hasFocus, boolean isHovered, float cornerRadiusDp) {
        float scale = v.getContext().getResources().getDisplayMetrics().density;
        boolean shouldHighlight = hasFocus || isHovered;

        if (shouldHighlight) {
            // Focus/Hover transition: scale up slightly to look like official Android TV Leanback
            v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).start();

            // Check if already has the highlight to prevent double-wrapping
            Drawable currentBg = v.getBackground();
            boolean alreadyHighlighted = false;
            if (currentBg instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) currentBg;
                if (ld.getNumberOfLayers() == 2 && ld.getId(1) == 9999) {
                    alreadyHighlighted = true;
                }
            }

            if (!alreadyHighlighted) {
                // Beautiful neon cyan glowing outline - Made thicker and brighter for better visibility
                GradientDrawable focusBorder = new GradientDrawable();
                focusBorder.setColor(Color.parseColor("#3300E5FF")); // Stronger Glow cyan tint background
                focusBorder.setCornerRadius(cornerRadiusDp * scale);
                focusBorder.setStroke((int)(4.0f * scale), Color.parseColor("#00E5FF")); // Thicker Cyber cyan glow border!

                Drawable baseBg = currentBg;
                if (baseBg == null) {
                    baseBg = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
                } else if (baseBg instanceof LayerDrawable) {
                    LayerDrawable ld = (LayerDrawable) baseBg;
                    if (ld.getNumberOfLayers() == 2 && ld.getId(1) == 9999) {
                        baseBg = ld.getDrawable(0);
                    } else if (ld.getNumberOfLayers() > 0) {
                        baseBg = ld.getDrawable(0);
                    }
                }

                Drawable[] layers = new Drawable[]{baseBg, focusBorder};
                LayerDrawable ld = new LayerDrawable(layers);
                ld.setId(1, 9999); // Mark this layer with ID 9999
                v.setBackground(ld);
            }
        } else {
            // Unfocus/Unhover transition: reset scale
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

            // Reset background if it is currently highlighted
            Drawable currentBg = v.getBackground();
            if (currentBg instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) currentBg;
                if (ld.getNumberOfLayers() == 2 && ld.getId(1) == 9999) {
                    v.setBackground(ld.getDrawable(0));
                }
            }
        }
    }

    /**
     * Hides both the status bar and bottom navigation bar globally for a true immersive experience.
     */
    public static void hideSystemUI(final android.app.Activity activity) {
        if (activity == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode = 
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } catch (Exception e) {}
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            final android.view.View decorView = activity.getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            decorView.setOnSystemUiVisibilityChangeListener(new android.view.View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );
                    }
                }
            });
        }
    }

    public static void loadCachedBackground(View view) {
        if (view == null) return;
        try {
            File f = new File(view.getContext().getFilesDir(), "splash_bg.jpg");
            if (f.exists()) {
                if (f.length() < 100) {
                    f.delete();
                    return;
                }
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(view.getResources(), bmp);
                    drawable.setGravity(android.view.Gravity.FILL);
                    view.setBackground(drawable);
                } else {
                    f.delete();
                }
            }
        } catch (Exception e) {}
    }

    /**
     * Loads the cached online logo (splash_logo.png) into an ImageView, falling back to a default resource
     */
    public static void loadCachedLogo(ImageView imageView, int fallbackResId) {
        if (imageView == null) return;
        imageView.setImageResource(fallbackResId);
        try {
            File f = new File(imageView.getContext().getFilesDir(), "splash_logo.png");
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                }
            }
        } catch (Exception e) {}
    }

    /**
     * Centralized AES-256-CBC decryption helper.
     * Decrypts base64-encoded ciphertext with a static 32-byte key.
     */
    public static String decryptAES(String encryptedBase64) {
        if (encryptedBase64 == null) return null;
        String content = encryptedBase64.trim();
        if (content.isEmpty()) return content;
        
        // If content starts with JSON delimiters, it's not encrypted
        if (content.startsWith("{") || content.startsWith("[")) {
            return encryptedBase64;
        }

        try {
            // 32-byte key matching the PHP panel AES_KEY
            String keyStr = "1997219972@@aas1997219972@@aas12"; 
            byte[] data = android.util.Base64.decode(content, android.util.Base64.DEFAULT);
            if (data.length < 16) return encryptedBase64; // not enough data to contain IV
            
            byte[] iv = new byte[16];
            System.arraycopy(data, 0, iv, 0, 16);
            
            byte[] encrypted = new byte[data.length - 16];
            System.arraycopy(data, 16, encrypted, 0, encrypted.length);
            
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyStr.getBytes("UTF-8"), "AES");
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encrypted);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            // Fallback to original string if decryption fails or not encrypted
            return encryptedBase64;
        }
    }

    public static void alignTextByLanguage(android.widget.TextView tv, String text) {
        if (tv == null) return;
        if (hasArabic(text)) {
            tv.setGravity(android.view.Gravity.LEFT);
        } else {
            tv.setGravity(android.view.Gravity.RIGHT);
        }
    }

    public static boolean hasArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) {
                return true;
            }
        }
        return false;
    }

    public static String formatNameByLanguage(String name) {
        if (name == null || name.trim().isEmpty()) return name;
        
        String delimiter = null;
        if (name.contains("|")) {
            delimiter = "|";
        } else if (name.contains(" - ")) {
            delimiter = " - ";
        } else if (name.contains(" / ")) {
            delimiter = " / ";
        }
        
        if (delimiter != null) {
            String regex = java.util.regex.Pattern.quote(delimiter);
            String[] parts = name.split(regex);
            if (parts.length > 1) {
                java.util.List<String> arabicParts = new java.util.ArrayList<>();
                java.util.List<String> englishParts = new java.util.ArrayList<>();
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (hasArabic(trimmed)) {
                        arabicParts.add(trimmed);
                    } else {
                        englishParts.add(trimmed);
                    }
                }
                
                if (!arabicParts.isEmpty() && !englishParts.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arabicParts.size(); i++) {
                        if (i > 0) sb.append(" ").append(delimiter.trim()).append(" ");
                        sb.append(arabicParts.get(i));
                    }
                    for (int i = 0; i < englishParts.size(); i++) {
                        sb.append(" ").append(delimiter.trim()).append(" ");
                        sb.append(englishParts.get(i));
                    }
                    return sb.toString();
                }
            }
        }
        return name;
    }

    public static String getAppLanguage(Context context) {
        if (context == null) return "ar";
        SharedPreferences sp = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        String lang = sp.getString("app_language", "auto");
        if ("auto".equals(lang)) {
            String deviceLang = java.util.Locale.getDefault().getLanguage();
            if (deviceLang != null && deviceLang.toLowerCase().startsWith("ar")) {
                return "ar";
            } else {
                return "en";
            }
        }
        return lang;
    }

    public static String translate(Context context, String key) {
        if (context == null || key == null) return key != null ? key : "";
        String lang = getAppLanguage(context);
        boolean isAr = "ar".equals(lang);
        
        switch (key) {
            case "Settings":
            case "الإعدادات":
                return isAr ? "الإعدادات" : "Settings";
            case "Add Playlist":
            case "أضف قائمة التشغيل":
                return isAr ? "أضف قائمة التشغيل" : "Add Playlist";
            case "Parental Control":
            case "مراقبة اهلية":
            case "مراقبة أبوية":
                return isAr ? "الرقابة الأبوية" : "Parental Control";
            case "Change Playlist":
            case "تغيير قائمة التسجيل":
            case "تغيير قائمة التشغيل":
                return isAr ? "تغيير قائمة التشغيل" : "Change Playlist";
            case "Hide Live Categories":
            case "إخفاء الفئات الحية":
                return isAr ? "إخفاء الفئات المباشرة" : "Hide Live Categories";
            case "Hide VOD Categories":
            case "إخفاء الفئات Vod":
                return isAr ? "إخفاء فئات Vod" : "Hide VOD Categories";
            case "Hide Series Categories":
            case "إخفاء فئات المسلسلات":
                return isAr ? "إخفاء فئات المسلسلات" : "Hide Series Categories";
            case "Clear Channel History":
            case "Clear History Channels":
                return isAr ? "مسح سجل القنوات" : "Clear Channel History";
            case "Clear Movie History":
            case "أفلام التاريخ واضحة":
                return isAr ? "مسح سجل الأفلام" : "Clear Movie History";
            case "Clear Series History":
            case "سلسلة مسح التاريخ":
                return isAr ? "مسح سجل المسلسلات" : "Clear Series History";
            case "Live Stream Format":
                return isAr ? "صيغة البث المباشر" : "Live Stream Format";
            case "Auto Start":
            case "تلقائي":
                return isAr ? "التشغيل التلقائي" : "Auto Start";
            case "Time Format":
            case "تنسيق الوقت":
                return isAr ? "تنسيق الوقت" : "Time Format";
            case "Subtitle Settings":
            case "إعدادات الترجمة":
                return isAr ? "إعدادات الترجمة" : "Subtitle Settings";
            case "Select Device Type":
                return isAr ? "تحديد نوع الجهاز" : "Select Device Type";
            case "Update Now":
            case "تحديث الآن":
                return isAr ? "تحديث الآن" : "Update Now";
            case "Parental Control ON/OFF":
                return isAr ? "تشغيل/إيقاف الرقابة الأبوية" : "Parental Control ON/OFF";
            case "App Language":
            case "لغة التطبيق":
                return isAr ? "لغة التطبيق" : "App Language";
            case "Auto Detect":
            case "تعرف تلقائي":
                return isAr ? "التعرف التلقائي على اللغة" : "Auto-Detect Language";
            case "Arabic":
            case "العربية":
                return isAr ? "العربية" : "Arabic";
            case "English":
            case "الإنجليزية":
                return isAr ? "الإنجليزية" : "English";
            case "Connect":
            case "يتصل":
                return isAr ? "اتصال" : "Connect";
            case "Connect (Live Only)":
            case "اتصال (بث مباشر فقط)":
                return isAr ? "اتصال (بث مباشر فقط)" : "Connect (Live Only)";
            case "Edit":
            case "تعديل":
                return isAr ? "تعديل" : "Edit";
            case "Delete":
            case "حذف":
                return isAr ? "حذف" : "Delete";
            case "Save Settings":
            case "حفظ الإعدادات":
                return isAr ? "حفظ الإعدادات" : "Save Settings";
            case "Playlist":
            case "قائمة تشغيل":
                return isAr ? "قائمة التشغيل" : "Playlist";
            case "Open Site":
            case "افتح الموقع":
                return isAr ? "افتح الموقع" : "Open Website";
            case "Activate MAC":
            case "تفعيل ماك":
                return isAr ? "تفعيل الماك" : "Activate MAC";
            case "Welcome to Blue +":
            case "اهلا وسهلا بك في Blue +":
                return isAr ? "أهلاً بك في Blue +" : "Welcome to Blue +";
            case "MAC Address":
            case "عنوان ماك":
                return isAr ? "عنوان الماك (MAC)" : "MAC Address";
            case "Device Key":
            case "مفتاح الجهاز":
                return isAr ? "مفتاح الجهاز" : "Device Key";
            case "To add/manage playlists, use the following values on website:":
            case "لإضافة / إدارة قوائم التشغيل ، استخدم القيم التالية على موقع الويب:":
                return isAr ? "لإضافة أو إدارة قوائم التشغيل، استخدم هذه القيم في الموقع:" : "To add/manage playlists, use the following values on the website:";
            case "Welcome":
                return isAr ? "مرحباً بك" : "Welcome";
            case "Enter activation code":
            case "أدخل كود التفعيل":
                return isAr ? "أدخل كود التفعيل" : "Enter Activation Code";
            case "LOGIN":
                return isAr ? "دخول" : "LOGIN";
            case "Enter Playlist Name":
                return isAr ? "أدخل اسم قائمة التشغيل" : "Enter Playlist Name";
            case "If you don't choose a name for the playlist, a name will be added automatically":
                return isAr ? "إذا لم تختر اسماً لقائمة التشغيل، فسيتم إضافته تلقائياً" : "If you don't choose a name for the playlist, it will be added automatically";
            case "EXIT":
                return isAr ? "خروج" : "EXIT";
            case "SAVE":
                return isAr ? "حفظ" : "SAVE";
            case "username":
                return isAr ? "اسم المستخدم" : "Username";
            case "password":
                return isAr ? "كلمة المرور" : "Password";
            case "التطبيق محمي":
                return isAr ? "التطبيق محمي" : "Application Protected";

            // Toasts & Dialogs Login / Activation
            case "يرجى إدخال الكود":
                return isAr ? "يرجى إدخال كود التفعيل" : "Please enter activation code";
            case "يرجى إدخال اسم المستخدم وكلمة المرور":
                return isAr ? "يرجى إدخال اسم المستخدم وكلمة المرور" : "Please enter username and password";
            case "سيرفرات الاتصال غير متوفرة. جاري استرجاعها...":
                return isAr ? "سيرفرات الاتصال غير متوفرة. جاري استرجاعها..." : "Connection servers unavailable. Retrieving...";
            case "الحساب غير صحيح أو منتهي الصلاحية على جميع السيرفرات!":
                return isAr ? "الحساب غير صحيح أو منتهي الصلاحية على جميع السيرفرات!" : "Invalid or expired account on all servers!";
            case "تم تعطيل هذا الكود من قبل المسؤول":
                return isAr ? "تم تعطيل هذا الكود من قبل المسؤول" : "This code has been disabled by the administrator";
            case "هذا الكود منتهي الصلاحية!":
                return isAr ? "هذا الكود منتهي الصلاحية!" : "This code has expired!";
            case "الكود غير صحيح":
                return isAr ? "الكود غير صحيح" : "Incorrect code";
            case "حدث خطأ في معالجة البيانات":
                return isAr ? "حدث خطأ في معالجة البيانات" : "An error occurred while processing data";
            case "فشل الاتصال بالسيرفر":
                return isAr ? "فشل الاتصال بالسيرفر" : "Failed to connect to the server";
            case "حدث خطأ في قفل الجهاز":
                return isAr ? "حدث خطأ في قفل الجهاز" : "An error occurred while locking the device";
            case "فشل الاتصال بسيرفر التحقق لحفظ التسجيل":
                return isAr ? "فشل الاتصال بسيرفر التحقق لحفظ التسجيل" : "Failed to connect to verification server to save registration";
            case "تم حفظ قائمة التشغيل بنجاح":
                return isAr ? "تم حفظ قائمة التشغيل بنجاح" : "Playlist saved successfully";

            // WaitingActivity Dialog & Toasts
            case "تحديث جديد متوفر! v":
                return isAr ? "تحديث جديد متوفر! v" : "New Update Available! v";
            case "يرجى تحديث التطبيق للحصول على أفضل تجربة وأحدث الميزات.":
                return isAr ? "يرجى تحديث التطبيق للحصول على أفضل تجربة وأحدث الميزات." : "Please update the app to get the best experience and latest features.";
            case "ما الجديد في هذا الإصدار:":
                return isAr ? "ما الجديد في هذا الإصدار:" : "What's new in this version:";
            case "لاحقاً":
                return isAr ? "لاحقاً" : "Later";
            case "بيانات الدخول غير مكتملة!":
                return isAr ? "بيانات الدخول غير مكتملة!" : "Login credentials incomplete!";
            case "فشل الاتصال: يرجى التحقق من الشبكة أو صلاحية الاشتراك!":
                return isAr ? "فشل الاتصال: يرجى التحقق من الشبكة أو صلاحية الاشتراك!" : "Connection failed: Please check your network or subscription validity!";
            case "فشل فتح رابط التحديث!":
                return isAr ? "فشل فتح رابط التحديث!" : "Failed to open update link!";
            case "بث مباشر":
                return isAr ? "بث مباشر" : "Live TV";
            case "افلام":
                return isAr ? "أفلام" : "Movies";
            case "Sports":
                return isAr ? "رياضة" : "Sports";
            case "مسلسلات":
                return isAr ? "مسلسلات" : "Series";
            case "قائمة التشغيل الحالية تنتهي: ":
                return isAr ? "قائمة التشغيل الحالية تنتهي: " : "Current playlist expires: ";
            case "جاري التحميل...":
                return isAr ? "جاري التحميل..." : "Loading...";
            case "غير محدود ♾️":
                return isAr ? "غير محدود ♾️" : "Unlimited ♾️";
            case "رجوع":
                return isAr ? "رجوع" : "Back";
            case "سيرفر نشط":
                return isAr ? "سيرفر نشط" : "Active Server";
            case "اختر قناة":
                return isAr ? "اختر قناة" : "Choose Channel";
            case "دليل القنوات":
                return isAr ? "دليل القنوات" : "Channels Guide";
            case "التنقل السريع بين قنوات البث المباشر":
                return isAr ? "التنقل السريع بين قنوات البث المباشر" : "Quick navigation between live channels";
            case "جدول مباريات اليوم":
                return isAr ? "جدول مباريات اليوم" : "Today's Matches";
            case "لا توجد مباريات في هذا القسم حالياً!":
                return isAr ? "لا توجد مباريات في هذا القسم حالياً!" : "No matches in this section currently!";
            case "شاهد الآن 🔴":
                return isAr ? "شاهد الآن 🔴" : "Watch Now 🔴";
            case "مباراة جارية ⚽":
                return isAr ? "مباراة جارية ⚽" : "Match Live ⚽";
            case "انتهت 🏁":
                return isAr ? "انتهت 🏁" : "Ended 🏁";
            case "تبدأ قريباً ⏱️":
                return isAr ? "تبدأ قريباً ⏱️" : "Starts Soon ⏱️";
            case "اختر جودة البث والتشغيل المفضلة لديك:":
                return isAr ? "اختر جودة البث والتشغيل المفضلة لديك:" : "Select your preferred stream quality:";
            case "اختر جودة البث المباشر":
                return isAr ? "اختر جودة البث المباشر" : "Select Live Stream Quality";
            case "إلغاء":
                return isAr ? "إلغاء" : "Cancel";
            case "▶ مشاهدة الآن":
                return isAr ? "▶ مشاهدة الآن" : "▶ Watch Now";
            case "▶ مشاهدة الموسم":
                return isAr ? "▶ مشاهدة الموسم" : "▶ Watch Season";
            case "ممثلين: ":
                return isAr ? "ممثلين: " : "Cast: ";
            case "تم إضافة التاريخ: ":
                return isAr ? "تم إضافة التاريخ: " : "Added Date: ";
            case "اضافة الى المفضلة":
                return isAr ? "اضافة الى المفضلة" : "Add to Favorites";
            case "إزالة من المفضلة":
                return isAr ? "إزالة من المفضلة" : "Remove from Favorites";
            case "غير متوفر":
                return isAr ? "غير متوفر" : "N/A";
            case "لا يوجد وصف متوفر لهذا الفيلم حالياً.":
                return isAr ? "لا يوجد وصف متوفر لهذا الفيلم حالياً." : "No description available for this movie currently.";
            case "لا يوجد وصف متوفر لهذا المسلسل حالياً.":
                return isAr ? "لا يوجد وصف متوفر لهذا المسلسل حالياً." : "No description available for this series currently.";
            case "حول التطبيق":
                return isAr ? "حول التطبيق" : "About App";
            case "موافق":
                return isAr ? "موافق" : "OK";
            case "إخفاء الفئات":
                return isAr ? "إخفاء الفئات" : "Hide Categories";
            case "مسح سجل المشاهدة":
                return isAr ? "مسح سجل المشاهدة" : "Clear History";
            case "نعم، مسح السجل":
                return isAr ? "نعم، مسح السجل" : "Yes, Clear History";
            case "نوع البث المباشر":
                return isAr ? "نوع البث المباشر" : "Live Stream Type";
            case "حفظ الاختيار":
                return isAr ? "حفظ الاختيار" : "Save Selection";
            case "تنسيق البث المباشر":
                return isAr ? "تنسيق البث المباشر" : "Live Stream Format";
            case "حفظ وتطبيق":
                return isAr ? "حفظ وتطبيق" : "Save & Apply";
            case "Change Player":
                return isAr ? "تغيير المشغل" : "Change Player";
            case "تطبيق المشغل":
                return isAr ? "تطبيق المشغل" : "Apply Player";
            case "اختر مشغل وسائط خارجي":
                return isAr ? "اختر مشغل وسائط خارجي" : "Select External Media Player";
            case "حفظ":
                return isAr ? "حفظ" : "Save";
            case "وضع التشغيل التلقائي":
                return isAr ? "وضع التشغيل التلقائي" : "Auto Playback Mode";
            case "حفظ الخيار":
                return isAr ? "حفظ الخيار" : "Save Option";
            case "حفظ التنسيق":
                return isAr ? "حفظ التنسيق" : "Save Format";
            case "تأكيد الحفظ":
                return isAr ? "تأكيد الحفظ" : "Confirm Saving";
            case "تأكيد":
                return isAr ? "تأكيد" : "Confirm";
            case "خروج":
                return isAr ? "خروج" : "Exit";
            case "اتصل بنا":
                return isAr ? "اتصل بنا" : "Contact Us";
            case "جاري تحميل الحلقات والمواسم...":
                return isAr ? "جاري تحميل الحلقات والمواسم..." : "Loading episodes and seasons...";
            case "جاري التحقق من الكود...":
                return isAr ? "جاري التحقق من الكود..." : "Verifying code...";
            case "جاري التحقق من وجود تحديثات على السيرفر وتحديث الخلفية والشعار...":
                return isAr ? "جاري التحقق من وجود تحديثات على السيرفر وتحديث الخلفية والشعار..." : "Checking for server updates and updating background/logo...";
            case "تطبيقك محدث بالكامل بنجاح! أنت على أحدث إصدار.":
                return isAr ? "تطبيقك محدث بالكامل بنجاح! أنت على أحدث إصدار." : "Your app is up to date with the latest version!";
            case "زيارة":
                return isAr ? "زيارة" : "Visit";
            case "متابعة المشاهدة":
                return isAr ? "متابعة المشاهدة" : "Resume Watching";
            case "هل تريد إكمال المشاهدة من حيث توقفت عند (":
                return isAr ? "هل تريد إكمال المشاهدة من حيث توقفت عند (" : "Do you want to resume watching from (";
            case ")؟":
                return isAr ? ")؟" : ")?";
            case "البدء من البداية":
                return isAr ? "البدء من البداية" : "Start from Beginning";
            case "الاتصال اللاسلكي":
                return isAr ? "الاتصال اللاسلكي" : "Wireless Connection";
            case "أنت متصل حالياً بالتلفاز. هل تريد قطع الاتصال؟":
                return isAr ? "أنت متصل حالياً بالتلفاز. هل تريد قطع الاتصال؟" : "You are currently connected to the TV. Do you want to disconnect?";
            case "قطع الاتصال وتوقف التشغيل":
                return isAr ? "قطع الاتصال وتوقف التشغيل" : "Disconnect and Stop Playback";
            case "قطع الاتصال والاستئناف هنا":
                return isAr ? "قطع الاتصال والاستئناف هنا" : "Disconnect and Resume Here";
            case "البحث عن أجهزة التلفاز...":
                return isAr ? "البحث عن أجهزة التلفاز..." : "Searching for TV devices...";

            // Live / Player Toasts & Dialogs
            case "الأخبار":
            case "News":
                return isAr ? "الأخبار" : "News";
            case "جاري الاتصال...":
                return isAr ? "جاري الاتصال..." : "Connecting...";
            case "السيرفر غير متصل":
                return isAr ? "السيرفر غير متصل" : "Server Offline";
            case "تشغيل / إيقاف":
                return isAr ? "تشغيل / إيقاف" : "Play / Pause";
            case "بحث":
                return isAr ? "بحث" : "Search";
            case "بحث عن قناة...":
                return isAr ? "بحث عن قناة..." : "Search channels...";
            case "بحث عن فيلم...":
                return isAr ? "بحث عن فيلم..." : "Search movies...";
            case "بحث عن مسلسل...":
                return isAr ? "بحث عن مسلسل..." : "Search series...";
            case "Playback Speed":
            case "سرعة التشغيل":
                return isAr ? "سرعة التشغيل" : "Playback Speed";
            case "تمت الإضافة للمفضلة":
                return isAr ? "تمت الإضافة للمفضلة" : "Added to Favorites";
            case "تمت الإزالة من المفضلة":
                return isAr ? "تمت الإزالة من المفضلة" : "Removed from Favorites";
            case "تم قفل الشاشة والتحكم":
                return isAr ? "تم قفل الشاشة والتحكم" : "Controls Locked";
            case "تم إلغاء القفل":
                return isAr ? "تم إلغاء القفل" : "Controls Unlocked";
            case "غير مدعوم للبث الحالي":
                return isAr ? "غير مدعوم للبث الحالي" : "Not supported for this stream";
            case "لا يوجد اتصال بالإنترنت، بانتظار عودة الشبكة...":
                return isAr ? "لا يوجد اتصال بالإنترنت، بانتظار عودة الشبكة..." : "No internet connection. Waiting for network...";
            case "تقديم 10 ثوانٍ":
                return isAr ? "تقديم 10 ثوانٍ" : "Forward 10 seconds";
            case "البث مباشر بالفعل ولا يمكن تقديمه أكثر":
                return isAr ? "البث مباشر بالفعل ولا يمكن تقديمه أكثر" : "Stream is already live";
            case "إرجاع 10 ثوانٍ":
                return isAr ? "إرجاع 10 ثوانٍ" : "Rewind 10 seconds";
            case "رابط غير صالح":
                return isAr ? "رابط غير صالح" : "Invalid Link";
            case "وضع التناسب (Fit)":
                return isAr ? "وضع التناسب (Fit)" : "Fit Mode";
            case "وضع ملء الشاشة (Fill)":
                return isAr ? "وضع ملء الشاشة (Fill)" : "Fill Mode";
            case "وضع التكبير (Zoom)":
                return isAr ? "وضع التكبير (Zoom)" : "Zoom Mode";
            case "فشل الاتصال بالبث بعد عدة محاولات":
                return isAr ? "فشل الاتصال بالبث بعد عدة محاولات" : "Playback failed after several attempts";
            case "خطأ في تشغيل المشغل الخارجي: ":
                return isAr ? "خطأ في تشغيل المشغل الخارجي: " : "Error launching external player: ";
            case "قائمة التصنيفات غير متوفرة":
                return isAr ? "قائمة التصنيفات غير متوفرة" : "Categories list unavailable";
            case "رابط البث غير صالح":
                return isAr ? "رابط البث غير صالح" : "Invalid stream link";
            case "غير مدعوم للفيديو الحالي":
                return isAr ? "غير مدعوم للفيديو الحالي" : "Not supported for this video";
            case "خطأ في تشغيل المشغل: ":
                return isAr ? "خطأ في تشغيل المشغل: " : "Player error: ";
            case "جاري العرض على التلفاز...":
                return isAr ? "جاري العرض على التلفاز..." : "Casting to TV...";
            case "تم قطع الاتصال وتوقف العرض":
                return isAr ? "تم قطع الاتصال وتوقف العرض" : "Casting disconnected";
            case "تم الاتصال! جاري العرض على ":
                return isAr ? "تم الاتصال! جاري العرض على " : "Connected! Casting to ";
            case "تم قطع الاتصال واستئناف التشغيل محلياً":
                return isAr ? "تم قطع الاتصال واستئناف التشغيل محلياً" : "Disconnected and resumed locally";
            case "لا توجد أفلام أو حلقات لعرضها":
                return isAr ? "لا توجد أفلام أو حلقات لعرضها" : "No movies or episodes to show";
            case "هذا الكود مستخدم بالفعل على جهاز آخر":
                return isAr ? "هذا الكود مستخدم بالفعل على جهاز آخر" : "This code is already used on another device";
            case "جاري التحقق من الحساب...":
                return isAr ? "جاري التحقق من الحساب..." : "Verifying account...";
        }
        return key;
    }

    public static Context updateBaseContextLocale(Context context) {
        if (context == null) return context;
        String lang = getAppLanguage(context);
        java.util.Locale locale = new java.util.Locale(lang);
        java.util.Locale.setDefault(locale);
        
        android.content.res.Resources resources = context.getResources();
        android.content.res.Configuration configuration = resources.getConfiguration();
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
            return context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return context;
        }
    }

    public static void setupDualLanguageTicker(final Context context, final android.widget.TextView badgeTv, final android.widget.TextView tickerTv) {
        if (context == null || tickerTv == null) return;
        
        final android.content.SharedPreferences sp = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String arRaw = sp.getString("ticker_text_ar", "");
        String enRaw = sp.getString("ticker_text_en", "");
        String fallback = sp.getString("ticker_text", "");

        if (arRaw.isEmpty() && !fallback.isEmpty()) arRaw = fallback;
        if (arRaw.isEmpty()) arRaw = "مرحباً بكم في تطبيقنا! نتمنى لكم مشاهدة ممتعة. انضموا لقناتنا على التليجرام لمتابعة آخر الأخبار والعروض.";
        if (enRaw.isEmpty()) enRaw = "Welcome to our application! We wish you enjoyable viewing. Join our Telegram channel for news and offers.";

        boolean[] isArTurn = new boolean[]{ true };
        setupDualLanguageTickerLoop(context, badgeTv, tickerTv, arRaw, enRaw, isArTurn);
    }

    private static void setupDualLanguageTickerLoop(final Context context, final android.widget.TextView badgeTv, final android.widget.TextView tickerTv, final String arText, final String enText, final boolean[] isArTurn) {
        if (context == null || tickerTv == null) return;

        tickerTv.setSingleLine(true);
        tickerTv.setEllipsize(null);
        tickerTv.setHorizontallyScrolling(true);

        final boolean currentAr = isArTurn[0];
        if (currentAr) {
            if (badgeTv != null) badgeTv.setText("  الأخبار  ");
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                tickerTv.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
                tickerTv.setTextDirection(android.view.View.TEXT_DIRECTION_RTL);
            }
            tickerTv.setText(arText);
        } else {
            if (badgeTv != null) badgeTv.setText("  NEWS  ");
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                tickerTv.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
                tickerTv.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
            }
            tickerTv.setText(enText);
        }

        tickerTv.post(new Runnable() {
            @Override
            public void run() {
                try {
                    float textWidth = tickerTv.getPaint().measureText(tickerTv.getText().toString());
                    float parentWidth = 0;
                    if (tickerTv.getParent() instanceof android.view.View) {
                        parentWidth = ((android.view.View) tickerTv.getParent()).getWidth();
                    }
                    if (parentWidth <= 0) {
                        parentWidth = context.getResources().getDisplayMetrics().widthPixels;
                    }

                    float startX, endX;
                    if (currentAr) {
                        // Arabic ▶️ (Left to Right)
                        startX = -textWidth;
                        endX = parentWidth;
                    } else {
                        // English ◀️ (Right to Left)
                        startX = parentWidth;
                        endX = -textWidth;
                    }
                    float totalDist = Math.abs(startX - endX);
                    long animDuration = (long) (totalDist * 12); // Smooth 12ms per pixel scroll

                    tickerTv.setTranslationX(startX);
                    android.animation.ObjectAnimator anim = android.animation.ObjectAnimator.ofFloat(tickerTv, "translationX", startX, endX);
                    anim.setDuration(animDuration);
                    anim.setInterpolator(new android.view.animation.LinearInterpolator());
                    anim.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            isArTurn[0] = !isArTurn[0];
                            tickerTv.post(new Runnable() {
                                @Override
                                public void run() {
                                    setupDualLanguageTickerLoop(context, badgeTv, tickerTv, arText, enText, isArTurn);
                                }
                            });
                        }
                    });
                    anim.start();
                } catch (Exception e) {}
            }
        });
    }
}
