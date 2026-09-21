package com.blue.plus;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.view.WindowManager;

public class Ot3Activity extends Activity {

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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#111111"));
        
        // Header
        TextView title = new TextView(this);
        title.setText("Sports Fixtures");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2);
        titleParams.topMargin = 50;
        title.setLayoutParams(titleParams);
        root.addView(title);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(-1, -1);
        containerParams.topMargin = 150;
        container.setLayoutParams(containerParams);
        
        for(int i = 0; i < 4; i++) {
            addEventCard(container, "Match " + (i+1));
        }
        
        root.addView(container);
        setContentView(root);
    }

    private void addEventCard(LinearLayout container, String matchName) {
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int)(100 * scale));
        lp.setMargins(20, 20, 20, 20);
        card.setLayoutParams(lp);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#222196F3"));
        gd.setCornerRadius(20);
        gd.setStroke(2, Color.parseColor("#552196F3"));
        card.setBackground(gd);
        TvUtil.applyTvFocusHighlight(card);
        
        TextView tv = new TextView(this);
        tv.setText(matchName);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv);
        
        container.addView(card);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            TvUtil.hideSystemUI(this);
        }
    }
}
