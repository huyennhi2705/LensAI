package com.example.lensai.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoloDetector {

    private static final String TAG = "YoloDetector";

    private static final int INPUT_SIZE = 640;
    private static final int NUM_CLASSES = 101;      // 105 - 4
    private static final float CONF_THRESHOLD = 0.40f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final int MAX_RESULTS = 15;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final ByteBuffer inputBuffer;
    private final float[][][] output = new float[1][105][8400];

    public YoloDetector(Context context, String modelPath, String labelPath) throws IOException {
        interpreter = new Interpreter(loadModelFile(context, modelPath));
        labels = FileUtil.loadLabels(context, labelPath);

        inputBuffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        Log.d(TAG, "Model loaded. Labels count: " + labels.size());
    }

    public List<Detection> detect(Bitmap bitmap) {
        List<Detection> results = new ArrayList<>();

        try {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            fillInputBuffer(resized);

            Object[] inputs = {inputBuffer};
            java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
            outputs.put(0, output);

            interpreter.runForMultipleInputsOutputs(inputs, outputs);

            results = postProcess(bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            Log.e(TAG, "Detect error", e);
        }

        return results;
    }

    private void fillInputBuffer(Bitmap bitmap) {
        inputBuffer.rewind();
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // NCHW + normalize 0-1
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                for (int j = 0; j < INPUT_SIZE; j++) {
                    int pixel = pixels[i * INPUT_SIZE + j];
                    float value;
                    if (c == 0) value = ((pixel >> 16) & 0xFF) / 255.0f; // R
                    else if (c == 1) value = ((pixel >> 8) & 0xFF) / 255.0f;  // G
                    else value = (pixel & 0xFF) / 255.0f;                     // B
                    inputBuffer.putFloat(value);
                }
            }
        }
    }

    private List<Detection> postProcess(int origW, int origH) {
        List<Detection> detections = new ArrayList<>();
        float scaleX = (float) origW / INPUT_SIZE;
        float scaleY = (float) origH / INPUT_SIZE;

        for (int i = 0; i < 8400; i++) {
            float maxScore = 0f;
            int classId = -1;

            for (int c = 0; c < NUM_CLASSES; c++) {
                float score = output[0][4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    classId = c;
                }
            }

            if (maxScore < CONF_THRESHOLD || classId < 0) continue;

            float x = output[0][0][i];
            float y = output[0][1][i];
            float w = output[0][2][i];
            float h = output[0][3][i];

            float left   = (x - w / 2f) * scaleX;
            float top    = (y - h / 2f) * scaleY;
            float right  = (x + w / 2f) * scaleX;
            float bottom = (y + h / 2f) * scaleY;

            left   = Math.max(0, left);
            top    = Math.max(0, top);
            right  = Math.min(origW, right);
            bottom = Math.min(origH, bottom);

            if (classId < labels.size()) {
                detections.add(new Detection(
                        labels.get(classId),
                        maxScore,
                        new RectF(left, top, right, bottom)
                ));
            }
        }

        return nms(detections);
    }

    private List<Detection> nms(List<Detection> detections) {
        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> result = new ArrayList<>();
        boolean[] removed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (removed[i]) continue;
            result.add(detections.get(i));
            if (result.size() >= MAX_RESULTS) break;

            for (int j = i + 1; j < detections.size(); j++) {
                if (removed[j]) continue;
                if (iou(detections.get(i).box, detections.get(j).box) > IOU_THRESHOLD) {
                    removed[j] = true;
                }
            }
        }
        return result;
    }

    private float iou(RectF a, RectF b) {
        float interLeft   = Math.max(a.left, b.left);
        float interTop    = Math.max(a.top, b.top);
        float interRight  = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);

        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        android.content.res.AssetFileDescriptor fd = context.getAssets().openFd(modelPath);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    // ===== Class Detection =====
    public static class Detection {
        public final String label;
        public final float confidence;
        public final RectF box;

        public Detection(String label, float confidence, RectF box) {
            this.label = label;
            this.confidence = confidence;
            this.box = box;
        }
    }
    /**
     * Đọc ảnh từ file và xoay đúng chiều theo EXIF
     */
    public static Bitmap decodeFileWithExif(String path) {
        try {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, options);
            if (bitmap == null) return null;

            android.media.ExifInterface exif = new android.media.ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);

            int rotation = 0;
            switch (orientation) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }

            if (rotation == 0) return bitmap;

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true
            );

            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            Log.e(TAG, "decodeFileWithExif error", e);
            return android.graphics.BitmapFactory.decodeFile(path);
        }
    }
}