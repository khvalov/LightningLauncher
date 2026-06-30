package com.threethan.launcher.web;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.threethan.launcher.R;
import com.threethan.launcher.data.Settings;

import java.io.IOException;

public class WebServerService extends Service {
    private static final String TAG = "WebServerService";
    private static final String CHANNEL_ID = "ll_web_server";
    private static final int NOTIF_ID = 5823;

    private LauncherHttpServer server;
    private static boolean running = false;
    private static String serverUrl = null;

    public static boolean isRunning() { return running; }
    public static String getServerUrl() { return serverUrl; }

    @Override
    public void onCreate() {
        super.onCreate();
        server = new LauncherHttpServer();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            try {
                server.start();
                running = true;
                serverUrl = "http://" + getWifiIpAddress() + ":" + Settings.WEB_SERVER_PORT;
                Log.i(TAG, "Web server started at " + serverUrl);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start web server", e);
                serverUrl = null;
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
        running = false;
        serverUrl = null;
        Log.i(TAG, "Web server stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        String text = serverUrl != null ? serverUrl : "Starting…";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Remote Web Control")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_refresh)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Remote Web Control", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Lightning Launcher web interface");
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }

    private String getWifiIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return String.format("%d.%d.%d.%d",
                    ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }
}
