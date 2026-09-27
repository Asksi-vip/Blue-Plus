package com.blue.plus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class MatchNotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "blue_plus_match_reminders";
    public static final String CHANNEL_NAME = "تنبيهات المباريات";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String matchId = intent.getStringExtra("match_id");
        String team1 = intent.getStringExtra("team1");
        String team2 = intent.getStringExtra("team2");
        String time = intent.getStringExtra("time");

        if (team1 == null || team1.isEmpty()) team1 = "فريق";
        if (team2 == null || team2.isEmpty()) team2 = "فريق";

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("تنبيهات اقتراب موعد انطلاق المباريات المباشرة");
            channel.enableLights(true);
            channel.setLightColor(Color.parseColor("#0A84FF"));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 400, 200, 400});
            nm.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, SportsActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("from_notification", true);
        openIntent.putExtra("match_id", matchId);

        int reqCode = (matchId != null) ? matchId.hashCode() : (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                reqCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String title = "⚽ اقتربت المباراة: " + team1 + " 🆚 " + team2;
        String contentText = "ستنطلق المباراة بعد 5 دقائق (" + (time != null ? time : "") + ")! اضغط هنا لفتح البث المباشر.";

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.home_logo)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 400, 200, 400})
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.parseColor("#0A84FF"))
                .setContentIntent(pendingIntent);

        nm.notify(reqCode, builder.build());
    }
}
