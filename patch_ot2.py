import re

with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java', 'r') as f:
    content = f.read()

# Replace adBitmaps with adUrls
content = re.sub(r'private java\.util\.List<Bitmap> adBitmaps = new java\.util\.ArrayList<\(\)>;', 
                 'private java.util.List<String> adUrls = new java.util.ArrayList<>();', 
                 content)
content = re.sub(r'private java\.util\.List<Bitmap> adBitmaps = new java\.util\.ArrayList<>\(\);', 
                 'private java.util.List<String> adUrls = new java.util.ArrayList<>();', 
                 content)

# Replace loadLocalAds
load_local_ads_new = """    private void loadLocalAds(final ImageView bannerImageView) {
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
    }"""
content = re.sub(r'    private void loadLocalAds\(final ImageView bannerImageView\)\s*\{.*?(?=\n    private void startAdCarousel)', load_local_ads_new + "\n", content, flags=re.DOTALL)

# Replace startAdCarousel
start_carousel_new = """    private void startAdCarousel(final ImageView bannerImageView) {
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
    }"""
content = re.sub(r'    private void startAdCarousel\(final ImageView bannerImageView\)\s*\{.*?(?=\n    private void animateBanner)', start_carousel_new + "\n", content, flags=re.DOTALL)

# Replace animateBanner
animate_banner_new = """    private void animateBanner(final ImageView img, final String nextUrl) {
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
    }"""
content = re.sub(r'    private void animateBanner\(final ImageView img, final Bitmap nextBmp\)\s*\{.*?\}\s*\}\s*\);\s*img\.startAnimation\(fadeOut\);\s*\}', animate_banner_new, content, flags=re.DOTALL)

with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java', 'w') as f:
    f.write(content)
