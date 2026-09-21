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

        // Dark Semi-Transparent overlay
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#E60A0A0A")); // Dark background overlay
        rootLayout.addView(overlay);

        // Main Vertical Layout for holding Back Button and Content
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        mainLayout.setPadding((int)(20 * scale), (int)(15 * scale), (int)(20 * scale), (int)(20 * scale));

        // Back Button
        TextView btnBack = new TextView(this);
        btnBack.setText("↩");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(26);
        btnBack.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int)(40 * scale), (int)(40 * scale));
        backLp.bottomMargin = (int)(10 * scale);
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TvUtil.applyTvFocusHighlight(btnBack);
        mainLayout.addView(btnBack);

        // Body Horizontal Layout (Poster Left, Details Right)
        LinearLayout bodyLayout = new LinearLayout(this);
        bodyLayout.setOrientation(LinearLayout.HORIZONTAL);
        bodyLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyLayout.setLayoutParams(bodyParams);

        // 1. Poster Image (Left)
        ivPoster = new ImageView(this);
        LinearLayout.LayoutParams posterLp = new LinearLayout.LayoutParams((int)(200 * scale), (int)(280 * scale));
        posterLp.rightMargin = (int)(25 * scale);
        ivPoster.setLayoutParams(posterLp);
        ivPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPoster.setBackgroundColor(Color.parseColor("#22FFFFFF"));

        // Clip Poster with rounded corners
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ivPoster.setClipToOutline(true);
            ivPoster.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * scale);
                }
            });
        }
        bodyLayout.addView(ivPoster);

        // 2. Details Column (Right)
        LinearLayout detailsLayout = new LinearLayout(this);
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailsLayout.setLayoutParams(detailsParams);

        // A. Horizontal Band (Watch Button & Favorite Icon)
        LinearLayout actionBand = new LinearLayout(this);
        actionBand.setOrientation(LinearLayout.HORIZONTAL);
        actionBand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bandParams = new LinearLayout.LayoutParams(-1, -2);
        bandParams.bottomMargin = (int)(15 * scale);
        actionBand.setLayoutParams(bandParams);

        // Watch Season / Play Movie Button
        final TextView btnWatch = new TextView(this);
        btnWatch.setText(type.equals("movies") ? TvUtil.translate(this, "▶ مشاهدة الآن") : TvUtil.translate(this, "▶ مشاهدة الموسم"));
        btnWatch.setTextColor(Color.WHITE);
        btnWatch.setTextSize(14);
        btnWatch.setTypeface(null, Typeface.BOLD);
        btnWatch.setGravity(Gravity.CENTER);
        btnWatch.setPadding((int)(20 * scale), (int)(10 * scale), (int)(20 * scale), (int)(10 * scale));

        GradientDrawable watchBg = new GradientDrawable();
        watchBg.setColor(Color.parseColor(BLUE_TRANS));
        watchBg.setCornerRadius(8 * scale);
        btnWatch.setBackground(watchBg);

        LinearLayout.LayoutParams watchLp = new LinearLayout.LayoutParams(-2, -2);
        watchLp.rightMargin = (int)(15 * scale);
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
        TvUtil.applyTvFocusHighlight(btnWatch);
        actionBand.addView(btnWatch);

        // Favorite Button
        final TextView btnFav = new TextView(this);
        final android.content.SharedPreferences sp = getSharedPreferences("SeriesFavs", MODE_PRIVATE);
        final boolean isFavInitially = sp.getBoolean("fav_" + seriesId, false);

        btnFav.setText(TvUtil.translate(this, isFavInitially ? "إزالة من المفضلة" : "اضافة الى المفضلة"));
        btnFav.setTextColor(Color.WHITE);
        btnFav.setTextSize(14);
        btnFav.setTypeface(null, Typeface.BOLD);
        btnFav.setGravity(Gravity.CENTER);
        btnFav.setPadding((int)(20 * scale), (int)(10 * scale), (int)(20 * scale), (int)(10 * scale));

        final GradientDrawable favBg = new GradientDrawable();
        favBg.setColor(Color.parseColor(BLUE_TRANS));
        favBg.setCornerRadius(8 * scale);
        btnFav.setBackground(favBg);

        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(-2, -2);
        btnFav.setLayoutParams(favLp);
        btnFav.setOnClickListener(new View.OnClickListener() {
            private boolean isFav = isFavInitially;
            @Override
            public void onClick(View v) {
                isFav = !isFav;
                btnFav.setText(TvUtil.translate(SeriesdetailActivity.this, isFav ? "إزالة من المفضلة" : "اضافة الى المفضلة"));
                sp.edit().putBoolean("fav_" + seriesId, isFav).apply();
                Toast.makeText(SeriesdetailActivity.this, isFav ? "تمت الإضافة للمفضلة" : "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show();
            }
        });
        TvUtil.applyTvFocusHighlight(btnFav);
        actionBand.addView(btnFav);

        detailsLayout.addView(actionBand);

        // B. Title
        tvTitle = new TextView(this);
        tvTitle.setText(name);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, (int)(4 * scale));
        detailsLayout.addView(tvTitle);

        // C. Year
        tvYear = new TextView(this);
        String yearText = (releaseDate != null && releaseDate.length() >= 4) ? releaseDate.substring(0, 4) : "2026";
        tvYear.setText(yearText);
        tvYear.setTextColor(Color.parseColor("#88FFFFFF"));
        tvYear.setTextSize(12);
        tvYear.setPadding(0, 0, 0, (int)(10 * scale));
        detailsLayout.addView(tvYear);

        // D. Cast (طاقم العمل)
        tvCast = new TextView(this);
        String castText = (cast != null && !cast.isEmpty()) ? cast : TvUtil.translate(this, "غير متوفر");
        tvCast.setText(TvUtil.translate(this, "ممثلين: ") + castText);
        tvCast.setTextColor(Color.parseColor("#BBFFFFFF"));
        tvCast.setTextSize(12);
        tvCast.setPadding(0, 0, 0, (int)(10 * scale));
        detailsLayout.addView(tvCast);

        // E. Ratings Row
        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        ratingRow.setPadding(0, 0, 0, (int)(10 * scale));

        starsLayout = new LinearLayout(this);
        starsLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        float ratingVal = 0f;
        try {
            if (rating != null && !rating.isEmpty()) ratingVal = Float.parseFloat(rating);
        } catch (Exception e) {}
        
        int yellowStars = Math.round(ratingVal / 2f); // Scale 10 stars to 5
        for (int i = 0; i < 5; i++) {
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(14);
            star.setTextColor(i < yellowStars ? Color.parseColor("#FFC107") : Color.GRAY);
            starsLayout.addView(star);
        }
        ratingRow.addView(starsLayout);

        tvRatingText = new TextView(this);
        tvRatingText.setText("  " + (ratingVal > 0 ? ratingVal : "0.0"));
        tvRatingText.setTextColor(Color.WHITE);
        tvRatingText.setTextSize(12);
        ratingRow.addView(tvRatingText);

        detailsLayout.addView(ratingRow);

        // F. Added Date (تاريخ الإضافة)
        tvAddedDate = new TextView(this);
        String addedText = (releaseDate != null && !releaseDate.isEmpty()) ? releaseDate : "٢٢/٠٥/٢٠٢٦";
        tvAddedDate.setText(TvUtil.translate(this, "تم إضافة التاريخ: ") + addedText);
        tvAddedDate.setTextColor(Color.parseColor("#88FFFFFF"));
        tvAddedDate.setTextSize(11);
        tvAddedDate.setPadding(0, 0, 0, (int)(12 * scale));
        detailsLayout.addView(tvAddedDate);

        // G. Plot/Description (القصة)
        tvPlot = new TextView(this);
        String plotText = (plot != null && !plot.isEmpty()) ? plot : (type.equals("movies") ? TvUtil.translate(this, "لا يوجد وصف متوفر لهذا الفيلم حالياً.") : TvUtil.translate(this, "لا يوجد وصف متوفر لهذا المسلسل حالياً."));
        tvPlot.setText(plotText);
        tvPlot.setTextColor(Color.parseColor("#DDFFFFFF"));
        tvPlot.setTextSize(12);
        tvPlot.setLineSpacing(3 * scale, 1.1f);
        detailsLayout.addView(tvPlot);

        bodyLayout.addView(detailsLayout);
        // Wrap bodyLayout in a ScrollView to allow scrolling details on small screens/landscape
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollView.setLayoutParams(svLp);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(bodyLayout);

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
