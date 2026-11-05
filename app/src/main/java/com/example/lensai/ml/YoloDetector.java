package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.DataType;
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
    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";

    public static final float CONF_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final int MAX_DETECTIONS = 5;

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
    private final List<String> labels;
    private final int inputW, inputH;
    private final boolean nchw;

    public YoloDetector(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context), new Interpreter.Options().setNumThreads(4));
        labels = loadLabels(context);

        int[] in = interpreter.getInputTensor(0).shape();
        if (interpreter.getInputTensor(0).dataType() != DataType.FLOAT32) {
            throw new IOException("Model phải là float32 (export không bật int8). Hiện tại: "
                    + interpreter.getInputTensor(0).dataType());
        }
        nchw = in[1] == 3;
        inputH = nchw ? in[2] : in[1];
        inputW = nchw ? in[3] : in[2];

        Log.d(TAG, "Loaded. input=" + inputW + "x" + inputH + " nchw=" + nchw
                + " out=" + java.util.Arrays.toString(interpreter.getOutputTensor(0).shape())
                + " labels=" + labels.size());
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<Detection> detect(Bitmap src) {
        final int srcW = src.getWidth(), srcH = src.getHeight();

        // 1. Letterbox giữ tỉ lệ, đệm xám 114 như Ultralytics
        float scale = Math.min(inputW / (float) srcW, inputH / (float) srcH);
        int newW = Math.round(srcW * scale), newH = Math.round(srcH * scale);
        float padX = (inputW - newW) / 2f, padY = (inputH - newH) / 2f;

        Bitmap boxed = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(boxed);
        canvas.drawColor(Color.rgb(114, 114, 114));
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, null, new RectF(padX, padY, padX + newW, padY + newH), paint);

        // 2. Bitmap -> float32 /255
        int[] px = new int[inputW * inputH];
        boxed.getPixels(px, 0, inputW, 0, 0, inputW, inputH);
        boxed.recycle();

        ByteBuffer input = ByteBuffer.allocateDirect(4 * 3 * inputW * inputH).order(ByteOrder.nativeOrder());
        if (nchw) {
            int plane = inputW * inputH;
            float[] chw = new float[3 * plane];
            for (int i = 0; i < plane; i++) {
                int p = px[i];
                chw[i] = ((p >> 16) & 0xFF) / 255f;
                chw[plane + i] = ((p >> 8) & 0xFF) / 255f;
                chw[2 * plane + i] = (p & 0xFF) / 255f;
            }
            input.asFloatBuffer().put(chw);
        } else {
            for (int p : px) {
                input.putFloat(((p >> 16) & 0xFF) / 255f);
                input.putFloat(((p >> 8) & 0xFF) / 255f);
                input.putFloat((p & 0xFF) / 255f);
            }
        }
        input.rewind();

        // 3. Chạy model
        int[] os = interpreter.getOutputTensor(0).shape();
        float[][][] out = new float[1][os[1]][os[2]];
        interpreter.run(input, out);

        // 4. Giải mã
        boolean channelsFirst = os[1] < os[2]; // [1, 4+nc, N]
        int numAnchors = channelsFirst ? os[2] : os[1];
        int numCh = channelsFirst ? os[1] : os[2];
        int nc = numCh - 4;
        if (nc <= 0) {
            Log.e(TAG, "Output shape lạ: " + java.util.Arrays.toString(os));
            return new ArrayList<>();
        }
        if (nc != labels.size()) {
            Log.w(TAG, "Model có " + nc + " lớp nhưng labels.txt có " + labels.size() + " dòng");
        }

        // Toạ độ thường chuẩn hoá 0..1; một số bản export dùng pixel
        float maxCoord = 0f;
        for (int a = 0; a < numAnchors; a++) {
            maxCoord = Math.max(maxCoord, channelsFirst ? out[0][2][a] : out[0][a][2]);
        }
        float coordScaleX = maxCoord <= 2f ? inputW : 1f;
        float coordScaleY = maxCoord <= 2f ? inputH : 1f;

        List<Detection> cands = new ArrayList<>();
        for (int a = 0; a < numAnchors; a++) {
            int best = -1;
            float bestScore = 0f;
            for (int c = 0; c < nc; c++) {
                float s = channelsFirst ? out[0][4 + c][a] : out[0][a][4 + c];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (best < 0 || bestScore < CONF_THRESHOLD) continue;

            float cx = (channelsFirst ? out[0][0][a] : out[0][a][0]) * coordScaleX;
            float cy = (channelsFirst ? out[0][1][a] : out[0][a][1]) * coordScaleY;
            float w  = (channelsFirst ? out[0][2][a] : out[0][a][2]) * coordScaleX;
            float h  = (channelsFirst ? out[0][3][a] : out[0][a][3]) * coordScaleY;

            float x1 = clamp((cx - w / 2f - padX) / scale, 0, srcW);
            float y1 = clamp((cy - h / 2f - padY) / scale, 0, srcH);
            float x2 = clamp((cx + w / 2f - padX) / scale, 0, srcW);
            float y2 = clamp((cy + h / 2f - padY) / scale, 0, srcH);

            cands.add(new Detection(nameOf(best), bestScore, new RectF(x1, y1, x2, y2)));
        }

        return nms(cands);
    }

    private List<Detection> nms(List<Detection> cands) {
        Collections.sort(cands, (a, b) -> Float.compare(b.confidence, a.confidence));
        List<Detection> keep = new ArrayList<>();
        for (Detection d : cands) {
            boolean drop = false;
            for (Detection k : keep) {
                if (k.label.equals(d.label) && iou(k.box, d.box) > IOU_THRESHOLD) { drop = true; break; }
            }
            if (!drop) {
                keep.add(d);
                if (keep.size() >= MAX_DETECTIONS) break;
            }
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

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String nameOf(int idx) {
        return idx < labels.size() ? labels.get(idx) : "cls" + idx;
    }

    private static List<String> loadLabels(Context ctx) throws IOException {
        List<String> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open(LABEL_PATH), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) list.add(line);
            }
        }
        return list;
    }

    private static MappedByteBuffer loadModelFile(Context ctx) throws IOException {
        AssetFileDescriptor fd = ctx.getAssets().openFd(MODEL_PATH);
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            return in.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    public void close() {
        interpreter.close();
    }
}