package com.blue.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends Activity {

    private FrameLayout rootLayout;
    private static final String TAG = "SettingsActivity";

    // Apple iOS Liquid Glass UI Theme Constants
    private final String THEME_CARD_TOP = "#E60B101C";
    private final String THEME_CARD_BOTTOM = "#E60D1526";
    private final String STROKE_BLUE = "#2680B4FF";
    private final String ACCENT_BLUE = "#0A84FF";
    private final String ACCENT_CYAN = "#40C4FF";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception ignored) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Deep Atmospheric Midnight Canvas Overlay (#7305070B)
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#7305070B"));
        rootLayout.addView(overlay);

        buildUI();
        loadCachedBackground();
        setContentView(rootLayout);
    }

    private void buildUI() {
        float scale = getResources().getDisplayMetrics().density;

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        mainLayout.setPadding((int) (24 * scale), (int) (14 * scale), (int) (24 * scale), (int) (14 * scale));

        // ── Top Header ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.bottomMargin = (int) (10 * scale);
        header.setLayoutParams(headerLp);

        // Frosted Circular Back Button (Apple Liquid Glass)
        LinearLayout btnBack = new LinearLayout(this);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setClickable(true);
        btnBack.setFocusable(true);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (38 * scale), (int) (38 * scale));
        btnBack.setLayoutParams(backLp);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#E60B101C"));
        backBg.setStroke((int) (1.2f * scale), Color.parseColor(STROKE_BLUE));
        btnBack.setBackground(backBg);
        TvUtil.applyTvFocusHighlight(btnBack, 19.0f);

        ImageView ivBack = new ImageView(this);
        android.graphics.drawable.Drawable backIcon = getResources().getDrawable(android.R.drawable.ic_menu_revert);
        if (backIcon != null) {
            backIcon.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            ivBack.setImageDrawable(backIcon);
        }
        btnBack.addView(ivBack, new LinearLayout.LayoutParams((int) (20 * scale), (int) (20 * scale)));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        header.addView(btnBack);

        // Title and Subtitle Container
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "الإعدادات"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(19);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        titleCol.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(TvUtil.translate(this, "تخصيص المشغل والبث والحسابات"));
        tvSubtitle.setTextColor(Color.parseColor("#8A99AD"));
        tvSubtitle.setTextSize(10.5f);
        tvSubtitle.setGravity(Gravity.CENTER);
        titleCol.addView(tvSubtitle);

        header.addView(titleCol);

        // Header Right: Version Pill
        LinearLayout versionPill = new LinearLayout(this);
        versionPill.setOrientation(LinearLayout.HORIZONTAL);
        versionPill.setGravity(Gravity.CENTER);
        versionPill.setPadding((int) (12 * scale), (int) (6 * scale), (int) (12 * scale), (int) (6 * scale));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#140A84FF"));
        pillBg.setCornerRadius(12 * scale);
        pillBg.setStroke((int) (1 * scale), Color.parseColor("#3380B4FF"));
        versionPill.setBackground(pillBg);

        String verName = "v1.3";
        try {
            verName = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        TextView tvPill = new TextView(this);
        tvPill.setText("Blue+ Pro " + verName);
        tvPill.setTextColor(Color.parseColor(ACCENT_CYAN));
        tvPill.setTextSize(11);
        tvPill.setTypeface(null, Typeface.BOLD);
        versionPill.addView(tvPill);
        header.addView(versionPill);

        mainLayout.addView(header);

        // ── Settings ScrollView & Grid ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setLayoutParams(new ScrollView.LayoutParams(-1, -2));

        // Read real-time SharedPreferences values for dynamic subtitles
        SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean useExternal = sp.getBoolean("use_external", false);
        String streamFormat = sp.getString("stream_format", "auto");
        boolean autoPlayLive = sp.getBoolean("auto_play_live", true);
        String subtitleSize = sp.getString("subtitle_size", "medium");
        String timeFormat = sp.getString("time_format", "12");
        int deviceMode = sp.getInt("device_mode", 0);
        String appLang = sp.getString("app_language", "auto");
        boolean parentalActive = sp.getBoolean("parental_active", false);

        String subSizeLabel = "متوسط";
        if ("small".equals(subtitleSize)) subSizeLabel = "صغير";
        else if ("large".equals(subtitleSize)) subSizeLabel = "كبير";

        String langLabel = "تلقائي";
        if ("ar".equals(appLang)) langLabel = "العربية";
        else if ("en".equals(appLang)) langLabel = "English";

        // Settings items: Exactly 20 items forming 5 balanced rows of 4
        ArrayList<SettingItem> items = new ArrayList<>();

        // ── Row 1: Playlists & Player Setup ──
        items.add(new SettingItem("أضف قائمة التشغيل", "Xtream / M3U", R.drawable.ic_settings_playlist, new View.OnClickListener() {
            @Override public void onClick(View v) { addPlaylistAction(); }
        }));
        items.add(new SettingItem("تغيير قائمة التشغيل", "التبديل بين القوائم", R.drawable.ic_settings_playlist, new View.OnClickListener() {
            @Override public void onClick(View v) { changePlaylistAction(); }
        }));
        items.add(new SettingItem("اختيار المشغل الافتراضي", useExternal ? "مشغل خارجي" : "المشغل المدمج", R.drawable.ic_settings_player, new View.OnClickListener() {
            @Override public void onClick(View v) { changePlayerAction(); }
        }));
        items.add(new SettingItem("تنسيق البث المباشر", "الصيغة: " + streamFormat.toUpperCase(), R.drawable.ic_settings_movie, new View.OnClickListener() {
            @Override public void onClick(View v) { liveStreamFormatAction(); }
        }));

        // ── Row 2: Category Management ──
        items.add(new SettingItem("إخفاء باقات القنوات", "فلترة باقات Live", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("live"); }
        }));
        items.add(new SettingItem("إخفاء باقات الأفلام", "فلترة باقات VOD", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("vod"); }
        }));
        items.add(new SettingItem("إخفاء باقات المسلسلات", "فلترة باقات Series", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("series"); }
        }));
        items.add(new SettingItem("رمز المراقبة الأبوية", "تعيين أو تغيير الرمز", R.drawable.ic_settings_lock, new View.OnClickListener() {
            @Override public void onClick(View v) { parentalControlAction(); }
        }));

        // ── Row 3: History & Security Controls ──
        items.add(new SettingItem("مسح سجل القنوات", "تفريغ ذاكرة المشاهدة", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("live"); }
        }));
        items.add(new SettingItem("مسح سجل الأفلام", "مسح المشاهدات السابقة", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("movies"); }
        }));
        items.add(new SettingItem("مسح سجل المسلسلات", "مسح الحلقات المشاهدة", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("series"); }
        }));
        items.add(new SettingItem("قفل المراقبة الأبوية", parentalActive ? "الحماية مفعلة" : "الحماية معطلة", R.drawable.ic_settings_lock, new View.OnClickListener() {
            @Override public void onClick(View v) { parentalToggleAction(); }
        }));

        // ── Row 4: Playback Preferences ──
        items.add(new SettingItem("التشغيل التلقائي للبث", autoPlayLive ? "تشغيل فوري عند الفتح" : "يدوي عند الاختيار", R.drawable.ic_settings_sync, new View.OnClickListener() {
            @Override public void onClick(View v) { autoStartAction(); }
        }));
        items.add(new SettingItem("إعدادات الترجمة", "حجم الخط: " + subSizeLabel, R.drawable.ic_settings_subtitle, new View.OnClickListener() {
            @Override public void onClick(View v) { subtitleSettingsAction(); }
        }));
        items.add(new SettingItem("تنسيق الوقت", timeFormat + " ساعة", R.drawable.ic_settings_clock, new View.OnClickListener() {
            @Override public void onClick(View v) { timeFormatAction(); }
        }));
        items.add(new SettingItem("نمط الجهاز", deviceMode == 1 ? "Android TV" : "الهاتف المحمول", R.drawable.ic_settings_device, new View.OnClickListener() {
            @Override public void onClick(View v) { selectDeviceTypeAction(); }
        }));

        // ── Row 5: System & Application Info ──
        items.add(new SettingItem("لغة التطبيق", langLabel, R.drawable.ic_settings_language, new View.OnClickListener() {
            @Override public void onClick(View v) { appLanguageAction(); }
        }));
        items.add(new SettingItem("التحقق من التحديثات", "فحص السيرفر السحابي", R.drawable.ic_settings_update, new View.OnClickListener() {
            @Override public void onClick(View v) { updateNowAction(); }
        }));
        items.add(new SettingItem("حول التطبيق والدعم", "Blue+ Pro " + verName, R.drawable.ic_settings_white, new View.OnClickListener() {
            @Override public void onClick(View v) { showAboutDialog(); }
        }));
        items.add(new SettingItem("خروج من الإعدادات", "العودة للشاشة الرئيسية", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }));

        // Construct 5 rows of 4 cards
        LinearLayout currentRow = null;
        View firstSettingCard = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % 4 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.topMargin = (int) (5 * scale);
                rowLp.bottomMargin = (int) (5 * scale);
                currentRow.setLayoutParams(rowLp);
                gridContainer.addView(currentRow);
            }

            View card = createSettingCard(items.get(i), scale);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, (int) (54 * scale), 1.0f);
            cardLp.leftMargin = (int) (5 * scale);
            cardLp.rightMargin = (int) (5 * scale);
            card.setLayoutParams(cardLp);

            TvUtil.applyTvFocusHighlight(card, 14.0f);

            if (i == 0) {
                firstSettingCard = card;
            }

            if (currentRow != null) {
                currentRow.addView(card);
            }
        }

        // ── Footer Device Info Capsule ──
        LinearLayout footerContainer = new LinearLayout(this);
        footerContainer.setOrientation(LinearLayout.HORIZONTAL);
        footerContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = (int) (16 * scale);
        footerLp.bottomMargin = (int) (8 * scale);
        footerContainer.setLayoutParams(footerLp);

        LinearLayout footerPill = new LinearLayout(this);
        footerPill.setOrientation(LinearLayout.HORIZONTAL);
        footerPill.setGravity(Gravity.CENTER_VERTICAL);
        footerPill.setPadding((int) (20 * scale), (int) (8 * scale), (int) (20 * scale), (int) (8 * scale));
        GradientDrawable footerBg = new GradientDrawable();
        footerBg.setColor(Color.parseColor("#B30B101C"));
        footerBg.setCornerRadius(14 * scale);
        footerBg.setStroke((int) (1.2f * scale), Color.parseColor(STROKE_BLUE));
        footerPill.setBackground(footerBg);

        TextView tvMac = new TextView(this);
        tvMac.setText("MAC: " + getMacAddress().toLowerCase());
        tvMac.setTextColor(Color.parseColor("#8A99AD"));
        tvMac.setTextSize(12);
        tvMac.setTypeface(null, Typeface.BOLD);
        footerPill.addView(tvMac);

        View separator = new View(this);
        separator.setBackgroundColor(Color.parseColor("#3380B4FF"));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams((int) (1 * scale), (int) (14 * scale));
        sepLp.leftMargin = (int) (16 * scale);
        sepLp.rightMargin = (int) (16 * scale);
        separator.setLayoutParams(sepLp);
        footerPill.addView(separator);

        TextView tvDeviceKey = new TextView(this);
        tvDeviceKey.setText("Device Key: " + getDeviceKey());
        tvDeviceKey.setTextColor(Color.parseColor("#8A99AD"));
        tvDeviceKey.setTextSize(12);
        tvDeviceKey.setTypeface(null, Typeface.BOLD);
        footerPill.addView(tvDeviceKey);

        footerContainer.addView(footerPill);
        gridContainer.addView(footerContainer);

        scrollView.addView(gridContainer);
        mainLayout.addView(scrollView);
        rootLayout.addView(mainLayout);

        if (firstSettingCard != null) {
            final View finalCard = firstSettingCard;
            finalCard.post(new Runnable() {
                @Override
                public void run() {
                    finalCard.requestFocus();
                }
            });
        }
    }

    private View createSettingCard(final SettingItem item, float scale) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (10 * scale), 0, (int) (12 * scale), 0);
        card.setClickable(true);
        card.setFocusable(true);

        // Apple Liquid Glass card background
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(THEME_CARD_TOP), Color.parseColor(THEME_CARD_BOTTOM)}
        );
        gd.setCornerRadius(14 * scale);
        gd.setStroke((int) (1.2f * scale), Color.parseColor(STROKE_BLUE));
        card.setBackground(gd);

        // Icon circular badge
        FrameLayout iconBadge = new FrameLayout(this);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams((int) (34 * scale), (int) (34 * scale));
        badgeLp.rightMargin = (int) (10 * scale);
        iconBadge.setLayoutParams(badgeLp);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.parseColor("#140A84FF"));
        badgeBg.setStroke((int) (1 * scale), Color.parseColor("#2680B4FF"));
        iconBadge.setBackground(badgeBg);

        ImageView ivIcon = new ImageView(this);
        android.graphics.drawable.Drawable drawable = getResources().getDrawable(item.iconRes);
        if (drawable != null) {
            drawable.setColorFilter(Color.parseColor(ACCENT_BLUE), android.graphics.PorterDuff.Mode.SRC_IN);
            ivIcon.setImageDrawable(drawable);
        }
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams((int) (18 * scale), (int) (18 * scale));
        iconLp.gravity = Gravity.CENTER;
        ivIcon.setLayoutParams(iconLp);
        iconBadge.addView(ivIcon);
        card.addView(iconBadge);

        // Texts column (Title + Subtitle)
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        textCol.setLayoutParams(colLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, item.title));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(11.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(tvTitle);

        if (item.subtitle != null && !item.subtitle.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(TvUtil.translate(this, item.subtitle));
            tvSub.setTextColor(Color.parseColor("#8A99AD"));
            tvSub.setTextSize(9.5f);
            tvSub.setSingleLine(true);
            tvSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvSub.setPadding(0, (int) (1.5f * scale), 0, 0);
            textCol.addView(tvSub);
        }
        card.addView(textCol);

        // Subtle indicator arrow
        TextView tvArrow = new TextView(this);
        boolean isAr = "ar".equals(TvUtil.getAppLanguage(this));
        tvArrow.setText(isAr ? "‹" : "›");
        tvArrow.setTextColor(Color.parseColor("#33FFFFFF"));
        tvArrow.setTextSize(14);
        tvArrow.setGravity(Gravity.CENTER);
        tvArrow.setPadding((int) (4 * scale), 0, (int) (2 * scale), 0);
        card.addView(tvArrow);

        card.setOnClickListener(item.listener);
        return card;
    }

    // ── Apple iOS Liquid Glass Dialog Helper ──

    private void showPremiumDialog(String title, View contentView, String positiveText, final Runnable positiveAction, String negativeText, final Runnable negativeAction) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int) (22 * scale), (int) (18 * scale), (int) (22 * scale), (int) (18 * scale));

        // Frosted midnight glass gradient with subtle blue glow
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#F20B101C"), Color.parseColor("#F20D1526")}
        );
        gd.setCornerRadius(20 * scale);
        gd.setStroke((int) (1.5f * scale), Color.parseColor("#400A84FF"));
        container.setBackground(gd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, title));
        tvTitle.setTextColor(Color.parseColor(ACCENT_CYAN));
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = (int) (14 * scale);
        tvTitle.setLayoutParams(titleLp);
        container.addView(tvTitle);

        if (contentView != null) {
            LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
            contentLp.bottomMargin = (int) (18 * scale);
            contentView.setLayoutParams(contentLp);
            container.addView(contentView);
        }

        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER);

        if (positiveText != null) {
            TextView btnPos = new TextView(this);
            btnPos.setText(TvUtil.translate(this, positiveText));
            btnPos.setTextColor(Color.WHITE);
            btnPos.setTextSize(13);
            btnPos.setTypeface(null, Typeface.BOLD);
            btnPos.setGravity(Gravity.CENTER);
            btnPos.setPadding((int) (16 * scale), (int) (9 * scale), (int) (16 * scale), (int) (9 * scale));
            btnPos.setFocusable(true);
            btnPos.setClickable(true);

            GradientDrawable posBg = new GradientDrawable();
            posBg.setColor(Color.parseColor("#E60A84FF"));
            posBg.setCornerRadius(12 * scale);
            posBg.setStroke((int) (1.2f * scale), Color.parseColor("#40C4FF"));
            btnPos.setBackground(posBg);
            TvUtil.applyTvFocusHighlight(btnPos, 12.0f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.rightMargin = (int) (6 * scale);
            btnPos.setLayoutParams(lp);
            btnPos.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (positiveAction != null) positiveAction.run();
                }
            });
            buttonsRow.addView(btnPos);
        }

        if (negativeText != null) {
            TextView btnNeg = new TextView(this);
            btnNeg.setText(TvUtil.translate(this, negativeText));
            btnNeg.setTextColor(Color.parseColor("#B0BEC5"));
            btnNeg.setTextSize(13);
            btnNeg.setTypeface(null, Typeface.BOLD);
            btnNeg.setGravity(Gravity.CENTER);
            btnNeg.setPadding((int) (16 * scale), (int) (9 * scale), (int) (16 * scale), (int) (9 * scale));
            btnNeg.setFocusable(true);
            btnNeg.setClickable(true);

            GradientDrawable negBg = new GradientDrawable();
            negBg.setColor(Color.parseColor("#1AFFFFFF"));
            negBg.setCornerRadius(12 * scale);
            negBg.setStroke((int) (1.2f * scale), Color.parseColor("#26FFFFFF"));
            btnNeg.setBackground(negBg);
            TvUtil.applyTvFocusHighlight(btnNeg, 12.0f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.leftMargin = (int) (6 * scale);
            btnNeg.setLayoutParams(lp);
            btnNeg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (negativeAction != null) negativeAction.run();
                }
            });
            buttonsRow.addView(btnNeg);
        }

        container.addView(buttonsRow);
        dialog.setContentView(container);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = Math.min((int) (420 * scale), (int) (screenWidth * 0.85f));
            dialog.getWindow().setLayout(dialogWidth, -2);
        }
        dialog.show();
    }

    private TextView createMessageTextView(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.parseColor("#CFD8DC"));
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(0, 1.25f);
        return tv;
    }

    // ── Button Actions ──

    private void addPlaylistAction() {
        startActivity(new Intent(this, LoginActivity.class));
    }

    private void changePlaylistAction() {
        Intent intent = new Intent(this, Ot1Activity.class);
        startActivity(intent);
        finish();
    }

    private void verifyPinThenAction(String title, final Runnable successAction) {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String pin = sp.getString("parental_pin", "");
        if (pin.isEmpty()) {
            if (successAction != null) successAction.run();
            return;
        }

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(TvUtil.translate(this, "أدخل رمز المراقبة الأبوية الحالي لتأكيد العملية:"));
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setPadding(0, 0, 0, (int) (8 * scale));
        container.addView(label);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(TvUtil.translate(this, "الرمز الحالي"));
        input.setHintTextColor(Color.parseColor("#88FFFFFF"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(14);
        input.setGravity(Gravity.CENTER);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#1AFFFFFF"));
        inputBg.setCornerRadius(8 * scale);
        inputBg.setStroke((int) (1 * scale), Color.parseColor("#33FFFFFF"));
        input.setBackground(inputBg);
        input.setPadding((int) (12 * scale), (int) (8 * scale), (int) (12 * scale), (int) (8 * scale));
        container.addView(input);

        showPremiumDialog(title, container, TvUtil.translate(this, "تأكيد"), new Runnable() {
            @Override
            public void run() {
                if (input.getText().toString().equals(pin)) {
                    if (successAction != null) successAction.run();
                } else {
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "الرمز السري غير صحيح!"), Toast.LENGTH_SHORT).show();
                }
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void parentalControlAction() {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String pin = sp.getString("parental_pin", "");

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final EditText etCurrentPin;
        if (!pin.isEmpty()) {
            TextView labelCurrent = new TextView(this);
            labelCurrent.setText(TvUtil.translate(this, "الرمز السري الحالي:"));
            labelCurrent.setTextColor(Color.WHITE);
            labelCurrent.setTextSize(13);
            labelCurrent.setPadding(0, 0, 0, (int) (4 * scale));
            container.addView(labelCurrent);

            etCurrentPin = new EditText(this);
            etCurrentPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            etCurrentPin.setHint(TvUtil.translate(this, "الرمز الحالي"));
            etCurrentPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
            etCurrentPin.setTextColor(Color.WHITE);
            etCurrentPin.setTextSize(14);
            etCurrentPin.setGravity(Gravity.CENTER);

            GradientDrawable currentBg = new GradientDrawable();
            currentBg.setColor(Color.parseColor("#1AFFFFFF"));
            currentBg.setCornerRadius(8 * scale);
            currentBg.setStroke((int) (1 * scale), Color.parseColor("#33FFFFFF"));
            etCurrentPin.setBackground(currentBg);
            etCurrentPin.setPadding((int) (12 * scale), (int) (8 * scale), (int) (12 * scale), (int) (8 * scale));
            container.addView(etCurrentPin);

            View space = new View(this);
            space.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (8 * scale)));
            container.addView(space);
        } else {
            etCurrentPin = null;
        }

        TextView labelNew = new TextView(this);
        labelNew.setText(TvUtil.translate(this, "الرمز السري الجديد (4 أرقام):"));
        labelNew.setTextColor(Color.WHITE);
        labelNew.setTextSize(13);
        labelNew.setPadding(0, 0, 0, (int) (4 * scale));
        container.addView(labelNew);

        final EditText etNewPin = new EditText(this);
        etNewPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNewPin.setHint(TvUtil.translate(this, "الرمز الجديد"));
        etNewPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
        etNewPin.setTextColor(Color.WHITE);
        etNewPin.setTextSize(14);
        etNewPin.setGravity(Gravity.CENTER);

        GradientDrawable newBg = new GradientDrawable();
        newBg.setColor(Color.parseColor("#1AFFFFFF"));
        newBg.setCornerRadius(8 * scale);
        newBg.setStroke((int) (1 * scale), Color.parseColor("#33FFFFFF"));
        etNewPin.setBackground(newBg);
        etNewPin.setPadding((int) (12 * scale), (int) (8 * scale), (int) (12 * scale), (int) (8 * scale));
        container.addView(etNewPin);

        View space2 = new View(this);
        space2.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (8 * scale)));
        container.addView(space2);

        TextView labelConfirm = new TextView(this);
        labelConfirm.setText(TvUtil.translate(this, "تأكيد الرمز السري الجديد:"));
        labelConfirm.setTextColor(Color.WHITE);
        labelConfirm.setTextSize(13);
        labelConfirm.setPadding(0, 0, 0, (int) (4 * scale));
        container.addView(labelConfirm);

        final EditText etConfirmPin = new EditText(this);
        etConfirmPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etConfirmPin.setHint(TvUtil.translate(this, "تأكيد الرمز"));
        etConfirmPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
        etConfirmPin.setTextColor(Color.WHITE);
        etConfirmPin.setTextSize(14);
        etConfirmPin.setGravity(Gravity.CENTER);

        GradientDrawable confirmBg = new GradientDrawable();
        confirmBg.setColor(Color.parseColor("#1AFFFFFF"));
        confirmBg.setCornerRadius(8 * scale);
        confirmBg.setStroke((int) (1 * scale), Color.parseColor("#33FFFFFF"));
        etConfirmPin.setBackground(confirmBg);
        etConfirmPin.setPadding((int) (12 * scale), (int) (8 * scale), (int) (12 * scale), (int) (8 * scale));
        container.addView(etConfirmPin);

        showPremiumDialog(TvUtil.translate(this, "مراقبة أبوية"), container, TvUtil.translate(this, "حفظ"), new Runnable() {
            @Override
            public void run() {
                if (etCurrentPin != null) {
                    String currentInput = etCurrentPin.getText().toString();
                    if (!currentInput.equals(pin)) {
                        Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "الرمز السري الحالي غير صحيح!"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String newPin = etNewPin.getText().toString();
                String confirmPin = etConfirmPin.getText().toString();

                if (newPin.length() != 4) {
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "الرمز السري الجديد يجب أن يتكون من 4 أرقام!"), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPin.equals(confirmPin)) {
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "رمز التأكيد غير متطابق مع الرمز الجديد!"), Toast.LENGTH_SHORT).show();
                    return;
                }

                sp.edit()
                        .putString("parental_pin", newPin)
                        .putBoolean("parental_active", true)
                        .apply();

                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم تعيين وتفعيل رمز المراقبة الأبوية بنجاح!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void parentalToggleAction() {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String pin = sp.getString("parental_pin", "");

        if (pin.isEmpty()) {
            Toast.makeText(this, TvUtil.translate(this, "يرجى تعيين رمز مراقبة أبوية أولاً لتفعيل الخدمة!"), Toast.LENGTH_LONG).show();
            parentalControlAction();
            return;
        }

        final boolean active = sp.getBoolean("parental_active", true);
        String title = active ? "تعطيل الحماية الأبوية" : "تفعيل الحماية الأبوية";
        verifyPinThenAction(title, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean("parental_active", !active).apply();
                Toast.makeText(SettingsActivity.this, !active ? TvUtil.translate(SettingsActivity.this, "تم تفعيل المراقبة الأبوية!") : TvUtil.translate(SettingsActivity.this, "تم إلغاء تفعيل المراقبة الأبوية!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
    }

    private void changePlayerAction() {
        final String[] items = {
                TvUtil.translate(this, "المشغل المدمج الأساسي Blue + Player (تلقائي)"),
                TvUtil.translate(this, "المشغل الخارجي لجميع صيغ البث (VLC / MX Player)")
        };
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean current = sp.getBoolean("use_external", false);

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if ((i == 0 && !current) || (i == 1 && current)) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "اختيار المشغل الافتراضي"), container, TvUtil.translate(this, "تطبيق المشغل"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putBoolean("use_external", selectedId == 1).apply();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم ضبط المشغل الافتراضي بنجاح!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void liveStreamFormatAction() {
        final String[] items = {
                TvUtil.translate(this, "تلقائي (مستحسن)"),
                TvUtil.translate(this, "MPEG-TS (.ts)"),
                TvUtil.translate(this, "HLS (.m3u8)")
        };
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        String current = sp.getString("stream_format", "auto");

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if ((i == 0 && current.equals("auto")) || (i == 1 && current.equals("ts")) || (i == 2 && current.equals("m3u8"))) {
                rb.setChecked(true);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "تنسيق البث المباشر"), container, TvUtil.translate(this, "حفظ وتطبيق"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                String val = "auto";
                String name = "تلقائي";
                if (selectedId == 1) {
                    val = "ts";
                    name = "MPEG-TS";
                } else if (selectedId == 2) {
                    val = "m3u8";
                    name = "m3u8";
                }
                sp.edit().putString("stream_format", val).apply();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم تعيين تنسيق البث بنجاح إلى: ") + name, Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void autoStartAction() {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean current = sp.getBoolean("auto_play_live", true);
        final String[] items = {
                TvUtil.translate(this, "تشغيل فوري للقناة عند الدخول للبث (افتراضي)"),
                TvUtil.translate(this, "عدم تشغيل البث تلقائياً (يدوياً عند الاختيار)")
        };

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if ((i == 0 && current) || (i == 1 && !current)) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "التشغيل التلقائي للبث"), container, TvUtil.translate(this, "حفظ"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putBoolean("auto_play_live", selectedId == 0).apply();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم حفظ إعدادات التشغيل التلقائي بنجاح!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void subtitleSettingsAction() {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        String current = sp.getString("subtitle_size", "medium");
        final String[] sizes = {
                TvUtil.translate(this, "حجم خط صغير (14sp)"),
                TvUtil.translate(this, "حجم خط متوسط (18sp - موصى به)"),
                TvUtil.translate(this, "حجم خط كبير جداً (24sp)")
        };
        final String[] keys = {"small", "medium", "large"};

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < sizes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(sizes[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if (keys[i].equals(current)) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "إعدادات الترجمة"), container, TvUtil.translate(this, "حفظ"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                if (selectedId >= 0 && selectedId < keys.length) {
                    sp.edit().putString("subtitle_size", keys[selectedId]).apply();
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم حفظ إعدادات وحجم خط الترجمة بنجاح!"), Toast.LENGTH_SHORT).show();
                    recreate();
                }
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void timeFormatAction() {
        final String[] items = {
                TvUtil.translate(this, "تنسيق 12 ساعة (ص/م)"),
                TvUtil.translate(this, "تنسيق 24 ساعة")
        };
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        String current = sp.getString("time_format", "12");
        int activeId = current.equals("24") ? 1 : 0;

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if (i == activeId) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "تنسيق الوقت"), container, TvUtil.translate(this, "حفظ"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                String val = (selectedId == 1) ? "24" : "12";
                sp.edit().putString("time_format", val).apply();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم تحديث نمط عرض الوقت بنجاح!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void selectDeviceTypeAction() {
        final String[] items = {
                TvUtil.translate(this, "الهاتف المحمول / تابلت (Mobile Mode)"),
                TvUtil.translate(this, "شاشات وأجهزة تلفاز (Android TV Mode)")
        };
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        int current = sp.getInt("device_mode", 0);

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if (i == current) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "نمط الجهاز"), container, TvUtil.translate(this, "تأكيد الحفظ"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putInt("device_mode", selectedId).apply();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم ضبط إعدادات توافق الشاشات بنجاح!"), Toast.LENGTH_SHORT).show();
                recreate();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void appLanguageAction() {
        final String[] items = {
                TvUtil.translate(this, "Auto Detect"),
                TvUtil.translate(this, "Arabic"),
                TvUtil.translate(this, "English")
        };
        final String[] values = {"auto", "ar", "en"};
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        String current = sp.getString("app_language", "auto");
        int currentIdx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                currentIdx = i;
                break;
            }
        }

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        final RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(items[i]);
            rb.setTextColor(Color.WHITE);
            rb.setTextSize(13);
            rb.setId(i);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }
            if (i == currentIdx) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "لغة التطبيق"), container, TvUtil.translate(this, "حفظ"), new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                if (selectedId >= 0 && selectedId < values.length) {
                    sp.edit().putString("app_language", values[selectedId]).apply();
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم حفظ لغة التطبيق..."), Toast.LENGTH_SHORT).show();
                    recreate();
                }
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    private void hideCategoriesAction(final String type) {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean active = sp.getBoolean("parental_active", false);
        String pin = sp.getString("parental_pin", "");

        if (active && !pin.isEmpty()) {
            verifyPinThenAction(TvUtil.translate(this, "تأكيد الرمز السري"), new Runnable() {
                @Override
                public void run() {
                    showHideCategoriesDialog(type);
                }
            });
        } else {
            showHideCategoriesDialog(type);
        }
    }

    private void showHideCategoriesDialog(final String type) {
        final List<String> cats = getLoadedCategories(type);
        if (cats.isEmpty()) {
            showPremiumDialog(TvUtil.translate(this, "إخفاء باقات المحتوى"), createMessageTextView(TvUtil.translate(this, "لا توجد فئات محملة حالياً. يرجى تصفح القنوات أولاً لتنشيط البيانات!")), TvUtil.translate(this, "موافق"), null, null, null);
            return;
        }

        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String prefPrefix = type.equals("live") ? "hide_live_cat_" : (type.equals("vod") ? "hide_movies_cat_" : "hide_series_cat_");

        float scale = getResources().getDisplayMetrics().density;

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        TextView tvIntro = new TextView(this);
        tvIntro.setText(TvUtil.translate(this, "ضع علامة صح (✓) بجانب الباقات التي ترغب في إخفائها من العرض:"));
        tvIntro.setTextColor(Color.parseColor("#B0BEC5"));
        tvIntro.setTextSize(12);
        tvIntro.setPadding(0, 0, 0, (int) (8 * scale));
        boolean isAr = "ar".equals(TvUtil.getAppLanguage(this));
        tvIntro.setGravity(isAr ? Gravity.RIGHT : Gravity.LEFT);
        contentLayout.addView(tvIntro);

        // Select All / Deselect All Row
        LinearLayout selectRow = new LinearLayout(this);
        selectRow.setOrientation(LinearLayout.HORIZONTAL);
        selectRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectRowLp = new LinearLayout.LayoutParams(-1, -2);
        selectRowLp.bottomMargin = (int) (8 * scale);
        selectRow.setLayoutParams(selectRowLp);

        final List<CheckBox> checkBoxes = new ArrayList<>();

        TextView btnSelectAll = new TextView(this);
        btnSelectAll.setText(TvUtil.translate(this, "تحديد الكل (إخفاء الكل)"));
        btnSelectAll.setTextColor(Color.parseColor(ACCENT_CYAN));
        btnSelectAll.setTextSize(11);
        btnSelectAll.setTypeface(null, Typeface.BOLD);
        btnSelectAll.setGravity(Gravity.CENTER);
        btnSelectAll.setPadding((int) (8 * scale), (int) (6 * scale), (int) (8 * scale), (int) (6 * scale));
        GradientDrawable selectBg = new GradientDrawable();
        selectBg.setColor(Color.parseColor("#1A4FC3F7"));
        selectBg.setCornerRadius(8 * scale);
        selectBg.setStroke((int) (1 * scale), Color.parseColor("#334FC3F7"));
        btnSelectAll.setBackground(selectBg);
        LinearLayout.LayoutParams selLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        selLp.rightMargin = (int) (4 * scale);
        btnSelectAll.setLayoutParams(selLp);
        btnSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (CheckBox cb : checkBoxes) cb.setChecked(true);
            }
        });
        selectRow.addView(btnSelectAll);

        TextView btnDeselectAll = new TextView(this);
        btnDeselectAll.setText(TvUtil.translate(this, "إلغاء التحديد (إظهار الكل)"));
        btnDeselectAll.setTextColor(Color.WHITE);
        btnDeselectAll.setTextSize(11);
        btnDeselectAll.setTypeface(null, Typeface.BOLD);
        btnDeselectAll.setGravity(Gravity.CENTER);
        btnDeselectAll.setPadding((int) (8 * scale), (int) (6 * scale), (int) (8 * scale), (int) (6 * scale));
        GradientDrawable deselectBg = new GradientDrawable();
        deselectBg.setColor(Color.parseColor("#1AFFFFFF"));
        deselectBg.setCornerRadius(8 * scale);
        deselectBg.setStroke((int) (1 * scale), Color.parseColor("#33FFFFFF"));
        btnDeselectAll.setBackground(deselectBg);
        LinearLayout.LayoutParams deselLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        deselLp.leftMargin = (int) (4 * scale);
        btnDeselectAll.setLayoutParams(deselLp);
        btnDeselectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (CheckBox cb : checkBoxes) cb.setChecked(false);
            }
        });
        selectRow.addView(btnDeselectAll);

        contentLayout.addView(selectRow);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (180 * scale)));
        sv.setVerticalScrollBarEnabled(true);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding((int) (8 * scale), 0, (int) (8 * scale), 0);

        for (final String cat : cats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cat);
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(13);
            cb.setChecked(sp.getBoolean(prefPrefix + cat, false));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT_BLUE)));
            }

            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, -2);
            cbLp.bottomMargin = (int) (6 * scale);
            cb.setLayoutParams(cbLp);

            listLayout.addView(cb);
            checkBoxes.add(cb);
        }
        sv.addView(listLayout);
        contentLayout.addView(sv);

        String dialogTitle = type.equals("live") ? "إخفاء باقات القنوات" : (type.equals("vod") ? "إخفاء باقات الأفلام" : "إخفاء باقات المسلسلات");

        showPremiumDialog(
                dialogTitle,
                contentLayout,
                TvUtil.translate(this, "تطبيق وإخفاء الباقات"),
                new Runnable() {
                    @Override
                    public void run() {
                        SharedPreferences.Editor editor = sp.edit();
                        int hiddenCount = 0;
                        for (int i = 0; i < cats.size(); i++) {
                            String cat = cats.get(i);
                            boolean isHidden = checkBoxes.get(i).isChecked();
                            editor.putBoolean(prefPrefix + cat, isHidden);
                            if (isHidden) hiddenCount++;
                        }
                        editor.apply();
                        Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم تطبيق التعديلات بنجاح!"), Toast.LENGTH_SHORT).show();
                    }
                },
                TvUtil.translate(this, "إلغاء"),
                null
        );
    }

    private void clearHistoryAction(final String type) {
        String typeLabel = type.equals("live") ? "القنوات" : (type.equals("movies") ? "الأفلام" : "المسلسلات");
        TextView msg = createMessageTextView(TvUtil.translate(this, "هل أنت متأكد من مسح سجل المشاهدة الخاص بـ (" + typeLabel + ") بالكامل؟ لا يمكن التراجع عن هذه الخطوة."));
        showPremiumDialog(TvUtil.translate(this, "مسح سجل المشاهدة"), msg, TvUtil.translate(this, "نعم، مسح السجل"), new Runnable() {
            @Override
            public void run() {
                if (type.equals("movies")) {
                    getSharedPreferences("SeriesHistory", MODE_PRIVATE).edit().putString("recent_movies", "").apply();
                } else if (type.equals("series")) {
                    getSharedPreferences("SeriesHistory", MODE_PRIVATE).edit().putString("recent_series", "").apply();
                } else if (type.equals("live")) {
                    getSharedPreferences("LivePrefs", MODE_PRIVATE).edit().putString("recent_names", "[]").apply();
                }
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تم مسح سجل المشاهدة بالكامل وتصفير الذاكرة التخزينية!"), Toast.LENGTH_SHORT).show();
            }
        }, TvUtil.translate(this, "إلغاء"), null);
    }

    // ── Real Cloud Update Action ──

    private void updateNowAction() {
        final ProgressDialog pd = new ProgressDialog(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        pd.setMessage(TvUtil.translate(this, "جاري التحقق من وجود تحديثات على السيرفر..."));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL configUrl = new URL("https://blueplus-auz.pages.dev/settings.json");
                    HttpURLConnection conn = (HttpURLConnection) configUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "BluePlus/1.3");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.connect();
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        String decrypted = TvUtil.decryptAES(sb.toString());
                        org.json.JSONObject json = new org.json.JSONObject(decrypted);

                        final String serverVersion = json.optString("app_version", "");
                        final String updateFeatures = json.optString("update_features", "");
                        final String updateLink = json.optString("update_link", "");

                        String localVersion = "1.3";
                        try {
                            localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        } catch (Exception ignored) {}

                        final String fLocalVersion = localVersion;

                        if (!serverVersion.isEmpty() && isServerVersionNewer(serverVersion, localVersion)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pd.dismiss();
                                    showUpdateAvailableDialog(serverVersion, updateFeatures, updateLink);
                                }
                            });
                            return;
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pd.dismiss();
                                    showUpToDateDialog(fLocalVersion);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update check failed: " + e.getMessage());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                        String localVer = "1.3";
                        try {
                            localVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        } catch (Exception ignored) {}
                        showUpToDateDialog(localVer);
                    }
                });
            }
        }).start();
    }

    private void showUpdateAvailableDialog(final String serverVersion, String features, final String link) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView tvMsg = new TextView(this);
        String details = (features != null && !features.trim().isEmpty()) ? features : TvUtil.translate(this, "تحسينات شاملة على الأداء والتوافق والاستقرار وتحديث المشغلات.");
        tvMsg.setText(TvUtil.translate(this, "يتوفر إصدار أحدث من التطبيق: ") + "v" + serverVersion + "\n\n" + details);
        tvMsg.setTextColor(Color.WHITE);
        tvMsg.setTextSize(13);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setLineSpacing(0, 1.25f);
        content.addView(tvMsg);

        showPremiumDialog(TvUtil.translate(this, "تحديث جديد متوفر!"), content, TvUtil.translate(this, "تحميل الآن"), new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(link));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "فشل فتح رابط التحديث!"), Toast.LENGTH_SHORT).show();
                }
            }
        }, TvUtil.translate(this, "لاحقاً"), null);
    }

    private void showUpToDateDialog(String version) {
        TextView tv = createMessageTextView(TvUtil.translate(this, "أنت تستخدم أحدث إصدار من التطبيق بنجاح") + " (" + version + ")\n" + TvUtil.translate(this, "لا توجد تحديثات جديدة حالياً."));
        showPremiumDialog(TvUtil.translate(this, "تطبيقك محدث"), tv, TvUtil.translate(this, "حسناً"), null, null, null);
    }

    // ── About App Dialog ──

    private void showAboutDialog() {
        String version = "1.3";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding((int) (14 * scale), (int) (8 * scale), (int) (14 * scale), (int) (8 * scale));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Blue + IPTV Player");
        tvTitle.setTextColor(Color.parseColor(ACCENT_CYAN));
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        container.addView(tvTitle);

        TextView tvVer = new TextView(this);
        tvVer.setText("Version " + version + " (Liquid Glass Edition)");
        tvVer.setTextColor(Color.parseColor("#80B4FF"));
        tvVer.setTextSize(12);
        tvVer.setGravity(Gravity.CENTER);
        tvVer.setPadding(0, (int) (4 * scale), 0, (int) (10 * scale));
        container.addView(tvVer);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(TvUtil.translate(this, "مشغل وسائط IPTV فائق السرعة يدعم البث المباشر، مكتبة الأفلام والمسلسلات وتجربة تلفزيونية فاخرة متوافقة مع أجهزة التحكم عن بعد (Android TV & Mobile)."));
        tvDesc.setTextColor(Color.parseColor("#CFD8DC"));
        tvDesc.setTextSize(12);
        tvDesc.setGravity(Gravity.CENTER);
        tvDesc.setLineSpacing(0, 1.25f);
        tvDesc.setPadding(0, 0, 0, (int) (12 * scale));
        container.addView(tvDesc);

        TextView tvMac = new TextView(this);
        tvMac.setText("MAC: " + getMacAddress().toLowerCase() + "   ✦   Key: " + getDeviceKey());
        tvMac.setTextColor(Color.parseColor("#8A99AD"));
        tvMac.setTextSize(11);
        tvMac.setGravity(Gravity.CENTER);
        container.addView(tvMac);

        showPremiumDialog(TvUtil.translate(this, "حول التطبيق"), container, TvUtil.translate(this, "الدعم الفني"), new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse("https://t.me/Match_sportss"));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }, TvUtil.translate(this, "إغلاق"), null);
    }

    // ── Category JSON Reader ──

    private List<String> getLoadedCategories(String type) {
        List<String> list = new ArrayList<>();
        File file;
        if (type.equals("live")) file = new File(getExternalFilesDir(null), "xtream_live.json");
        else if (type.equals("vod")) file = new File(getExternalFilesDir(null), "xtream_vod.json");
        else file = new File(getExternalFilesDir(null), "xtream_series.json");

        if (!file.exists()) return list;

        String targetKey = "";
        if (type.equals("live")) targetKey = "get_live_categories";
        else if (type.equals("vod")) targetKey = "get_vod_categories";
        else if (type.equals("series")) targetKey = "get_series_categories";

        if (targetKey.isEmpty()) return list;

        try (com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("data")) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String subName = reader.nextName();
                        if (subName.equals(targetKey)) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                String catName = "";
                                while (reader.hasNext()) {
                                    String key = reader.nextName();
                                    if (key.equals("category_name")) {
                                        catName = reader.nextString();
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                                if (!catName.isEmpty() && !list.contains(catName)) {
                                    list.add(catName);
                                }
                            }
                            reader.endArray();
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
        } catch (Exception e) {
            Log.e(TAG, "getLoadedCategories failed: " + e.getMessage());
        }
        return list;
    }

    // ── Version Comparison & Device Utilities ──

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
        } catch (Exception ignored) {}
        return false;
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
            return "E1:AA:63:DE:99:AC";
        }
    }

    private void loadCachedBackground() {
        try {
            File f = new File(getFilesDir(), "splash_bg.jpg");
            if (f.exists()) {
                if (f.length() < 100) {
                    f.delete();
                    return;
                }
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
                    drawable.setGravity(Gravity.FILL);
                    rootLayout.setBackground(drawable);
                } else {
                    f.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed loading settings background: " + e.getMessage());
        }
    }

    private static class SettingItem {
        String title;
        String subtitle;
        int iconRes;
        View.OnClickListener listener;

        SettingItem(String title, String subtitle, int iconRes, View.OnClickListener listener) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.listener = listener;
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
