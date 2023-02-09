package com.prasunpersonal.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


//

public class ForegroundService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("recording", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Recording saved successfully!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this,"recording")
                .setContentTitle("Recording Service")
                .setContentText("Running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
        return START_NOT_STICKY;
    }
}
