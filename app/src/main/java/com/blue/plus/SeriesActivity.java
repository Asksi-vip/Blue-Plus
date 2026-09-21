package com.blue.plus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SeriesActivity extends Activity {

    private FrameLayout rootLayout;
    private RecyclerView rvCategories, rvSeries;
    private CatAdapter catAdapter;
    private SeriesAdapter seriesAdapter;
    private ProgressBar loadingBar;

    private List<CategoryItem> categories = new ArrayList<>();
    private List<SeriesItem> allSeries = new ArrayList<>();
    private List<SeriesItem> filteredSeries = new ArrayList<>();
    private List<SeriesItem> displayedSeries = new ArrayList<>();

    private int itemsPerPage = 100;
    private void loadNextPage() {
        if (displayedSeries.size() >= filteredSeries.size()) return;
        int start = displayedSeries.size();
        int end = Math.min(start + itemsPerPage, filteredSeries.size());
        for (int i = start; i < end; i++) {
            displayedSeries.add(filteredSeries.get(i));
        }
        seriesAdapter.notifyItemRangeInserted(start, end - start);
    }

    private String selectedCategory = "ALL";
    private Map<String, String> categoryIdToName = new java.util.LinkedHashMap<>();
    private Map<String, Integer> categoryCounts = new java.util.LinkedHashMap<>();

    private String type = "series";
    private String dns = "", username = "", password = "";
    private String currentLang;

    private static final String CAT_ALL = "ALL";
    private static final String CAT_FAV = "المفضلات";
    private static final String CAT_RECENT = "شوهدت مؤخراً";
    private static final String BLUE_ACTIVE = "#2196F3";
    private static final String BLUE_TRANS = "#332196F3";
    private static final String STROKE_BLUE = "#552196F3";
    private static final String FILE_BG = "splash_bg.jpg";

    private static final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(250);

    public static List<SeriesItem> cachedMovies = null;
    public static List<SeriesItem> cachedSeries = null;
    public static Map<String, Integer> cachedMoviesCounts = null;
    public static Map<String, Integer> cachedSeriesCounts = null;
    public static Map<String, String> cachedMoviesIdToName = null;
    public static Map<String, String> cachedSeriesIdToName = null;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = TvUtil.getAppLanguage(this);
        TvUtil.enableTls12(this); // Fix SSL for older devices
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        type = getIntent().getStringExtra("type");
        if (type == null) type = "series";
        selectedCategory = getSharedPreferences("SeriesPrefs", MODE_PRIVATE).getString("last_selected_category_" + type, "ALL");

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);
        rootLayout.setFocusable(true);
        rootLayout.setFocusableInTouchMode(true);
        rootLayout.requestFocus();

        buildUI(); // Call immediately to draw the UI layout

        loadData(); // Load local split files instantly on the main thread
        updateCategories();
        refreshSeries();
        loadCachedBackground();
        focusSelectedCategory();
    }

    private void focusSelectedCategory() {
        if (rvCategories == null || categories.isEmpty()) return;
        
        int selectedIndex = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name.equals(selectedCategory)) {
                selectedIndex = i;
                break;
            }
        }
        
        final int targetIdx = selectedIndex;
        rvCategories.post(new Runnable() {
            @Override
            public void run() {
                if (rvCategories != null && rvCategories.getLayoutManager() != null) {
                    rvCategories.getLayoutManager().scrollToPosition(targetIdx);
                    rvCategories.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvCategories.findViewHolderForAdapterPosition(targetIdx);
                            if (holder != null && holder.itemView instanceof ViewGroup) {
                                View row = ((ViewGroup) holder.itemView).getChildAt(0);
                                if (row != null) {
                                    row.requestFocus();
                                }
                            } else {
                                // Fallback
                                if (rvCategories.getChildCount() > targetIdx) {
                                    View child = rvCategories.getChildAt(targetIdx);
                                    if (child instanceof ViewGroup) {
                                        View row = ((ViewGroup) child).getChildAt(0);
                                        if (row != null) row.requestFocus();
                                    }
                                } else if (rvCategories.getChildCount() > 0) {
                                    View child = rvCategories.getChildAt(0);
                                    if (child instanceof ViewGroup) {
                                        View row = ((ViewGroup) child).getChildAt(0);
                                        if (row != null) row.requestFocus();
                                    }
                                }
                            }
                        }
                    }, 150);
                }
            }
        });
    }

    private void styleTab(TextView tv, boolean active) {
        tv.setTextColor(active ? Color.parseColor(BLUE_ACTIVE) : Color.WHITE);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void loadData() {
        if (type.equals("movies") && cachedMovies != null) {
            allSeries = cachedMovies;
            categoryCounts = cachedMoviesCounts;
            categoryIdToName = cachedMoviesIdToName;
            setupInitialData();
            return;
        }
        if (type.equals("series") && cachedSeries != null) {
            allSeries = cachedSeries;
            categoryCounts = cachedSeriesCounts;
            categoryIdToName = cachedSeriesIdToName;
            setupInitialData();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(getExternalFilesDir(null), type.equals("movies") ? "xtream_vod.json" : "xtream_series.json");
                    if (!f.exists()) {
                        return;
                    }

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

                    String dnsStr = activeDns, userStr = activeUser, passStr = activePass;

                    final String dnsFinal = dnsStr;
                    final String userFinal = userStr;
                    final String passFinal = passStr;
                    final Map<String, Integer> tempCategoryCounts = new java.util.LinkedHashMap<>();
                    final Map<String, String> tempCategoryIdToName = new java.util.LinkedHashMap<>();
                    final List<SeriesItem> tempAllSeries = new ArrayList<>();
                    final boolean[] firstBatchPosted = {false};
                    final int PREVIEW = 60;

                    final String targetCatKey = type.equals("movies") ? "get_vod_categories" : "get_series_categories";
                    final String targetItemKey = type.equals("movies") ? "get_vod_streams" : "get_series";

                    try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String name = reader.nextName();
                            if (name.equals("data")) {
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    String key = reader.nextName();
                                    if (key.equals(targetCatKey)) {
                                        try {
                                            parseCategoriesToMap(reader, tempCategoryIdToName);
                                            for (String catName : tempCategoryIdToName.values()) {
                                                if (catName != null) {
                                                    tempCategoryCounts.put(catName, 0);
                                                }
                                            }
                                        } catch (Exception catEx) {
                                            Log.e("SeriesActivity", "Error parsing categories: " + catEx.getMessage());
                                            try { reader.skipValue(); } catch (Exception ignored) {}
                                        }
                                    } else if (key.equals(targetItemKey)) {
                                        try {
                                            // Streaming parse with instant first-batch UI update
                                            if (type.equals("movies")) {
                                                parseMoviesToListStreaming(reader, tempCategoryCounts, tempCategoryIdToName, tempAllSeries, firstBatchPosted, PREVIEW, dnsFinal, userFinal, passFinal);
                                            } else {
                                                parseSeriesToListStreaming(reader, tempCategoryCounts, tempCategoryIdToName, tempAllSeries, firstBatchPosted, PREVIEW);
                                            }
                                        } catch (Exception itemEx) {
                                            Log.e("SeriesActivity", "Error parsing movie/series list: " + itemEx.getMessage());
                                            try { reader.skipValue(); } catch (Exception ignored) {}
                                        }
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
                    } catch (Exception parseEx) {
                        Log.e("SeriesActivity", "JSON Parsing warning/interruption: " + parseEx.getMessage());
                    }

                    // Final full update
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dns = dnsFinal;
                            username = userFinal;
                            password = passFinal;
                            allSeries = tempAllSeries;
                            categoryCounts = tempCategoryCounts;
                            categoryIdToName = tempCategoryIdToName;

                            if (type.equals("movies")) {
                                cachedMovies = allSeries;
                                cachedMoviesCounts = categoryCounts;
                                cachedMoviesIdToName = categoryIdToName;
                            } else {
                                cachedSeries = allSeries;
                                cachedSeriesCounts = categoryCounts;
                                cachedSeriesIdToName = categoryIdToName;
                            }

                            if (!firstBatchPosted[0]) {
                                setupInitialData();
                            } else {
                                // Safe background update: refresh filtered list based on current selection
                                updateCategories();
                                filteredSeries.clear();
                                if (selectedCategory.equals(CAT_ALL)) {
                                    filteredSeries.addAll(allSeries);
                                } else {
                                    for (SeriesItem item : allSeries) {
                                        if (item.category.equals(selectedCategory)) {
                                            filteredSeries.add(item);
                                        }
                                    }
                                }
                                displayedSeries.clear();
                                displayedSeries.addAll(filteredSeries);
                                if (seriesAdapter != null) {
                                    seriesAdapter.notifyDataSetChanged();
                                }
                                if (catAdapter != null) catAdapter.notifyDataSetChanged();
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e("SeriesActivity", "Background load error: " + e.getMessage());
                }
            }
        }).start();
    }

    // Streaming parse for movies with instant first-batch UI post
    private void parseMoviesToListStreaming(JsonReader reader, Map<String, Integer> categoryCounts, Map<String, String> catIdMap,
            List<SeriesItem> list, boolean[] firstBatchPosted, int previewCount,
            String dns, String user, String pass) throws java.io.IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            try {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    continue;
                }
                reader.beginObject();
                int num = 0;
                String name = "", cover = "", streamId = "", catId = "", ext = "mp4", rating = "", plot = "";
                while (reader.hasNext()) {
                    String k = reader.nextName();
                    if (k.equals("num")) num = getNextInt(reader);
                    else if (k.equals("name")) name = getNextString(reader);
                    else if (k.equals("stream_icon")) cover = getNextString(reader);
                    else if (k.equals("category_id")) catId = getNextString(reader);
                    else if (k.equals("stream_id")) streamId = getNextString(reader);
                    else if (k.equals("container_extension")) ext = getNextString(reader);
                    else if (k.equals("rating")) rating = getNextString(reader);
                    else if (k.equals("plot")) plot = getNextString(reader);
                    else reader.skipValue();
                }
                reader.endObject();

                String category = catIdMap.get(catId);
                if (category == null || category.isEmpty()) category = "General";
                Integer count = categoryCounts.get(category);
                categoryCounts.put(category, (count == null ? 0 : count) + 1);

                SeriesItem movie = new SeriesItem(num, name, cover, plot, "", "", "", "", rating, category, streamId);
                movie.containerExtension = ext;
                list.add(movie);

                if (!firstBatchPosted[0] && list.size() >= previewCount) {
                    firstBatchPosted[0] = true;
                    final List<SeriesItem> firstBatch = new ArrayList<>(list);
                    final Map<String, String> firstCatMap = new java.util.HashMap<>(catIdMap);
                    final Map<String, Integer> firstCounts = new java.util.LinkedHashMap<>(categoryCounts);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            allSeries = firstBatch;
                            categoryIdToName = firstCatMap;
                            SeriesActivity.this.categoryCounts = firstCounts;
                            setupInitialData();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("SeriesActivity", "Error parsing movie item: " + e.getMessage());
                try { reader.skipValue(); } catch (Exception ignored) {}
            }
        }
        reader.endArray();
    }

    // Streaming parse for series with instant first-batch UI post
    private void parseSeriesToListStreaming(JsonReader reader, Map<String, Integer> categoryCounts, Map<String, String> catIdMap,
            List<SeriesItem> list, boolean[] firstBatchPosted, int previewCount) throws java.io.IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            try {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    continue;
                }
                reader.beginObject();
                int num = 0;
                String name = "", cover = "", plot = "", cast = "", director = "", genre = "", releaseDate = "", rating = "", catId = "", seriesId = "";
                while (reader.hasNext()) {
                    String k = reader.nextName();
                    if (k.equals("num")) num = getNextInt(reader);
                    else if (k.equals("name")) name = getNextString(reader);
                    else if (k.equals("cover")) cover = getNextString(reader);
                    else if (k.equals("plot")) plot = getNextString(reader);
                    else if (k.equals("cast")) cast = getNextString(reader);
                    else if (k.equals("director")) director = getNextString(reader);
                    else if (k.equals("genre")) genre = getNextString(reader);
                    else if (k.equals("releaseDate")) releaseDate = getNextString(reader);
                    else if (k.equals("rating")) rating = getNextString(reader);
                    else if (k.equals("category_id")) catId = getNextString(reader);
                    else if (k.equals("series_id")) seriesId = getNextString(reader);
                    else reader.skipValue();
                }
                reader.endObject();

                String category = catIdMap.get(catId);
                if (category == null || category.isEmpty()) category = "General";
                Integer cc = categoryCounts.get(category);
                categoryCounts.put(category, (cc == null ? 0 : cc) + 1);
                list.add(new SeriesItem(num, name, cover, plot, cast, director, genre, releaseDate, rating, category, seriesId));

                if (!firstBatchPosted[0] && list.size() >= previewCount) {
                    firstBatchPosted[0] = true;
                    final List<SeriesItem> firstBatch = new ArrayList<>(list);
                    final Map<String, String> firstCatMap = new java.util.HashMap<>(catIdMap);
                    final Map<String, Integer> firstCounts = new java.util.LinkedHashMap<>(categoryCounts);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            allSeries = firstBatch;
                            categoryIdToName = firstCatMap;
                            SeriesActivity.this.categoryCounts = firstCounts;
                            setupInitialData();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("SeriesActivity", "Error parsing series item: " + e.getMessage());
                try { reader.skipValue(); } catch (Exception ignored) {}
            }
        }
        reader.endArray();
    }

    private void parseCategoriesToMap(JsonReader reader, Map<String, String> map) throws java.io.IOException {
        JsonToken peek = reader.peek();
        if (peek == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String id = "", name = "";
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.equals("category_id")) id = getNextString(reader);
                    else if (key.equals("category_name")) name = getNextString(reader);
                    else reader.skipValue();
                }
                reader.endObject();
                if (!id.isEmpty() && !name.isEmpty()) map.put(id, name);
            }
            reader.endArray();
        } else if (peek == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            while (reader.hasNext()) {
                String catIdKey = reader.nextName();
                JsonToken innerPeek = reader.peek();
                if (innerPeek == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject();
                    String id = "", name = "";
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if (key.equals("category_id")) id = getNextString(reader);
                        else if (key.equals("category_name")) name = getNextString(reader);
                        else reader.skipValue();
                    }
                    reader.endObject();
                    if (!id.isEmpty() && !name.isEmpty()) map.put(id, name);
                } else if (innerPeek == JsonToken.STRING || innerPeek == JsonToken.NUMBER) {
                    map.put(catIdKey, getNextString(reader));
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } else {
            reader.skipValue();
        }
    }

    private void parseSeriesToList(JsonReader reader, Map<String, Integer> categoryCounts, Map<String, String> catIdMap, List<SeriesItem> list) throws java.io.IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            int num = 0;
            String name = "", cover = "", plot = "", cast = "", director = "", genre = "", releaseDate = "", rating = "", catId = "", seriesId = "";
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("num")) num = getNextInt(reader);
                else if (key.equals("name")) name = getNextString(reader);
                else if (key.equals("cover")) cover = getNextString(reader);
                else if (key.equals("plot")) plot = getNextString(reader);
                else if (key.equals("cast")) cast = getNextString(reader);
                else if (key.equals("director")) director = getNextString(reader);
                else if (key.equals("genre")) genre = getNextString(reader);
                else if (key.equals("releaseDate")) releaseDate = getNextString(reader);
                else if (key.equals("rating")) rating = getNextString(reader);
                else if (key.equals("category_id")) catId = getNextString(reader);
                else if (key.equals("series_id")) seriesId = getNextString(reader);
                else reader.skipValue();
            }
            reader.endObject();

            String category = catIdMap.get(catId);
            if (category == null || category.isEmpty()) category = "General";
            Integer currentCount = categoryCounts.get(category);
            categoryCounts.put(category, (currentCount == null ? 0 : currentCount) + 1);
            list.add(new SeriesItem(num, name, cover, plot, cast, director, genre, releaseDate, rating, category, seriesId));
        }
        reader.endArray();
    }

    private void parseMoviesToList(JsonReader reader, Map<String, Integer> categoryCounts, Map<String, String> catIdMap, List<SeriesItem> list) throws java.io.IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            int num = 0;
            String name = "", cover = "", streamId = "", catId = "", ext = "mp4", rating = "", plot = "";
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("num")) num = getNextInt(reader);
                else if (key.equals("name")) name = getNextString(reader);
                else if (key.equals("stream_icon")) cover = getNextString(reader);
                else if (key.equals("category_id")) catId = getNextString(reader);
                else if (key.equals("stream_id")) streamId = getNextString(reader);
                else if (key.equals("container_extension")) ext = getNextString(reader);
                else if (key.equals("rating")) rating = getNextString(reader);
                else if (key.equals("plot")) plot = getNextString(reader);
                else reader.skipValue();
            }
            reader.endObject();

            String category = catIdMap.get(catId);
            if (category == null || category.isEmpty()) category = "General";
            Integer count = categoryCounts.get(category);
            categoryCounts.put(category, (count == null ? 0 : count) + 1);

            SeriesItem movie = new SeriesItem(num, name, cover, plot, "", "", "", "", rating, category, streamId);
            movie.containerExtension = ext;
            list.add(movie);
        }
        reader.endArray();
    }

    private void setupInitialData() {
        updateCategories();
        refreshSeries();
        loadCachedBackground();
        focusSelectedCategory();
    }

    private String getNextString(JsonReader reader) throws java.io.IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return "";
        }
        if (token == JsonToken.STRING) {
            return reader.nextString();
        }
        if (token == JsonToken.NUMBER) {
            double val = reader.nextDouble();
            if (val == (long) val) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        }
        if (token == JsonToken.BOOLEAN) {
            return String.valueOf(reader.nextBoolean());
        }
        reader.skipValue();
        return "";
    }

    private int getNextInt(JsonReader reader) throws java.io.IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NUMBER) {
            return reader.nextInt();
        }
        if (token == JsonToken.STRING) {
            try {
                return Integer.parseInt(reader.nextString());
            } catch (Exception e) {
                return 0;
            }
        }
        if (token == JsonToken.BOOLEAN) {
            return reader.nextBoolean() ? 1 : 0;
        }
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return 0;
        }
        reader.skipValue();
        return 0;
    }

    private void parseCategories(JsonReader reader) throws java.io.IOException {
        // Redundant with parseCategoriesToMap but kept for safety if called elsewhere
        parseCategoriesToMap(reader, categoryIdToName);
    }

    private void parseSeries(JsonReader reader, Map<String, Integer> categoryCounts) throws java.io.IOException {
        // Redundant with parseSeriesToList
        parseSeriesToList(reader, categoryCounts, categoryIdToName, allSeries);
    }

    private void parseMovies(JsonReader reader, Map<String, Integer> categoryCounts) throws java.io.IOException {
        // Redundant with parseMoviesToList
        parseMoviesToList(reader, categoryCounts, categoryIdToName, allSeries);
    }

    private void buildUI() {
        float scale = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        // Header Layout
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#080808"));
        header.setPadding((int)(16 * scale), (int)(10 * scale), (int)(16 * scale), (int)(10 * scale));



        // Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        final String[] tabNames = {"رجوع", "بث مباشر", "افلام", "مسلسلات"};
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(TvUtil.translate(this, tabNames[i]));
            tv.setTextSize(14);
            tv.setPadding((int)(15 * scale), 0, (int)(15 * scale), 0);

            boolean active = false;
            if (idx == 2 && type.equals("movies")) active = true;
            else if (idx == 3 && type.equals("series")) active = true;
            styleTab(tv, active);
            TvUtil.applyTvFocusHighlight(tv);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == 0) {
                        Intent intent = new Intent(SeriesActivity.this, Ot2Activity.class);
                        startActivity(intent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    } else if (idx == 1) {
                        Intent intent = new Intent(SeriesActivity.this, LiveActivity.class);
                        startActivity(intent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    } else if (idx == 2) {
                        if (!type.equals("movies")) {
                            Intent intent = new Intent(SeriesActivity.this, SeriesActivity.class);
                            intent.putExtra("type", "movies");
                            startActivity(intent);
                            finish();
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        }
                    } else if (idx == 3) {
                        if (!type.equals("series")) {
                            Intent intent = new Intent(SeriesActivity.this, SeriesActivity.class);
                            intent.putExtra("type", "series");
                            startActivity(intent);
                            finish();
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        }
                    }
                }
            });
            tabs.addView(tv);

            if (i < tabNames.length - 1) {
                View sep = new View(this);
                sep.setBackgroundColor(Color.parseColor("#333333"));
                tabs.addView(sep, new LinearLayout.LayoutParams(2, (int)(20 * scale)));
            }
        }
        header.addView(tabs);

        // Spacer to push logo to the right
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        // Premium Search Bar (Harmonized with left panel buttons)
        EditText search = new EditText(this);
        search.setHint(type.equals("movies") ? TvUtil.translate(this, "بحث عن فيلم...") : TvUtil.translate(this, "بحث عن مسلسل..."));
        search.setHintTextColor(Color.parseColor("#66A0C0F0")); // Translucent icy-blue hint
        search.setTextColor(Color.WHITE);
        search.setTextSize(12);
        search.setSingleLine(true);
        search.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        searchBg.setColors(new int[]{Color.parseColor("#0F172A"), Color.parseColor("#1E293B")}); // Premium deep slate-blue gradient
        searchBg.setCornerRadius(8 * scale);
        searchBg.setStroke((int)(1.4f * scale), Color.parseColor(STROKE_BLUE)); // Glowing neon-blue outline
        search.setBackground(searchBg);
        search.setPadding((int)(12 * scale), 0, (int)(12 * scale), 0);
        search.setFocusable(true);
        search.setFocusableInTouchMode(true);
        TvUtil.applyTvFocusHighlight(search, 8.0f);

        android.graphics.drawable.Drawable searchIcon = getResources().getDrawable(android.R.drawable.ic_menu_search);
        if (searchIcon != null) {
            searchIcon.setColorFilter(Color.parseColor(BLUE_ACTIVE), android.graphics.PorterDuff.Mode.SRC_IN); // Glowing active neon-blue search icon
            search.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
            search.setCompoundDrawablePadding((int)(8 * scale));
        }

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, (int)(38 * scale));
        searchLp.setMargins((int)(6 * scale), (int)(6 * scale), (int)(6 * scale), (int)(12 * scale));
        search.setLayoutParams(searchLp);
        
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        search.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH 
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    v.clearFocus();
                    if (TvUtil.isTvMode(SeriesActivity.this)) {
                        if (rvCategories != null && rvCategories.getChildCount() > 0) {
                            rvCategories.requestFocus();
                        }
                    } else {
                        rootLayout.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        setupSearchListener(search);

        // Premium Logo Box for 100% UI consistency
        FrameLayout logoBox = new FrameLayout(this);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(Color.parseColor("#111111"));
        lb.setCornerRadius(4 * scale);
        lb.setStroke(1, Color.parseColor("#333333"));
        logoBox.setBackground(lb);
        logoBox.setPadding((int)(4 * scale), (int)(4 * scale), (int)(4 * scale), (int)(4 * scale));

        ImageView logoIv = new ImageView(this);
        logoIv.setImageResource(R.drawable.home_logo);
        logoIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoBox.addView(logoIv, new FrameLayout.LayoutParams((int)(20 * scale), (int)(20 * scale)));
        header.addView(logoBox);

        root.addView(header);

        loadingBar = new ProgressBar(this);
        loadingBar.setIndeterminate(true);
        loadingBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams((int)(40 * scale), (int)(40 * scale));
        lbLp.gravity = Gravity.CENTER;
        lbLp.topMargin = (int)(20 * scale);
        root.addView(loadingBar, lbLp);

        // Body Layout
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding((int)(8 * scale), (int)(8 * scale), (int)(8 * scale), (int)(8 * scale));

        // Categories List (Left) - Premium Translucent Blue Background
        LinearLayout catPanel = new LinearLayout(this);
        catPanel.setOrientation(LinearLayout.VERTICAL);
        catPanel.setPadding((int)(4 * scale), (int)(4 * scale), (int)(4 * scale), (int)(4 * scale));
        GradientDrawable catPanelBg = new GradientDrawable();
        catPanelBg.setColor(Color.parseColor(BLUE_TRANS));
        catPanelBg.setStroke(2, Color.parseColor(STROKE_BLUE));
        catPanelBg.setCornerRadius(10 * scale);
        catPanel.setBackground(catPanelBg);

        // Add category icon ImageView above search
        ImageView catIcon = new ImageView(this);
        int resId = type.equals("movies") ? R.drawable.picsart_26_05_20_21_50_41_231 : R.drawable.picsart_26_05_20_21_50_20_637;
        catIcon.setImageResource(resId);
        catIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-1, (int)(60 * scale));
        iconLp.setMargins((int)(12 * scale), (int)(12 * scale), (int)(12 * scale), (int)(6 * scale));
        catIcon.setLayoutParams(iconLp);
        catPanel.addView(catIcon);

        catPanel.addView(search); // Add search right below the category icon!

        rvCategories = new RecyclerView(this);
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        catAdapter = new CatAdapter();
        rvCategories.setAdapter(catAdapter);
        catPanel.addView(rvCategories, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams((int)(190 * scale), -1);
        catLp.rightMargin = (int)(8 * scale);
        body.addView(catPanel, catLp);

        // Series Grid (Right)
        LinearLayout seriesPanel = new LinearLayout(this);
        seriesPanel.setOrientation(LinearLayout.VERTICAL);
        seriesPanel.setBackgroundColor(Color.TRANSPARENT);

        rvSeries = new RecyclerView(this);
        rvSeries.setLayoutManager(new GridLayoutManager(this, 4));
        seriesAdapter = new SeriesAdapter();
        rvSeries.setAdapter(seriesAdapter);
        seriesPanel.addView(rvSeries, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams seriesLp = new LinearLayout.LayoutParams(0, -1, 1);
        body.addView(seriesPanel, seriesLp);

        root.addView(body, new LinearLayout.LayoutParams(-1, -1));
        rootLayout.addView(root);
        setContentView(rootLayout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLang != null && !currentLang.equals(TvUtil.getAppLanguage(this))) {
            recreate();
            return;
        }
        TvUtil.hideSystemUI(this);
        updateCategories();
        refreshSeries();

        focusLastSelectedCategory();
    }

    private void focusLastSelectedCategory() {
        if (TvUtil.isTvMode(this) && rvCategories != null && !categories.isEmpty()) {
            rvCategories.post(new Runnable() {
                @Override
                public void run() {
                    int targetPos = 0;
                    for (int i = 0; i < categories.size(); i++) {
                        if (selectedCategory != null && selectedCategory.equals(categories.get(i).name)) {
                            targetPos = i;
                            break;
                        }
                    }
                    final int pos = targetPos;
                    rvCategories.scrollToPosition(pos);
                    rvCategories.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvCategories.findViewHolderForAdapterPosition(pos);
                            if (holder != null && holder.itemView != null) {
                                View row = null;
                                if (holder.itemView instanceof ViewGroup) {
                                    ViewGroup container = (ViewGroup) holder.itemView;
                                    if (container.getChildCount() > 0) {
                                        row = container.getChildAt(0);
                                    }
                                }
                                if (row != null) {
                                    row.requestFocus();
                                } else {
                                    holder.itemView.requestFocus();
                                }
                            }
                        }
                    }, 150);
                }
            });
        }
    }

    private int getFavoritesCount() {
        android.content.SharedPreferences sp = getSharedPreferences("SeriesFavs", MODE_PRIVATE);
        int count = 0;
        for (SeriesItem item : allSeries) {
            if (sp.getBoolean("fav_" + item.seriesId, false)) {
                count++;
            }
        }
        return count;
    }

    private int getRecentCount() {
        android.content.SharedPreferences sp = getSharedPreferences("SeriesHistory", MODE_PRIVATE);
        String historyKey = type.equals("movies") ? "recent_movies" : "recent_series";
        String currentHistory = sp.getString(historyKey, "");
        if (currentHistory.isEmpty()) return 0;
        
        String[] parts = currentHistory.split(",");
        int count = 0;
        for (String id : parts) {
            String clean = id.trim();
            if (clean.isEmpty()) continue;
            for (SeriesItem item : allSeries) {
                if (item.seriesId.equals(clean)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private void updateCategories() {
        categories.clear();
        categories.add(new CategoryItem(CAT_RECENT, getRecentCount()));
        categories.add(new CategoryItem(CAT_FAV, getFavoritesCount()));
        categories.add(new CategoryItem(CAT_ALL, allSeries.size()));
        android.content.SharedPreferences settingsSp = getSharedPreferences("Settings", MODE_PRIVATE);
        String prefix = type.equals("movies") ? "hide_movies_cat_" : "hide_series_cat_";
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            String catName = entry.getKey();
            if (entry.getValue() > 0) {
                if (!settingsSp.getBoolean(prefix + catName, false)) {
                    categories.add(new CategoryItem(catName, entry.getValue()));
                }
            }
        }
        if (catAdapter != null) catAdapter.notifyDataSetChanged();
    }

    private void refreshSeries() {
        if (selectedCategory.equals(CAT_ALL)) {
            // Direct reference - no copying to save memory
            filteredSeries = allSeries;
        } else if (selectedCategory.equals(CAT_FAV)) {
            android.content.SharedPreferences sp = getSharedPreferences("SeriesFavs", MODE_PRIVATE);
            List<SeriesItem> favList = new ArrayList<>();
            for (SeriesItem item : allSeries) {
                if (sp.getBoolean("fav_" + item.seriesId, false)) {
                    favList.add(item);
                }
            }
            filteredSeries = favList;
        } else if (selectedCategory.equals(CAT_RECENT)) {
            android.content.SharedPreferences sp = getSharedPreferences("SeriesHistory", MODE_PRIVATE);
            String historyKey = type.equals("movies") ? "recent_movies" : "recent_series";
            String currentHistory = sp.getString(historyKey, "");
            List<SeriesItem> recentList = new ArrayList<>();
            if (!currentHistory.isEmpty()) {
                String[] parts = currentHistory.split(",");
                for (String id : parts) {
                    String clean = id.trim();
                    if (clean.isEmpty()) continue;
                    for (SeriesItem item : allSeries) {
                        if (item.seriesId.equals(clean)) {
                            recentList.add(item);
                            break;
                        }
                    }
                }
            }
            filteredSeries = recentList;
        } else {
            List<SeriesItem> catList = new ArrayList<>();
            for (SeriesItem item : allSeries) {
                if (item.category.equals(selectedCategory)) {
                    catList.add(item);
                }
            }
            filteredSeries = catList;
        }
        displayedSeries.clear();
        displayedSeries.addAll(filteredSeries);
        if (seriesAdapter != null) {
            seriesAdapter.notifyDataSetChanged();
        }
        if (rvSeries != null) {
            rvSeries.scrollToPosition(0);
        }
    }

    private final Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    private void setupSearchListener(EditText search) {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(final CharSequence s, int start, int before, int count) { 
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        filterSeriesInBackground(s.toString());
                    }
                };
                searchHandler.postDelayed(searchRunnable, 400); // 400ms debounce
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterSeriesInBackground(final String query) {
        final List<SeriesItem> tempFiltered = new ArrayList<>();
        android.content.SharedPreferences spFav = getSharedPreferences("SeriesFavs", MODE_PRIVATE);
        android.content.SharedPreferences spHist = getSharedPreferences("SeriesHistory", MODE_PRIVATE);
        String historyKey = type.equals("movies") ? "recent_movies" : "recent_series";
        String currentHistory = spHist.getString(historyKey, "");

        if (selectedCategory.equals(CAT_RECENT)) {
            if (!currentHistory.isEmpty()) {
                String[] parts = currentHistory.split(",");
                for (String id : parts) {
                    String clean = id.trim();
                    if (clean.isEmpty()) continue;
                    for (SeriesItem item : allSeries) {
                        if (item.seriesId.equals(clean)) {
                            boolean nameOk = query.isEmpty() || item.name.toLowerCase().contains(query.toLowerCase());
                            if (nameOk) tempFiltered.add(item);
                            break;
                        }
                    }
                }
            }
        } else {
            for (SeriesItem item : allSeries) {
                boolean catOk = selectedCategory.equals(CAT_ALL) || (selectedCategory.equals(CAT_FAV) ? spFav.getBoolean("fav_" + item.seriesId, false) : item.category.equals(selectedCategory));
                boolean nameOk = query.isEmpty() || item.name.toLowerCase().contains(query.toLowerCase());
                if (catOk && nameOk) tempFiltered.add(item);
            }
        }
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                filteredSeries = tempFiltered;
                displayedSeries.clear();
                displayedSeries.addAll(filteredSeries);
                if (seriesAdapter != null) seriesAdapter.notifyDataSetChanged();
            }
        });
    }

    private void filterSeries(String query) {
        filterSeriesInBackground(query);
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
        } catch (Exception e) {}
    }

    class CatAdapter extends RecyclerView.Adapter<CatAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            View root;
            VH(View v) {
                super(v);
                root = v;
                tvName = v.findViewWithTag("name");
                tvCount = v.findViewWithTag("count");
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            float scale = getResources().getDisplayMetrics().density;
            LinearLayout container = new LinearLayout(SeriesActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);

            LinearLayout row = new LinearLayout(SeriesActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(16 * scale), (int)(10 * scale), (int)(16 * scale), (int)(10 * scale));

            TextView tvN = new TextView(SeriesActivity.this);
            tvN.setTag("name");
            tvN.setTextColor(Color.WHITE);
            tvN.setTextSize(11);
            row.addView(tvN, new LinearLayout.LayoutParams(0, -2, 1));

            TextView tvC = new TextView(SeriesActivity.this);
            tvC.setTag("count");
            tvC.setTextColor(Color.parseColor(BLUE_ACTIVE));
            tvC.setTextSize(11);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
            countLp.leftMargin = (int)(10 * scale);
            tvC.setLayoutParams(countLp);
            row.addView(tvC);

            container.addView(row);
            
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    // Focus highlight is handled automatically by TvUtil. D-pad scrolling is now 100% smooth.
                }
            });
            TvUtil.applyTvFocusHighlight(row, 0.0f);

            View div = new View(SeriesActivity.this);
            div.setBackgroundColor(Color.parseColor("#222222"));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, 1);
            divLp.topMargin = (int)(2 * scale);
            container.addView(div, divLp);

            return new VH(container);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            final CategoryItem cat = categories.get(pos);
            h.tvName.setText(TvUtil.formatNameByLanguage(cat.name));
            h.tvCount.setText(String.valueOf(cat.count));
            if (!cat.name.equals(CAT_ALL) && !cat.name.equals(CAT_FAV) && !cat.name.equals(CAT_RECENT)) {
                TvUtil.alignTextByLanguage(h.tvName, cat.name);
            } else {
                h.tvName.setGravity(android.view.Gravity.RIGHT);
            }

            boolean sel = cat.name.equals(selectedCategory);
            View row = ((ViewGroup) h.root).getChildAt(0);
            row.setBackgroundColor(sel ? Color.parseColor(BLUE_TRANS) : Color.TRANSPARENT);
            h.tvName.setTextColor(sel ? Color.parseColor(BLUE_ACTIVE) : Color.WHITE);
            h.tvName.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedCategory = cat.name;
                    getSharedPreferences("SeriesPrefs", MODE_PRIVATE).edit().putString("last_selected_category_" + type, cat.name).apply();
                    notifyDataSetChanged();
                    refreshSeries();
                    focusSelectedCategory();
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (rvSeries != null && filteredSeries.size() > 0) {
                                rvSeries.scrollToPosition(0);
                                rvSeries.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (rvSeries == null) return;
                                        RecyclerView.ViewHolder holder = rvSeries.findViewHolderForAdapterPosition(0);
                                        if (holder != null && holder.itemView != null) {
                                            holder.itemView.requestFocus();
                                        } else {
                                            View firstItem = rvSeries.getChildAt(0);
                                            if (firstItem != null) firstItem.requestFocus();
                                        }
                                    }
                                }, 50);
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }
    }

    // Series Grid Adapter
    class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView ivCover;
            TextView tvName;
            View root;
            VH(View v) {
                super(v);
                root = v;
                ivCover = v.findViewWithTag("cover");
                tvName = v.findViewWithTag("name");
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            final float scale = getResources().getDisplayMetrics().density;

            LinearLayout card = new LinearLayout(SeriesActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding((int)(5 * scale), (int)(5 * scale), (int)(5 * scale), (int)(5 * scale));

            RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(-1, (int)(180 * scale));
            cardLp.setMargins((int)(6 * scale), (int)(6 * scale), (int)(6 * scale), (int)(6 * scale));
            card.setLayoutParams(cardLp);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor(BLUE_TRANS));
            gd.setCornerRadius(10 * scale);
            gd.setStroke(2, Color.parseColor(STROKE_BLUE));
            card.setBackground(gd);

            ImageView iv = new ImageView(SeriesActivity.this);
            iv.setTag("cover");
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(-1, (int)(130 * scale));
            iv.setLayoutParams(ivLp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            // Clip cover with rounded corners
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                iv.setClipToOutline(true);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8 * scale);
                    }
                });
            }
            card.addView(iv);

            TextView tv = new TextView(SeriesActivity.this);
            tv.setTag("name");
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-1, -2);
            tvLp.topMargin = (int)(6 * scale);
            tv.setLayoutParams(tvLp);
            card.addView(tv);
            TvUtil.applyTvFocusHighlight(card, 10.0f);

            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            if (pos >= displayedSeries.size()) return;
            final SeriesItem item = displayedSeries.get(pos);
            h.tvName.setText(TvUtil.formatNameByLanguage(item.name));

            if (item.cover != null && !item.cover.isEmpty()) {
                TvUtil.loadImage(h.ivCover, item.cover);
            } else {
                h.ivCover.setImageDrawable(null);
            }

            h.root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SeriesActivity.this, SeriesdetailActivity.class);
                    intent.putExtra("series_id", item.seriesId);
                    intent.putExtra("name", item.name);
                    intent.putExtra("cover", item.cover);
                    intent.putExtra("plot", item.plot);
                    intent.putExtra("cast", item.cast);
                    intent.putExtra("director", item.director);
                    intent.putExtra("genre", item.genre);
                    intent.putExtra("releaseDate", item.releaseDate);
                    intent.putExtra("rating", item.rating);
                    intent.putExtra("category", item.category);
                    intent.putExtra("type", type);
                    if ("movies".equals(type)) {
                        intent.putExtra("container_extension", item.containerExtension);
                    }
                    startActivity(intent);
                }
            });
            h.root.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                            int position = rvSeries.getChildAdapterPosition(v);
                            if (position % 4 == 0) { // Column 0 in Grid
                                focusSelectedCategory();
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return displayedSeries.size();
        }

        @Override
        public void onViewRecycled(VH h) {
            super.onViewRecycled(h);
            if (h.ivCover != null) TvUtil.cancelLoad(h.ivCover);
        }
    }

    static class CategoryItem {
        String name;
        int count;
        CategoryItem(String n, int c) {
            name = n;
            count = c;
        }
    }

    static class SeriesItem {
        int num;
        String name, cover, plot, cast, director, genre, releaseDate, rating, category, seriesId;
        String containerExtension = "mp4";

        SeriesItem(int num, String name, String cover, String plot, String cast, String director, String genre, String releaseDate, String rating, String category, String seriesId) {
            this.num = num;
            this.name = name;
            this.cover = cover;
            this.plot = plot;
            this.cast = cast;
            this.director = director;
            this.genre = genre;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.category = category;
            this.seriesId = seriesId;
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
