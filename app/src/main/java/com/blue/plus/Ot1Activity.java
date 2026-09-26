package com.blue.plus;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
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

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Ot1Activity extends Activity {

    private FrameLayout rootLayout;
    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private final List<Map<String, Object>> playlists = new ArrayList<>();
    private TextView tvStatusCount;

    private static final String TAG = "Ot1Debug";
    private static final String FILE_BG = "splash_bg.jpg";
    private static final String FILE_LOGO = "splash_logo.png";
    private String currentLang;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = TvUtil.getAppLanguage(this);
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception ignored) {}
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Apple Atmospheric Midnight Canvas Overlay (matches Ot2Activity)
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#7305070B"));
        rootLayout.addView(overlay);

        loadSavedPlaylists();
        addHeader();
        addSidebar();
        addMainContent();
        addTicker();
        addVersionBadge();

        setContentView(rootLayout);
        loadCachedBackground();
        requestPlaylistFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLang != null && !currentLang.equals(TvUtil.getAppLanguage(this))) {
            recreate();
            return;
        }
        loadSavedPlaylists();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateStatusCount();
        requestPlaylistFocus();
    }

    private void requestPlaylistFocus() {
        if (recyclerView == null) return;

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                recyclerView.removeOnLayoutChangeListener(this);
                recyclerView.post(new Runnable() {
                    @Override
                    public void run() {
                        focusFirstItem();
                    }
                });
            }
        });

        recyclerView.post(new Runnable() {
            @Override
            public void run() {
                focusFirstItem();
            }
        });

        recyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                focusFirstItem();
            }
        }, 250);
    }

    private void focusFirstItem() {
        if (recyclerView != null) {
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (lm != null) {
                View firstChild = lm.findViewByPosition(0);
                if (firstChild != null) {
                    firstChild.requestFocus();
                } else if (recyclerView.getChildCount() > 0) {
                    View child = recyclerView.getChildAt(0);
                    if (child != null) {
                        child.requestFocus();
                    }
                }
            }
        }
    }

    private void loadSavedPlaylists() {
        SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        String json = sp.getString("list", "[]");
        List<Map<String, Object>> loaded = new Gson().fromJson(json, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
        playlists.clear();
        if (loaded != null) {
            playlists.addAll(loaded);
        }
        updateStatusCount();
    }

    private void updateStatusCount() {
        if (tvStatusCount != null) {
            tvStatusCount.setText(TvUtil.translate(this, "السيرفرات المحفوظة: ") + playlists.size());
        }
    }

    // ==========================================
    // 1. Apple Header (Logo + Title + Status Pill)
    // ==========================================
    private void addHeader() {
        float scale = getResources().getDisplayMetrics().density;

        // Top Left / Start: Title & Icon Badge
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-2, -2);
        titleLp.gravity = Gravity.TOP | Gravity.LEFT;
        titleLp.topMargin = (int)(12 * scale);
        titleLp.leftMargin = (int)(22 * scale);
        titleBox.setLayoutParams(titleLp);

        // Icon Tile (14dp squircle, Apple Blue accent)
        FrameLayout iconTile = new FrameLayout(this);
        LinearLayout.LayoutParams iconTileLp = new LinearLayout.LayoutParams((int)(38 * scale), (int)(38 * scale));
        iconTileLp.rightMargin = (int)(10 * scale);
        iconTile.setLayoutParams(iconTileLp);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(12 * scale);
        iconBg.setColor(Color.parseColor("#260A84FF"));
        iconBg.setStroke((int)(1.2f * scale), Color.parseColor("#4D0A84FF"));
        iconTile.setBackground(iconBg);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(R.drawable.picsart_26_05_20_21_48_52_267);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams((int)(22 * scale), (int)(22 * scale));
        ivLp.gravity = Gravity.CENTER;
        ivIcon.setLayoutParams(ivLp);
        iconTile.addView(ivIcon);
        titleBox.addView(iconTile);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "قوائم التشغيل"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(TvUtil.translate(this, "الأكواد وسيرفرات البث المحفوظة"));
        tvSub.setTextColor(Color.parseColor("#80FFFFFF"));
        tvSub.setTextSize(10.5f);
        textCol.addView(tvSub);

        titleBox.addView(textCol);
        rootLayout.addView(titleBox);

        // Center Brand Logo (matches Ot2Activity exactly)
        ImageView logo = new ImageView(this);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams((int)(88 * scale), (int)(44 * scale));
        logoParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        logoParams.topMargin = (int)(10 * scale);
        logo.setLayoutParams(logoParams);
        logo.setImageResource(R.drawable.home_logo);
        loadCachedLogo(logo);
        rootLayout.addView(logo);

        // Right Status Capsule Pill (matches Ot2Activity)
        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2);
        statusParams.gravity = Gravity.TOP | Gravity.RIGHT;
        statusParams.topMargin = (int)(12 * scale);
        statusParams.rightMargin = (int)(22 * scale);
        statusPill.setLayoutParams(statusParams);

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.parseColor("#E60B101C"));
        statusBg.setCornerRadius(999 * scale);
        statusBg.setStroke((int)(1.2f * scale), Color.parseColor("#3380B4FF"));
        statusPill.setBackground(statusBg);
        statusPill.setPadding((int)(14 * scale), (int)(6 * scale), (int)(14 * scale), (int)(6 * scale));

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams((int)(8 * scale), (int)(8 * scale));
        dotParams.leftMargin = (int)(8 * scale);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#30D158")); // iOS Green
        dot.setBackground(dotBg);
        statusPill.addView(dot);

        tvStatusCount = new TextView(this);
        tvStatusCount.setTextColor(Color.parseColor("#EBEBF5"));
        tvStatusCount.setTextSize(12);
        tvStatusCount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusPill.addView(tvStatusCount);
        updateStatusCount();

        rootLayout.addView(statusPill);
    }

    // ==========================================
    // 2. Right Sidebar (Frosted Glass Card)
    // ==========================================
    private void addSidebar() {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        int widthPx = (int)((isTv ? 280 : 240) * scale);

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthPx, -1);
        params.gravity = Gravity.RIGHT;
        params.topMargin = (int)(62 * scale);
        params.rightMargin = (int)(18 * scale);
        params.bottomMargin = (int)(32 * scale);
        sidebar.setLayoutParams(params);

        // Apple Midnight Frosted Glass Container
        GradientDrawable sideBg = new GradientDrawable();
        sideBg.setColor(Color.parseColor("#E60D1526"));
        sideBg.setCornerRadius(20 * scale);
        sideBg.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF"));
        sidebar.setBackground(sideBg);
        sidebar.setPadding((int)(14 * scale), (int)(16 * scale), (int)(14 * scale), (int)(14 * scale));

        // Header inside sidebar
        LinearLayout sideHeader = new LinearLayout(this);
        sideHeader.setOrientation(LinearLayout.HORIZONTAL);
        sideHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams shParams = new LinearLayout.LayoutParams(-1, -2);
        shParams.bottomMargin = (int)(10 * scale);
        sideHeader.setLayoutParams(shParams);

        FrameLayout iconTile = new FrameLayout(this);
        LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams((int)(34 * scale), (int)(34 * scale));
        itLp.rightMargin = (int)(8 * scale);
        iconTile.setLayoutParams(itLp);
        GradientDrawable itBg = new GradientDrawable();
        itBg.setCornerRadius(10 * scale);
        itBg.setColor(Color.parseColor("#260A84FF"));
        itBg.setStroke((int)(1.2f * scale), Color.parseColor("#4D0A84FF"));
        iconTile.setBackground(itBg);

        ImageView sideIv = new ImageView(this);
        sideIv.setImageResource(R.drawable.picsart_26_05_20_21_49_58_854);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams((int)(20 * scale), (int)(20 * scale));
        ivLp.gravity = Gravity.CENTER;
        sideIv.setLayoutParams(ivLp);
        iconTile.addView(sideIv);
        sideHeader.addView(iconTile);

        LinearLayout sideTitleCol = new LinearLayout(this);
        sideTitleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvSideTitle = new TextView(this);
        tvSideTitle.setText(TvUtil.translate(this, "تفعيل الماك"));
        tvSideTitle.setTextColor(Color.WHITE);
        tvSideTitle.setTextSize(isTv ? 14 : 12.5f);
        tvSideTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sideTitleCol.addView(tvSideTitle);

        TextView tvSideSub = new TextView(this);
        tvSideSub.setText(TvUtil.translate(this, "معلومات الجهاز للتفعيل"));
        tvSideSub.setTextColor(Color.parseColor("#80FFFFFF"));
        tvSideSub.setTextSize(9.5f);
        sideTitleCol.addView(tvSideSub);

        sideHeader.addView(sideTitleCol);
        sidebar.addView(sideHeader);

        // Thin Frosted Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, (int)(1 * scale));
        divLp.bottomMargin = (int)(10 * scale);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        sidebar.addView(divider);

        // MAC Address Info Tile
        final String macStr = getMacAddress().toUpperCase(Locale.ENGLISH);
        addInfoTile(sidebar, TvUtil.translate(this, "عنوان MAC"), macStr, "#0A84FF", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(TvUtil.translate(Ot1Activity.this, "عنوان MAC"), macStr);
            }
        });

        // Device Key Info Tile
        final String devKeyStr = getDeviceKey();
        addInfoTile(sidebar, TvUtil.translate(this, "مفتاح الجهاز"), devKeyStr, "#30D158", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(TvUtil.translate(Ot1Activity.this, "مفتاح الجهاز"), devKeyStr);
            }
        });

        // Spacer
        View spacer = new View(this);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        spacer.setLayoutParams(spLp);
        sidebar.addView(spacer);

        // Action Button: Open Website / Telegram (iOS Accent Pill)
        TextView btnWeb = new TextView(this);
        btnWeb.setText(TvUtil.translate(this, "افتح الموقع / الدعم"));
        btnWeb.setTextColor(Color.WHITE);
        btnWeb.setTextSize(isTv ? 13 : 11.5f);
        btnWeb.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnWeb.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, (int)((isTv ? 42 : 36) * scale));
        btnLp.topMargin = (int)(8 * scale);
        btnWeb.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#260A84FF"));
        btnBg.setCornerRadius(14 * scale);
        btnBg.setStroke((int)(1.2f * scale), Color.parseColor("#660A84FF"));
        btnWeb.setBackground(btnBg);

        btnWeb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://t.me/Match_sportss"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(Ot1Activity.this, "فشل فتح الرابط!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        TvUtil.applyTvFocusHighlight(btnWeb, 14.0f);
        sidebar.addView(btnWeb);

        rootLayout.addView(sidebar);
    }

    private void addInfoTile(LinearLayout container, String label, final String value, String accentHex, View.OnClickListener onClick) {
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int)(8 * scale);
        tile.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#E60B101C"));
        gd.setCornerRadius(12 * scale);
        gd.setStroke((int)(1.2f * scale), Color.parseColor("#2680B4FF"));
        tile.setBackground(gd);
        tile.setPadding((int)(10 * scale), (int)(6 * scale), (int)(10 * scale), (int)(6 * scale));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#80FFFFFF"));
        tvLabel.setTextSize(9.5f);
        tile.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(Color.parseColor(accentHex));
        tvVal.setTextSize(13);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvVal.setSingleLine(true);
        tvVal.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tile.addView(tvVal);

        tile.setOnClickListener(onClick);
        TvUtil.applyTvFocusHighlight(tile, 12.0f);
        container.addView(tile);
    }

    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText(label, text);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "تم نسخ " + label + ": " + text, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    // ==========================================
    // 3. Main Content: Playlists Grid
    // ==========================================
    private void addMainContent() {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);

        recyclerView = new RecyclerView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.topMargin = (int)(62 * scale);
        params.leftMargin = (int)(20 * scale);
        params.rightMargin = (int)((isTv ? 310 : 270) * scale);
        params.bottomMargin = (int)(32 * scale);
        recyclerView.setLayoutParams(params);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PlaylistAdapter(this, playlists);
        recyclerView.setAdapter(adapter);

        rootLayout.addView(recyclerView);
    }

    // ==========================================
    // 4. Apple Footer: News Ticker & Version Badge
    // ==========================================
    private void addTicker() {
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout tickerContainer = new LinearLayout(this);
        tickerContainer.setOrientation(LinearLayout.HORIZONTAL);
        tickerContainer.setBackgroundColor(Color.parseColor("#E6080E1A"));
        tickerContainer.setGravity(Gravity.CENTER_VERTICAL);
        tickerContainer.setPadding((int)(12 * scale), 0, (int)(12 * scale), 0);

        // Apple Accent News Badge
        TextView badge = new TextView(this);
        badge.setText("  الأخبار  ");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(9.5f);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badge.setGravity(Gravity.CENTER);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#0A84FF"));
        badgeBg.setCornerRadius(6 * scale);
        badge.setBackground(badgeBg);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, (int)(16 * scale));
        badgeLp.rightMargin = (int)(8 * scale);
        badge.setLayoutParams(badgeLp);
        tickerContainer.addView(badge);

        MarqueeTextView tickerTv = new MarqueeTextView(this);
        tickerTv.setSingleLine(true);
        tickerTv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tickerTv.setMarqueeRepeatLimit(-1);
        tickerTv.setHorizontallyScrolling(true);
        tickerTv.setTextColor(Color.parseColor("#EBEBF5"));
        tickerTv.setTextSize(11);
        tickerTv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        tickerContainer.addView(tickerTv);

        TvUtil.setupDualLanguageTicker(this, badge, tickerTv);

        FrameLayout.LayoutParams tickerLp = new FrameLayout.LayoutParams(-1, (int)(22 * scale));
        tickerLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tickerContainer.setLayoutParams(tickerLp);
        rootLayout.addView(tickerContainer);
    }

    private void addVersionBadge() {
        float scale = getResources().getDisplayMetrics().density;
        TextView tvVersion = new TextView(this);
        tvVersion.setText("Blue+ Pro • v1.3");
        tvVersion.setTextColor(Color.parseColor("#66FFFFFF"));
        tvVersion.setTextSize(10.5f);
        tvVersion.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams verParams = new FrameLayout.LayoutParams(-2, -2);
        verParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        verParams.bottomMargin = (int)(26 * scale);
        verParams.rightMargin = (int)(24 * scale);
        tvVersion.setLayoutParams(verParams);
        rootLayout.addView(tvVersion);
    }

    // ==========================================
    // 5. Playlist Adapter & Apple Glass Cards
    // ==========================================
    private class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Map<String, Object>> data;
        private final Context ctx;

        public PlaylistAdapter(Context ctx, List<Map<String, Object>> data) {
            this.ctx = ctx;
            this.data = data;
        }

        @Override
        public int getItemViewType(int position) {
            return (position == 0) ? 0 : 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            float scale = ctx.getResources().getDisplayMetrics().density;

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, (int)(102 * scale));
            lp.rightMargin = (int)(10 * scale);
            lp.bottomMargin = (int)(12 * scale);
            card.setLayoutParams(lp);
            card.setPadding((int)(12 * scale), (int)(10 * scale), (int)(12 * scale), (int)(10 * scale));

            TvUtil.applyTvFocusHighlight(card, 18.0f);

            if (viewType == 0) {
                // Add Playlist Card (Dashed / Specular Accent Glass)
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color.parseColor("#150A84FF"));
                gd.setCornerRadius(18 * scale);
                gd.setStroke((int)(1.5f * scale), Color.parseColor("#4D0A84FF"));
                card.setBackground(gd);

                LinearLayout innerRow = new LinearLayout(ctx);
                innerRow.setOrientation(LinearLayout.HORIZONTAL);
                innerRow.setGravity(Gravity.CENTER_VERTICAL);
                innerRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

                // Plus Icon Squircle Tile
                FrameLayout plusTile = new FrameLayout(ctx);
                LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams((int)(46 * scale), (int)(46 * scale));
                ptLp.rightMargin = (int)(12 * scale);
                plusTile.setLayoutParams(ptLp);
                GradientDrawable ptBg = new GradientDrawable();
                ptBg.setCornerRadius(14 * scale);
                ptBg.setColor(Color.parseColor("#260A84FF"));
                ptBg.setStroke((int)(1.2f * scale), Color.parseColor("#800A84FF"));
                plusTile.setBackground(ptBg);

                TextView plus = new TextView(ctx);
                plus.setText("+");
                plus.setTextColor(Color.parseColor("#0A84FF"));
                plus.setTextSize(26);
                plus.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                FrameLayout.LayoutParams plusLp = new FrameLayout.LayoutParams(-2, -2);
                plusLp.gravity = Gravity.CENTER;
                plus.setLayoutParams(plusLp);
                plusTile.addView(plus);
                innerRow.addView(plusTile);

                LinearLayout textCol = new LinearLayout(ctx);
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView txt = new TextView(ctx);
                txt.setText(TvUtil.translate(ctx, "أضف قائمة التشغيل"));
                txt.setTextColor(Color.WHITE);
                txt.setTextSize(13.5f);
                txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                textCol.addView(txt);

                TextView sub = new TextView(ctx);
                sub.setText(TvUtil.translate(ctx, "كود جديد أو Xtream"));
                sub.setTextColor(Color.parseColor("#80FFFFFF"));
                sub.setTextSize(10);
                textCol.addView(sub);

                innerRow.addView(textCol);
                card.addView(innerRow);

                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ctx.startActivity(new Intent(ctx, LoginActivity.class));
                    }
                });
            } else {
                // Saved Playlist Card (Midnight Frosted Glass)
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color.parseColor("#E60D1526"));
                gd.setCornerRadius(18 * scale);
                gd.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF"));
                card.setBackground(gd);

                // Top row: Icon tile + Status capsule
                LinearLayout topRow = new LinearLayout(ctx);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                topRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

                // Icon Tile (Apple squircle)
                FrameLayout iconTile = new FrameLayout(ctx);
                LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams((int)(34 * scale), (int)(34 * scale));
                itLp.rightMargin = (int)(10 * scale);
                iconTile.setLayoutParams(itLp);

                GradientDrawable itBg = new GradientDrawable();
                itBg.setCornerRadius(10 * scale);
                itBg.setColor(Color.parseColor("#260A84FF"));
                itBg.setStroke((int)(1.2f * scale), Color.parseColor("#4D0A84FF"));
                iconTile.setBackground(itBg);

                ImageView iv = new ImageView(ctx);
                iv.setImageResource(R.drawable.picsart_26_05_20_21_48_52_267);
                FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams((int)(20 * scale), (int)(20 * scale));
                ivLp.gravity = Gravity.CENTER;
                iv.setLayoutParams(ivLp);
                iconTile.addView(iv);
                topRow.addView(iconTile);

                // Title & Host
                LinearLayout titleCol = new LinearLayout(ctx);
                titleCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                titleCol.setLayoutParams(tcLp);

                TextView title = new TextView(ctx);
                title.setTextColor(Color.WHITE);
                title.setTextSize(13);
                title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                titleCol.addView(title);

                TextView host = new TextView(ctx);
                host.setTextColor(Color.parseColor("#80FFFFFF"));
                host.setTextSize(9.5f);
                host.setSingleLine(true);
                host.setEllipsize(TextUtils.TruncateAt.END);
                titleCol.addView(host);

                topRow.addView(titleCol);

                // Active / VIP Capsule Badge
                TextView badge = new TextView(ctx);
                badge.setTextSize(9.5f);
                badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                badge.setPadding((int)(8 * scale), (int)(3 * scale), (int)(8 * scale), (int)(3 * scale));
                badge.setVisibility(View.GONE);
                topRow.addView(badge);

                card.addView(topRow);

                // Active Bottom Accent Line
                View line = new View(ctx);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, (int)(2.5f * scale));
                lineLp.topMargin = (int)(8 * scale);
                line.setLayoutParams(lineLp);
                line.setBackgroundColor(Color.parseColor("#0A84FF"));
                line.setVisibility(View.GONE);
                card.addView(line);
            }
            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 1) {
                LinearLayout card = (LinearLayout) holder.itemView;
                LinearLayout topRow = (LinearLayout) card.getChildAt(0);
                FrameLayout iconTile = (FrameLayout) topRow.getChildAt(0);
                LinearLayout titleCol = (LinearLayout) topRow.getChildAt(1);
                TextView title = (TextView) titleCol.getChildAt(0);
                TextView host = (TextView) titleCol.getChildAt(1);
                TextView badge = (TextView) topRow.getChildAt(2);
                View line = card.getChildAt(1);

                final Map<String, Object> item = data.get(position - 1);
                final int pos = position - 1;

                String nameStr = (String) item.get("name");
                title.setText(nameStr != null ? nameStr : "Playlist");

                host.setText(TvUtil.translate(ctx, "سيرفر سحابي مشفر"));

                // Check if active
                SharedPreferences spPlaylists = ctx.getSharedPreferences("Playlists", MODE_PRIVATE);
                String activeDns = spPlaylists.getString("active_dns", "");
                String activeUser = spPlaylists.getString("active_username", "");
                String activePass = spPlaylists.getString("active_password", "");

                boolean isActive = activeDns.equalsIgnoreCase((String)item.get("dns"))
                                && activeUser.equalsIgnoreCase((String)item.get("username"))
                                && activePass.equalsIgnoreCase((String)item.get("password"));

                float scale = ctx.getResources().getDisplayMetrics().density;
                String codeStr = (String) item.get("code");
                boolean isVip = codeStr != null && codeStr.equalsIgnoreCase("VIP");

                // Style Card Background
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(18 * scale);
                if (isVip) {
                    gd.setColor(Color.parseColor("#E6171520"));
                    gd.setStroke((int)(1.5f * scale), Color.parseColor("#FFD4AF37"));
                    title.setTextColor(Color.parseColor("#FFD4AF37"));
                } else if (isActive) {
                    gd.setColor(Color.parseColor("#E60D172E"));
                    gd.setStroke((int)(1.5f * scale), Color.parseColor("#4D0A84FF"));
                    title.setTextColor(Color.WHITE);
                } else {
                    gd.setColor(Color.parseColor("#E60D1526"));
                    gd.setStroke((int)(1.5f * scale), Color.parseColor("#2680B4FF"));
                    title.setTextColor(Color.WHITE);
                }
                card.setBackground(gd);

                // Badge handling
                if (isVip) {
                    badge.setVisibility(View.VISIBLE);
                    badge.setText("VIP");
                    badge.setTextColor(Color.parseColor("#FFD4AF37"));
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius(999 * scale);
                    badgeBg.setColor(Color.parseColor("#26D4AF37"));
                    badgeBg.setStroke((int)(1 * scale), Color.parseColor("#66D4AF37"));
                    badge.setBackground(badgeBg);
                } else if (isActive) {
                    badge.setVisibility(View.VISIBLE);
                    badge.setText("نشط");
                    badge.setTextColor(Color.parseColor("#30D158"));
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius(999 * scale);
                    badgeBg.setColor(Color.parseColor("#2630D158"));
                    badgeBg.setStroke((int)(1 * scale), Color.parseColor("#6630D158"));
                    badge.setBackground(badgeBg);
                } else {
                    badge.setVisibility(View.GONE);
                }

                // Bottom Active Line
                if (isActive) {
                    line.setVisibility(View.VISIBLE);
                    line.setBackgroundColor(isVip ? Color.parseColor("#FFD4AF37") : Color.parseColor("#0A84FF"));
                } else {
                    line.setVisibility(View.GONE);
                }

                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showOptionsDialog(item, pos);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return data.size() + 1;
        }
    }

    // ==========================================
    // 6. Apple Liquid Glass Modal Options Dialog
    // ==========================================
    private void showOptionsDialog(final Map<String, Object> item, final int position) {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.78f;
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        // Frosted Modal Sheet Container
        LinearLayout modal = new LinearLayout(this);
        modal.setOrientation(LinearLayout.VERTICAL);
        modal.setGravity(Gravity.CENTER_HORIZONTAL);

        int modalWidth = (int)((isTv ? 380 : 340) * scale);
        FrameLayout.LayoutParams modalParams = new FrameLayout.LayoutParams(modalWidth, -2);
        modalParams.gravity = Gravity.CENTER;
        modal.setLayoutParams(modalParams);

        GradientDrawable modalBg = new GradientDrawable();
        modalBg.setColor(Color.parseColor("#E60D1526")); // Deep midnight glass
        modalBg.setCornerRadius(22 * scale);
        modalBg.setStroke((int)(1.5f * scale), Color.parseColor("#3380B4FF")); // Specular reflection border
        modal.setBackground(modalBg);
        modal.setPadding((int)(20 * scale), (int)(20 * scale), (int)(20 * scale), (int)(20 * scale));

        // Header: Squircle Icon Tile + Name + DNS
        LinearLayout headBox = new LinearLayout(this);
        headBox.setOrientation(LinearLayout.HORIZONTAL);
        headBox.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hbLp = new LinearLayout.LayoutParams(-1, -2);
        hbLp.bottomMargin = (int)(14 * scale);
        headBox.setLayoutParams(hbLp);

        FrameLayout iconTile = new FrameLayout(this);
        LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams((int)(42 * scale), (int)(42 * scale));
        itLp.rightMargin = (int)(12 * scale);
        iconTile.setLayoutParams(itLp);
        GradientDrawable itBg = new GradientDrawable();
        itBg.setCornerRadius(12 * scale);
        itBg.setColor(Color.parseColor("#260A84FF"));
        itBg.setStroke((int)(1.2f * scale), Color.parseColor("#4D0A84FF"));
        iconTile.setBackground(itBg);

        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.picsart_26_05_20_21_48_52_267);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams((int)(24 * scale), (int)(24 * scale));
        ivLp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ivLp);
        iconTile.addView(iv);
        headBox.addView(iconTile);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText((String) item.get("name"));
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(tvName);

        TextView tvSec = new TextView(this);
        tvSec.setText(TvUtil.translate(this, "سيرفر سحابي مشفر وآمن"));
        tvSec.setTextColor(Color.parseColor("#80FFFFFF"));
        tvSec.setTextSize(10.5f);
        titleCol.addView(tvSec);

        headBox.addView(titleCol);
        modal.addView(headBox);

        // Subtle Divider
        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, (int)(1 * scale));
        divLp.bottomMargin = (int)(12 * scale);
        div.setLayoutParams(divLp);
        div.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        modal.addView(div);

        // 1. Primary Action: Connect Full
        TextView btnConnect = addDialogButton(modal, TvUtil.translate(this, "يتصل"), "#0A84FF", "#FFFFFF", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                movePlaylistToFirst(position);
                getSharedPreferences("Playlists", MODE_PRIVATE).edit().putBoolean("auto_login", true).apply();

                Intent intent = new Intent(Ot1Activity.this, WaitingActivity.class);
                intent.putExtra("dns", (String)item.get("dns"));
                intent.putExtra("username", (String)item.get("username"));
                intent.putExtra("password", (String)item.get("password"));
                intent.putExtra("code", (String)item.get("code"));
                startActivity(intent);
            }
        });

        // 2. Secondary Action: Live Stream Only
        addDialogButton(modal, TvUtil.translate(this, "اتصال (بث مباشر فقط)"), "#2640C8E0", "#40C8E0", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                movePlaylistToFirst(position);
                getSharedPreferences("Playlists", MODE_PRIVATE).edit().putBoolean("auto_login", true).apply();

                Intent intent = new Intent(Ot1Activity.this, WaitingActivity.class);
                intent.putExtra("dns", (String)item.get("dns"));
                intent.putExtra("username", (String)item.get("username"));
                intent.putExtra("password", (String)item.get("password"));
                intent.putExtra("code", (String)item.get("code"));
                intent.putExtra("only_live", true);
                startActivity(intent);
            }
        });

        // 3. Edit Playlist Action
        addDialogButton(modal, TvUtil.translate(this, "تعديل"), "#E60B101C", "#EBEBF5", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(Ot1Activity.this, TvUtil.translate(Ot1Activity.this, "التطبيق محمي"), Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Destructive Action: Delete Playlist
        addDialogButton(modal, TvUtil.translate(this, "حذف"), "#26FF453A", "#FF453A", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                deletePlaylist(position);
            }
        });

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        root.addView(modal);
        dialog.setContentView(root);
        dialog.show();

        if (TvUtil.isTvMode(this)) {
            btnConnect.requestFocus();
        }
    }

    private TextView addDialogButton(LinearLayout container, String text, String bgHex, String textHex, boolean isSolid, View.OnClickListener listener) {
        float scale = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(textHex));
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int)(44 * scale));
        lp.bottomMargin = (int)(9 * scale);
        btn.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(14 * scale);
        gd.setColor(Color.parseColor(bgHex));
        if (!isSolid) {
            gd.setStroke((int)(1.2f * scale), Color.parseColor("#3380B4FF"));
        }
        btn.setBackground(gd);

        btn.setOnClickListener(listener);
        TvUtil.applyTvFocusHighlight(btn, 14.0f);
        container.addView(btn);
        return btn;
    }

    // ==========================================
    // 7. Data Helpers & System Utilities
    // ==========================================
    private void deletePlaylist(int position) {
        if (position < 0 || position >= playlists.size()) return;
        playlists.remove(position);
        SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("list", new Gson().toJson(playlists));
        if (playlists.isEmpty()) {
            editor.putBoolean("auto_login", false);
        }
        editor.apply();
        adapter.notifyDataSetChanged();
        updateStatusCount();
        requestPlaylistFocus();
        Toast.makeText(this, "تم حذف القائمة بنجاح", Toast.LENGTH_SHORT).show();
    }

    private void movePlaylistToFirst(int position) {
        if (position <= 0 || position >= playlists.size()) return;
        Map<String, Object> item = playlists.remove(position);
        playlists.add(0, item);

        SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        sp.edit().putString("list", new Gson().toJson(playlists)).apply();
        adapter.notifyDataSetChanged();
    }

    private String getDeviceKey() {
        try {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null) return "136115";
            String cleanId = androidId.replaceAll("[^0-9]", "");
            if (cleanId.length() >= 6) return cleanId.substring(0, 6);
            return (androidId.hashCode() + "").substring(1, 7);
        } catch (Exception e) {
            return "136115";
        }
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
        } catch (Exception ex) {
            return "E1:AA:63:DE:99:AC:22";
        }
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
                    drawable.setGravity(Gravity.FILL);
                    rootLayout.setBackground(drawable);
                } else {
                    f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadCachedLogo(ImageView iv) {
        try {
            File f = new File(getFilesDir(), FILE_LOGO);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) iv.setImageBitmap(bmp);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }

    private static class MarqueeTextView extends TextView {
        public MarqueeTextView(Context context) {
            super(context);
        }
        @Override
        public boolean isFocused() {
            return true;
        }
    }
}
