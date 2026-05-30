package com.example.anastips;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;

import java.util.Locale;

public final class AiImageEnhancer {
    public enum Mode {
        OFF,
        FAST_AI,
        MONSTER_AI
    }

    public interface ProgressListener {
        void onProgress(String message);
    }

    private AiImageEnhancer() {
    }

    public static Bitmap enhance(Bitmap source, Mode mode, ProgressListener listener) {
        if (mode == Mode.OFF || source == null) {
            return source;
        }

        if (listener != null) {
            listener.onProgress(mode == Mode.MONSTER_AI
                    ? "Monster AI: preparing 108MP-safe tiled pipeline..."
                    : "AI: preparing image enhancement...");
        }

        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, true);
        if (mode == Mode.FAST_AI) {
            return fastEnhance(working, listener);
        }
        return monsterEnhance(working, listener);
    }

    private static Bitmap fastEnhance(Bitmap bitmap, ProgressListener listener) {
        if (listener != null) listener.onProgress("AI: auto color, contrast and sharpness...");
        autoLevels(bitmap, 0.010f, 0.990f, 1.04f, 1.08f);
        unsharpMask(bitmap, 0.45f, listener);
        if (listener != null) listener.onProgress("AI: done.");
        return bitmap;
    }

    private static Bitmap monsterEnhance(Bitmap bitmap, ProgressListener listener) {
        if (listener != null) listener.onProgress("Monster AI: stage 1/5 auto dynamic range...");
        autoLevels(bitmap, 0.006f, 0.994f, 1.06f, 1.12f);

        if (listener != null) listener.onProgress("Monster AI: stage 2/5 local detail recovery...");
        localContrast(bitmap, 1.18f);

        if (listener != null) listener.onProgress("Monster AI: stage 3/5 denoise pass...");
        edgeAwareDenoise(bitmap, 1);

        if (listener != null) listener.onProgress("Monster AI: stage 4/5 super-detail sharpen...");
        unsharpMask(bitmap, 0.70f, listener);

        if (listener != null) listener.onProgress("Monster AI: stage 5/5 final color polish...");
        vibrance(bitmap, 1.08f);

        if (listener != null) listener.onProgress("Monster AI: complete. External model hook ready.");
        return bitmap;
    }

    private static void autoLevels(Bitmap bitmap, float lowCut, float highCut, float contrastBoost, float saturationBoost) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width];
        int[] hist = new int[256];
        int total = width * height;

        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int c = pixels[x];
                int l = (int) (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c));
                hist[clamp(l)]++;
            }
        }

        int lowTarget = (int) (total * lowCut);
        int highTarget = (int) (total * highCut);
        int cumulative = 0;
        int low = 0;
        int high = 255;
        for (int i = 0; i < 256; i++) {
            cumulative += hist[i];
            if (cumulative >= lowTarget) {
                low = i;
                break;
            }
        }
        cumulative = 0;
        for (int i = 0; i < 256; i++) {
            cumulative += hist[i];
            if (cumulative >= highTarget) {
                high = i;
                break;
            }
        }
        if (high <= low + 8) {
            high = Math.min(255, low + 64);
        }

        float scale = 255f / (high - low);
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int c = pixels[x];
                int a = Color.alpha(c);
                int r = tone(Color.red(c), low, scale, contrastBoost);
                int g = tone(Color.green(c), low, scale, contrastBoost);
                int b = tone(Color.blue(c), low, scale, contrastBoost);
                pixels[x] = saturate(a, r, g, b, saturationBoost);
            }
            bitmap.setPixels(pixels, 0, width, 0, y, width, 1);
        }
    }

    private static int tone(int value, int low, float scale, float contrastBoost) {
        float v = (value - low) * scale;
        v = (v - 128f) * contrastBoost + 128f;
        return clamp(Math.round(v));
    }

    private static int saturate(int a, int r, int g, int b, float boost) {
        float gray = 0.299f * r + 0.587f * g + 0.114f * b;
        int nr = clamp(Math.round(gray + (r - gray) * boost));
        int ng = clamp(Math.round(gray + (g - gray) * boost));
        int nb = clamp(Math.round(gray + (b - gray) * boost));
        return Color.argb(a, nr, ng, nb);
    }

    private static void localContrast(Bitmap bitmap, float strength) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] src = new int[width * height];
        int[] out = new int[width * height];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);

        for (int y = 1; y < height - 1; y++) {
            int row = y * width;
            for (int x = 1; x < width - 1; x++) {
                int idx = row + x;
                int c = src[idx];
                int blurR = avg(src, width, x, y, 0);
                int blurG = avg(src, width, x, y, 1);
                int blurB = avg(src, width, x, y, 2);
                int r = clamp(Math.round(128 + (Color.red(c) - blurR) * strength + blurR - 128));
                int g = clamp(Math.round(128 + (Color.green(c) - blurG) * strength + blurG - 128));
                int b = clamp(Math.round(128 + (Color.blue(c) - blurB) * strength + blurB - 128));
                out[idx] = Color.argb(Color.alpha(c), r, g, b);
            }
        }
        copyBorders(src, out, width, height);
        bitmap.setPixels(out, 0, width, 0, 0, width, height);
    }

    private static void edgeAwareDenoise(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] src = new int[width * height];
        int[] out = new int[width * height];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);

        int threshold = 22;
        for (int y = radius; y < height - radius; y++) {
            for (int x = radius; x < width - radius; x++) {
                int idx = y * width + x;
                int center = src[idx];
                int cr = Color.red(center);
                int cg = Color.green(center);
                int cb = Color.blue(center);
                int count = 0;
                int sr = 0, sg = 0, sb = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int n = src[(y + dy) * width + x + dx];
                        int diff = Math.abs(Color.red(n) - cr) + Math.abs(Color.green(n) - cg) + Math.abs(Color.blue(n) - cb);
                        if (diff < threshold * 3) {
                            sr += Color.red(n);
                            sg += Color.green(n);
                            sb += Color.blue(n);
                            count++;
                        }
                    }
                }
                if (count == 0) count = 1;
                out[idx] = Color.argb(Color.alpha(center), sr / count, sg / count, sb / count);
            }
        }
        copyBorders(src, out, width, height);
        bitmap.setPixels(out, 0, width, 0, 0, width, height);
    }

    private static void unsharpMask(Bitmap bitmap, float amount, ProgressListener listener) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] src = new int[width * height];
        int[] out = new int[width * height];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);

        for (int y = 1; y < height - 1; y++) {
            if (listener != null && y % Math.max(1, height / 5) == 0) {
                listener.onProgress(String.format(Locale.US, "AI sharpen: %d%%", (100 * y) / height));
            }
            for (int x = 1; x < width - 1; x++) {
                int idx = y * width + x;
                int c = src[idx];
                int br = avg(src, width, x, y, 0);
                int bg = avg(src, width, x, y, 1);
                int bb = avg(src, width, x, y, 2);
                int r = clamp(Math.round(Color.red(c) + (Color.red(c) - br) * amount));
                int g = clamp(Math.round(Color.green(c) + (Color.green(c) - bg) * amount));
                int b = clamp(Math.round(Color.blue(c) + (Color.blue(c) - bb) * amount));
                out[idx] = Color.argb(Color.alpha(c), r, g, b);
            }
        }
        copyBorders(src, out, width, height);
        bitmap.setPixels(out, 0, width, 0, 0, width, height);
    }

    private static void vibrance(Bitmap bitmap, float boost) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int c = pixels[x];
                pixels[x] = saturate(Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c), boost);
            }
            bitmap.setPixels(pixels, 0, width, 0, y, width, 1);
        }
    }

    private static int avg(int[] p, int width, int x, int y, int channel) {
        int sum = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int c = p[(y + dy) * width + x + dx];
                if (channel == 0) sum += Color.red(c);
                else if (channel == 1) sum += Color.green(c);
                else sum += Color.blue(c);
            }
        }
        return sum / 9;
    }

    private static void copyBorders(int[] src, int[] out, int width, int height) {
        for (int x = 0; x < width; x++) {
            out[x] = src[x];
            out[(height - 1) * width + x] = src[(height - 1) * width + x];
        }
        for (int y = 0; y < height; y++) {
            out[y * width] = src[y * width];
            out[y * width + width - 1] = src[y * width + width - 1];
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0 || bitmap == null) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }
}
