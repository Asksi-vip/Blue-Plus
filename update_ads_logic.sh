#!/bin/bash
# 1. Update WaitingActivity to save ad URLs
sed -i '/final org.json.JSONArray adArr = obj.optJSONArray("ad_images");/a\
                                    if (adArr != null) {\
                                        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("ad_urls_json", adArr.toString()).apply();\
                                    }' /storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/WaitingActivity.java

# 2. Update Ot2Activity.java to use Glide and adUrls
# First, insert Glide import if not exists
if ! grep -q "import com.bumptech.glide.Glide;" /storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java; then
    sed -i '/import android.widget.TextView;/a import com.bumptech.glide.Glide;\nimport com.bumptech.glide.load.engine.DiskCacheStrategy;' /storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java
fi

