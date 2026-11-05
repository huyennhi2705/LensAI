package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.example.lensai.model.AIResult;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On-device DETECTOR cho model YOLO11n (101 lớp) export từ Ultralytics.
 *
 * Model: input  [1, 3, 640, 640] float32 (NCHW, RGB, /255)
 *        output [1, 105, 8400]   = 4 (cx, cy, w, h) + 101 điểm số lớp (không có objectness)
 *
 * Kích thước input và layout được đọc từ chính model nên nếu sau này export lại
 * với imgsz khác (ví dụ 224) thì code vẫn chạy mà không cần sửa.
 */
public class OnDeviceDetector {

    private static final String TAG = "OnDeviceDetector";
    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";

    /** Kích thước tiền xử lý để LƯU / HIỂN THỊ ảnh chụp theo yêu cầu đề tài. */
    public static final int PREPROCESS_SIZE = 224;

    private static final float CONF_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final int MAX_RESULTS = 10;

    /** Một đối tượng phát hiện được, box tính theo pixel của ảnh gốc. */
    public static class Detection {
        public final String label;
        public final int classId;
        public final float score;
        public final RectF box;

        Detection(String label, int classId, float score, RectF box) {
            this.label = label;
            this.classId = classId;
            this.score = score;
            this.box = box;
        }
    }

    private static class Cand {
        float cx, cy, w, h, score;
        int cls;
    }

    private final Interpreter interpreter;
    private final List<String> labels;

    private final boolean nchw;
    private final int inW, inH;
    private final boolean featuresFirst; // true: [1, 4+nc, anchors]; false: [1, anchors, 4+nc]
    private final int numAnchors;

    private final ByteBuffer inputBuffer;
    private final int[] pixels;
    private final float[][][] output;

    public OnDeviceDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(context, MODEL_PATH), options);
        labels = FileUtil.loadLabels(context, LABEL_PATH);

        Tensor in = interpreter.getInputTensor(0);
        Tensor out = interpreter.getOutputTensor(0);
        int[] inShape = in.shape();
        int[] outShape = out.shape();

        Log.d(TAG, "Input shape: " + Arrays.toString(inShape) + " type=" + in.dataType());
        Log.d(TAG, "Output shape: " + Arrays.toString(outShape) + " type=" + out.dataType());
        Log.d(TAG, "Labels: " + labels.size());

        if (in.dataType() != DataType.FLOAT32) {
            throw new IOException("Model phải là float32, hiện là " + in.dataType());
        }
        if (inShape.length != 4) {
            throw new IOException("Input shape không hợp lệ: " + Arrays.toString(inShape));
        }

        // [1,3,H,W] => NCHW ; [1,H,W,3] => NHWC
        nchw = inShape[1] == 3;
        inH = nchw ? inShape[2] : inShape[1];
        inW = nchw ? inShape[3] : inShape[2];

        // Output YOLO: 4 + số lớp
        int attrs = 4 + labels.size();
        if (outShape.length != 3) {
            throw new IOException("Output shape không phải dạng YOLO: " + Arrays.toString(outShape));
        }
        if (outShape[1] == attrs) {
            featuresFirst = true;
            numAnchors = outShape[2];
        } else if (outShape[2] == attrs) {
            featuresFirst = false;
            numAnchors = outShape[1];
        } else {
            throw new IOException("Output " + Arrays.toString(outShape)
                    + " không khớp 4 + " + labels.size() + " lớp. Kiểm tra lại labels.txt.");
        }

        inputBuffer = ByteBuffer.allocateDirect(4 * 3 * inW * inH).order(ByteOrder.nativeOrder());
        pixels = new int[inW * inH];
        output = new float[outShape[0]][outShape[1]][outShape[2]];
    }

    /** Tiền xử lý ảnh chụp về 224x224 để lưu / hiển thị. KHÔNG đưa ảnh này vào detector. */
    public static Bitmap preprocess(Bitmap src) {
        return Bitmap.createScaledBitmap(src, PREPROCESS_SIZE, PREPROCESS_SIZE, true);
    }

    /** API cũ, giữ nguyên chữ ký: mỗi lớp chỉ giữ kết quả có điểm cao nhất. */
    public List<AIResult> detect(Bitmap bitmap) {
        List<AIResult> results = new ArrayList<>();
        try {
            Set<String> seen = new HashSet<>();
            for (Detection d : detectBoxes(bitmap)) {
                if (!seen.add(d.label)) continue;
                results.add(new AIResult(d.label, d.score, "tflite"));
                if (results.size() >= MAX_RESULTS) break;
            }
        } catch (Exception e) {
            Log.e(TAG, "detect error", e);
        }
        return results;
    }

    /** Trả về đầy đủ box để vẽ lên ảnh. Truyền ảnh GỐC (độ phân giải đầy đủ). */
    public synchronized List<Detection> detectBoxes(Bitmap src) {
        List<Detection> result = new ArrayList<>();
        if (src == null) return result;

        int ow = src.getWidth();
        int oh = src.getHeight();
        float scale = Math.min(inW / (float) ow, inH / (float) oh);
        int nw = Math.round(ow * scale);
        int nh = Math.round(oh * scale);
        float padX = (inW - nw) / 2f;
        float padY = (inH - nh) / 2f;

        Bitmap letterboxed = letterbox(src, nw, nh, padX, padY);
        fillInput(letterboxed);
        letterboxed.recycle();

        inputBuffer.rewind();
        interpreter.run(inputBuffer, output);

        // ---- Giải mã output ----
        float[][] o = output[0];
        int nc = labels.size();
        List<Cand> cands = new ArrayList<>();
        float maxCoord = 0f;

        for (int i = 0; i < numAnchors; i++) {
            int best = -1;
            float bestScore = 0f;
            for (int c = 0; c < nc; c++) {
                float s = featuresFirst ? o[4 + c][i] : o[i][4 + c];
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            if (best < 0 || bestScore < CONF_THRESHOLD) continue;

            Cand cd = new Cand();
            cd.cx = featuresFirst ? o[0][i] : o[i][0];
            cd.cy = featuresFirst ? o[1][i] : o[i][1];
            cd.w = featuresFirst ? o[2][i] : o[i][2];
            cd.h = featuresFirst ? o[3][i] : o[i][3];
            cd.score = bestScore;
            cd.cls = best;
            maxCoord = Math.max(maxCoord, Math.max(Math.max(cd.cx, cd.cy), Math.max(cd.w, cd.h)));
            cands.add(cd);
        }

        // Một số bản export cho toạ độ chuẩn hoá 0..1, số khác cho pixel 0..640
        boolean normalized = !cands.isEmpty() && maxCoord <= 1.5f;

        List<Detection> raw = new ArrayList<>();
        for (Cand cd : cands) {
            float cx = normalized ? cd.cx * inW : cd.cx;
            float cy = normalized ? cd.cy * inH : cd.cy;
            float w = normalized ? cd.w * inW : cd.w;
            float h = normalized ? cd.h * inH : cd.h;

            // Về toạ độ ảnh gốc (bỏ padding, chia scale)
            float x1 = clamp((cx - w / 2f - padX) / scale, 0, ow);
            float y1 = clamp((cy - h / 2f - padY) / scale, 0, oh);
            float x2 = clamp((cx + w / 2f - padX) / scale, 0, ow);
            float y2 = clamp((cy + h / 2f - padY) / scale, 0, oh);
            if (x2 <= x1 || y2 <= y1) continue;

            raw.add(new Detection(labels.get(cd.cls), cd.cls, cd.score, new RectF(x1, y1, x2, y2)));
        }

        result = nms(raw);
        Log.d(TAG, "Detected " + result.size() + " objects (normalized=" + normalized + ")");
        for (Detection d : result) {
            Log.d(TAG, "  " + d.label + " " + d.score + " " + d.box);
        }
        return result;
    }

    // ------------------------------------------------------------------

    private Bitmap letterbox(Bitmap src, int nw, int nh, float padX, float padY) {
        Bitmap out = Bitmap.createBitmap(inW, inH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(114, 114, 114)); // màu đệm giống Ultralytics
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, null, new RectF(padX, padY, padX + nw, padY + nh), paint);
        return out;
    }

    private void fillInput(Bitmap bmp) {
        bmp.getPixels(pixels, 0, inW, 0, 0, inW, inH);
        inputBuffer.rewind();
        FloatBuffer fb = inputBuffer.asFloatBuffer();
        if (nchw) {
            // Toàn bộ R, rồi toàn bộ G, rồi toàn bộ B
            for (int c = 0; c < 3; c++) {
                int shift = 16 - 8 * c;
                for (int p : pixels) {
                    fb.put(((p >> shift) & 0xFF) / 255f);
                }
            }
        } else {
            for (int p : pixels) {
                fb.put(((p >> 16) & 0xFF) / 255f);
                fb.put(((p >> 8) & 0xFF) / 255f);
                fb.put((p & 0xFF) / 255f);
            }
        }
    }

    /** NMS theo từng lớp. */
    private static List<Detection> nms(List<Detection> dets) {
        Collections.sort(dets, (a, b) -> Float.compare(b.score, a.score));
        List<Detection> kept = new ArrayList<>();
        for (Detection d : dets) {
            boolean suppressed = false;
            for (Detection k : kept) {
                if (k.classId == d.classId && iou(k.box, d.box) > IOU_THRESHOLD) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) kept.add(d);
        }
        return kept;
    }

    private static float iou(RectF a, RectF b) {
        float x1 = Math.max(a.left, b.left);
        float y1 = Math.max(a.top, b.top);
        float x2 = Math.min(a.right, b.right);
        float y2 = Math.min(a.bottom, b.bottom);
        float inter = Math.max(0f, x2 - x1) * Math.max(0f, y2 - y1);
        float union = a.width() * a.height() + b.width() * b.height() - inter;
        return union <= 0f ? 0f : inter / union;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }

    private static MappedByteBuffer loadModelFile(Context context, String path) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(path);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }
}