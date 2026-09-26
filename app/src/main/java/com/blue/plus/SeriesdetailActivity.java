package com.blue.plus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class SeriesdetailActivity extends Activity {

    private FrameLayout rootLayout;
    private ImageView ivPoster, ivBackground;
    private TextView tvTitle, tvYear, tvCast, tvRatingText, tvAddedDate, tvPlot;
    private LinearLayout starsLayout;

    private String seriesId, name, cover, plot, cast, director, genre, releaseDate, rating, category;
    private String type = "series";
    private String containerExtension = "mp4";
    private String dns = "", username = "", password = "";
    
    private static final String BLUE_ACTIVE = "#2196F3";
    private static final String BLUE_TRANS = "#552196F3";
    private static final String FILE_BG = "splash_bg.jpg";

    private static final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(250);

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvUtil.hideSystemUI(this);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Retrieve intent extras
        seriesId = getIntent().getStringExtra("series_id");
        name = getIntent().getStringExtra("name");
        cover = getIntent().getStringExtra("cover");
        plot = getIntent().getStringExtra("plot");
        cast = getIntent().getStringExtra("cast");
        director = getIntent().getStringExtra("director");
        genre = getIntent().getStringExtra("genre");
        releaseDate = getIntent().getStringExtra("release_date");
        if (releaseDate == null || releaseDate.isEmpty()) releaseDate = getIntent().getStringExtra("releaseDate");
        rating = getIntent().getStringExtra("rating");
        category = getIntent().getStringExtra("category");
        type = getIntent().getStringExtra("type");
        if (type == null) type = "series";
        containerExtension = getIntent().getStringExtra("container_extension");
        if (containerExtension == null || containerExtension.trim().isEmpty()) containerExtension = "mp4";

        loadCredentials();
        buildUI();
        loadCachedBackground();
        loadPosterAndBackground();
    }

    private void loadCredentials() {
        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        String activeDns = sp.getString("active_dns", "");
        String activeUser = sp.getString("active_username", "");
        String activePass = sp.getString("active_password", "");

        if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
            String json = sp.getString("list", "[]");
            java.util.ArrayList<java.util.HashMap<String, Object>> list =
                    new com.google.gson.Gson().fromJson(json,
                            new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            if (list != null && !list.isEmpty()) {
                java.util.Map<String, Object> last = list.get(list.size() - 1);
                activeDns = last.get("dns") != null ? (String) last.get("dns") : "";
                activeUser = last.get("username") != null ? (String) last.get("username") : "";
                activePass = last.get("password") != null ? (String) last.get("password") : "";
            }
        }

        dns = activeDns;
        username = activeUser;
        password = activePass;
    }

    private void buildUI() {
        final float scale = getResources().getDisplayMetrics().density;

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        // Background Image for blur effect overlay
        ivBackground = new ImageView(this);
        ivBackground.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        ivBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootLayout.addView(ivBackground);

        // Apple Atmospheric Midnight Canvas Overlay
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#B305070B"));
        rootLayout.addView(overlay);

        // Main Vertical Layout for holding Header and Master Glass Card
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        mainLayout.setPadding((int)(20 * scale), (int)(12 * scale), (int)(20 * scale), (int)(14 * scale));

        // Top Header Bar (Back Button + Capsule Type Pill)
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.bottomMargin = (int)(10 * scale);
        headerBar.setLayoutParams(headerLp);

        // Apple Circular Back Button
        TextView btnBack = new TextView(this);
        btnBack.setText("↩");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(18);
        btnBack.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int)(38 * scale), (int)(38 * scale));
        backLp.rightMargin = (int)(12 * scale);
        btnBack.setLayoutParams(backLp);

        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#E60B101C"));
        backBg.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        btnBack.setBackground(backBg);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TvUtil.applyTvFocusHighlight(btnBack, 19.0f);
        headerBar.addView(btnBack);

        // Apple Type Badge Capsule (Movie / Series)
        LinearLayout typePill = new LinearLayout(this);
        typePill.setOrientation(LinearLayout.HORIZONTAL);
        typePill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable tpBg = new GradientDrawable();
        tpBg.setColor(Color.parseColor("#E60B101C"));
        tpBg.setCornerRadius(999 * scale);
        tpBg.setStroke((int)(1.2f * scale), Color.parseColor("#3380B4FF"));
        typePill.setBackground(tpBg);
        typePill.setPadding((int)(12 * scale), (int)(5 * scale), (int)(12 * scale), (int)(5 * scale));

        TextView tvTypeBadge = new TextView(this);
        tvTypeBadge.setText(type.equals("movies") ? TvUtil.translate(this, "🎬 فيلم سينمائي") : TvUtil.translate(this, "📺 مسلسل تلفزيوني"));
        tvTypeBadge.setTextColor(Color.parseColor("#0A84FF"));
        tvTypeBadge.setTextSize(11);
        tvTypeBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        typePill.addView(tvTypeBadge);
        headerBar.addView(typePill);

        mainLayout.addView(headerBar);

        // Apple Master Frosted Glass Card Container
        LinearLayout glassCard = new LinearLayout(this);
        glassCard.setOrientation(LinearLayout.HORIZONTAL);
        glassCard.setGravity(Gravity.CENTER_VERTICAL);
        glassCard.setPadding((int)(18 * scale), (int)(18 * scale), (int)(18 * scale), (int)(18 * scale));

        GradientDrawable cardBg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.parseColor("#E60B101C"), Color.parseColor("#E60D1526")}
        );
        cardBg.setCornerRadius(22 * scale);
        cardBg.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF"));
        glassCard.setBackground(cardBg);

        // 1. Poster Frame (Left)
        FrameLayout posterFrame = new FrameLayout(this);
        LinearLayout.LayoutParams posterLp = new LinearLayout.LayoutParams((int)(180 * scale), (int)(260 * scale));
        posterLp.rightMargin = (int)(22 * scale);
        posterFrame.setLayoutParams(posterLp);

        ivPoster = new ImageView(this);
        ivPoster.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        ivPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPoster.setBackgroundColor(Color.parseColor("#1AFFFFFF"));

        // Clip Poster with Apple squircle corners
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ivPoster.setClipToOutline(true);
            ivPoster.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16 * scale);
                }
            });
        }
        posterFrame.addView(ivPoster);

        View posterBorder = new View(this);
        GradientDrawable pbBg = new GradientDrawable();
        pbBg.setColor(Color.TRANSPARENT);
        pbBg.setCornerRadius(16 * scale);
        pbBg.setStroke((int)(1.5f * scale), Color.parseColor("#3380B4FF"));
        posterBorder.setBackground(pbBg);
        posterFrame.addView(posterBorder, new FrameLayout.LayoutParams(-1, -1));

        glassCard.addView(posterFrame);

        // 2. Details Column (Right)
        LinearLayout detailsLayout = new LinearLayout(this);
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailsLayout.setLayoutParams(detailsParams);

        // A. Horizontal Action Band (Watch Button & Favorite Pill)
        LinearLayout actionBand = new LinearLayout(this);
        actionBand.setOrientation(LinearLayout.HORIZONTAL);
        actionBand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bandParams = new LinearLayout.LayoutParams(-1, -2);
        bandParams.bottomMargin = (int)(12 * scale);
        actionBand.setLayoutParams(bandParams);

        // Apple Vibrant Glowing Watch Button
        final TextView btnWatch = new TextView(this);
        btnWatch.setText(type.equals("movies") ? TvUtil.translate(this, "▶  مشاهدة الفيلم") : TvUtil.translate(this, "▶  مشاهدة الحلقات"));
        btnWatch.setTextColor(Color.WHITE);
        btnWatch.setTextSize(13.5f);
        btnWatch.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnWatch.setGravity(Gravity.CENTER);
        btnWatch.setPadding((int)(22 * scale), (int)(9 * scale), (int)(22 * scale), (int)(9 * scale));

        GradientDrawable watchBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor("#0A84FF"), Color.parseColor("#0071E3")}
        );
        watchBg.setCornerRadius(18 * scale);
        btnWatch.setBackground(watchBg);

        LinearLayout.LayoutParams watchLp = new LinearLayout.LayoutParams(-2, -2);
        watchLp.rightMargin = (int)(12 * scale);
        btnWatch.setLayoutParams(watchLp);
        btnWatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (type.equals("movies")) {
                    // Save to recently watched history
                    try {
                        android.content.SharedPreferences spHistory = getSharedPreferences("SeriesHistory", MODE_PRIVATE);
                        String currentHistory = spHistory.getString("recent_movies", "");
                        java.util.ArrayList<String> list = new java.util.ArrayList<>();
                        list.add(seriesId);
                        if (!currentHistory.isEmpty()) {
                            for (String part : currentHistory.split(",")) {
                                String clean = part.trim();
                                if (!clean.isEmpty() && !clean.equals(seriesId)) {
                                    list.add(clean);
                                }
                            }
                        }
                        if (list.size() > 50) {
                            list = new java.util.ArrayList<>(list.subList(0, 50));
                        }
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < list.size(); i++) {
                            sb.append(list.get(i));
                            if (i < list.size() - 1) sb.append(",");
                        }
                        spHistory.edit().putString("recent_movies", sb.toString()).apply();
                    } catch (Exception e) {}

                    SeriesepisodesActivity.cachedEpisodes = null;
                    String streamUrl = dns + "/movie/" + username + "/" + password + "/" + seriesId + "." + containerExtension;
                    Intent intent = new Intent(SeriesdetailActivity.this, PlayerActivity.class);
                    intent.putExtra("url", streamUrl);
                    intent.putExtra("title", name);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(SeriesdetailActivity.this, SeriesepisodesActivity.class);
                    intent.putExtra("series_id", seriesId);
                    intent.putExtra("name", name);
                    intent.putExtra("cover", cover);
                    startActivity(intent);
                }
            }
        });
        TvUtil.applyTvFocusHighlight(btnWatch, 18.0f);
        actionBand.addView(btnWatch);

        // Apple Frosted Favorite Capsule Button
        final TextView btnFav = new TextView(this);
        final android.content.SharedPreferences sp = getSharedPreferences("SeriesFavs", MODE_PRIVATE);
        final boolean isFavInitially = sp.getBoolean("fav_" + seriesId, false);

        btnFav.setText(TvUtil.translate(this, isFavInitially ? "♥ إزالة من المفضلة" : "♡ إضافة للمفضلة"));
        btnFav.setTextColor(Color.parseColor("#EBEBF5"));
        btnFav.setTextSize(13);
        btnFav.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnFav.setGravity(Gravity.CENTER);
        btnFav.setPadding((int)(18 * scale), (int)(9 * scale), (int)(18 * scale), (int)(9 * scale));

        final GradientDrawable favBg = new GradientDrawable();
        favBg.setColor(Color.parseColor("#E60B101C"));
        favBg.setCornerRadius(18 * scale);
        favBg.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        btnFav.setBackground(favBg);

        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(-2, -2);
        btnFav.setLayoutParams(favLp);
        btnFav.setOnClickListener(new View.OnClickListener() {
            private boolean isFav = isFavInitially;
            @Override
            public void onClick(View v) {
                isFav = !isFav;
                btnFav.setText(TvUtil.translate(SeriesdetailActivity.this, isFav ? "♥ إزالة من المفضلة" : "♡ إضافة للمفضلة"));
                sp.edit().putBoolean("fav_" + seriesId, isFav).apply();
                Toast.makeText(SeriesdetailActivity.this, isFav ? "تمت الإضافة للمفضلة" : "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show();
            }
        });
        TvUtil.applyTvFocusHighlight(btnFav, 18.0f);
        actionBand.addView(btnFav);

        detailsLayout.addView(actionBand);

        // B. Title (SF Pro Bold)
        tvTitle = new TextView(this);
        tvTitle.setText(name);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvTitle.setPadding(0, 0, 0, (int)(4 * scale));
        detailsLayout.addView(tvTitle);

        // C. Badges Pill Row (Year, Rating, Category)
        LinearLayout badgesRow = new LinearLayout(this);
        badgesRow.setOrientation(LinearLayout.HORIZONTAL);
        badgesRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams badgesParams = new LinearLayout.LayoutParams(-1, -2);
        badgesParams.bottomMargin = (int)(10 * scale);
        badgesRow.setLayoutParams(badgesParams);

        // Year Pill
        String yearText = (releaseDate != null && releaseDate.length() >= 4) ? releaseDate.substring(0, 4) : "2026";
        TextView tvYearPill = new TextView(this);
        tvYearPill.setText(yearText);
        tvYearPill.setTextColor(Color.parseColor("#EBEBF5"));
        tvYearPill.setTextSize(11);
        tvYearPill.setPadding((int)(10 * scale), (int)(3 * scale), (int)(10 * scale), (int)(3 * scale));
        GradientDrawable yrBg = new GradientDrawable();
        yrBg.setColor(Color.parseColor("#1FFFFFFF"));
        yrBg.setCornerRadius(999 * scale);
        tvYearPill.setBackground(yrBg);
        LinearLayout.LayoutParams yrLp = new LinearLayout.LayoutParams(-2, -2);
        yrLp.rightMargin = (int)(8 * scale);
        tvYearPill.setLayoutParams(yrLp);
        badgesRow.addView(tvYearPill);

        // Rating Pill
        float ratingVal = 0f;
        try {
            if (rating != null && !rating.isEmpty()) ratingVal = Float.parseFloat(rating);
        } catch (Exception e) {}
        TextView tvRatePill = new TextView(this);
        tvRatePill.setText("★ " + (ratingVal > 0 ? String.format(java.util.Locale.US, "%.1f", ratingVal) : "8.2"));
        tvRatePill.setTextColor(Color.parseColor("#FF9F0A")); // iOS Amber
        tvRatePill.setTextSize(11);
        tvRatePill.setTypeface(null, Typeface.BOLD);
        tvRatePill.setPadding((int)(10 * scale), (int)(3 * scale), (int)(10 * scale), (int)(3 * scale));
        GradientDrawable rtBg = new GradientDrawable();
        rtBg.setColor(Color.parseColor("#26FF9F0A"));
        rtBg.setCornerRadius(999 * scale);
        rtBg.setStroke((int)(1.0f * scale), Color.parseColor("#4DFF9F0A"));
        tvRatePill.setBackground(rtBg);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(-2, -2);
        rtLp.rightMargin = (int)(8 * scale);
        tvRatePill.setLayoutParams(rtLp);
        badgesRow.addView(tvRatePill);

        // Category Pill
        if (category != null && !category.isEmpty()) {
            TextView tvCatPill = new TextView(this);
            tvCatPill.setText(category);
            tvCatPill.setTextColor(Color.parseColor("#40C8E0"));
            tvCatPill.setTextSize(11);
            tvCatPill.setPadding((int)(10 * scale), (int)(3 * scale), (int)(10 * scale), (int)(3 * scale));
            GradientDrawable catBg = new GradientDrawable();
            catBg.setColor(Color.parseColor("#2640C8E0"));
            catBg.setCornerRadius(999 * scale);
            catBg.setStroke((int)(1.0f * scale), Color.parseColor("#4D40C8E0"));
            tvCatPill.setBackground(catBg);
            badgesRow.addView(tvCatPill);
        }
        detailsLayout.addView(badgesRow);

        // D. Cast (طاقم العمل)
        tvCast = new TextView(this);
        String castText = (cast != null && !cast.isEmpty()) ? cast : TvUtil.translate(this, "غير متوفر");
        tvCast.setText(TvUtil.translate(this, "الممثلين: ") + castText);
        tvCast.setTextColor(Color.parseColor("#B0BEC5"));
        tvCast.setTextSize(11.5f);
        tvCast.setPadding(0, 0, 0, (int)(6 * scale));
        detailsLayout.addView(tvCast);

        // E. Added Date (تاريخ الإضافة)
        tvAddedDate = new TextView(this);
        String addedText = (releaseDate != null && !releaseDate.isEmpty()) ? releaseDate : "٢٢/٠٥/٢٠٢٦";
        tvAddedDate.setText(TvUtil.translate(this, "تاريخ الإصدار: ") + addedText);
        tvAddedDate.setTextColor(Color.parseColor("#66FFFFFF"));
        tvAddedDate.setTextSize(11);
        tvAddedDate.setPadding(0, 0, 0, (int)(8 * scale));
        detailsLayout.addView(tvAddedDate);

        // F. Plot / Description Box
        LinearLayout plotBox = new LinearLayout(this);
        plotBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable plotBoxBg = new GradientDrawable();
        plotBoxBg.setColor(Color.parseColor("#14000000"));
        plotBoxBg.setCornerRadius(12 * scale);
        plotBoxBg.setStroke((int)(1.0f * scale), Color.parseColor("#1AFFFFFF"));
        plotBox.setBackground(plotBoxBg);
        plotBox.setPadding((int)(12 * scale), (int)(10 * scale), (int)(12 * scale), (int)(10 * scale));

        tvPlot = new TextView(this);
        String plotText = (plot != null && !plot.isEmpty()) ? plot : (type.equals("movies") ? TvUtil.translate(this, "لا يوجد وصف متوفر لهذا الفيلم حالياً.") : TvUtil.translate(this, "لا يوجد وصف متوفر لهذا المسلسل حالياً."));
        tvPlot.setText(plotText);
        tvPlot.setTextColor(Color.parseColor("#D1D1D6"));
        tvPlot.setTextSize(12);
        tvPlot.setLineSpacing(2.5f * scale, 1.15f);
        plotBox.addView(tvPlot);
        detailsLayout.addView(plotBox);

        glassCard.addView(detailsLayout);

        // Wrap glassCard in a ScrollView to support all landscape screens
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollView.setLayoutParams(svLp);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(glassCard);

        mainLayout.addView(scrollView);
        rootLayout.addView(mainLayout);
        setContentView(rootLayout);

        btnWatch.post(new Runnable() {
            @Override
            public void run() {
                btnWatch.requestFocus();
            }
        });
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
                    ivBackground.setImageBitmap(bmp);
                } else {
                    f.delete();
                }
            }
        } catch (Exception e) {}
    }

    private void loadPosterAndBackground() {
        if (cover != null && !cover.isEmpty()) {
            Bitmap cached = imageCache.get(cover);
            if (cached != null) {
                ivPoster.setImageBitmap(cached);
                ivBackground.setImageBitmap(cached);
            } else {
                new DownloadCoverTask(ivPoster, ivBackground, cover).execute(cover);
            }
        }
    }

    private static class DownloadCoverTask extends AsyncTask<String, Void, Bitmap> {
        private final java.lang.ref.WeakReference<ImageView> posterRef;
        private final java.lang.ref.WeakReference<ImageView> backgroundRef;
        private final String imageUrl;

        public DownloadCoverTask(ImageView poster, ImageView background, String url) {
            posterRef = new java.lang.ref.WeakReference<>(poster);
            backgroundRef = new java.lang.ref.WeakReference<>(background);
            this.imageUrl = url;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();
                Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                if (bmp != null) {
                    imageCache.put(imageUrl, bmp);
                }
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                ImageView poster = posterRef.get();
                if (poster != null) poster.setImageBitmap(result);

                ImageView background = backgroundRef.get();
                if (background != null) background.setImageBitmap(result);
            }
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
