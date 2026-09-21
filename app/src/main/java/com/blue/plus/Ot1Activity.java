package com.blue.plus;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.WindowManager;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

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
    private List<Map<String, Object>> playlists = new ArrayList<>();

    private static final String TAG = "Ot1Debug";
    private static final String FILE_BG = "splash_bg.jpg";
    private static final String FILE_LOGO = "splash_logo.png";
    private static final String THEME_COLOR = "#2196F3";
    private String currentLang;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(TvUtil.updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = TvUtil.getAppLanguage(this);
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
        
        loadSavedPlaylists();
        addTopBar();
        addMainContent();
        addSidebar();
        addFooter();
 
        // Horizontal Bottom News Ticker (TV-like Marquee)
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout tickerContainer = new LinearLayout(this);
        tickerContainer.setOrientation(LinearLayout.HORIZONTAL);
        tickerContainer.setBackgroundColor(Color.parseColor("#E6080808"));
        tickerContainer.setGravity(Gravity.CENTER_VERTICAL);
        tickerContainer.setPadding((int)(10 * scale), 0, (int)(10 * scale), 0);

        TextView badge = new TextView(this);
        badge.setText("  الأخبار  ");
        badge.setTextColor(Color.BLACK);
        badge.setBackgroundColor(Color.parseColor("#FFC107"));
        badge.setTextSize(10);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, (int)(16 * scale));
        badgeLp.rightMargin = (int)(8 * scale);
        badge.setLayoutParams(badgeLp);
        tickerContainer.addView(badge);

        MarqueeTextView tickerTv = new MarqueeTextView(this);
        tickerTv.setSingleLine(true);
        tickerTv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tickerTv.setMarqueeRepeatLimit(-1);
        tickerTv.setHorizontallyScrolling(true);
        tickerTv.setTextColor(Color.WHITE);
        tickerTv.setTextSize(11);
        tickerTv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        tickerContainer.addView(tickerTv);

        TvUtil.setupDualLanguageTicker(this, badge, tickerTv);

        FrameLayout.LayoutParams tickerLp = new FrameLayout.LayoutParams(-1, (int)(22 * scale));
        tickerLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tickerContainer.setLayoutParams(tickerLp);
        rootLayout.addView(tickerContainer);

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
    }

    private void addTopBar() {
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout topBar = new LinearLayout(this);
        topBar.setLayoutParams(new FrameLayout.LayoutParams(-1, (int)(70 * scale)));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding((int)(25 * scale), 0, 0, 0);

        ImageView logo = new ImageView(this);
        logo.setLayoutParams(new LinearLayout.LayoutParams((int)(35 * scale), (int)(35 * scale)));
        logo.setImageResource(R.drawable.home_logo);
        loadCachedLogo(logo);
        topBar.addView(logo);

        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams((int)(1.5f * scale), (int)(25 * scale));
        divParams.leftMargin = (int)(15 * scale); divParams.rightMargin = (int)(15 * scale);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(Color.WHITE);
        topBar.addView(divider);

        TextView tvPlaylist = new TextView(this);
        tvPlaylist.setText(TvUtil.translate(this, "Playlist"));
        tvPlaylist.setTextColor(Color.WHITE);
        tvPlaylist.setTextSize(20);
        tvPlaylist.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        topBar.addView(tvPlaylist);

        rootLayout.addView(topBar);
    }

    private void addSidebar() {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setBackgroundColor(Color.parseColor("#44000000"));

        int widthPx = (int)((isTv ? 280 : 220) * scale);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthPx, -1);
        params.gravity = Gravity.RIGHT;
        sidebar.setLayoutParams(params);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding((int)((isTv ? 20 : 10) * scale), (int)((isTv ? 60 : 30) * scale), (int)((isTv ? 20 : 10) * scale), (int)((isTv ? 20 : 10) * scale));
        sidebar.setGravity(Gravity.CENTER_HORIZONTAL);

        View edgeDivider = new View(this);
        FrameLayout.LayoutParams edgeParams = new FrameLayout.LayoutParams((int)(1.5f * scale), -1);
        edgeParams.gravity = Gravity.RIGHT;
        edgeParams.rightMargin = widthPx;
        edgeDivider.setLayoutParams(edgeParams);
        edgeDivider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        rootLayout.addView(edgeDivider);

        addSidebarText(sidebar, TvUtil.translate(this, "افتح الموقع"), isTv ? 14 : 11, Color.WHITE, isTv ? 20 : 10, true);
        addSidebarText(sidebar, TvUtil.translate(this, "تفعيل ماك"), isTv ? 18 : 14, Color.WHITE, isTv ? 40 : 15, false);
        addSidebarText(sidebar, TvUtil.translate(this, "اهلا وسهلا بك في Blue +"), isTv ? 15 : 12, Color.parseColor(THEME_COLOR), isTv ? 15 : 8, false);
        addSidebarText(sidebar, TvUtil.translate(this, "لإضافة / إدارة قوائم التشغيل ، استخدم القيم التالية على موقع الويب:"), isTv ? 11 : 9, Color.parseColor("#BBFFFFFF"), isTv ? 10 : 6, false);
        addSidebarText(sidebar, TvUtil.translate(this, "عنوان ماك"), isTv ? 13 : 10, Color.parseColor("#88FFFFFF"), isTv ? 30 : 12, false);
        addSidebarText(sidebar, getMacAddress().toLowerCase(), isTv ? 18 : 13, Color.parseColor(THEME_COLOR), 5, false);
        addSidebarText(sidebar, TvUtil.translate(this, "مفتاح الجهاز"), isTv ? 13 : 10, Color.parseColor("#88FFFFFF"), isTv ? 20 : 10, false);
        addSidebarText(sidebar, getDeviceKey(), isTv ? 20 : 14, Color.parseColor(THEME_COLOR), 5, false);

        rootLayout.addView(sidebar);
    }

    private void addSidebarText(LinearLayout container, String text, int size, int color, int topMargin, boolean isButton) {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(size); tv.setTextColor(color); tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int)(topMargin * scale);
        tv.setLayoutParams(lp);
        
        if (isButton) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius((isTv ? 25 : 15) * scale);
            gd.setColor(Color.parseColor("#33" + THEME_COLOR.substring(1)));
            gd.setStroke(2, Color.parseColor("#55FFFFFF"));
            tv.setBackground(gd);
            tv.setPadding(0, (int)((isTv ? 12 : 8) * scale), 0, (int)((isTv ? 12 : 8) * scale));
            
            TvUtil.applyTvFocusHighlight(tv, isTv ? 25.0f : 15.0f);
            
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(android.net.Uri.parse("https://t.me/Match_sportss"));
                        startActivity(intent);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(Ot1Activity.this, "فشل فتح الرابط!", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        container.addView(tv);
    }

    private void addMainContent() {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        recyclerView = new RecyclerView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.rightMargin = (int)((isTv ? 300 : 240) * scale); 
        params.topMargin = (int)(80 * scale); 
        params.leftMargin = (int)(25 * scale);
        params.bottomMargin = (int)(120 * scale);
        recyclerView.setLayoutParams(params);
        
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        adapter = new PlaylistAdapter(this, playlists);
        recyclerView.setAdapter(adapter);
        
        rootLayout.addView(recyclerView);
    }

    private void addFooter() {
        float scale = getResources().getDisplayMetrics().density;
        boolean isTv = TvUtil.isTvMode(this);
        LinearLayout footer = new LinearLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = (int)(40 * scale);
        params.rightMargin = (int)((isTv ? 280 : 220) * scale);
        footer.setLayoutParams(params);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);

        TextView tvWelcome = new TextView(this);
        tvWelcome.setText("أهلاً بكم في تطبيق Blue +");
        tvWelcome.setTextColor(Color.WHITE); tvWelcome.setTextSize(isTv ? 16 : 12);
        footer.addView(tvWelcome);

        TextView tvTele = new TextView(this);
        tvTele.setText("انضمو الى قناتنا على التليجرام للحصول على أكواد التفعيل @match_sportss");
        tvTele.setTextColor(Color.parseColor("#AAFFFFFF"));
        tvTele.setTextSize(isTv ? 12 : 9);
        tvTele.setPadding(0, (int)(8 * scale), 0, 0);
        footer.addView(tvTele);

        rootLayout.addView(footer);
    }

    private class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Map<String, Object>> data;
        private Context ctx;

        public PlaylistAdapter(Context ctx, List<Map<String, Object>> data) {
            this.ctx = ctx; this.data = data;
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
            card.setGravity(Gravity.CENTER);
            
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, (int)(90 * scale));
            lp.rightMargin = (int)(10 * scale);
            lp.bottomMargin = (int)(15 * scale);
            card.setLayoutParams(lp);
            
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(12 * scale);
            gd.setColor(Color.parseColor("#332196F3"));
            card.setBackground(gd);
            
            TvUtil.applyTvFocusHighlight(card);

            if (viewType == 0) {
                TextView plus = new TextView(ctx);
                plus.setText("+"); plus.setTextColor(Color.WHITE); plus.setTextSize(28);
                plus.setGravity(Gravity.CENTER);
                card.addView(plus);

                TextView txt = new TextView(ctx);
                txt.setText(TvUtil.translate(ctx, "أضف قائمة التشغيل")); txt.setTextColor(Color.WHITE); txt.setTextSize(12);
                txt.setGravity(Gravity.CENTER);
                card.addView(txt);

                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ctx.startActivity(new android.content.Intent(ctx, LoginActivity.class));
                    }
                });
            } else { 
                TextView title = new TextView(ctx);
                title.setTextColor(Color.WHITE); title.setTextSize(14);
                title.setGravity(Gravity.CENTER);
                card.addView(title);

                View line = new View(ctx);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams((int)(60 * scale), (int)(2.5f * scale));
                lineLp.topMargin = (int)(2 * scale);
                line.setLayoutParams(lineLp);
                line.setBackgroundColor(Color.parseColor("#FF2196F3")); // Blue line
                card.addView(line);
            }
            return new RecyclerView.ViewHolder(card) {};
        }

        @Override 
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 1) {
                LinearLayout card = (LinearLayout) holder.itemView;
                TextView title = (TextView) card.getChildAt(0);
                View line = card.getChildAt(1);
                final Map<String, Object> item = data.get(position - 1);
                final int pos = position - 1;
                title.setText((String)item.get("name"));

                // Show blue line if this is the active/last connected playlist
                SharedPreferences spPlaylists = ctx.getSharedPreferences("Playlists", MODE_PRIVATE);
                String activeDns = spPlaylists.getString("active_dns", "");
                String activeUser = spPlaylists.getString("active_username", "");
                String activePass = spPlaylists.getString("active_password", "");
                
                boolean isActive = activeDns.equalsIgnoreCase((String)item.get("dns")) 
                                && activeUser.equalsIgnoreCase((String)item.get("username")) 
                                && activePass.equalsIgnoreCase((String)item.get("password"));
                
                if (isActive) {
                    line.setVisibility(View.VISIBLE);
                } else {
                    line.setVisibility(View.GONE);
                }
                
                float scale = ctx.getResources().getDisplayMetrics().density;
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(12 * scale);
                String codeStr = (String) item.get("code");
                if (codeStr != null && codeStr.equalsIgnoreCase("VIP")) {
                    gd.setColor(Color.parseColor("#44D4AF37"));
                    gd.setStroke((int)(1.5f * scale), Color.parseColor("#FFD4AF37"));
                    title.setTextColor(Color.parseColor("#FFD4AF37"));
                } else {
                    gd.setColor(Color.parseColor("#332196F3"));
                    gd.setStroke((int)(1.5f * scale), Color.parseColor("#55FFFFFF"));
                    title.setTextColor(Color.WHITE);
                }
                card.setBackground(gd);
                
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showOptionsDialog(item, pos);
                    }
                });
            }
        }

        @Override public int getItemCount() { return data.size() + 1; }
    }

    private void showOptionsDialog(final Map<String, Object> item, final int position) {
        float scale = getResources().getDisplayMetrics().density;
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT); 

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(-1, -1);
        container.setLayoutParams(containerParams);
        
        dialog.setContentView(root);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.85f; 
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        TextView btnConnect = addOptionButton(container, TvUtil.translate(this, "يتصل"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                movePlaylistToFirst(position); // Move to the top of the list
                getSharedPreferences("Playlists", MODE_PRIVATE).edit().putBoolean("auto_login", true).apply();
                
                Intent intent = new Intent(Ot1Activity.this, WaitingActivity.class);
                intent.putExtra("dns", (String)item.get("dns"));
                intent.putExtra("username", (String)item.get("username"));
                intent.putExtra("password", (String)item.get("password"));
                intent.putExtra("code", (String)item.get("code"));
                startActivity(intent);
            }
        });
        
        addOptionButton(container, TvUtil.translate(this, "اتصال (بث مباشر فقط)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                movePlaylistToFirst(position); // Move to the top of the list
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
        
        addOptionButton(container, TvUtil.translate(this, "تعديل"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.widget.Toast.makeText(Ot1Activity.this, TvUtil.translate(Ot1Activity.this, "التطبيق محمي"), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        addOptionButton(container, TvUtil.translate(this, "حذف"), new View.OnClickListener() {
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
        root.setFocusable(false);
        root.setFocusableInTouchMode(false);

        root.addView(container);
        dialog.show();

        if (TvUtil.isTvMode(this)) {
            btnConnect.requestFocus();
        }
    }

    private TextView addOptionButton(LinearLayout container, String text, View.OnClickListener listener) {
        float scale = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(16);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(250 * scale), (int)(50 * scale));
        lp.topMargin = (int)(15 * scale);
        btn.setLayoutParams(lp);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(15 * scale);
        gd.setColor(Color.parseColor("#552196F3")); 
        gd.setStroke(2, Color.parseColor("#AA2196F3"));
        btn.setBackground(gd);
        
        btn.setOnClickListener(listener);
        TvUtil.applyTvFocusHighlight(btn, 15.0f);
        container.addView(btn);
        return btn;
    }

    private void deletePlaylist(int position) {
        playlists.remove(position);
        SharedPreferences sp = getSharedPreferences("Playlists", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("list", new Gson().toJson(playlists));
        if (playlists.isEmpty()) {
            editor.putBoolean("auto_login", false);
        }
        editor.apply();
        adapter.notifyDataSetChanged();
        requestPlaylistFocus();
        android.widget.Toast.makeText(this, "تم حذف القائمة بنجاح", android.widget.Toast.LENGTH_SHORT).show();
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
        } catch (Exception e) { return "136115"; }
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
        } catch (Exception ex) { return "E1:AA:63:DE:99:AC22"; }
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

    private void loadCachedLogo(ImageView iv) {
        try {
            File f = new File(getFilesDir(), FILE_LOGO);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) iv.setImageBitmap(bmp);
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

    private static class MarqueeTextView extends android.widget.TextView {
        public MarqueeTextView(android.content.Context context) {
            super(context);
        }
        @Override
        public boolean isFocused() {
            return true;
        }
    }
}

