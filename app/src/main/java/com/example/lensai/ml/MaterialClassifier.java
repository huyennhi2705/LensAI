package com.example.lensai.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.Map;

public class MaterialClassifier {
    private static final String TAG = "MaterialClassifier";
    private static final String MODEL_PATH = "material_model.tflite";
    private static final String LABEL_PATH = "material_labels.txt";
    private static final int INPUT_SIZE = 224;
    private Interpreter interpreter;
    private List<String> labels;
    private ImageProcessor imageProcessor;
    public MaterialClassifier(Context context) {
        try {
            MappedByteBuffer model = FileUtil.loadMappedFile(context, MODEL_PATH);
            interpreter = new Interpreter(model);
            labels = FileUtil.loadLabels(context, LABEL_PATH);

            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0f, 255f))
                    .build();
            Log.d(TAG, "Material model loaded. Labels: " + labels.size());
        } catch (IOException e) {
            Log.e(TAG, "Failed to load material model", e);
        }
    }
    public String predict(Bitmap bitmap) {
        if (interpreter == null || labels == null || bitmap == null) {
            return "không xác định";
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
            String bestLabel = "Not yet determined";
            float maxProb = 0f;
            for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
                if (entry.getValue() > maxProb) {
                    maxProb = entry.getValue();
                    bestLabel = entry.getKey();
                }
            }
            if (maxProb < 0.35f) {
                return "Not yet determined";
            }
            return bestLabel + " (" + String.format("%.0f%%", maxProb * 100) + ")";
        } catch (Exception e) {
            Log.e(TAG, "Predict material error", e);
            return "lỗi dự đoán";
        }
    }
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}