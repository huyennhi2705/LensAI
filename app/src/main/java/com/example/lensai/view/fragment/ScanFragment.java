package com.example.lensai.view.fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lensai.BuildConfig;
import com.example.lensai.R;
import com.example.lensai.ml.GeminiRecognitionEngine;
import com.example.lensai.ml.YoloDetector;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.utils.PermissionUtils;
import com.example.lensai.utils.TextToSpeechHelper;
import com.example.lensai.view.BoundingBoxView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";

    // ================= VIEWS =================
    private PreviewView previewView;
    private ImageView ivResult;
    private TextView tvStatus, tvResult, tvDetectedObjectsTitle, tvAiSuggestion;
    private ProgressBar progress, progressAi;
    private BoundingBoxView boxOverlay;
    private RecyclerView rvDetectedObjects;
    private View bottomSheet, layoutEmptyState, layoutResults, aiCard;
    private View btnCapture; // FloatingActionButton trong layout
    private Button btnSpeak, btnTranslate, btnAiAsk, btnAiRefresh;
    private EditText etAiQuestion;
    private ChipGroup chipGroupSuggest;

    // ================= SERVICES =================
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private YoloDetector yoloDetector;
    private GeminiRecognitionEngine geminiEngine;
    private TextToSpeechHelper tts;
    private ExecutorService bgExecutor;
    private BottomSheetBehavior<View> sheetBehavior;
    private DatabaseReference databaseRef;

    // ================= STATE =================
    private Bitmap lastCapturedBitmap;
    private List<YoloDetector.Detection> currentDetections = new ArrayList<>();
    private String mainObjectNameEn = "";
    private String mainObjectNameVi = "";
    private String descriptionEn = "";
    private String descriptionVi = "";
    private String aiSummaryEn = "";
    private String aiSummaryVi = "";
    private final List<String> questionsEn = new ArrayList<>();
    private final List<String> questionsVi = new ArrayList<>();

    private boolean showingVietnamese = false;
    private boolean isDataSaved = false;
    private DetectedObjectAdapter detectedAdapter;

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(requireContext(), "Cần quyền Camera để quét", Toast.LENGTH_SHORT).show();
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        // 1. Ánh xạ Views
        previewView = v.findViewById(R.id.previewView);
        ivResult = v.findViewById(R.id.ivResult);
        tvStatus = v.findViewById(R.id.tvStatus);
        progress = v.findViewById(R.id.progress);
        boxOverlay = v.findViewById(R.id.boxOverlay);
        btnCapture = v.findViewById(R.id.btnCapture);

        bottomSheet = v.findViewById(R.id.bottomSheet);
        layoutEmptyState = v.findViewById(R.id.layoutEmptyState);
        layoutResults = v.findViewById(R.id.layoutResults);

        tvResult = v.findViewById(R.id.tvResult);
        btnTranslate = v.findViewById(R.id.btnTranslate);
        btnSpeak = v.findViewById(R.id.btnSpeak);

        rvDetectedObjects = v.findViewById(R.id.rvDetectedObjects);
        tvDetectedObjectsTitle = v.findViewById(R.id.tvDetectedObjectsTitle);

        aiCard = v.findViewById(R.id.aiCard);
        tvAiSuggestion = v.findViewById(R.id.tvAiSuggestion);
        progressAi = v.findViewById(R.id.progressAi);
        chipGroupSuggest = v.findViewById(R.id.chipGroupSuggest);
        etAiQuestion = v.findViewById(R.id.etAiQuestion);
        btnAiAsk = v.findViewById(R.id.btnAiAsk);
        btnAiRefresh = v.findViewById(R.id.btnAiRefresh);

        // 2. Setup BottomSheet (peekHeight đã khai báo trong XML)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // 3. Setup RecyclerView
        rvDetectedObjects.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        detectedAdapter = null;

        // 4. Khởi tạo Services
        bgExecutor = Executors.newSingleThreadExecutor();
        yoloDetector = new YoloDetector(requireContext());
        geminiEngine = new GeminiRecognitionEngine(BuildConfig.GEMINI_API_KEY);
        tts = new TextToSpeechHelper(requireContext());

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        databaseRef = FirebaseDatabase.getInstance("https://lensai-97dc7-default-rtdb.firebaseio.com/")
                .getReference("history").child(uid);

        // 5. Setup Events
        btnCapture.setOnClickListener(view -> takePhoto());
        btnTranslate.setOnClickListener(view -> toggleLanguage());
        btnSpeak.setOnClickListener(view -> speakResult());
        btnAiRefresh.setOnClickListener(view -> loadAiSuggestions());
        btnAiAsk.setOnClickListener(view -> askAi());
        etAiQuestion.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                askAi();
                return true;
            }
            return false;
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        resetUI();
        if (PermissionUtils.hasCamera(requireActivity())) {
            previewView.post(this::startCamera);
        } else {
            cameraPermLauncher.launch(android.Manifest.permission.CAMERA);
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
        if (bgExecutor != null) bgExecutor.shutdownNow();
        if (tts != null) tts.shutdown();
        if (yoloDetector != null) yoloDetector.close();
        lastCapturedBitmap = null;
    }

    /** Chạy trên UI thread, bỏ qua nếu fragment đã bị gỡ. */
    private void runOnUi(Runnable r) {
        if (!isAdded() || getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (isAdded()) r.run();
        });
    }

    private void resetUI() {
        showingVietnamese = false;
        isDataSaved = false;
        currentDetections.clear();
        lastCapturedBitmap = null;
        mainObjectNameEn = "";
        mainObjectNameVi = "";

        if (previewView != null) previewView.setVisibility(View.VISIBLE);
        if (ivResult != null) ivResult.setVisibility(View.GONE);
        if (boxOverlay != null) boxOverlay.clear();

        if (sheetBehavior != null) sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        if (layoutResults != null) layoutResults.setVisibility(View.GONE);
        if (aiCard != null) aiCard.setVisibility(View.GONE);
        if (progress != null) progress.setVisibility(View.GONE);
        if (btnCapture != null) btnCapture.setEnabled(true);
        if (tvStatus != null) tvStatus.setText("🟢  Ready to scan");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        progress.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(false);
        tvStatus.setText("🟡  Đang phân tích...");

        File photoFile = new File(requireContext().getCacheDir(), "scan_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        bgExecutor.execute(() -> {
                            Bitmap bmp = decodeWithRotation(photoFile);
                            //noinspection ResultOfMethodCallIgnored
                            photoFile.delete();
                            lastCapturedBitmap = bmp;
                            runOnUi(() -> {
                                if (bmp == null) {
                                    progress.setVisibility(View.GONE);
                                    btnCapture.setEnabled(true);
                                    tvStatus.setText("🔴 Lỗi đọc ảnh");
                                    return;
                                }
                                previewView.setVisibility(View.GONE);
                                ivResult.setVisibility(View.VISIBLE);
                                ivResult.setImageBitmap(bmp);
                                runYoloDetection(bmp);
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        Toast.makeText(requireContext(), "Lỗi chụp ảnh", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Đọc ảnh và xoay theo EXIF để ảnh và khung box đúng chiều. */
    private Bitmap decodeWithRotation(File file) {
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bmp == null) return null;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            if (degrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (rotated != bmp) bmp.recycle();
                return rotated;
            }
        } catch (Exception e) {
            Log.e(TAG, "EXIF error", e);
        }
        return bmp;
    }

    private void runYoloDetection(Bitmap bitmap) {
        bgExecutor.execute(() -> {
            List<YoloDetector.Detection> result;
            try {
                result = yoloDetector.detect(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "YOLO error", e);
                result = new ArrayList<>();
            }
            final List<YoloDetector.Detection> detections = result;

            runOnUi(() -> {
                currentDetections = detections;
                progress.setVisibility(View.GONE);
                btnCapture.setEnabled(true);
                layoutEmptyState.setVisibility(View.GONE);
                layoutResults.setVisibility(View.VISIBLE);
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                if (currentDetections.isEmpty()) {
                    tvStatus.setText("🔴 Không tìm thấy vật thể");
                    tvResult.setText("Hãy thử chụp lại gần hơn hoặc rõ hơn.");
                    return;
                }

                tvStatus.setText("🟢 Đã nhận diện");

                YoloDetector.Detection mainDet = currentDetections.get(0);
                mainObjectNameEn = mainDet.label;
                mainObjectNameVi = translateLabel(mainObjectNameEn);

                drawBoundingBoxes(bitmap, currentDetections);
                setupRecyclerView(currentDetections);

                fetchDescriptionFromGemini(mainObjectNameEn);
            });
        });
    }

    private void drawBoundingBoxes(Bitmap bitmap, List<YoloDetector.Detection> detections) {
        ivResult.post(() -> {
            int viewWidth = ivResult.getWidth();
            int viewHeight = ivResult.getHeight();
            int bmpWidth = bitmap.getWidth();
            int bmpHeight = bitmap.getHeight();
            if (viewWidth == 0 || viewHeight == 0) return;

            float scale = Math.min((float) viewWidth / bmpWidth, (float) viewHeight / bmpHeight);
            float offsetX = (viewWidth - bmpWidth * scale) / 2f;
            float offsetY = (viewHeight - bmpHeight * scale) / 2f;

            List<BoundingBoxView.DetectionBox> overlayBoxes = new ArrayList<>();
            for (YoloDetector.Detection det : detections) {
                RectF scaledRect = new RectF(
                        det.boundingBox.left * scale + offsetX,
                        det.boundingBox.top * scale + offsetY,
                        det.boundingBox.right * scale + offsetX,
                        det.boundingBox.bottom * scale + offsetY
                );
                overlayBoxes.add(new BoundingBoxView.DetectionBox(
                        scaledRect,
                        det.label,
                        det.confidence,
                        Color.parseColor("#22C55E")
                ));
            }
            boxOverlay.setBoxes(overlayBoxes);
        });
    }

    private void fetchDescriptionFromGemini(String objectName) {
        tvResult.setText("Đang lấy mô tả từ AI...");
        String prompt = "Provide a short description (max 3 sentences) for a \"" + objectName
                + "\" in English and Vietnamese. Reply ONLY with JSON: "
                + "{\"en\":\"...\",\"vi\":\"...\"}";

        geminiEngine.generateJson(prompt).thenAccept(json -> runOnUi(() -> {
            descriptionEn = json.optString("en", "No description.");
            descriptionVi = json.optString("vi", "Không có mô tả.");
            renderResultUI();
            saveToFirebase();
            loadAiSuggestions();
        })).exceptionally(t -> {
            Log.e(TAG, "Gemini description error", t);
            runOnUi(() -> {
                descriptionEn = "Description unavailable.";
                descriptionVi = "Không thể lấy mô tả.";
                renderResultUI();
                saveToFirebase();
            });
            return null;
        });
    }

    private void renderResultUI() {
        String desc = showingVietnamese ? descriptionVi : descriptionEn;
        String countText = showingVietnamese
                ? "Số lượng: " + currentDetections.size()
                : "Count: " + currentDetections.size();
        String mainText = showingVietnamese
                ? "Chính: " + mainObjectNameVi
                : "Main: " + mainObjectNameEn;

        tvResult.setText(countText + "\n" + mainText + "\n\n" + desc);
        btnTranslate.setText(showingVietnamese ? "English" : "Tiếng Việt");
        btnTranslate.setVisibility(View.VISIBLE);

        if (detectedAdapter != null) detectedAdapter.notifyDataSetChanged();
    }

    /** Cập nhật phần AI theo ngôn ngữ hiện tại, KHÔNG đổi ngôn ngữ. */
    private void renderAiUI() {
        tvAiSuggestion.setText(showingVietnamese ? aiSummaryVi : aiSummaryEn);
        chipGroupSuggest.removeAllViews();
        List<String> qs = showingVietnamese ? questionsVi : questionsEn;
        for (String q : qs) {
            Chip chip = new Chip(requireContext());
            chip.setText(q);
            chip.setOnClickListener(x -> askAiWithQuestion(q));
            chipGroupSuggest.addView(chip);
        }
    }

    private void toggleLanguage() {
        showingVietnamese = !showingVietnamese;
        renderResultUI();
        renderAiUI();
    }

    private void speakResult() {
        if (currentDetections.isEmpty()) return;
        String textToSpeak = showingVietnamese
                ? "Có " + currentDetections.size() + " vật thể. Vật chính là " + mainObjectNameVi + ". " + descriptionVi
                : "There are " + currentDetections.size() + " objects. Main is " + mainObjectNameEn + ". " + descriptionEn;
        tts.speak(textToSpeak, showingVietnamese, null);
    }

    private void loadAiSuggestions() {
        if (mainObjectNameEn.isEmpty()) return;

        aiCard.setVisibility(View.VISIBLE);
        progressAi.setVisibility(View.VISIBLE);
        tvAiSuggestion.setText(showingVietnamese ? "Đang tải gợi ý..." : "Loading suggestions...");

        String prompt = "The object is: " + mainObjectNameEn + ". "
                + "Help a curious learner explore it. Reply ONLY with JSON in this exact shape: "
                + "{\"summary_en\":\"3 short facts, one per line starting with '• '\","
                + "\"summary_vi\":\"same in Vietnamese\","
                + "\"questions_en\":[\"3 short follow-up questions\"],"
                + "\"questions_vi\":[\"3 questions in Vietnamese\"]}";

        geminiEngine.generateJson(prompt).thenAccept(json -> runOnUi(() -> {
            progressAi.setVisibility(View.GONE);
            aiSummaryEn = json.optString("summary_en", "");
            aiSummaryVi = json.optString("summary_vi", "");
            questionsEn.clear();
            questionsVi.clear();

            JSONArray qEn = json.optJSONArray("questions_en");
            JSONArray qVi = json.optJSONArray("questions_vi");
            if (qEn != null) for (int i = 0; i < qEn.length(); i++) questionsEn.add(qEn.optString(i));
            if (qVi != null) for (int i = 0; i < qVi.length(); i++) questionsVi.add(qVi.optString(i));

            renderAiUI();
        })).exceptionally(t -> {
            Log.e(TAG, "Gemini suggestions error", t);
            runOnUi(() -> {
                progressAi.setVisibility(View.GONE);
                tvAiSuggestion.setText("Không tải được gợi ý.");
            });
            return null;
        });
    }

    private void askAi() {
        String q = etAiQuestion.getText().toString().trim();
        if (!q.isEmpty()) askAiWithQuestion(q);
    }

    private void askAiWithQuestion(String question) {
        if (mainObjectNameEn.isEmpty()) return;

        progressAi.setVisibility(View.VISIBLE);
        tvAiSuggestion.setText(showingVietnamese ? "Đang trả lời..." : "Thinking...");
        etAiQuestion.setText("");

        String prompt = "About the object \"" + mainObjectNameEn + "\", the learner asks: " + question + "\n"
                + "Answer briefly (max 5 sentences). Reply ONLY with JSON: "
                + "{\"answer_en\":\"answer in English\",\"answer_vi\":\"answer in Vietnamese\"}";

        geminiEngine.generateJson(prompt).thenAccept(json -> runOnUi(() -> {
            progressAi.setVisibility(View.GONE);
            aiSummaryEn = json.optString("answer_en", "");
            aiSummaryVi = json.optString("answer_vi", "");
            tvAiSuggestion.setText(showingVietnamese ? aiSummaryVi : aiSummaryEn);
            // Ẩn các câu hỏi gợi ý khi đã hỏi đáp
            questionsEn.clear();
            questionsVi.clear();
            chipGroupSuggest.removeAllViews();
        })).exceptionally(t -> {
            Log.e(TAG, "Gemini ask error", t);
            runOnUi(() -> {
                progressAi.setVisibility(View.GONE);
                tvAiSuggestion.setText("Lỗi kết nối AI.");
            });
            return null;
        });
    }

    private void saveToFirebase() {
        if (isDataSaved || lastCapturedBitmap == null) return;
        isDataSaved = true;

        // 1. Nén ảnh
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        lastCapturedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

        // 2. Đóng gói tọa độ box
        JSONArray boxArray = new JSONArray();
        for (YoloDetector.Detection d : currentDetections) {
            JSONObject box = new JSONObject();
            try {
                box.put("label", d.label);
                box.put("confidence", d.confidence);
                box.put("x", d.boundingBox.left);
                box.put("y", d.boundingBox.top);
                box.put("w", d.boundingBox.width());
                box.put("h", d.boundingBox.height());
                boxArray.put(box);
            } catch (Exception ignored) {
            }
        }

        // 3. Tạo model và push lên Firebase (ScanHistory cần có public String boxData)
        ScanHistory history = new ScanHistory();
        history.timestamp = System.currentTimeMillis();
        history.imageBase64 = base64Image;
        history.labelEn = mainObjectNameEn;
        history.labelVi = mainObjectNameVi;
        history.descriptionEn = descriptionEn;
        history.descriptionVi = descriptionVi;
        history.objectCount = currentDetections.size();
        history.boxData = boxArray.toString();

        databaseRef.push().setValue(history).addOnCompleteListener(task -> {
            if (task.isSuccessful() && isAdded()) {
                Toast.makeText(requireContext(), "Đã lưu vào CSDL", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView(List<YoloDetector.Detection> detections) {
        tvDetectedObjectsTitle.setVisibility(View.VISIBLE);
        rvDetectedObjects.setVisibility(View.VISIBLE);

        if (detectedAdapter == null) {
            detectedAdapter = new DetectedObjectAdapter(detections);
            rvDetectedObjects.setAdapter(detectedAdapter);
        } else {
            detectedAdapter.updateData(detections);
        }
    }

    private String translateLabel(String en) {
        // Từ điển mini dịch nhãn COCO sang tiếng Việt
        switch (en) {
            case "chair": return "Cái ghế";
            case "bottle": return "Chai lọ";
            case "dining table": return "Bàn ăn";
            case "person": return "Người";
            case "cup": return "Cái cốc";
            case "cell phone": return "Điện thoại";
            case "laptop": return "Máy tính xách tay";
            default: return en;
        }
    }

    // ================= ADAPTER =================
    private class DetectedObjectAdapter extends RecyclerView.Adapter<DetectedObjectAdapter.VH> {
        private List<YoloDetector.Detection> data;

        DetectedObjectAdapter(List<YoloDetector.Detection> data) {
            this.data = new ArrayList<>(data);
        }

        void updateData(List<YoloDetector.Detection> newData) {
            this.data = new ArrayList<>(newData);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_detected_object, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            YoloDetector.Detection det = data.get(position);
            String nameEn = det.label;
            String nameVi = translateLabel(nameEn);

            h.tvName.setText(showingVietnamese ? nameVi : nameEn);
            h.tvVi.setText(showingVietnamese ? nameEn : nameVi);
            h.tvConfidence.setText(String.format(Locale.US, "Độ tin cậy: %.0f%%", det.confidence * 100));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvVi, tvConfidence;

            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tvObjName);
                tvVi = v.findViewById(R.id.tvObjVi);
                tvConfidence = v.findViewById(R.id.tvObjConfidence);
            }
        }
    }
}