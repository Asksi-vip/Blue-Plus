package com.blue.plus;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SeriesepisodesActivity extends Activity {

    public static List<EpisodeItem> cachedEpisodes = null;
    public static String cachedSeriesName = "";

    private FrameLayout rootLayout;
    private RecyclerView rvSeasons, rvEpisodes;
    private SeasonAdapter seasonAdapter;
    private EpisodeAdapter episodeAdapter;

    private List<SeasonItem> seasons = new ArrayList<>();
    private Map<String, List<EpisodeItem>> episodesMap = new HashMap<>();
    private List<EpisodeItem> currentEpisodesList = new ArrayList<>();

    private String seriesId, seriesName, seriesCover;
    private String selectedSeasonNum = "";

    private String dns = "", username = "", password = "";

    private static final String BLUE_ACTIVE = "#2196F3";
    private static final String BLUE_TRANS = "#332196F3";
    private static final String STROKE_BLUE = "#552196F3";
    private static final String FILE_BG = "splash_bg.jpg";

    private static final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(250);

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvUtil.enableTls12(this); // Fix SSL for older devices
        TvUtil.hideSystemUI(this);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        seriesId = getIntent().getStringExtra("series_id");
        seriesName = getIntent().getStringExtra("name");
        seriesCover = getIntent().getStringExtra("cover");

        loadCredentials();
        buildUI();
        loadCachedBackground();
        fetchEpisodesData();
    }

    private void loadCredentials() {
        android.content.SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        try {
            String activeDns = sp.getString("active_dns", "");
            String activeUser = sp.getString("active_username", "");
            String activePass = sp.getString("active_password", "");

            if (activeDns.isEmpty() || activeUser.isEmpty() || activePass.isEmpty()) {
                String json = sp.getString("list", "[]");
                ArrayList<HashMap<String, Object>> list = new com.google.gson.Gson().fromJson(
                        json,
                        new com.google.gson.reflect.TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType()
                );

                if (list != null && !list.isEmpty()) {
                    Map<String, Object> lastItem = list.get(list.size() - 1);
                    activeDns = (String) lastItem.get("dns");
                    activeUser = (String) lastItem.get("username");
                    activePass = (String) lastItem.get("password");
                }
            }

            dns = activeDns;
            username = activeUser;
            password = activePass;
        } catch (Exception e) {
            Log.e("SeriesepisodesActivity", "Error loading credentials: " + e.getMessage());
        }
    }

    private void buildUI() {
        float scale = getResources().getDisplayMetrics().density;

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Apple Atmospheric Midnight Canvas Overlay
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#7305070B"));
        rootLayout.addView(overlay);

        // Horizontal Layout split (Left: Sidebar, Right: Main Content)
        LinearLayout splitLayout = new LinearLayout(this);
        splitLayout.setOrientation(LinearLayout.HORIZONTAL);
        splitLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        splitLayout.setPadding((int)(14 * scale), (int)(10 * scale), (int)(14 * scale), (int)(10 * scale));

        // ─── LEFT SIDEBAR (Seasons List) ───
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding((int)(12 * scale), (int)(12 * scale), (int)(12 * scale), (int)(12 * scale));

        GradientDrawable sbBg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.parseColor("#E60B101C"), Color.parseColor("#E60D1526")}
        );
        sbBg.setCornerRadius(20 * scale);
        sbBg.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        sidebar.setBackground(sbBg);

        // Header Row inside Sidebar (Back Button + Title)
        LinearLayout sbHeader = new LinearLayout(this);
        sbHeader.setOrientation(LinearLayout.HORIZONTAL);
        sbHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sbHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        sbHeaderLp.bottomMargin = (int)(12 * scale);
        sbHeader.setLayoutParams(sbHeaderLp);

        // Back Button
        TextView btnBack = new TextView(this);
        btnBack.setText("↩");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(18);
        btnBack.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int)(36 * scale), (int)(36 * scale));
        backLp.rightMargin = (int)(10 * scale);
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
        TvUtil.applyTvFocusHighlight(btnBack, 18.0f);
        sbHeader.addView(btnBack);

        TextView tvSbTitle = new TextView(this);
        tvSbTitle.setText(TvUtil.translate(this, "المواسم"));
        tvSbTitle.setTextColor(Color.parseColor("#0A84FF"));
        tvSbTitle.setTextSize(14);
        tvSbTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sbHeader.addView(tvSbTitle);

        sidebar.addView(sbHeader);

        // Seasons RecyclerView
        rvSeasons = new RecyclerView(this);
        rvSeasons.setLayoutManager(new LinearLayoutManager(this));
        seasonAdapter = new SeasonAdapter();
        rvSeasons.setAdapter(seasonAdapter);
        sidebar.addView(rvSeasons, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams sidebarLp = new LinearLayout.LayoutParams((int)(210 * scale), -1);
        sidebarLp.rightMargin = (int)(12 * scale);
        splitLayout.addView(sidebar, sidebarLp);

        // ─── RIGHT SECTION (Episodes List) ───
        LinearLayout rightSection = new LinearLayout(this);
        rightSection.setOrientation(LinearLayout.VERTICAL);

        // Header Row (Series Name + Episode count pill)
        LinearLayout epHeader = new LinearLayout(this);
        epHeader.setOrientation(LinearLayout.HORIZONTAL);
        epHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams epHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        epHeaderLp.bottomMargin = (int)(10 * scale);
        epHeader.setLayoutParams(epHeaderLp);

        TextView tvHeader = new TextView(this);
        tvHeader.setText(seriesName);
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setTextSize(20);
        tvHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams thLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tvHeader.setLayoutParams(thLp);
        epHeader.addView(tvHeader);

        // Episodes Count Pill
        LinearLayout countPill = new LinearLayout(this);
        countPill.setOrientation(LinearLayout.HORIZONTAL);
        countPill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cpBg = new GradientDrawable();
        cpBg.setColor(Color.parseColor("#E60B101C"));
        cpBg.setCornerRadius(999 * scale);
        cpBg.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        countPill.setBackground(cpBg);
        countPill.setPadding((int)(12 * scale), (int)(4 * scale), (int)(12 * scale), (int)(4 * scale));

        TextView tvCount = new TextView(this);
        tvCount.setText(TvUtil.translate(this, "الحلقات"));
        tvCount.setTextColor(Color.parseColor("#40C8E0"));
        tvCount.setTextSize(11);
        tvCount.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        countPill.addView(tvCount);
        epHeader.addView(countPill);

        rightSection.addView(epHeader);

        // Episodes RecyclerView
        rvEpisodes = new RecyclerView(this);
        rvEpisodes.setLayoutManager(new LinearLayoutManager(this));
        episodeAdapter = new EpisodeAdapter();
        rvEpisodes.setAdapter(episodeAdapter);
        rightSection.addView(rvEpisodes, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1);
        splitLayout.addView(rightSection, rightLp);

        rootLayout.addView(splitLayout);
        setContentView(rootLayout);
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

    private void fetchEpisodesData() {
        if (dns.isEmpty() || username.isEmpty() || password.isEmpty() || seriesId == null) {
            Toast.makeText(this, "بيانات الاتصال غير صالحة", Toast.LENGTH_SHORT).show();
            return;
        }

        String requestUrl = dns + "/player_api.php?action=get_series_info&series_id=" + seriesId + "&username=" + username + "&password=" + password;
        new GetSeriesInfoTask(this).execute(requestUrl);
    }

    private void onDataLoaded(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            Toast.makeText(this, "فشل جلب الحلقات من السيرفر", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            
            // Parse Episodes
            JSONObject episodesObj = responseObj.optJSONObject("episodes");
            if (episodesObj != null) {
                Iterator<String> keys = episodesObj.keys();
                while (keys.hasNext()) {
                    String seasonKey = keys.next();
                    JSONArray episodesArr = episodesObj.getJSONArray(seasonKey);
                    List<EpisodeItem> episodesList = new ArrayList<>();
                    
                    for (int i = 0; i < episodesArr.length(); i++) {
                        JSONObject epJson = episodesArr.getJSONObject(i);
                        String epId = epJson.optString("id");
                        int epNum = epJson.optInt("episode_num");
                        String epTitle = epJson.optString("title");
                        String epExt = epJson.optString("container_extension", "mp4");
                        
                        episodesList.add(new EpisodeItem(epId, epNum, epTitle, epExt));
                    }
                    episodesMap.put(seasonKey, episodesList);
                }
            }

            // Parse Seasons
            seasons.clear();
            JSONArray seasonsArr = responseObj.optJSONArray("seasons");
            if (seasonsArr != null && seasonsArr.length() > 0) {
                for (int i = 0; i < seasonsArr.length(); i++) {
                    JSONObject seasonJson = seasonsArr.getJSONObject(i);
                    String sNum = seasonJson.optString("season_number");
                    String sName = seasonJson.optString("name", "Season " + sNum);
                    seasons.add(new SeasonItem(sNum, sName));
                }
            } else {
                // Generate seasons from episodes map if missing
                for (String seasonKey : episodesMap.keySet()) {
                    seasons.add(new SeasonItem(seasonKey, "Season " + seasonKey));
                }
            }

            // Select first season by default
            if (!seasons.isEmpty()) {
                selectedSeasonNum = seasons.get(0).seasonNumber;
                currentEpisodesList.clear();
                List<EpisodeItem> list = episodesMap.get(selectedSeasonNum);
                if (list != null) currentEpisodesList.addAll(list);
            }

            if (seasonAdapter != null) seasonAdapter.notifyDataSetChanged();
            if (episodeAdapter != null) episodeAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            Log.e("SeriesepisodesActivity", "Error parsing series info: " + e.getMessage());
            Toast.makeText(this, "خطأ في معالجة القنوات", Toast.LENGTH_SHORT).show();
        }
    }

    private void focusSelectedSeason() {
        if (rvSeasons == null || seasons.isEmpty()) return;
        
        int selectedIndex = 0;
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).seasonNumber.equals(selectedSeasonNum)) {
                selectedIndex = i;
                break;
            }
        }
        
        final int targetIdx = selectedIndex;
        rvSeasons.post(new Runnable() {
            @Override
            public void run() {
                if (rvSeasons != null && rvSeasons.getLayoutManager() != null) {
                    rvSeasons.getLayoutManager().scrollToPosition(targetIdx);
                    rvSeasons.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.ViewHolder holder = rvSeasons.findViewHolderForAdapterPosition(targetIdx);
                            if (holder != null && holder.itemView != null) {
                                holder.itemView.requestFocus();
                            } else {
                                if (rvSeasons.getChildCount() > targetIdx) {
                                    View child = rvSeasons.getChildAt(targetIdx);
                                    if (child != null) child.requestFocus();
                                } else if (rvSeasons.getChildCount() > 0) {
                                    View child = rvSeasons.getChildAt(0);
                                    if (child != null) child.requestFocus();
                                }
                            }
                        }
                    }, 150);
                }
            }
        });
    }

    // Seasons List Adapter
    class SeasonAdapter extends RecyclerView.Adapter<SeasonAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvSeasonName;
            VH(View v) {
                super(v);
                tvSeasonName = (TextView) v;
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            float scale = getResources().getDisplayMetrics().density;
            TextView tv = new TextView(SeriesepisodesActivity.this);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding((int)(14 * scale), (int)(12 * scale), (int)(14 * scale), (int)(12 * scale));
            
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            tv.setLayoutParams(lp);
            TvUtil.applyTvFocusHighlight(tv, 14.0f);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            final SeasonItem item = seasons.get(pos);
            h.tvSeasonName.setText(item.name);

            boolean isSelected = item.seasonNumber.equals(selectedSeasonNum);
            
            float scale = getResources().getDisplayMetrics().density;
            GradientDrawable gd;
            if (isSelected) {
                gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#0A84FF"), Color.parseColor("#0071E3")}
                );
                gd.setCornerRadius(14 * scale);
                h.tvSeasonName.setBackground(gd);
                h.tvSeasonName.setTextColor(Color.WHITE);
                h.tvSeasonName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            } else {
                gd = new GradientDrawable();
                gd.setColor(Color.parseColor("#140B101C"));
                gd.setCornerRadius(14 * scale);
                gd.setStroke((int)(1.0f * scale), Color.parseColor("#1A80B4FF"));
                h.tvSeasonName.setBackground(gd);
                h.tvSeasonName.setTextColor(Color.parseColor("#B0BEC5"));
                h.tvSeasonName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }

            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedSeasonNum = item.seasonNumber;
                    notifyDataSetChanged();

                    currentEpisodesList.clear();
                    List<EpisodeItem> list = episodesMap.get(selectedSeasonNum);
                    if (list != null) currentEpisodesList.addAll(list);
                    if (episodeAdapter != null) {
                        episodeAdapter.notifyDataSetChanged();
                    }
                    if (rvEpisodes != null) {
                        rvEpisodes.scrollToPosition(0);
                    }
                    focusSelectedSeason();
                }
            });
            h.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (rvEpisodes != null && currentEpisodesList.size() > 0) {
                                rvEpisodes.scrollToPosition(0);
                                rvEpisodes.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (rvEpisodes == null) return;
                                        RecyclerView.ViewHolder holder = rvEpisodes.findViewHolderForAdapterPosition(0);
                                        if (holder != null && holder.itemView != null) {
                                            holder.itemView.requestFocus();
                                        } else {
                                            View first = rvEpisodes.getChildAt(0);
                                            if (first != null) first.requestFocus();
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
            return seasons.size();
        }
    }

    // Episodes List Adapter
    class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView ivThumb;
            TextView tvNumber, tvTitle, tvSub;
            VH(View v) {
                super(v);
                ivThumb = v.findViewWithTag("thumb");
                tvNumber = v.findViewWithTag("number");
                tvTitle = v.findViewWithTag("title");
                tvSub = v.findViewWithTag("sub");
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            final float scale = getResources().getDisplayMetrics().density;

            LinearLayout container = new LinearLayout(SeriesepisodesActivity.this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding((int)(10 * scale), (int)(8 * scale), (int)(12 * scale), (int)(8 * scale));

            RecyclerView.LayoutParams containerLp = new RecyclerView.LayoutParams(-1, (int)(78 * scale));
            containerLp.bottomMargin = (int)(8 * scale);
            container.setLayoutParams(containerLp);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#E60D1526"));
            gd.setCornerRadius(16 * scale);
            gd.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
            container.setBackground(gd);

            // Left Thumbnail Frame
            FrameLayout thumbFrame = new FrameLayout(SeriesepisodesActivity.this);
            LinearLayout.LayoutParams tfLp = new LinearLayout.LayoutParams((int)(90 * scale), (int)(58 * scale));
            tfLp.rightMargin = (int)(14 * scale);
            thumbFrame.setLayoutParams(tfLp);

            ImageView iv = new ImageView(SeriesepisodesActivity.this);
            iv.setTag("thumb");
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                iv.setClipToOutline(true);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10 * scale);
                    }
                });
            }
            thumbFrame.addView(iv, new FrameLayout.LayoutParams(-1, -1));

            // Episode Number Label inside thumbnail (Floating Pill)
            TextView tvNum = new TextView(SeriesepisodesActivity.this);
            tvNum.setTag("number");
            tvNum.setTextColor(Color.WHITE);
            tvNum.setTextSize(9.5f);
            tvNum.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            GradientDrawable numBg = new GradientDrawable();
            numBg.setColor(Color.parseColor("#CC0B101C"));
            numBg.setCornerRadius(6 * scale);
            tvNum.setBackground(numBg);
            tvNum.setPadding((int)(6 * scale), (int)(2 * scale), (int)(6 * scale), (int)(2 * scale));
            
            FrameLayout.LayoutParams numLp = new FrameLayout.LayoutParams(-2, -2);
            numLp.gravity = Gravity.TOP | Gravity.LEFT;
            numLp.topMargin = (int)(4 * scale);
            numLp.leftMargin = (int)(4 * scale);
            tvNum.setLayoutParams(numLp);
            thumbFrame.addView(tvNum);

            View border = new View(SeriesepisodesActivity.this);
            GradientDrawable borderGd = new GradientDrawable();
            borderGd.setColor(Color.TRANSPARENT);
            borderGd.setCornerRadius(10 * scale);
            borderGd.setStroke((int)(1.2f * scale), Color.parseColor("#3380B4FF"));
            border.setBackground(borderGd);
            thumbFrame.addView(border, new FrameLayout.LayoutParams(-1, -1));

            container.addView(thumbFrame);

            // Middle Column: Title & Season/Episode Subtitle
            LinearLayout textCol = new LinearLayout(SeriesepisodesActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            textCol.setLayoutParams(textColLp);

            TextView tvT = new TextView(SeriesepisodesActivity.this);
            tvT.setTag("title");
            tvT.setTextColor(Color.WHITE);
            tvT.setTextSize(13);
            tvT.setSingleLine(true);
            tvT.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            textCol.addView(tvT);

            TextView tvSub = new TextView(SeriesepisodesActivity.this);
            tvSub.setTag("sub");
            tvSub.setTextColor(Color.parseColor("#8E8E93"));
            tvSub.setTextSize(11);
            tvSub.setSingleLine(true);
            tvSub.setPadding(0, (int)(3 * scale), 0, 0);
            textCol.addView(tvSub);

            container.addView(textCol);

            // Play Icon Pill on right
            TextView playPill = new TextView(SeriesepisodesActivity.this);
            playPill.setText("▶");
            playPill.setTextColor(Color.parseColor("#0A84FF"));
            playPill.setTextSize(14);
            playPill.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams((int)(32 * scale), (int)(32 * scale));
            playLp.leftMargin = (int)(8 * scale);
            playPill.setLayoutParams(playLp);

            GradientDrawable playBg = new GradientDrawable();
            playBg.setShape(GradientDrawable.OVAL);
            playBg.setColor(Color.parseColor("#1A0A84FF"));
            playBg.setStroke((int)(1.0f * scale), Color.parseColor("#4D0A84FF"));
            playPill.setBackground(playBg);
            container.addView(playPill);

            TvUtil.applyTvFocusHighlight(container, 16.0f);

            return new VH(container);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            final EpisodeItem ep = currentEpisodesList.get(pos);
            h.tvNumber.setText(String.valueOf(ep.episodeNum));
            h.tvTitle.setText(ep.title != null && !ep.title.trim().isEmpty() ? ep.title : ("الحلقة " + ep.episodeNum));
            if (h.tvSub != null) {
                h.tvSub.setText("الموسم " + selectedSeasonNum + " • الحلقة " + ep.episodeNum);
            }

            if (seriesCover != null && !seriesCover.isEmpty()) {
                TvUtil.loadImage(h.ivThumb, seriesCover);
            } else {
                h.ivThumb.setImageDrawable(null);
            }

            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Save to recently watched history
                    try {
                        android.content.SharedPreferences spHistory = getSharedPreferences("SeriesHistory", MODE_PRIVATE);
                        String currentHistory = spHistory.getString("recent_series", "");
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
                        spHistory.edit().putString("recent_series", sb.toString()).apply();
                    } catch (Exception e) {}

                    // Play video stream internally!
                    SeriesepisodesActivity.cachedEpisodes = currentEpisodesList;
                    SeriesepisodesActivity.cachedSeriesName = seriesName;
                    String streamUrl = dns + "/series/" + username + "/" + password + "/" + ep.id + "." + ep.containerExtension;
                    Intent intent = new Intent(SeriesepisodesActivity.this, PlayerActivity.class);
                    intent.putExtra("url", streamUrl);
                    intent.putExtra("title", seriesName + " - S" + (selectedSeasonNum.length() < 2 ? "0" + selectedSeasonNum : selectedSeasonNum) + "E" + (ep.episodeNum < 10 ? "0" + ep.episodeNum : ep.episodeNum) + " - " + ep.title);
                    startActivity(intent);
                }
            });
            h.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                            focusSelectedSeason();
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return currentEpisodesList.size();
        }
    }

    private static class GetSeriesInfoTask extends AsyncTask<String, Void, String> {
        private final java.lang.ref.WeakReference<SeriesepisodesActivity> activityRef;
        private ProgressDialog pd;

        public GetSeriesInfoTask(SeriesepisodesActivity activity) {
            activityRef = new java.lang.ref.WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            SeriesepisodesActivity activity = activityRef.get();
            if (activity != null) {
                pd = new ProgressDialog(activity);
                pd.setMessage(TvUtil.translate(activity, "جاري تحميل الحلقات والمواسم..."));
                pd.setCancelable(false);
                pd.show();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(25000);
                connection.setReadTimeout(25000);
                connection.connect();

                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            } catch (Exception e) {
                return null;
            } finally {
                if (connection != null) connection.disconnect();
                try {
                    if (reader != null) reader.close();
                } catch (Exception e) {}
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (pd != null && pd.isShowing()) pd.dismiss();
            SeriesepisodesActivity activity = activityRef.get();
            if (activity != null) {
                activity.onDataLoaded(result);
            }
        }
    }



    static class SeasonItem {
        String seasonNumber;
        String name;
        SeasonItem(String sNum, String name) {
            this.seasonNumber = sNum;
            this.name = name;
        }
    }

    static class EpisodeItem {
        String id;
        int episodeNum;
        String title;
        String containerExtension;

        EpisodeItem(String id, int epNum, String title, String ext) {
            this.id = id;
            this.episodeNum = epNum;
            this.title = title;
            this.containerExtension = ext;
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
