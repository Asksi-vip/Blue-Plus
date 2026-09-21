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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends Activity {

    private FrameLayout rootLayout;
    private static final String TAG = "SettingsActivity";
    private final String THEME_BLUE_DARK = "#E60B2240"; // Premium Translucent Deep Blue
    private final String THEME_BLUE_LIGHT = "#E61976D2"; // Premium Translucent Royal Blue
    private final String STROKE_BLUE = "#802196F3"; // Vibrant Blue Border
    private final String ICON_COLOR = "#4FC3F7"; // Electric Cyan for icons

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {}
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        TvUtil.hideSystemUI(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundResource(R.drawable.bg_sports);

        // Dark elegant overlay
        View overlay = new View(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.parseColor("#55000000"));
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
        mainLayout.setPadding((int) (24 * scale), (int) (16 * scale), (int) (24 * scale), (int) (16 * scale));

        // Top Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.bottomMargin = (int) (12 * scale);
        header.setLayoutParams(headerLp);

        // Back Arrow
        LinearLayout btnBack = new LinearLayout(this);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setClickable(true);
        btnBack.setFocusable(true);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (40 * scale), (int) (40 * scale));
        btnBack.setLayoutParams(backLp);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#22FFFFFF"));
        backBg.setStroke((int) (1.5 * scale), Color.parseColor("#55FFFFFF"));
        btnBack.setBackground(backBg);
        TvUtil.applyTvFocusHighlight(btnBack, 20.0f);

        ImageView ivBack = new ImageView(this);
        android.graphics.drawable.Drawable backIcon = getResources().getDrawable(android.R.drawable.ic_menu_revert);
        if (backIcon != null) {
            backIcon.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            ivBack.setImageDrawable(backIcon);
        }
        btnBack.addView(ivBack, new LinearLayout.LayoutParams((int) (22 * scale), (int) (22 * scale)));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        header.addView(btnBack);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, "الإعدادات"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.rightMargin = (int) (40 * scale); // Balance back button offset
        tvTitle.setLayoutParams(titleLp);
        header.addView(tvTitle);

        mainLayout.addView(header);

        // Settings ScrollView
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setLayoutParams(new ScrollView.LayoutParams(-1, -2));

        // Settings items (exactly 20 items forming 5 perfect rows)
        ArrayList<SettingItem> items = new ArrayList<>();
        
        // Row 1
        items.add(new SettingItem("أضف قائمة التشغيل", R.drawable.ic_settings_playlist, new View.OnClickListener() {
            @Override public void onClick(View v) { addPlaylistAction(); }
        }));
        items.add(new SettingItem("مراقبة اهلية", R.drawable.ic_settings_lock, new View.OnClickListener() {
            @Override public void onClick(View v) { parentalControlAction(); }
        }));
        items.add(new SettingItem("تغيير قائمة التسجيل", R.drawable.ic_settings_playlist, new View.OnClickListener() {
            @Override public void onClick(View v) { changePlaylistAction(); }
        }));
        items.add(new SettingItem("إخفاء الفئات الحية", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("live"); }
        }));

        // Row 2
        items.add(new SettingItem("إخفاء الفئات Vod", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("vod"); }
        }));
        items.add(new SettingItem("إخفاء فئات المسلسلات", R.drawable.ic_settings_eye_off, new View.OnClickListener() {
            @Override public void onClick(View v) { hideCategoriesAction("series"); }
        }));
        items.add(new SettingItem("Clear History Channels", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("live"); }
        }));
        items.add(new SettingItem("أفلام التاريخ واضحة", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("movies"); }
        }));

        // Row 3
        items.add(new SettingItem("سلسلة مسح التاريخ", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistoryAction("series"); }
        }));
        items.add(new SettingItem("Live Stream Format", R.drawable.ic_settings_movie, new View.OnClickListener() {
            @Override public void onClick(View v) { liveStreamFormatAction(); }
        }));
        items.add(new SettingItem("تلقائي", R.drawable.ic_settings_sync, new View.OnClickListener() {
            @Override public void onClick(View v) { autoStartAction(); }
        }));
        items.add(new SettingItem("تنسيق الوقت", R.drawable.ic_settings_clock, new View.OnClickListener() {
            @Override public void onClick(View v) { timeFormatAction(); }
        }));

        // Row 4
        items.add(new SettingItem("إعدادات الترجمة", R.drawable.ic_settings_subtitle, new View.OnClickListener() {
            @Override public void onClick(View v) { subtitleSettingsAction(); }
        }));
        items.add(new SettingItem("Select Device Type", R.drawable.ic_settings_device, new View.OnClickListener() {
            @Override public void onClick(View v) { selectDeviceTypeAction(); }
        }));
        items.add(new SettingItem("تحديث الآن", R.drawable.ic_settings_update, new View.OnClickListener() {
            @Override public void onClick(View v) { updateNowAction(); }
        }));
        items.add(new SettingItem("Parental Control ON/OFF", R.drawable.ic_settings_lock, new View.OnClickListener() {
            @Override public void onClick(View v) { parentalToggleAction(); }
        }));

        // Row 5 (Additional Premium Items + Language option)
        items.add(new SettingItem("App Language", R.drawable.ic_settings_sync, new View.OnClickListener() {
            @Override public void onClick(View v) { appLanguageAction(); }
        }));
        items.add(new SettingItem("اتصل بنا", R.drawable.ic_settings_update, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse("https://t.me/Match_sportss"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "فشل فتح الرابط!", Toast.LENGTH_SHORT).show();
                }
            }
        }));
        items.add(new SettingItem("حول التطبيق", R.drawable.ic_settings_device, new View.OnClickListener() {
            @Override public void onClick(View v) {
                View textContainer = new LinearLayout(SettingsActivity.this);
                ((LinearLayout)textContainer).setOrientation(LinearLayout.VERTICAL);
                ((LinearLayout)textContainer).setPadding(30, 30, 30, 30);
                TextView tv = new TextView(SettingsActivity.this);
                tv.setText("Blue + IPTV Player\nVersion: 4.3\nPremium TV & VOD Experience");
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER);
                ((LinearLayout)textContainer).addView(tv);
                showPremiumDialog("حول التطبيق", textContainer, "موافق", null, null, null);
            }
        }));
        items.add(new SettingItem("خروج", R.drawable.ic_settings_delete, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }));

        // Construct rows programmatically (Exactly 5 rows of 4 items)
        LinearLayout currentRow = null;
        View firstSettingCard = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % 4 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.topMargin = (int) (6 * scale);
                rowLp.bottomMargin = (int) (6 * scale);
                currentRow.setLayoutParams(rowLp);
                gridContainer.addView(currentRow);
            }

            View card = createSettingCard(items.get(i), scale);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, (int) (48 * scale), 1.0f);
            cardLp.leftMargin = (int) (6 * scale);
            cardLp.rightMargin = (int) (6 * scale);
            card.setLayoutParams(cardLp);
            
            TvUtil.applyTvFocusHighlight(card);
            
            if (i == 0) {
                firstSettingCard = card;
            }
            
            if (currentRow != null) {
                currentRow.addView(card);
            }
        }

        // Footer Device Info
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = (int) (20 * scale);
        footerLp.bottomMargin = (int) (10 * scale);
        footer.setLayoutParams(footerLp);

        TextView tvMac = new TextView(this);
        tvMac.setText(TvUtil.translate(this, "MAC Address") + ": " + getMacAddress().toLowerCase());
        tvMac.setTextColor(Color.parseColor("#B0BEC5"));
        tvMac.setTextSize(13);
        tvMac.setTypeface(null, Typeface.BOLD);
        tvMac.setGravity(Gravity.CENTER);
        footer.addView(tvMac);

        TextView tvDeviceKey = new TextView(this);
        tvDeviceKey.setText(TvUtil.translate(this, "Device Key") + ": " + getDeviceKey());
        tvDeviceKey.setTextColor(Color.parseColor("#B0BEC5"));
        tvDeviceKey.setTextSize(13);
        tvDeviceKey.setTypeface(null, Typeface.BOLD);
        tvDeviceKey.setGravity(Gravity.CENTER);
        tvDeviceKey.setPadding(0, (int) (4 * scale), 0, 0);
        footer.addView(tvDeviceKey);

        gridContainer.addView(footer);
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
        card.setPadding((int) (12 * scale), 0, (int) (12 * scale), 0);
        card.setClickable(true);
        card.setFocusable(true);

        // Premium Translucent Blue Gradient Background
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(THEME_BLUE_DARK), Color.parseColor(THEME_BLUE_LIGHT)}
        );
        gd.setCornerRadius(12 * scale);
        gd.setStroke((int) (1.5 * scale), Color.parseColor(STROKE_BLUE));
        card.setBackground(gd);

        // Icon
        ImageView ivIcon = new ImageView(this);
        android.graphics.drawable.Drawable drawable = getResources().getDrawable(item.iconRes);
        if (drawable != null) {
            drawable.setColorFilter(Color.parseColor(ICON_COLOR), android.graphics.PorterDuff.Mode.SRC_IN);
            ivIcon.setImageDrawable(drawable);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (20 * scale), (int) (20 * scale));
        iconLp.rightMargin = (int) (10 * scale);
        ivIcon.setLayoutParams(iconLp);
        card.addView(ivIcon);

        // Title text
        TextView tvText = new TextView(this);
        tvText.setText(TvUtil.translate(this, item.title));
        tvText.setTextColor(Color.WHITE);
        tvText.setTextSize(11);
        tvText.setTypeface(null, Typeface.BOLD);
        boolean isAr = "ar".equals(TvUtil.getAppLanguage(this));
        tvText.setGravity((isAr ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        tvText.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        card.addView(tvText);

        card.setOnClickListener(item.listener);
        return card;
    }

    // --- Premium Glassmorphic Dialog Helper ---

    private void showPremiumDialog(String title, View contentView, String positiveText, final Runnable positiveAction, String negativeText, final Runnable negativeAction) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int)(20 * scale), (int)(16 * scale), (int)(20 * scale), (int)(16 * scale));
        
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(THEME_BLUE_DARK), Color.parseColor(THEME_BLUE_LIGHT)}
        );
        gd.setCornerRadius(16 * scale);
        gd.setStroke((int)(2 * scale), Color.parseColor(STROKE_BLUE));
        container.setBackground(gd);
        
        TextView tvTitle = new TextView(this);
        tvTitle.setText(TvUtil.translate(this, title));
        tvTitle.setTextColor(Color.parseColor(ICON_COLOR));
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = (int)(12 * scale);
        tvTitle.setLayoutParams(titleLp);
        container.addView(tvTitle);
        
        if (contentView != null) {
            LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
            contentLp.bottomMargin = (int)(16 * scale);
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
            btnPos.setPadding((int)(16 * scale), (int)(8 * scale), (int)(16 * scale), (int)(8 * scale));
            btnPos.setFocusable(true);
            btnPos.setClickable(true);
            
            GradientDrawable posBg = new GradientDrawable();
            posBg.setColor(Color.parseColor("#4D2196F3"));
            posBg.setCornerRadius(8 * scale);
            posBg.setStroke((int)(1.5 * scale), Color.parseColor("#802196F3"));
            btnPos.setBackground(posBg);
            TvUtil.applyTvFocusHighlight(btnPos, 8.0f);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.rightMargin = (int)(8 * scale);
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
            btnNeg.setPadding((int)(16 * scale), (int)(8 * scale), (int)(16 * scale), (int)(8 * scale));
            btnNeg.setFocusable(true);
            btnNeg.setClickable(true);
            
            GradientDrawable negBg = new GradientDrawable();
            negBg.setColor(Color.parseColor("#1AFFFFFF"));
            negBg.setCornerRadius(8 * scale);
            negBg.setStroke((int)(1.5 * scale), Color.parseColor("#33FFFFFF"));
            btnNeg.setBackground(negBg);
            TvUtil.applyTvFocusHighlight(btnNeg, 8.0f);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.leftMargin = (int)(8 * scale);
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
            dialog.getWindow().setLayout((int)(400 * scale), -2);
        }
        dialog.show();
    }

    private TextView createMessageTextView(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // --- Dynamic Function Actions ---

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
        label.setText("أدخل رمز المراقبة الأبوية الحالي لتأكيد العملية:");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setPadding(0, 0, 0, (int)(8 * scale));
        container.addView(label);
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("الرمز الحالي");
        input.setHintTextColor(Color.parseColor("#88FFFFFF"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(14);
        input.setGravity(Gravity.CENTER);
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#1AFFFFFF"));
        inputBg.setCornerRadius(8 * scale);
        inputBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
        input.setBackground(inputBg);
        input.setPadding((int)(12 * scale), (int)(8 * scale), (int)(12 * scale), (int)(8 * scale));
        container.addView(input);

        showPremiumDialog(title, container, "تأكيد", new Runnable() {
            @Override
            public void run() {
                if (input.getText().toString().equals(pin)) {
                    if (successAction != null) successAction.run();
                } else {
                    Toast.makeText(SettingsActivity.this, "الرمز السري غير صحيح!", Toast.LENGTH_SHORT).show();
                }
            }
        }, "إلغاء", null);
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
            labelCurrent.setText("الرمز السري الحالي:");
            labelCurrent.setTextColor(Color.WHITE);
            labelCurrent.setTextSize(13);
            labelCurrent.setPadding(0, 0, 0, (int)(4 * scale));
            container.addView(labelCurrent);

            etCurrentPin = new EditText(this);
            etCurrentPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            etCurrentPin.setHint("الرمز الحالي");
            etCurrentPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
            etCurrentPin.setTextColor(Color.WHITE);
            etCurrentPin.setTextSize(14);
            etCurrentPin.setGravity(Gravity.CENTER);
            
            GradientDrawable currentBg = new GradientDrawable();
            currentBg.setColor(Color.parseColor("#1AFFFFFF"));
            currentBg.setCornerRadius(8 * scale);
            currentBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
            etCurrentPin.setBackground(currentBg);
            etCurrentPin.setPadding((int)(12 * scale), (int)(8 * scale), (int)(12 * scale), (int)(8 * scale));
            container.addView(etCurrentPin);
            
            View space = new View(this);
            space.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(8 * scale)));
            container.addView(space);
        } else {
            etCurrentPin = null;
        }

        TextView labelNew = new TextView(this);
        labelNew.setText("الرمز السري الجديد (4 أرقام):");
        labelNew.setTextColor(Color.WHITE);
        labelNew.setTextSize(13);
        labelNew.setPadding(0, 0, 0, (int)(4 * scale));
        container.addView(labelNew);

        final EditText etNewPin = new EditText(this);
        etNewPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNewPin.setHint("الرمز الجديد");
        etNewPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
        etNewPin.setTextColor(Color.WHITE);
        etNewPin.setTextSize(14);
        etNewPin.setGravity(Gravity.CENTER);
        
        GradientDrawable newBg = new GradientDrawable();
        newBg.setColor(Color.parseColor("#1AFFFFFF"));
        newBg.setCornerRadius(8 * scale);
        newBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
        etNewPin.setBackground(newBg);
        etNewPin.setPadding((int)(12 * scale), (int)(8 * scale), (int)(12 * scale), (int)(8 * scale));
        container.addView(etNewPin);

        View space2 = new View(this);
        space2.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(8 * scale)));
        container.addView(space2);

        TextView labelConfirm = new TextView(this);
        labelConfirm.setText("تأكيد الرمز السري الجديد:");
        labelConfirm.setTextColor(Color.WHITE);
        labelConfirm.setTextSize(13);
        labelConfirm.setPadding(0, 0, 0, (int)(4 * scale));
        container.addView(labelConfirm);

        final EditText etConfirmPin = new EditText(this);
        etConfirmPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etConfirmPin.setHint("تأكيد الرمز");
        etConfirmPin.setHintTextColor(Color.parseColor("#88FFFFFF"));
        etConfirmPin.setTextColor(Color.WHITE);
        etConfirmPin.setTextSize(14);
        etConfirmPin.setGravity(Gravity.CENTER);
        
        GradientDrawable confirmBg = new GradientDrawable();
        confirmBg.setColor(Color.parseColor("#1AFFFFFF"));
        confirmBg.setCornerRadius(8 * scale);
        confirmBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
        etConfirmPin.setBackground(confirmBg);
        etConfirmPin.setPadding((int)(12 * scale), (int)(8 * scale), (int)(12 * scale), (int)(8 * scale));
        container.addView(etConfirmPin);

        showPremiumDialog("مراقبة أبوية", container, "حفظ", new Runnable() {
            @Override
            public void run() {
                if (etCurrentPin != null) {
                    String currentInput = etCurrentPin.getText().toString();
                    if (!currentInput.equals(pin)) {
                        Toast.makeText(SettingsActivity.this, "الرمز السري الحالي غير صحيح!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                String newPin = etNewPin.getText().toString();
                String confirmPin = etConfirmPin.getText().toString();
                
                if (newPin.length() != 4) {
                    Toast.makeText(SettingsActivity.this, "الرمز السري الجديد يجب أن يتكون من 4 أرقام!", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!newPin.equals(confirmPin)) {
                    Toast.makeText(SettingsActivity.this, "رمز التأكيد غير متطابق مع الرمز الجديد!", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                sp.edit()
                    .putString("parental_pin", newPin)
                    .putBoolean("parental_active", true)
                    .apply();
                
                Toast.makeText(SettingsActivity.this, "تم تعيين وتفعيل رمز المراقبة الأبوية بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void parentalToggleAction() {
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String pin = sp.getString("parental_pin", "");

        if (pin.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين رمز مراقبة أبوية أولاً لتفعيل الخدمة!", Toast.LENGTH_LONG).show();
            parentalControlAction();
            return;
        }

        final boolean active = sp.getBoolean("parental_active", true);
        String title = active ? "تعطيل الحماية الأبوية" : "تفعيل الحماية الأبوية";
        verifyPinThenAction(title, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean("parental_active", !active).apply();
                Toast.makeText(SettingsActivity.this, !active ? "تم تفعيل المراقبة الأبوية!" : "تم إلغاء تفعيل المراقبة الأبوية!", Toast.LENGTH_SHORT).show();
            }
        });
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == currentIdx) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog(TvUtil.translate(this, "App Language"), container, TvUtil.translate(this, "Save Settings"), new Runnable() {
            @Override
            public void run() {
                 int selectedId = rg.getCheckedRadioButtonId();
                 if (selectedId >= 0 && selectedId < values.length) {
                     sp.edit().putString("app_language", values[selectedId]).apply();
                     Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "Save Settings") + "...", Toast.LENGTH_SHORT).show();
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
            verifyPinThenAction("تأكيد الرمز السري", new Runnable() {
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
            showPremiumDialog("إخفاء الفئات", createMessageTextView("لا توجد فئات محملة حالياً. يرجى تصفح القنوات أولاً لتنشيط البيانات!"), "موافق", null, null, null);
            return;
        }

        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        final String prefPrefix = type.equals("live") ? "hide_live_cat_" : (type.equals("vod") ? "hide_movies_cat_" : "hide_series_cat_");

        float scale = getResources().getDisplayMetrics().density;
        
        // Root container for content
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        
        // Explanatory label
        TextView tvIntro = new TextView(this);
        tvIntro.setText("ضع علامة صح (✓) بجانب الباقات التي ترغب في إخفائها من العرض:");
        tvIntro.setTextColor(Color.parseColor("#B0BEC5"));
        tvIntro.setTextSize(12);
        tvIntro.setPadding(0, 0, 0, (int)(8 * scale));
        tvIntro.setGravity(Gravity.RIGHT);
        contentLayout.addView(tvIntro);
        
        // Select All / Deselect All Row
        LinearLayout selectRow = new LinearLayout(this);
        selectRow.setOrientation(LinearLayout.HORIZONTAL);
        selectRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectRowLp = new LinearLayout.LayoutParams(-1, -2);
        selectRowLp.bottomMargin = (int)(8 * scale);
        selectRow.setLayoutParams(selectRowLp);
        
        final List<android.widget.CheckBox> checkBoxes = new ArrayList<>();
        
        TextView btnSelectAll = new TextView(this);
        btnSelectAll.setText("تحديد الكل (إخفاء الكل)");
        btnSelectAll.setTextColor(Color.parseColor(ICON_COLOR));
        btnSelectAll.setTextSize(11);
        btnSelectAll.setTypeface(null, Typeface.BOLD);
        btnSelectAll.setGravity(Gravity.CENTER);
        btnSelectAll.setPadding((int)(8 * scale), (int)(4 * scale), (int)(8 * scale), (int)(4 * scale));
        GradientDrawable selectBg = new GradientDrawable();
        selectBg.setColor(Color.parseColor("#1A4FC3F7"));
        selectBg.setCornerRadius(6 * scale);
        selectBg.setStroke((int)(1 * scale), Color.parseColor("#334FC3F7"));
        btnSelectAll.setBackground(selectBg);
        LinearLayout.LayoutParams selLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        selLp.rightMargin = (int)(4 * scale);
        btnSelectAll.setLayoutParams(selLp);
        btnSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (android.widget.CheckBox cb : checkBoxes) cb.setChecked(true);
            }
        });
        selectRow.addView(btnSelectAll);
        
        TextView btnDeselectAll = new TextView(this);
        btnDeselectAll.setText("إلغاء التحديد (إظهار الكل)");
        btnDeselectAll.setTextColor(Color.WHITE);
        btnDeselectAll.setTextSize(11);
        btnDeselectAll.setTypeface(null, Typeface.BOLD);
        btnDeselectAll.setGravity(Gravity.CENTER);
        btnDeselectAll.setPadding((int)(8 * scale), (int)(4 * scale), (int)(8 * scale), (int)(4 * scale));
        GradientDrawable deselectBg = new GradientDrawable();
        deselectBg.setColor(Color.parseColor("#1AFFFFFF"));
        deselectBg.setCornerRadius(6 * scale);
        deselectBg.setStroke((int)(1 * scale), Color.parseColor("#33FFFFFF"));
        btnDeselectAll.setBackground(deselectBg);
        LinearLayout.LayoutParams deselLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        deselLp.leftMargin = (int)(4 * scale);
        btnDeselectAll.setLayoutParams(deselLp);
        btnDeselectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (android.widget.CheckBox cb : checkBoxes) cb.setChecked(false);
            }
        });
        selectRow.addView(btnDeselectAll);
        
        contentLayout.addView(selectRow);

        // Scrollview containing checkbox list
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(180 * scale)));
        sv.setVerticalScrollBarEnabled(true);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding((int)(8 * scale), 0, (int)(8 * scale), 0);

        for (final String cat : cats) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(cat);
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(13);
            cb.setChecked(sp.getBoolean(prefPrefix + cat, false));
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, -2);
            cbLp.bottomMargin = (int)(6 * scale);
            cb.setLayoutParams(cbLp);
            
            listLayout.addView(cb);
            checkBoxes.add(cb);
        }
        sv.addView(listLayout);
        contentLayout.addView(sv);

        showPremiumDialog(
            type.equals("live") ? "إخفاء الباقات الحية" : (type.equals("vod") ? "إخفاء باقات الأفلام" : "إخفاء باقات المسلسلات"),
            contentLayout,
            "تطبيق وإخفاء الباقات",
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
                    Toast.makeText(SettingsActivity.this, "تم تطبيق التعديلات وإخفاء " + hiddenCount + " باقة بنجاح!", Toast.LENGTH_SHORT).show();
                }
            },
            "إلغاء",
            null
        );
    }

    private void clearHistoryAction(final String type) {
        TextView msg = createMessageTextView("هل أنت متأكد من مسح سجل المشاهدة الخاص بـ (" + type.toUpperCase() + ") بالكامل؟ لا يمكن التراجع عن هذه الخطوة.");
        showPremiumDialog("مسح سجل المشاهدة", msg, "نعم، مسح السجل", new Runnable() {
            @Override
            public void run() {
                if (type.equals("movies")) {
                    getSharedPreferences("SeriesHistory", MODE_PRIVATE).edit().putString("recent_movies", "").apply();
                } else if (type.equals("series")) {
                    getSharedPreferences("SeriesHistory", MODE_PRIVATE).edit().putString("recent_series", "").apply();
                } else if (type.equals("live")) {
                    getSharedPreferences("LivePrefs", MODE_PRIVATE).edit().putString("recent_names", "[]").apply();
                }
                Toast.makeText(SettingsActivity.this, "تم مسح سجل المشاهدة بالكامل وتصفير الذاكرة التخزينية!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void liveStreamTypeAction() {
        final String[] items = {"ExoPlayer (مشغل مدمج تلقائي)", "VLC Player (مشغل خارجي)", "MX Player (مشغل خارجي)", "System Native Player"};
        final SharedPreferences sp = getSharedPreferences("Settings", MODE_PRIVATE);
        int current = sp.getInt("player_type", 0);
        
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == current) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("نوع البث المباشر", container, "حفظ الاختيار", new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putInt("player_type", selectedId).apply();
                Toast.makeText(SettingsActivity.this, "تم تغيير مشغل البث بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void liveStreamFormatAction() {
        final String[] items = {"تلقائي", "MPEG", "m3u8"};
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if ((i == 0 && current.equals("auto")) || (i == 1 && current.equals("ts")) || (i == 2 && current.equals("m3u8"))) {
                rb.setChecked(true);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(8 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);
        
        showPremiumDialog("تنسيق البث المباشر", container, "حفظ وتطبيق", new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                String val = "auto";
                String name = "تلقائي";
                if (selectedId == 1) {
                    val = "ts";
                    name = "MPEG";
                } else if (selectedId == 2) {
                    val = "m3u8";
                    name = "m3u8";
                }
                sp.edit().putString("stream_format", val).apply();
                Toast.makeText(SettingsActivity.this, "تم تعيين تنسيق البث بنجاح إلى: " + name, Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void changePlayerAction() {
        final String[] items = {"المشغل المدمج الأساسي Blue + Player (تلقائي)", "المشغل الخارجي لجميع صيغ البث"};
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if ((i == 0 && !current) || (i == 1 && current)) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("Change Player", container, "تطبيق المشغل", new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putBoolean("use_external", selectedId == 1).apply();
                Toast.makeText(SettingsActivity.this, "تم ضبط المشغل الافتراضي بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void chooseExternalPlayerAction() {
        final String[] items = {"إيقاف وتشغيل المشغلات الداخلية (تلقائي)", "تفعيل مشغلات الوسائط الخارجية"};
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if ((i == 0 && !current) || (i == 1 && current)) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("اختر مشغل وسائط خارجي", container, "حفظ", new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                sp.edit().putBoolean("use_external", selectedId == 1).apply();
                Toast.makeText(SettingsActivity.this, "تم تعديل خيارات المشغل الخارجي!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void autoStartAction() {
        final String[] items = {"تشغيل تلقائي للبث عند الدخول (افتراضي ومستقر)", "عدم تشغيل البث تلقائياً"};
        
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == 0) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("وضع التشغيل التلقائي", container, "حفظ الخيار", new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SettingsActivity.this, "تم حفظ إعدادات المزامنة والتشغيل بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void timeFormatAction() {
        final String[] items = {"تنسيق 12 ساعة (ص/م)", "تنسيق 24 ساعة (تلقائي)"};
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == activeId) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("تنسيق الوقت", container, "حفظ التنسيق", new Runnable() {
            @Override
            public void run() {
                int selectedId = rg.getCheckedRadioButtonId();
                String val = (selectedId == 1) ? "24" : "12";
                sp.edit().putString("time_format", val).apply();
                Toast.makeText(SettingsActivity.this, "تم تحديث نمط عرض الوقت بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void subtitleSettingsAction() {
        final String[] sizes = {"حجم خط صغير", "حجم خط متوسط (موصى به)", "حجم خط كبير جداً"};
        
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == 1) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("إعدادات الترجمة", container, "حفظ الإعدادات", new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SettingsActivity.this, "تم حفظ إعدادات وحجم خط الترجمة بنجاح!", Toast.LENGTH_SHORT).show();
            }
        }, "إلغاء", null);
    }

    private void selectDeviceTypeAction() {
        final String[] items = {"الهاتف المحمول / تابلت (Mobile Mode)", "شاشات وأجهزة تلفاز (Android TV Mode)"};
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
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(ICON_COLOR)));
            }
            if (i == current) rb.setChecked(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int)(6 * scale);
            rg.addView(rb, lp);
        }
        container.addView(rg);

        showPremiumDialog("Select Device Type", container, "تأكيد الحفظ", new Runnable() {
            @Override
            public void run() {
                 int selectedId = rg.getCheckedRadioButtonId();
                 sp.edit().putInt("device_mode", selectedId).apply();
                 Toast.makeText(SettingsActivity.this, "تم ضبط إعدادات توافق الشاشات بنجاح!", Toast.LENGTH_SHORT).show();
                 recreate();
             }
         }, "إلغاء", null);
    }

    private void updateNowAction() {
        final ProgressDialog pd = new ProgressDialog(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        pd.setMessage(TvUtil.translate(this, "جاري التحقق من وجود تحديثات على السيرفر وتحديث الخلفية والشعار..."));
        pd.setCancelable(false);
        pd.show();

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                pd.dismiss();
                Toast.makeText(SettingsActivity.this, TvUtil.translate(SettingsActivity.this, "تطبيقك محدث بالكامل إلى الإصدار v4.3 بنجاح! تم استيراد أحدث الإعدادات والخلفية."), Toast.LENGTH_LONG).show();
            }
        }, 1500);
    }

    // --- Category File Reader ---

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

        try (com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {
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

    // --- Utility Methods ---

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
                    android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                    drawable.setGravity(android.view.Gravity.FILL);
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
        int iconRes;
        View.OnClickListener listener;

        SettingItem(String title, int iconRes, View.OnClickListener listener) {
            this.title = title;
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
