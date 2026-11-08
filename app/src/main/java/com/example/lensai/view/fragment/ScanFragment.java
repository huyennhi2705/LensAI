package com.example.lensai.view.fragment;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.example.lensai.BuildConfig;
import com.example.lensai.R;
import com.example.lensai.ml.GeminiRecognitionEngine;
import com.example.lensai.model.AIResult;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.example.lensai.service.AIService;
import com.example.lensai.utils.PermissionUtils;
import com.example.lensai.utils.TextToSpeechHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";

    // Key lấy từ local.properties -> BuildConfig (KHÔNG hardcode trong code)
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;

    // Camera thu nhỏ về 28% chiều cao màn hình sau khi chụp
    private static final float CAMERA_SHRUNK_PERCENT = 0.28f;

    // ===== Views =====
    private PreviewView previewView;
    private ImageView ivResult;
    private TextView tvResult, tvStatus;
    private ProgressBar progress;
    private Button btnCapture, btnSpeak, btnTranslate;
    private NestedScrollView scrollContent;
    private Guideline guideCamera;
    private View resultCard;

    // ===== Views: AI suggestion =====
    private View aiCard;
    private TextView tvAiSuggestion;
    private ProgressBar progressAi;
    private ChipGroup chipGroupSuggest;
    private EditText etAiQuestion;
    private Button btnAiAsk, btnAiRefresh;

    // ===== Services =====
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private AIService aiService;
    private TextToSpeechHelper tts;
    private HistoryRepository historyRepo;
    private GeminiRecognitionEngine geminiEngine;
    private ExecutorService bg; // giải mã ảnh ở luồng nền

    // ===== State =====
    private File lastPhotoFile;
    private Bitmap lastCroppedBitmap;
    private AIResult bestResult;
    private String lastSpokenText = "";
    private boolean cameraShrunk = false;

    private String nameEn = "";
    private String nameVi = "";
    private String descEn = "";
    private String descVi = "";
    private String category = "other";
    private float confidence = 0f;
    private boolean showingVietnamese = false;
    private boolean savedCurrent = false;
    private boolean savingCurrent = false;

    private int objectCount = 0;
    private String objectsListEn = "";
    private String objectsListVi = "";
    private final List<String> objectNamesEn = new ArrayList<>();
    private final List<String> objectNamesVi = new ArrayList<>();

    // ===== State: AI suggestion =====
    private String aiSummaryEn = "";
    private String aiSummaryVi = "";
    private final List<String> questionsEn = new ArrayList<>();
    private final List<String> questionsVi = new ArrayList<>();

    // Xin quyền camera theo cách mới (kết quả trả đúng về Fragment)
    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(),
                            "Camera permission is required", Toast.LENGTH_LONG).show();
                }
            });


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
        btnTranslate = v.findViewById(R.id.btnTranslate);
        scrollContent = v.findViewById(R.id.scrollContent);
        guideCamera = v.findViewById(R.id.guideCamera);
        resultCard = v.findViewById(R.id.resultCard);

        aiCard = v.findViewById(R.id.aiCard);
        tvAiSuggestion = v.findViewById(R.id.tvAiSuggestion);
        progressAi = v.findViewById(R.id.progressAi);
        chipGroupSuggest = v.findViewById(R.id.chipGroupSuggest);
        etAiQuestion = v.findViewById(R.id.etAiQuestion);
        btnAiAsk = v.findViewById(R.id.btnAiAsk);
        btnAiRefresh = v.findViewById(R.id.btnAiRefresh);

        cameraShrunk = false;
        bg = Executors.newSingleThreadExecutor();

        aiService = new AIService(requireContext());
        tts = new TextToSpeechHelper(requireContext());
        historyRepo = new HistoryRepository();
        geminiEngine = new GeminiRecognitionEngine(GEMINI_API_KEY);

        btnCapture.setOnClickListener(view -> takePhoto());

        btnSpeak.setOnClickListener(view -> {
            if (bestResult == null) {
                Toast.makeText(requireContext(),
                        "Please recognize an object first", Toast.LENGTH_SHORT).show();
                return;
            }
            // Đọc đúng ngôn ngữ đang hiển thị
            String speechText = buildSpeakText(showingVietnamese);
            if (tts != null) {
                tts.speak(speechText, showingVietnamese, () -> autoSave(null));
            }
        });


        btnTranslate.setOnClickListener(view -> {
            showingVietnamese = !showingVietnamese;
            renderResult();
            renderAi();
            lastSpokenText = buildSpeakText(showingVietnamese);

            // Đọc luôn bằng ngôn ngữ vừa chuyển (xóa dòng này nếu không muốn tự đọc)
            if (bestResult != null && tts != null) {
                tts.speak(lastSpokenText, showingVietnamese, () -> autoSave(null));
            }
        });

        // AI suggestion
        btnAiRefresh.setOnClickListener(view -> loadAiSuggestions());
        btnAiAsk.setOnClickListener(view -> submitAiQuestion());
        etAiQuestion.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitAiQuestion();
                return true;
            }
            return false;
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
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
        if (bg != null) {
            bg.shutdownNow();
        }
        if (aiService != null) {
            aiService.release();
        }
        // Rời màn hình khi chưa đọc xong -> vẫn lưu để không mất kết quả
        if (bestResult != null && !savedCurrent && !savingCurrent) {
            autoSave(null);
        }
        if (tts != null) {
            tts.shutdown();
        }
        lastCroppedBitmap = null;
    }

    /** Fragment còn gắn với Activity và view chưa bị hủy */
    private boolean uiAlive() {
        return isAdded() && getView() != null;
    }

    // =====================================================================
    // Camera
    // =====================================================================

    private void startCamera() {
        if (previewView == null) return;

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            try {
                if (!uiAlive()) return;
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

    /** Thu camera lên trên (có animation) và hiện khung kết quả bên dưới. */
    private void shrinkCameraAndShowResult() {
        resultCard.setVisibility(View.VISIBLE);

        if (cameraShrunk || guideCamera == null) return;
        cameraShrunk = true;

        final ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) guideCamera.getLayoutParams();
        ValueAnimator anim = ValueAnimator.ofFloat(lp.guidePercent, CAMERA_SHRUNK_PERCENT);
        anim.setDuration(350);
        anim.addUpdateListener(a -> {
            if (!uiAlive()) return;
            lp.guidePercent = (float) a.getAnimatedValue();
            guideCamera.setLayoutParams(lp);
        });
        anim.start();
    }

    /** Đọc ảnh từ file, thu nhỏ nếu quá lớn và xoay đúng chiều theo EXIF. */
    private Bitmap decodeFileWithExif(String path) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);

            // Giải mã ở kích thước ~1024-2048px là đủ (Gemini chỉ nhận 768px)
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null) return null;

            int orientation = new ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  degrees = 90;  break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                default: degrees = 0;
            }
            if (degrees == 0) return bmp;

            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            return rotated;
        } catch (Exception e) {
            Log.e(TAG, "decodeFileWithExif error", e);
            return null;
        }
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

        // Reset toàn bộ state cho lần quét mới
        bestResult = null;
        lastSpokenText = "";
        showingVietnamese = false;
        savedCurrent = false;
        savingCurrent = false;

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

        aiSummaryEn = "";
        aiSummaryVi = "";
        questionsEn.clear();
        questionsVi.clear();
        chipGroupSuggest.removeAllViews();
        tvAiSuggestion.setText("");
        aiCard.setVisibility(View.GONE);

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
                        if (!uiAlive()) return;
                        final String path = lastPhotoFile.getAbsolutePath();

                        // Giải mã + xoay ảnh ở luồng nền, không làm đơ UI
                        bg.execute(() -> {
                            final Bitmap bmp = decodeFileWithExif(path);

                            // Đã đọc xong vào bộ nhớ -> xóa file tạm
                            //noinspection ResultOfMethodCallIgnored
                            new File(path).delete();

                            if (!isAdded() || getActivity() == null) return;
                            requireActivity().runOnUiThread(() -> {
                                if (!uiAlive()) return;

                                if (bmp == null) {
                                    progress.setVisibility(View.GONE);
                                    btnCapture.setEnabled(true);
                                    tvStatus.setText("🔴  Image error");
                                    Toast.makeText(requireContext(),
                                            "Could not read the image",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                lastCroppedBitmap = bmp;
                                ivResult.setImageBitmap(bmp);
                                ivResult.setVisibility(View.VISIBLE);

                                // Thu camera lên trên, hiện ảnh + kết quả bên dưới
                                shrinkCameraAndShowResult();
                                scrollContent.post(() -> scrollContent.smoothScrollTo(0, 0));

                                // Gemini cho kết quả chính
                                recognizeWithGemini(bmp);
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (!uiAlive()) return;
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        tvStatus.setText("🔴  Capture failed");
                        Toast.makeText(requireContext(),
                                "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =====================================================================
    // Nhận diện bằng Gemini
    // =====================================================================

    private void recognizeWithGemini(Bitmap bitmap) {
        if (geminiEngine == null) {
            progress.setVisibility(View.GONE);
            btnCapture.setEnabled(true);
            tvStatus.setText("🔴  Engine not ready");
            return;
        }

        geminiEngine.recognize(bitmap)
                .thenAccept(result -> {
                    if (!isAdded()) return;

                    requireActivity().runOnUiThread(() -> {
                        if (!uiAlive()) return;

                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);

                        try {
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

                            category = "other";
                            confidence = 0f;

                            objectCount = result.optInt("object_count", 0);
                            objectNamesEn.clear();
                            objectNamesVi.clear();

                            JSONArray objs = result.optJSONArray("objects");
                            StringBuilder listEn = new StringBuilder();
                            StringBuilder listVi = new StringBuilder();

                            if (objs != null && objs.length() > 0) {
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

                                    // Lấy category + confidence của vật chính
                                    boolean isMain = nameEn.equalsIgnoreCase(nEn);
                                    if (i == 0 || isMain) {
                                        category = o.optString("category", "other");
                                        confidence = (float) o.optDouble("confidence", 0.0);
                                    }
                                }
                            }

                            objectsListEn = listEn.toString();
                            objectsListVi = listVi.toString();

                            // Tiếng Anh trước
                            showingVietnamese = false;
                            btnTranslate.setVisibility(View.VISIBLE);
                            renderResult();
                            tvStatus.setText("🟢  Done");

                            bestResult = new AIResult();
                            bestResult.label = nameEn;
                            bestResult.confidence = confidence;
                            savedCurrent = false;

                            // Đọc tiếng Anh trước; đọc xong thì tự lưu vào lịch sử
                            lastSpokenText = buildSpeakText(false);
                            tts.speak(lastSpokenText, false, () -> autoSave(null));

                            // Dự phòng: nếu bộ đọc không báo "xong" (không có tiếng, lỗi engine...)
                            // thì vẫn lưu sau 10 giây. autoSave có cờ chống lưu trùng.
                            resultCard.postDelayed(() -> {
                                if (uiAlive()) autoSave(null);
                            }, 10_000);

                            // Gợi ý tìm hiểu từ AI
                            loadAiSuggestions();

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
                        if (!uiAlive()) return;
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        tvStatus.setText("🔴  Error");
                        tvResult.setText("Recognition failed: " + throwable.getMessage());
                        Log.e(TAG, "Gemini failed", throwable);
                    });
                    return null;
                });
    }

    // =====================================================================
    // Gợi ý tìm hiểu từ AI
    // =====================================================================

    private void loadAiSuggestions() {
        if (nameEn.isEmpty() || geminiEngine == null) return;

        aiCard.setVisibility(View.VISIBLE);
        progressAi.setVisibility(View.VISIBLE);
        btnAiRefresh.setEnabled(false);
        tvAiSuggestion.setText(showingVietnamese ? "Đang tải gợi ý..." : "Loading suggestions...");

        String prompt = "The object is: " + nameEn + ". "
                + "Help a curious learner explore it. Reply ONLY with JSON in this exact shape: "
                + "{\"summary_en\":\"3 short interesting facts or uses, one per line starting with '• '\","
                + "\"summary_vi\":\"the same content in Vietnamese\","
                + "\"questions_en\":[\"3 short follow-up questions to learn more\"],"
                + "\"questions_vi\":[\"the same 3 questions in Vietnamese\"]}";

        geminiEngine.generateJson(prompt)
                .thenAccept(json -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!uiAlive()) return;
                        progressAi.setVisibility(View.GONE);
                        btnAiRefresh.setEnabled(true);

                        aiSummaryEn = json.optString("summary_en", "");
                        aiSummaryVi = json.optString("summary_vi", "");
                        fillList(json.optJSONArray("questions_en"), questionsEn);
                        fillList(json.optJSONArray("questions_vi"), questionsVi);
                        renderAi();
                    });
                })
                .exceptionally(t -> {
                    Log.e(TAG, "AI suggestions failed", t);
                    if (!isAdded()) return null;
                    requireActivity().runOnUiThread(() -> {
                        if (!uiAlive()) return;
                        progressAi.setVisibility(View.GONE);
                        btnAiRefresh.setEnabled(true);
                        tvAiSuggestion.setText(showingVietnamese
                                ? "Không tải được gợi ý. Thử lại nhé."
                                : "Could not load suggestions. Please try again.");
                    });
                    return null;
                });
    }

    private void submitAiQuestion() {
        String q = etAiQuestion.getText().toString().trim();
        if (!q.isEmpty()) {
            askAi(q);
        }
    }

    private void askAi(String question) {
        if (nameEn.isEmpty() || geminiEngine == null) {
            Toast.makeText(requireContext(),
                    "Please recognize an object first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressAi.setVisibility(View.VISIBLE);
        btnAiAsk.setEnabled(false);
        tvAiSuggestion.setText(showingVietnamese ? "Đang trả lời..." : "Thinking...");

        String prompt = "About the object \"" + nameEn + "\", the learner asks: " + question + "\n"
                + "Answer briefly (max 5 sentences). Reply ONLY with JSON: "
                + "{\"answer_en\":\"answer in English\",\"answer_vi\":\"answer in Vietnamese\"}";

        geminiEngine.generateJson(prompt)
                .thenAccept(json -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!uiAlive()) return;
                        progressAi.setVisibility(View.GONE);
                        btnAiAsk.setEnabled(true);

                        // Lưu cả 2 ngôn ngữ để nút Translate vẫn đổi được
                        aiSummaryEn = json.optString("answer_en", "");
                        aiSummaryVi = json.optString("answer_vi", "");
                        etAiQuestion.setText("");
                        renderAi();
                    });
                })
                .exceptionally(t -> {
                    Log.e(TAG, "AI ask failed", t);
                    if (!isAdded()) return null;
                    requireActivity().runOnUiThread(() -> {
                        if (!uiAlive()) return;
                        progressAi.setVisibility(View.GONE);
                        btnAiAsk.setEnabled(true);
                        tvAiSuggestion.setText(showingVietnamese
                                ? "Không trả lời được. Thử lại nhé."
                                : "Could not get an answer. Please try again.");
                    });
                    return null;
                });
    }

    private void fillList(JSONArray arr, List<String> out) {
        out.clear();
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
    }

    /** Hiển thị nội dung + chip câu hỏi theo ngôn ngữ hiện tại */
    private void renderAi() {
        if (!uiAlive()) return;

        tvAiSuggestion.setText(showingVietnamese ? aiSummaryVi : aiSummaryEn);

        chipGroupSuggest.removeAllViews();
        List<String> qs = showingVietnamese ? questionsVi : questionsEn;
        for (String q : qs) {
            Chip chip = new Chip(requireContext());
            chip.setText(q);
            chip.setOnClickListener(x -> askAi(q));
            chipGroupSuggest.addView(chip);
        }
    }

    // =====================================================================
    // Hiển thị kết quả + TTS
    // =====================================================================

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

    // =====================================================================
    // Lưu lịch sử (tự động sau khi nhận diện xong)
    // =====================================================================

    private void autoSave(List<AIResult> results) {
        if (bestResult == null || lastCroppedBitmap == null) {
            Log.e(TAG, "Cannot save: result or image is null");
            return;
        }
        if (savedCurrent || savingCurrent) {
            return;
        }
        if (historyRepo == null) {
            Log.e(TAG, "HistoryRepository is null");
            return;
        }

        savingCurrent = true;

        ScanHistory history = new ScanHistory();
        history.labelEn = nameEn;
        history.labelVi = nameVi;

        history.description = descEn;
        history.descriptionEn = descEn;
        history.descriptionVi = descVi;

        history.category = category;
        history.type = category;

        history.confidence = confidence;
        history.timestamp = System.currentTimeMillis();

        history.objectCount = objectCount;
        history.objectsListEn = objectsListEn;
        history.objectsListVi = objectsListVi;

        historyRepo.saveScan(
                history,
                lastCroppedBitmap,

                unused -> {
                    savingCurrent = false;
                    savedCurrent = true;
                    Log.d(TAG, "Save successful: " + nameEn);
                    if (uiAlive()) {
                        Toast.makeText(requireContext(),
                                "Đã lưu vào lịch sử", Toast.LENGTH_SHORT).show();
                    }
                },

                e -> {
                    savingCurrent = false;
                    savedCurrent = false;
                    Log.e(TAG, "Firebase save failed", e);
                    if (uiAlive()) {
                        Toast.makeText(requireContext(),
                                "Lưu thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}