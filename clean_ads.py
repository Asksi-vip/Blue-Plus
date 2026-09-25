import re

with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java', 'r') as f:
    content = f.read()

# Remove the block in Ot2Activity starting with `if (obj.has("ad_images")) {`
content = re.sub(r'if \(obj\.has\("ad_images"\)\) \{.*?getSharedPreferences\("Playlists", MODE_PRIVATE\)\.edit\(\)\.putInt\("cached_ads_count", count\)\.apply\(\);\s*\}', 
                 '// Ad images are now loaded dynamically via Glide', 
                 content, flags=re.DOTALL)

with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/Ot2Activity.java', 'w') as f:
    f.write(content)


with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/WaitingActivity.java', 'r') as f:
    content = f.read()

content = re.sub(r'new Thread\(new Runnable\(\) \{\s*@Override\s*public void run\(\) \{\s*preCacheImages\(adArr\);\s*\}\s*\}\)\.start\(\);',
                 '// preCacheImages removed, handled dynamically by Glide',
                 content, flags=re.DOTALL)

with open('/storage/emulated/0/Download/blue/app/src/main/java/com/blue/plus/WaitingActivity.java', 'w') as f:
    f.write(content)

