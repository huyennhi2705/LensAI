package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoloDetector {
    private static final String TAG = "YoloDetector";
    public static class Detection {
        public final String label;
        public final float confidence;
        public final RectF box;
        Detection(String label, float confidence, RectF box) {
            this.label = label;
            this.confidence = confidence;
            this.box = box;
        }
    }
    private final Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();
    private final boolean nchw;
    private final int inW, inH;
    private final boolean outTransposed;
    private final int numClasses, numAnchors;
    private final float confThreshold;
    private final float iouThreshold;
    private final ByteBuffer inputBuffer;
    private final float[][][] output;
    private final int[] pixels;

    public YoloDetector(Context context, String modelPath, String labelPath,
                        float confThreshold, float iouThreshold) throws IOException {
        this.confThreshold = confThreshold;
        this.iouThreshold = iouThreshold;

        interpreter = new Interpreter(loadModelFile(context, modelPath),
                new Interpreter.Options().setNumThreads(4));

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open(labelPath)))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) labels.add(line);
            }
        }

        int[] in = interpreter.getInputTensor(0).shape();
        nchw = (in[1] == 3);
        inH = nchw ? in[2] : in[1];
        inW = nchw ? in[3] : in[2];
        inputBuffer = ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder());
        pixels = new int[inW * inH];
        int[] out = interpreter.getOutputTensor(0).shape();
        outTransposed = out[1] > out[2];
        numClasses = (outTransposed ? out[2] : out[1]) - 4;
        numAnchors = outTransposed ? out[1] : out[2];
        output = new float[1][out[1]][out[2]];
        Log.d(TAG, "input=" + java.util.Arrays.toString(in)
                + " output=" + java.util.Arrays.toString(out)
                + " labels=" + labels.size() + " nc=" + numClasses);
        if (labels.size() != numClasses) {
            Log.e(TAG, "labels.txt (" + labels.size() + ") != số lớp của model (" + numClasses + ")");
        }
    }

    public List<Detection> detect(Bitmap src) {
        int srcW = src.getWidth(), srcH = src.getHeight();

        // 1. Letterbox: giữ tỉ lệ, đệm xám 114 (giống lúc train/predict của Ultralytics)
        float scale = Math.min((float) inW / srcW, (float) inH / srcH);
        int newW = Math.round(srcW * scale), newH = Math.round(srcH * scale);
        int padX = (inW - newW) / 2, padY = (inH - newH) / 2;

        Bitmap canvasBmp = Bitmap.createBitmap(inW, inH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBmp);
        canvas.drawColor(Color.rgb(114, 114, 114));
        Bitmap scaled = Bitmap.createScaledBitmap(src, newW, newH, true);
        canvas.drawBitmap(scaled, padX, padY, null);
        if (scaled != src) scaled.recycle();
        canvasBmp.getPixels(pixels, 0, inW, 0, 0, inW, inH);
        canvasBmp.recycle();
        inputBuffer.rewind();
        int plane = inW * inH;
        if (nchw) {
            for (int i = 0; i < plane; i++) inputBuffer.putFloat(i * 4, ((pixels[i] >> 16) & 0xFF) / 255f);          // R
            for (int i = 0; i < plane; i++) inputBuffer.putFloat((plane + i) * 4, ((pixels[i] >> 8) & 0xFF) / 255f); // G
            for (int i = 0; i < plane; i++) inputBuffer.putFloat((2 * plane + i) * 4, (pixels[i] & 0xFF) / 255f);    // B
        } else {
            for (int p : pixels) {
                inputBuffer.putFloat(((p >> 16) & 0xFF) / 255f);
                inputBuffer.putFloat(((p >> 8) & 0xFF) / 255f);
                inputBuffer.putFloat((p & 0xFF) / 255f);
            }
        }
        inputBuffer.rewind();
        interpreter.run(inputBuffer, output);
        float[][] o = output[0];
        List<Detection> candidates = new ArrayList<>();
        for (int a = 0; a < numAnchors; a++) {
            int best = -1;
            float bestScore = 0f;
            for (int c = 0; c < numClasses; c++) {
                float s = outTransposed ? o[a][4 + c] : o[4 + c][a];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (best < 0 || bestScore < confThreshold) continue;
            float cx = outTransposed ? o[a][0] : o[0][a];
            float cy = outTransposed ? o[a][1] : o[1][a];
            float w  = outTransposed ? o[a][2] : o[2][a];
            float h  = outTransposed ? o[a][3] : o[3][a];

            if (cx <= 1.5f && cy <= 1.5f && w <= 1.5f && h <= 1.5f) {
                cx *= inW; w *= inW; cy *= inH; h *= inH;
            }
            float x1 = (cx - w / 2f - padX) / scale;
            float y1 = (cy - h / 2f - padY) / scale;
            float x2 = (cx + w / 2f - padX) / scale;
            float y2 = (cy + h / 2f - padY) / scale;
            RectF box = new RectF(
                    Math.max(0, x1), Math.max(0, y1),
                    Math.min(srcW, x2), Math.min(srcH, y2));

            String name = best < labels.size() ? labels.get(best) : "class_" + best;
            candidates.add(new Detection(name, bestScore, box));
        }
        return nms(candidates);
    }
    private List<Detection> nms(List<Detection> dets) {
        Collections.sort(dets, (a, b) -> Float.compare(b.confidence, a.confidence));
        List<Detection> keep = new ArrayList<>();
        for (Detection d : dets) {
            boolean ok = true;
            for (Detection k : keep) {
                if (k.label.equals(d.label) && iou(k.box, d.box) > iouThreshold) { ok = false; break; }
            }
            if (ok) keep.add(d);
            if (keep.size() >= 20) break;
        }
        return keep;
    }
    private static float iou(RectF a, RectF b) {
        float ix = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
        float iy = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        float inter = ix * iy;
        float union = a.width() * a.height() + b.width() * b.height() - inter;
        return union <= 0 ? 0 : inter / union;
    }
    private static MappedByteBuffer loadModelFile(Context ctx, String path) throws IOException {
        AssetFileDescriptor fd = ctx.getAssets().openFd(path);
        try (FileInputStream is = new FileInputStream(fd.getFileDescriptor())) {
            return is.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());
        }
    }
    public void close() {
        interpreter.close();
    }
}