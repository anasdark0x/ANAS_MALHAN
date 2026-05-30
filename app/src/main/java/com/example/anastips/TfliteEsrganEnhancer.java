package com.example.anastips;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

public final class TfliteEsrganEnhancer {
    private static final String MODEL_PATH = "models/esrgan.tflite";
    private static final int INPUT_SIZE = 50;
    private static final int OUTPUT_SIZE = 200;
    private static final int CHANNELS = 3;

    private TfliteEsrganEnhancer() {
    }

    public static boolean isAvailable(Context context) {
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(MODEL_PATH);
            afd.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Bitmap enhanceSameResolution(Context context, Bitmap source, AiImageEnhancer.ProgressListener listener) {
        if (source == null) return null;
        Interpreter interpreter = null;
        try {
            if (listener != null) listener.onProgress("Real ESRGAN: loading embedded TFLite model...");
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
            interpreter = new Interpreter(loadModel(context), options);

            int width = source.getWidth();
            int height = source.getHeight();
            Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas outCanvas = new Canvas(outputBitmap);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

            int tilesX = (width + INPUT_SIZE - 1) / INPUT_SIZE;
            int tilesY = (height + INPUT_SIZE - 1) / INPUT_SIZE;
            int totalTiles = Math.max(1, tilesX * tilesY);
            int done = 0;

            ByteBuffer input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * 4);
            input.order(ByteOrder.nativeOrder());
            float[][][][] modelOutput = new float[1][OUTPUT_SIZE][OUTPUT_SIZE][CHANNELS];
            int[] outputPixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];

            for (int y = 0; y < height; y += INPUT_SIZE) {
                int tileH = Math.min(INPUT_SIZE, height - y);
                for (int x = 0; x < width; x += INPUT_SIZE) {
                    int tileW = Math.min(INPUT_SIZE, width - x);
                    Bitmap tile = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
                    Canvas tileCanvas = new Canvas(tile);
                    Rect srcRect = new Rect(x, y, x + tileW, y + tileH);
                    RectF dstRect = new RectF(0, 0, INPUT_SIZE, INPUT_SIZE);
                    tileCanvas.drawColor(Color.BLACK);
                    tileCanvas.drawBitmap(source, srcRect, dstRect, paint);

                    input.rewind();
                    for (int ty = 0; ty < INPUT_SIZE; ty++) {
                        for (int tx = 0; tx < INPUT_SIZE; tx++) {
                            int c = tile.getPixel(tx, ty);
                            input.putFloat(Color.red(c));
                            input.putFloat(Color.green(c));
                            input.putFloat(Color.blue(c));
                        }
                    }

                    interpreter.run(input, modelOutput);

                    int i = 0;
                    for (int oy = 0; oy < OUTPUT_SIZE; oy++) {
                        for (int ox = 0; ox < OUTPUT_SIZE; ox++) {
                            int r = clamp(Math.round(modelOutput[0][oy][ox][0]));
                            int g = clamp(Math.round(modelOutput[0][oy][ox][1]));
                            int b = clamp(Math.round(modelOutput[0][oy][ox][2]));
                            outputPixels[i++] = Color.rgb(r, g, b);
                        }
                    }

                    Bitmap srTile = Bitmap.createBitmap(outputPixels, OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
                    Bitmap sameSizeTile = Bitmap.createScaledBitmap(srTile, tileW, tileH, true);
                    outCanvas.drawBitmap(sameSizeTile, x, y, paint);

                    tile.recycle();
                    srTile.recycle();
                    sameSizeTile.recycle();

                    done++;
                    if (listener != null && (done == 1 || done % 20 == 0 || done == totalTiles)) {
                        int percent = (int) ((100L * done) / totalTiles);
                        listener.onProgress(String.format(Locale.US,
                                "Real ESRGAN model: %d%% (%d/%d tiles)", percent, done, totalTiles));
                    }
                }
            }

            if (listener != null) listener.onProgress("Real ESRGAN: final polish...");
            AiImageEnhancer.enhance(outputBitmap, AiImageEnhancer.Mode.MONSTER_AI, listener);
            return outputBitmap;
        } catch (Throwable t) {
            if (listener != null) listener.onProgress("Real ESRGAN unavailable: " + t.getMessage());
            return null;
        } finally {
            if (interpreter != null) interpreter.close();
        }
    }

    private static MappedByteBuffer loadModel(Context context) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
