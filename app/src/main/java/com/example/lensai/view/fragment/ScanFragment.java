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
import com.example.lensai.ml.YoloDetector;
import com.example.lensai.model.AIResult;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.example.lensai.service.AIService;
import com.example.lensai.utils.ObjectDescriptionHelper;
import com.example.lensai.utils.PermissionUtils;
import com.example.lensai.utils.TextToSpeechHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.List;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final float MIN_CONFIDENCE = 0.55f;

    private PreviewView previewView;
    private ImageView ivResult;
    private TextView tvResult;
    private ProgressBar progress;
    private Button btnCapture, btnSpeak, btnSave;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private AIService aiService;
    private TextToSpeechHelper tts;
    private HistoryRepository historyRepo;

    private File lastPhotoFile;
    private Bitmap lastCroppedBitmap;
    private AIResult bestResult;
    private String lastSpokenText = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        previewView = v.findViewById(R.id.previewView);
        ivResult = v.findViewById(R.id.ivResult);
        tvResult = v.findViewById(R.id.tvResult);
        progress = v.findViewById(R.id.progress);
        btnCapture = v.findViewById(R.id.btnCapture);
        btnSpeak = v.findViewById(R.id.btnSpeak);
        btnSave = v.findViewById(R.id.btnSave);

        aiService = new AIService(requireContext());
        tts = new TextToSpeechHelper(requireContext());
        historyRepo = new HistoryRepository();

        btnCapture.setOnClickListener(view -> takePhoto());
        btnSpeak.setOnClickListener(view -> {
            if (!lastSpokenText.isEmpty()) {
                tts.speak(lastSpokenText);
            }
        });
        if (btnSave != null) {
            btnSave.setOnClickListener(view -> saveCurrentResult());
        }

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
                Log.e(TAG, "Mở camera lỗi", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        lastPhotoFile = new File(requireContext().getCacheDir(),
                "scan_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(lastPhotoFile).build();

        progress.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(false);
        tvResult.setText("Đang nhận dạng...");

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        if (!isAdded()) return;

                        // Đọc ảnh + xoay đúng chiều theo EXIF
                        Bitmap fullBitmap = YoloDetector.decodeFileWithExif(lastPhotoFile.getAbsolutePath());
                        if (fullBitmap == null) {
                            progress.setVisibility(View.GONE);
                            btnCapture.setEnabled(true);
                            Toast.makeText(requireContext(), "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        lastCroppedBitmap = fullBitmap;
                        ivResult.setImageBitmap(fullBitmap);
                        ivResult.setVisibility(View.VISIBLE);

                        // Gửi ảnh vào AIService (YOLO)
                        aiService.analyze(fullBitmap,
                                results -> {
                                    if (!isAdded()) return;
                                    progress.setVisibility(View.GONE);
                                    btnCapture.setEnabled(true);
                                    showResults(results);
                                    autoSave(results);
                                },
                                e -> {
                                    if (!isAdded()) return;
                                    Log.e(TAG, "analyze lỗi", e);
                                    progress.setVisibility(View.GONE);
                                    btnCapture.setEnabled(true);
                                    tvResult.setText("Lỗi nhận dạng");
                                    Toast.makeText(requireContext(),
                                            "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        Toast.makeText(requireContext(), "Chụp ảnh thất bại", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showResults(List<AIResult> results) {
        if (results == null || results.isEmpty()) {
            tvResult.setText("Không nhận dạng được vật thể nào");
            lastSpokenText = "";
            bestResult = null;
            return;
        }

        // Lấy kết quả có độ tin cậy cao nhất
        AIResult top = results.get(0);
        for (AIResult r : results) {
            if (r.confidence > top.confidence) {
                top = r;
            }
        }

        if (top.confidence < MIN_CONFIDENCE) {
            tvResult.setText("Chưa chắc chắn (" + String.format("%.0f%%", top.confidence * 100)
                    + "). Hãy chụp gần hơn, rõ hơn và đủ sáng.");
            lastSpokenText = "";
            bestResult = null;
            return;
        }

        bestResult = top;

        String englishName = ObjectDescriptionHelper.getEnglishName(bestResult.label);
        bestResult.label = englishName;

        String description = ObjectDescriptionHelper.getDescription(englishName, lastCroppedBitmap);

        String displayText = "Name: " + englishName + "\n\n"
                + "Description: " + description + "\n\n"
                + "Confidence: " + String.format("%.0f%%", bestResult.confidence * 100);

        tvResult.setText(displayText);
        lastSpokenText = englishName + ". " + description;

        tts.speak(englishName);
    }

    private void autoSave(List<AIResult> results) {
        if (bestResult == null || lastCroppedBitmap == null) return;

        ScanHistory history = new ScanHistory();
        history.labelEn = bestResult.label;
        history.description = ObjectDescriptionHelper.getDescription(bestResult.label, lastCroppedBitmap);
        history.type = bestResult.type;
        history.confidence = bestResult.confidence;

        historyRepo.saveScan(history, lastCroppedBitmap,
                unused -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Đã lưu: " + history.labelEn, Toast.LENGTH_SHORT).show();
                    }
                },
                e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Lưu thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void saveCurrentResult() {
        if (bestResult == null) {
            Toast.makeText(requireContext(), "Chưa có kết quả để lưu", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), "Cần quyền Camera để sử dụng", Toast.LENGTH_LONG).show();
        }
    }
}