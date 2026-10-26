package com.example.lensai.view.fragment;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.lensai.R;
import com.example.lensai.ml.GeminiRecognitionEngine;
import com.example.lensai.ml.YoloDetector;
import com.example.lensai.model.AIResult;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.example.lensai.service.AIService;
import com.example.lensai.utils.ObjectDescriptionHelper;
import com.example.lensai.utils.PermissionUtils;
import com.example.lensai.utils.TextToSpeechHelper;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final float MIN_CONFIDENCE = 0.55f;



    private PreviewView previewView;
    private ImageView ivResult;
    private TextView tvResult, tvStatus;
    private ProgressBar progress;
    private Button btnCapture, btnSpeak, btnSave, btnTranslate;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private AIService aiService;
    private TextToSpeechHelper tts;
    private HistoryRepository historyRepo;
    private GeminiRecognitionEngine geminiEngine;

    private File lastPhotoFile;
    private Bitmap lastCroppedBitmap;
    private AIResult bestResult;
    private String lastSpokenText = "";

    // Recognition result (English first, Vietnamese on demand)
    private String nameEn = "";
    private String nameVi = "";
    private String descEn = "";
    private String descVi = "";
    private String category = "other";
    private float confidence = 0f;
    private boolean showingVietnamese = false;
    private boolean savedCurrent = false;

    // ========== BỔ SUNG MỚI ==========
    private int objectCount = 0;
    private String objectsListEn = "";
    private String objectsListVi = "";
    private final List<String> objectNamesEn = new ArrayList<>();
    private final List<String> objectNamesVi = new ArrayList<>();
    // =================================

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        previewView = v.findViewById(R.id.previewView);
        ivResult = v.findViewById(R.id.ivResult);
        tvResult = v.findViewById(R.id.tvResult);
        tvStatus = v.findViewById(R.id.tvStatus);
        progress = v.findViewById(R.id.progress);
        btnCapture = v.findViewById(R.id.btnCapture);
        btnSpeak = v.findViewById(R.id.btnSpeak);
        btnSave = v.findViewById(R.id.btnSave);
        btnTranslate = v.findViewById(R.id.btnTranslate);

        aiService = new AIService(requireContext());
        tts = new TextToSpeechHelper(requireContext());
        historyRepo = new HistoryRepository();


        btnCapture.setOnClickListener(view -> takePhoto());

        // Replay always reads English (US), whatever language is on screen
        btnSpeak.setOnClickListener(view -> {
            if (!lastSpokenText.isEmpty()) {
                tts.speak(lastSpokenText);
            }
        });

        if (btnSave != null) {
            btnSave.setOnClickListener(view -> saveCurrentResult());
        }

        btnTranslate.setOnClickListener(view -> {
            showingVietnamese = !showingVietnamese;
            renderResult();
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PermissionUtils.hasCamera(requireActivity())) {
            previewView.post(this::startCamera);
        } else {
            PermissionUtils.requestCamera(requireActivity());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (aiService != null) {
            aiService.release();
        }
        if (tts != null) {
            tts.shutdown();
        }
        lastCroppedBitmap = null;
    }

    private void startCamera() {
        if (previewView == null) return;

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (Exception e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera is not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        lastPhotoFile = new File(requireContext().getCacheDir(),
                "scan_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(lastPhotoFile).build();

        // Reset state for the new scan
        bestResult = null;
        savedCurrent = false;
        showingVietnamese = false;
        lastSpokenText = "";

        // ========== BỔ SUNG MỚI: reset count + list ==========
        objectCount = 0;
        objectsListEn = "";
        objectsListVi = "";
        objectNamesEn.clear();
        objectNamesVi.clear();
        nameEn = "";
        nameVi = "";
        descEn = "";
        descVi = "";
        category = "other";
        confidence = 0f;
        // =====================================================

        progress.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(false);
        btnTranslate.setVisibility(View.GONE);
        tvResult.setText("Recognizing...");
        tvStatus.setText("🟡  Recognizing...");

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        if (!isAdded()) return;

                        Bitmap fullBitmap = YoloDetector.decodeFileWithExif(
                                lastPhotoFile.getAbsolutePath());
                        if (fullBitmap == null) {
                            progress.setVisibility(View.GONE);
                            btnCapture.setEnabled(true);
                            tvStatus.setText("🔴  Image error");
                            Toast.makeText(requireContext(),
                                    "Could not read the image", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        lastCroppedBitmap = fullBitmap;
                        ivResult.setImageBitmap(fullBitmap);
                        ivResult.setVisibility(View.VISIBLE);

                        // YOLO runs in the background (optional)
                        aiService.analyze(fullBitmap,
                                results -> Log.d(TAG, "YOLO done: "
                                        + (results != null ? results.size() : 0) + " objects"),
                                e -> Log.e(TAG, "YOLO error", e)
                        );

                        // Gemini provides the main result
                        recognizeWithGemini(fullBitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        tvStatus.setText("🔴  Capture failed");
                        Toast.makeText(requireContext(),
                                "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void recognizeWithGemini(Bitmap bitmap) {
        if (geminiEngine == null) return;

        geminiEngine.recognize(bitmap)
                .thenAccept(result -> {
                    if (!isAdded()) return;

                    requireActivity().runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);

                        try {
                            // ===== Code cũ: main_object + description =====
                            JSONObject main = result.optJSONObject("main_object");
                            nameEn = main != null
                                    ? main.optString("name_en", "Unknown") : "Unknown";
                            nameVi = main != null
                                    ? main.optString("name_vi", "Không xác định")
                                    : "Không xác định";
                            descEn = result.optString("description_en",
                                    "No description available.");
                            descVi = result.optString("description_vi",
                                    "Không có mô tả.");

                            // ===== Code cũ: confidence + category =====
                            category = "other";
                            confidence = 0f;

                            // ===== BỔ SUNG MỚI: object_count + list =====
                            objectCount = result.optInt("object_count", 0);
                            objectNamesEn.clear();
                            objectNamesVi.clear();

                            JSONArray objs = result.optJSONArray("objects");
                            StringBuilder listEn = new StringBuilder();
                            StringBuilder listVi = new StringBuilder();

                            if (objs != null && objs.length() > 0) {
                                // Ưu tiên độ dài mảng thực tế
                                objectCount = objs.length();

                                for (int i = 0; i < objs.length(); i++) {
                                    JSONObject o = objs.getJSONObject(i);
                                    String nEn = o.optString("name_en", "Unknown");
                                    String nVi = o.optString("name_vi", "Không xác định");

                                    objectNamesEn.add(nEn);
                                    objectNamesVi.add(nVi);

                                    if (i > 0) {
                                        listEn.append(", ");
                                        listVi.append(", ");
                                    }
                                    listEn.append(nEn);
                                    listVi.append(nVi);

                                    // Giữ logic cũ: lấy category + confidence của vật chính
                                    boolean isMain = nameEn.equalsIgnoreCase(nEn);
                                    if (i == 0 || isMain) {
                                        category = o.optString("category", "other");
                                        confidence = (float) o.optDouble("confidence", 0.0);
                                        if (isMain) {
                                            // đã khớp vật chính
                                        }
                                    }
                                }
                            }

                            objectsListEn = listEn.toString();
                            objectsListVi = listVi.toString();
                            // =============================================

                            // English first
                            showingVietnamese = false;
                            btnTranslate.setVisibility(View.VISIBLE);
                            renderResult();
                            tvStatus.setText("🟢  Done");

                            // Speak: đọc số lượng + danh sách + mô tả bao quát
                            lastSpokenText = buildSpeakText(false);
                            tts.speak(lastSpokenText);

                            bestResult = new AIResult();
                            bestResult.label = nameEn;
                            bestResult.confidence = confidence;

                            savedCurrent = false;
                            autoSave(null); // every result goes to history

                        } catch (Exception e) {
                            tvResult.setText("Failed to process result: " + e.getMessage());
                            tvStatus.setText("🔴  Error");
                            Log.e(TAG, "Parse Gemini error", e);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    if (!isAdded()) return null;

                    requireActivity().runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        tvStatus.setText("🔴  Error");
                        tvResult.setText("Recognition failed: " + throwable.getMessage());
                        Log.e(TAG, "Gemini failed", throwable);
                    });
                    return null;
                });
    }

    /** BỔ SUNG MỚI: text để TTS đọc */
    private String buildSpeakText(boolean vietnamese) {
        if (vietnamese) {
            return "Có " + objectCount + " đồ vật. "
                    + "Đó là: " + objectsListVi + ". "
                    + "Vật chính: " + nameVi + ". "
                    + descVi;
        } else {
            return "There are " + objectCount + " objects. "
                    + "They are: " + objectsListEn + ". "
                    + "Main object: " + nameEn + ". "
                    + descEn;
        }
    }

    /**
     * Shows English or Vietnamese text.
     * BỔ SUNG: hiển thị Count + Objects list + Main + Description.
     */
    private void renderResult() {
        if (showingVietnamese) {
            String text = "Số lượng: " + objectCount + "\n"
                    + "Các đồ vật: " + objectsListVi + "\n"
                    + "Vật chính: " + nameVi + "\n"
                    + "Mô tả: " + descVi;
            tvResult.setText(text);
            btnTranslate.setText("English");
        } else {
            String text = "Count: " + objectCount + "\n"
                    + "Objects: " + objectsListEn + "\n"
                    + "Main: " + nameEn + "\n"
                    + "Description: " + descEn;
            tvResult.setText(text);
            btnTranslate.setText("Translate");
        }
    }

    // Legacy YOLO method, kept for later use
    private void showResults(List<AIResult> results) {
        if (results == null || results.isEmpty()) {
            tvResult.setText("No object recognized");
            lastSpokenText = "";
            bestResult = null;
            return;
        }

        AIResult top = results.get(0);
        for (AIResult r : results) {
            if (r.confidence > top.confidence) {
                top = r;
            }
        }

        if (top.confidence < MIN_CONFIDENCE) {
            tvResult.setText("Not sure (" + String.format("%.0f%%", top.confidence * 100)
                    + "). Try getting closer, with better light.");
            lastSpokenText = "";
            bestResult = null;
            return;
        }

        bestResult = top;

        String englishName = ObjectDescriptionHelper.getEnglishName(bestResult.label);
        bestResult.label = englishName;

        String description = ObjectDescriptionHelper.getDescription(
                englishName, lastCroppedBitmap);

        tvResult.setText("Name: " + englishName + "\nDescription: " + description
                + "\nConfidence: " + String.format("%.0f%%", bestResult.confidence * 100));
        lastSpokenText = englishName + ". " + description;

        tts.speak(englishName);
    }

    private void autoSave(List<AIResult> results) {
        if (bestResult == null || lastCroppedBitmap == null) return;
        if (savedCurrent) return;
        savedCurrent = true;

        ScanHistory history = new ScanHistory();

        // Thông tin cũ
        history.labelEn = nameEn;
        history.labelVi = nameVi;
        history.description = descEn;
        history.descriptionEn = descEn;
        history.descriptionVi = descVi;
        history.category = category;
        history.type = category;
        history.confidence = confidence;
        history.timestamp = System.currentTimeMillis();

        // ===== MỚI: bắt buộc gán =====
        history.objectCount = objectCount;
        history.objectsListEn = objectsListEn;
        history.objectsListVi = objectsListVi;
        // =============================

        historyRepo.saveScan(history, lastCroppedBitmap,
                unused -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Saved: " + history.labelEn
                                        + " (" + history.objectCount + " objects)",
                                Toast.LENGTH_SHORT).show();
                    }
                },
                e -> {
                    savedCurrent = false;
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void saveCurrentResult() {
        if (bestResult == null) {
            Toast.makeText(requireContext(),
                    "No result to save yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (savedCurrent) {
            Toast.makeText(requireContext(),
                    "Already saved in history", Toast.LENGTH_SHORT).show();
            return;
        }
        autoSave(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PermissionUtils.REQ_CAMERA
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(requireContext(),
                    "Camera permission is required", Toast.LENGTH_LONG).show();
        }
    }
}