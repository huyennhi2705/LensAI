package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class YoloDetector {
    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();
    private static final int INPUT_SIZE = 640; // Kích thước chuẩn của YOLO
    private static final float CONF_THRESHOLD = 0.4f;
    private static final float IOU_THRESHOLD = 0.5f;

    public static class Detection {
        public RectF boundingBox;
        public String label;
        public float confidence;

        public Detection(RectF boundingBox, String label, float confidence) {
            this.boundingBox = boundingBox;
            this.label = label;
            this.confidence = confidence;
        }
    }

    public YoloDetector(Context context) {
        try {
            // Load model & labels (Bạn cần đảm bảo 2 file này có trong assets)
            MappedByteBuffer tfliteModel = loadModelFile(context.getAssets(), "model.tflite");
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(tfliteModel, options);
            loadLabels(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile(android.content.res.AssetManager assets, String modelPath) throws Exception {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void loadLabels(Context context) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("labels.txt")));
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line.trim());
        }
        reader.close();
    }

    public List<Detection> detect(Bitmap bitmap) {
        if (interpreter == null) return new ArrayList<>();

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resized);

        // Output shape của YOLOv8: [1, 84, 8400] (84 = 4 box + 80 class)
        float[][][] output = new float[1][84][8400];
        interpreter.run(inputBuffer, output);

        List<Detection> detections = new ArrayList<>();

        // --- BƯỚC HẬU XỬ LÝ (POST-PROCESSING) ---
        // (Lưu ý: Đoạn này cần bạn tinh chỉnh tùy theo chuẩn output của file .tflite cụ thể bạn đang dùng)
        for (int i = 0; i < 8400; i++) {
            float maxConf = 0;
            int maxClassIndex = -1;

            // Tìm class có confidence cao nhất
            for (int j = 4; j < 84; j++) {
                if (output[0][j][i] > maxConf) {
                    maxConf = output[0][j][i];
                    maxClassIndex = j - 4;
                }
            }

            if (maxConf > CONF_THRESHOLD) {
                // Tọa độ YOLO trả về là [x_center, y_center, width, height] theo tỷ lệ 0-1
                float x = output[0][0][i];
                float y = output[0][1][i];
                float w = output[0][2][i];
                float h = output[0][3][i];

                float left = (x - w / 2f) * bitmap.getWidth();
                float top = (y - h / 2f) * bitmap.getHeight();
                float right = (x + w / 2f) * bitmap.getWidth();
                float bottom = (y + h / 2f) * bitmap.getHeight();

                RectF rect = new RectF(left, top, right, bottom);
                String label = maxClassIndex >= 0 && maxClassIndex < labels.size() ? labels.get(maxClassIndex) : "Unknown";

                detections.add(new Detection(rect, label, maxConf));
            }
        }

        // Cần thêm hàm Non-Maximum Suppression (NMS) ở đây nếu model chưa tích hợp sẵn NMS
        return applyNMS(detections);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        byteBuffer.rewind();
        for (int i = 0; i < INPUT_SIZE * INPUT_SIZE; i++) {
            final int val = intValues[i];
            byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((val & 0xFF) / 255.0f);
        }
        return byteBuffer;
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        // Hàm lọc trùng lặp (Bạn có thể tìm code NMS chuẩn trên mạng để thay thế)
        return detections;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}