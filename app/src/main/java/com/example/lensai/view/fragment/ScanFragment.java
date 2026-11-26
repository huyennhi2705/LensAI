package com.example.lensai.view.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.lensai.R;
import com.example.lensai.model.AIResult;
import com.example.lensai.service.AIService;
import com.example.lensai.utils.PermissionUtils;
import com.example.lensai.voice.PositionDescriber;
import com.example.lensai.voice.SearchSession;
import com.example.lensai.voice.TtsManager;
import com.example.lensai.voice.VoiceCommandParser;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ⭐ DÒNG QUAN TRỌNG ĐỂ SỬA LỖI ĐỎ TRONG ẢNH CỦA BẠN:
@androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final int  FRAMES_TO_CONFIRM   = 3;
    private static final long REMINDER_INTERVAL_MS = 10_000L;

    // ===== VIEWS =====
    private PreviewView    previewView;
    private TextView       tvStatus, tvResult;
    private ProgressBar    progress;
    private MaterialButton btnVoiceSearch, btnStopSearch, btnSpeak;
    private ImageButton    btnFlash, btnGallery;
    private View           bottomSheet;
    private LinearLayout   layoutEmptyState, layoutResults;

    // ===== CORE =====
    private ExecutorService cameraExecutor;
    private AIService       aiService;
    private SearchSession   session;

    // ===== STATE =====
    private volatile boolean  isScanning = false;
    private String            targetName = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ===== LAUNCHERS =====
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent>   speechLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean cam = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    boolean mic = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                    if (cam && mic) {
                        if (isAdded()) initAll();
                    } else {
                        if (isAdded()) Toast.makeText(requireContext(),
                                "Cần quyền Camera và Micro để sử dụng", Toast.LENGTH_LONG).show();
                    }
                });

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        Log.d(TAG, "Speech cancelled");
                        setStatus("🟢 Đã huỷ nghe");
                        return;
                    }
                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        Log.d(TAG, "Heard: " + text);
                        processVoiceText(text);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView      = view.findViewById(R.id.previewView);
        tvStatus         = view.findViewById(R.id.tvStatus);
        tvResult         = view.findViewById(R.id.tvResult);
        progress         = view.findViewById(R.id.progress);
        btnVoiceSearch   = view.findViewById(R.id.btnVoiceSearch);
        btnStopSearch    = view.findViewById(R.id.btnStopSearch);
        btnSpeak         = view.findViewById(R.id.btnSpeak);
        btnFlash         = view.findViewById(R.id.btnFlash);
        btnGallery       = view.findViewById(R.id.btnGallery);
        bottomSheet      = view.findViewById(R.id.bottomSheet);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        layoutResults    = view.findViewById(R.id.layoutResults);

        cameraExecutor = Executors.newSingleThreadExecutor();
        session = new SearchSession(FRAMES_TO_CONFIRM, REMINDER_INTERVAL_MS);

        TtsManager.get().init(requireContext().getApplicationContext(),
                (ok, msg) -> Log.d(TAG, "TtsManager init: ok=" + ok + " msg=" + msg));

        btnVoiceSearch.setOnClickListener(v -> onVoiceSearchClicked());
        btnStopSearch.setOnClickListener(v -> stopAll());
        btnSpeak.setOnClickListener(v -> {
            String t = tvResult != null ? tvResult.getText().toString().trim() : "";
            if (t.isEmpty()) t = "Xin chào, đây là bài kiểm tra giọng nói.";
            speak(t);
        });
        btnFlash.setOnClickListener(v -> Toast.makeText(requireContext(), "Flash sẽ có sau", Toast.LENGTH_SHORT).show());
        btnGallery.setOnClickListener(v -> Toast.makeText(requireContext(), "Chọn ảnh sẽ có sau", Toast.LENGTH_SHORT).show());

        if (PermissionUtils.hasAllPermissions(requireActivity())) {
            initAll();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            });
        }
    }

    private void initAll() {
        if (!isAdded()) return;
        try {
            if (aiService == null) aiService = new AIService(requireContext());
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khởi tạo AI", e);
        }
        startCamera();
        setStatus("🟢 Sẵn sàng – nhấn 🎤 để nói tên vật");
    }

    private void startCamera() {
        if (!isAdded()) return;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            try {
                if (!isAdded()) return;
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(640, 480))
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                provider.unbindAll();
                provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                Log.d(TAG, "Camera bound");
            } catch (Exception e) {
                Log.e(TAG, "startCamera failed", e);
                if (isAdded()) setStatus("❌ Không mở được camera");
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!isScanning || aiService == null || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        try {
            // ⭐ NHỊP TIM: Kiểm tra xem camera có đang gửi frame cho YOLO không
            Log.d(TAG, "❤️ Nhận frame từ camera, đang đưa vào YOLO...");

            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            Bitmap bitmap = imageProxy.toBitmap();

            // Xoay ảnh
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            final int imgW = bitmap.getWidth();
            final int imgH = bitmap.getHeight();
            final Bitmap finalBitmap = bitmap;

            aiService.analyze(finalBitmap,
                    results -> {
                        imageProxy.close();
                        if (isAdded()) handleYoloResults(results, imgW, imgH);
                    },
                    e -> {
                        imageProxy.close();
                        Log.e(TAG, "YOLO error", e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "analyzeImage error", e);
            imageProxy.close();
        }
    }

    private void handleYoloResults(List<AIResult> results, int imgW, int imgH) {
        if (!isAdded() || !isScanning || results == null) return;

        // ⭐ IN LOG: Xem YOLO nhìn thấy vật gì
        Log.d(TAG, "--- YOLO Frame: Tìm thấy " + results.size() + " vật thể ---");
        for (AIResult r : results) {
            Log.d(TAG, "👉 " + r.label + " | Độ tự tin: " + r.confidence);
        }

        List<SearchSession.Hit> hits = new ArrayList<>();
        for (AIResult r : results) {
            if (r == null || r.label == null) continue;
            float x1 = r.x1, y1 = r.y1, x2 = r.x2, y2 = r.y2;

            // Nếu BBox là tỷ lệ (0..1) thì nhân với kích thước ảnh
            if (x2 <= 1.0f && y2 <= 1.0f) {
                x1 *= imgW; x2 *= imgW;
                y1 *= imgH; y2 *= imgH;
            }

            if (x2 <= x1 || y2 <= y1) continue;

            float cx   = (x1 + x2) / 2f / imgW;
            float cy   = (y1 + y2) / 2f / imgH;
            float area = (x2 - x1) * (y2 - y1) / (float)(imgW * imgH);

            // Chỉ thêm vào danh sách nếu độ tự tin > 0.3
            if (r.confidence > 0.3f) {
                hits.add(new SearchSession.Hit(r.label, r.confidence, cx, cy, area));
            }
        }

        SearchSession.Action action = session.onFrame(hits, System.currentTimeMillis());
        if (action == null || action.type == SearchSession.ActionType.NONE) return;

        if (action.type == SearchSession.ActionType.SPEAK) {
            speak(action.text);
            setStatus("🔍 Vẫn đang tìm: " + targetName);
        } else if (action.type == SearchSession.ActionType.CAPTURE) {
            SearchSession.Hit best = action.best;
            String spoken = PositionDescriber.describe(targetName, best.cx, best.cy, best.area, action.count);
            String guide  = PositionDescriber.isCentered(best.cx, best.cy)
                    ? "Đồ vật ở chính giữa, bạn có thể với tay tới."
                    : PositionDescriber.guide(targetName, best.cx, best.cy);

            String fullMsg = action.text + " " + spoken + " " + guide;
            isScanning = false;
            session.finish();

            mainHandler.post(() -> {
                if (!isAdded()) return;
                setStatus("✅ Đã tìm thấy " + targetName);
                showResultArea(spoken);
                speak(fullMsg);
            });
        }
    }

    private void onVoiceSearchClicked() {
        TtsManager.get().stop();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên đồ vật cần tìm...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        setStatus("🎤 Đang mở Google Voice...");
        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Máy chưa cài Google Voice Search", Toast.LENGTH_LONG).show();
            setStatus("❌ Chưa cài Google Voice Search");
        }
    }

    private void processVoiceText(String text) {
        if (text == null || text.isEmpty()) return;

        VoiceCommandParser.Command cmd = VoiceCommandParser.parse(text);
        if (cmd.type == VoiceCommandParser.Type.STOP) {
            stopAll();
            speak("Đã dừng tìm kiếm.");
            return;
        }
        if (cmd.type != VoiceCommandParser.Type.SEARCH) {
            setStatus("⚠️ Chưa hiểu lệnh");
            speak("Mình chưa hiểu. Hãy nói: tìm chai nước, tìm cái ghế.");
            return;
        }

        targetName = cmd.spokenName;
        SearchSession.Action action = session.start(cmd.cocoLabel, cmd.spokenName, System.currentTimeMillis());
        isScanning = true;

        setStatus("🔍 Đang tìm: " + targetName);
        showResultArea("Đang tìm " + targetName + "…");
        speak(action.text);
    }

    private void speak(String text) {
        if (text == null || text.trim().isEmpty() || !isAdded()) return;
        TtsManager.get().speak(text, null);
    }

    private void setStatus(String s) {
        if (isAdded() && tvStatus != null) tvStatus.setText(s);
    }

    private void showResultArea(String text) {
        if (!isAdded()) return;
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (layoutResults    != null) layoutResults.setVisibility(View.VISIBLE);
        if (tvResult         != null) tvResult.setText(text);
        if (bottomSheet      != null) {
            try {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> bb =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                bb.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            } catch (Exception ignored) { }
        }
    }

    private void stopAll() {
        isScanning = false;
        targetName = null;
        if (session != null) session.stop();
        TtsManager.get().stop();
        setStatus("🟢 Đã dừng – nhấn 🎤 để bắt đầu lại");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        if (cameraExecutor != null) { cameraExecutor.shutdownNow(); cameraExecutor = null; }
        if (aiService      != null) { aiService.release(); aiService = null; }
    }
}