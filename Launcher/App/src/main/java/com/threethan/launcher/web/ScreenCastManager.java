package com.threethan.launcher.web;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCastManager {
    private static final String TAG = "ScreenCastManager";
    private static final int WIDTH = 1280, HEIGHT = 720, DPI = 320;

    private final MediaProjection projection;
    private final VirtualDisplay virtualDisplay;
    private final ImageReader imageReader;
    private volatile boolean active = true;

    private ScreenCastManager(MediaProjection projection) {
        this.projection = projection;
        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "ScreenCast",
                WIDTH, HEIGHT, DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    public static ScreenCastManager start(MediaProjection projection) {
        return new ScreenCastManager(projection);
    }

    public byte[] captureFrame() {
        if (!active) return null;
        Image image = null;
        try {
            // Poll for up to 200ms
            for (int i = 0; i < 20; i++) {
                image = imageReader.acquireLatestImage();
                if (image != null) break;
                try { Thread.sleep(10); } catch (InterruptedException e) { return null; }
            }
            if (image == null) return null;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();

            Bitmap bmp;
            if (pixelStride == 4 && rowStride == WIDTH * 4) {
                bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);
            } else {
                // Handle row padding
                byte[] rowData = new byte[rowStride];
                byte[] pixels = new byte[WIDTH * HEIGHT * 4];
                for (int row = 0; row < HEIGHT; row++) {
                    buffer.get(rowData, 0, rowStride);
                    for (int col = 0; col < WIDTH; col++) {
                        int srcIdx = col * pixelStride;
                        int dstIdx = (row * WIDTH + col) * 4;
                        pixels[dstIdx] = rowData[srcIdx];
                        pixels[dstIdx + 1] = rowData[srcIdx + 1];
                        pixels[dstIdx + 2] = rowData[srcIdx + 2];
                        pixels[dstIdx + 3] = rowData[srcIdx + 3];
                    }
                }
                bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                ByteBuffer wrapped = ByteBuffer.wrap(pixels);
                bmp.copyPixelsFromBuffer(wrapped);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out);
            bmp.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "captureFrame error", e);
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    public void stop() {
        if (!active) return;
        active = false;
        try { virtualDisplay.release(); } catch (Exception ignored) {}
        try { projection.stop(); } catch (Exception ignored) {}
        try { imageReader.close(); } catch (Exception ignored) {}
    }

    public boolean isActive() { return active; }
}
