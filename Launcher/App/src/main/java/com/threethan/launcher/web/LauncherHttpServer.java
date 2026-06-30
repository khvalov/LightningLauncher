package com.threethan.launcher.web;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.metadata.IconLoader;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.Launch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class LauncherHttpServer extends NanoHTTPD {
    private static final String TAG = "LauncherHttpServer";
    private static final Gson GSON = new Gson();

    public LauncherHttpServer() {
        super(Settings.WEB_SERVER_PORT);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        try {
            if (uri.equals("/") || uri.equals("/index.html")) {
                return serveAsset("web/index.html", "text/html");
            }
            if (uri.equals("/api/apps") && method == Method.GET) {
                return serveApps();
            }
            if (uri.equals("/api/launch") && method == Method.POST) {
                return serveLaunch(session);
            }
            if (uri.startsWith("/api/icon/") && method == Method.GET) {
                String packageName = URLDecoder.decode(uri.substring("/api/icon/".length()), "UTF-8");
                return serveIcon(packageName);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        } catch (Exception e) {
            Log.e(TAG, "Error serving " + uri, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private Response serveAsset(String path, String mimeType) {
        try {
            InputStream is = Core.context().getAssets().open(path);
            return newChunkedResponse(Response.Status.OK, mimeType, is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found: " + path);
        }
    }

    private Response serveApps() {
        List<Map<String, String>> result = new ArrayList<>();
        for (ApplicationInfo app : PlatformExt.apps) {
            App.Type type = App.getType(app);
            if (type == App.Type.UNSUPPORTED || type == App.Type.UTILITY) continue;
            Map<String, String> entry = new java.util.HashMap<>();
            entry.put("packageName", app.packageName);
            entry.put("label", SettingsManager.getAppLabel(app));
            entry.put("type", type.name());
            result.add(entry);
        }
        result.sort((a, b) -> a.get("label").compareToIgnoreCase(b.get("label")));
        return newFixedLengthResponse(Response.Status.OK, "application/json", GSON.toJson(result));
    }

    private Response serveLaunch(IHTTPSession session) throws IOException, NanoHTTPD.ResponseException {
        Map<String, String> body = new java.util.HashMap<>();
        session.parseBody(body);
        String json = body.get("postData");
        if (json == null || json.isEmpty()) {
            return jsonError("Missing request body");
        }
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        if (obj == null || !obj.has("packageName")) {
            return jsonError("Missing packageName");
        }
        String packageName = obj.get("packageName").getAsString();

        ApplicationInfo appInfo = findApp(packageName);
        if (appInfo == null) {
            return jsonError("App not found: " + packageName);
        }

        LauncherActivity fg = LauncherActivity.getForegroundInstance();
        if (fg != null) {
            fg.runOnUiThread(() -> LaunchExt.launchApp(fg, appInfo));
        } else {
            android.content.Intent intent = Launch.getVrOsLaunchIntent(packageName);
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                Core.context().startActivity(intent);
            } else {
                android.content.Intent fallback = Launch.getLaunchIntent(appInfo);
                if (fallback != null) {
                    fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    Core.context().startActivity(fallback);
                } else {
                    return jsonError("No launch intent found for: " + packageName);
                }
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
    }

    private Response serveIcon(String packageName) {
        ApplicationInfo appInfo = findApp(packageName);
        if (appInfo != null) {
            // Try the icon cache file first (fast path)
            File cacheFile = IconLoader.iconCacheFileForApp(appInfo);
            if (cacheFile.exists()) {
                try {
                    return newChunkedResponse(Response.Status.OK, "image/webp", new FileInputStream(cacheFile));
                } catch (IOException ignored) {}
            }
            // Fallback: decode from PackageManager and return as PNG
            try {
                PackageManager pm = Core.context().getPackageManager();
                Drawable drawable = pm.getApplicationIcon(appInfo);
                Bitmap bmp = drawableToBitmap(drawable);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                byte[] bytes = out.toByteArray();
                return newFixedLengthResponse(Response.Status.OK, "image/png",
                        new ByteArrayInputStream(bytes), bytes.length);
            } catch (Exception e) {
                Log.w(TAG, "Failed to load icon for " + packageName, e);
            }
        }
        // Return a 1x1 transparent PNG if we can't find the icon
        byte[] empty = {(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0,0,0,13,73,72,68,82,
                0,0,0,1,0,0,0,1,8,6,0,0,0,0x1F,0x15,(byte)0xC4,(byte)0x89,0,0,0,11,73,68,65,
                84,8,(byte)0x99,99,96,96,(byte)0xF8,0x0F,0,0,2,1,0x01,(byte)0xE2,0x21,(byte)0xBC,
                0x33,0,0,0,0,73,69,78,68,(byte)0xAE,0x42,0x60,(byte)0x82};
        return newFixedLengthResponse(Response.Status.OK, "image/png",
                new ByteArrayInputStream(empty), empty.length);
    }

    private ApplicationInfo findApp(String packageName) {
        for (ApplicationInfo app : PlatformExt.apps) {
            if (packageName.equals(app.packageName)) return app;
        }
        return null;
    }

    private Response jsonError(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", false);
        obj.addProperty("error", message);
        return newFixedLengthResponse(Response.Status.OK, "application/json", GSON.toJson(obj));
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        int size = 128;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bmp;
    }
}
