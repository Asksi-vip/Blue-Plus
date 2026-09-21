package com.blue.plus;

import android.app.Application;
import android.content.Context;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mApplicationContext = getApplicationContext();
    }
}
