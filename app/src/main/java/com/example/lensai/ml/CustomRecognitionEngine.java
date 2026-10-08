package com.example.lensai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.lensai.model.AIResult;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
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

public class CustomRecognitionEngine {

    private static final String TAG = "CustomEngine";
    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";
    private static final int IMAGE_SIZE = 224;

    private Interpreter interpreter;
    private List<String> labels;
    private ImageProcessor imageProcessor;

    public CustomRecognitionEngine(Context context) {
        try {
            interpreter = new Interpreter(loadModelFile(context));
            labels = FileUtil.loadLabels(context, LABEL_PATH);

            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0f, 255f))
                    .build();

            Log.d(TAG, "Model loaded successfully. Labels: " + labels.size());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public List<AIResult> recognize(Bitmap bitmap) {
        List<AIResult> results = new ArrayList<>();
        if (interpreter == null || labels == null) {
            Log.e(TAG, "Interpreter or labels is null");
            return results;
        }

        try {
            TensorImage tensorImage = new TensorImage();
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                    new int[]{1, labels.size()},
                    org.tensorflow.lite.DataType.FLOAT32
            );

            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());

            Map<String, Float> labeledProbability =
                    new TensorLabel(labels, outputBuffer).getMapWithFloatValue();

            for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
                if (entry.getValue() > 0.25f) { // ngưỡng tin cậy
                    results.add(new AIResult(entry.getKey(), entry.getValue(), "custom"));
                }
            }

            // Sắp xếp theo độ tin cậy giảm dần
            results.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        } catch (Exception e) {
            Log.e(TAG, "Recognize error", e);
        }

        return results;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}