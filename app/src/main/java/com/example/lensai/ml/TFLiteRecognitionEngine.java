package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.lensai.model.AIResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class TFLiteRecognitionEngine {

    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";
    private static final int INPUT_SIZE = 224;          // Đổi nếu model của bạn khác
    private static final float IMAGE_MEAN = 0f;         // Thường là 0 hoặc 127.5
    private static final float IMAGE_STD = 255f;        // Thường là 255 hoặc 127.5
    private static final int MAX_RESULTS = 5;
    private static final float THRESHOLD = 0.35f;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final ImageProcessor imageProcessor;

    public TFLiteRecognitionEngine(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context));
        labels = FileUtil.loadLabels(context, LABEL_PATH);

        // Preprocessing: resize + normalize
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                .build();
    }

    public void recognize(Bitmap bitmap,
                          OnSuccessListener<List<AIResult>> onSuccess,
                          OnFailureListener onFailure) {
        try {
            // 1. Resize về đúng size model
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

            // 2. Tạo TensorImage đúng kiểu dữ liệu của model
            TensorImage tensorImage = new TensorImage(interpreter.getInputTensor(0).dataType());
            tensorImage.load(resized);

            // 3. Preprocess
            ImageProcessor imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                    .build();

            tensorImage = imageProcessor.process(tensorImage);

            // 4. Chạy inference
            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                    interpreter.getOutputTensor(0).shape(),
                    interpreter.getOutputTensor(0).dataType());

            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());

            // 5. Lấy kết quả
            Map<String, Float> labeledProbability =
                    new TensorLabel(labels, outputBuffer).getMapWithFloatValue();

            List<AIResult> results = getTopResults(labeledProbability);
            onSuccess.onSuccess(results);

        } catch (Exception e) {
            e.printStackTrace();
            onFailure.onFailure(e);
        }
    }

    private List<AIResult> getTopResults(Map<String, Float> labelProb) {
        PriorityQueue<Map.Entry<String, Float>> pq =
                new PriorityQueue<>((a, b) -> Float.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            if (entry.getValue() > THRESHOLD) {
                pq.offer(entry);
            }
        }

        List<AIResult> results = new ArrayList<>();
        int count = 0;
        while (!pq.isEmpty() && count < MAX_RESULTS) {
            Map.Entry<String, Float> entry = pq.poll();
            results.add(new AIResult(entry.getKey(), entry.getValue(), "tflite"));
            count++;
        }
        return results;
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}