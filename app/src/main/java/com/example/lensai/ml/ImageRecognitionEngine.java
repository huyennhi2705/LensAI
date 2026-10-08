package com.example.lensai.ml;

import android.graphics.Bitmap;
import com.example.lensai.model.AIResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.ArrayList;
import java.util.List;

public class ImageRecognitionEngine {

    private final ObjectDetector objectDetector;
    private final ImageLabeler labeler;
    private final TextRecognizer textRecognizer;
    private final BarcodeScanner barcodeScanner;

    public ImageRecognitionEngine() {
        ObjectDetectorOptions opt = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(opt);
        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        barcodeScanner = BarcodeScanning.getClient();
    }

    public void recognize(Bitmap bitmap,
                          OnSuccessListener<List<AIResult>> onSuccess,
                          OnFailureListener onFailure) {

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        List<AIResult> results = new ArrayList<>();

        // 1. Object Detection
        objectDetector.process(image)
                .addOnSuccessListener(objects -> {
                    for (DetectedObject obj : objects) {
                        if (!obj.getLabels().isEmpty()) {
                            DetectedObject.Label top = obj.getLabels().get(0);
                            results.add(new AIResult(top.getText(), top.getConfidence(), "object"));
                        }
                    }

                    // 2. Image Labeling
                    labeler.process(image)
                            .addOnSuccessListener(labels -> {
                                for (ImageLabel l : labels) {
                                    if (l.getConfidence() > 0.55f) {
                                        results.add(new AIResult(l.getText(), l.getConfidence(), "label"));
                                    }
                                }

                                // 3. OCR
                                textRecognizer.process(image)
                                        .addOnSuccessListener(visionText -> {
                                            String fullText = visionText.getText();
                                            if (fullText != null && !fullText.trim().isEmpty()) {
                                                results.add(new AIResult(fullText.trim(), 1.0f, "text"));
                                            }

                                            // 4. Barcode
                                            barcodeScanner.process(image)
                                                    .addOnSuccessListener(barcodes -> {
                                                        for (Barcode b : barcodes) {
                                                            String raw = b.getRawValue();
                                                            if (raw != null) {
                                                                results.add(new AIResult(raw, 1.0f, "barcode"));
                                                            }
                                                        }
                                                        onSuccess.onSuccess(results);
                                                    })
                                                    .addOnFailureListener(e -> onSuccess.onSuccess(results));
                                        })
                                        .addOnFailureListener(e -> onSuccess.onSuccess(results));
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    public void close() {
        objectDetector.close();
        labeler.close();
        textRecognizer.close();
        barcodeScanner.close();
    }
}