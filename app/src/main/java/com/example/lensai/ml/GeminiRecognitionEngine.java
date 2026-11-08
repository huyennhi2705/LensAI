package com.example.lensai.ml;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.example.lensai.BuildConfig;
import com.example.lensai.model.AIResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class GeminiRecognitionEngine {

    private static final String TAG = "GeminiEngine";

    // Thử lần lượt, model nào trả 404 thì chuyển sang model kế tiếp
    // Model nhanh đứng đầu; các model sau chỉ là dự phòng khi 404/quá tải
    private static final String[] MODELS = {
            "gemini-2.5-flash",
            "gemini-flash-lite-latest",
            "gemini-flash-latest",
            "gemini-3.5-flash"
    };
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_SIDE = 768;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final String apiKey;

    /** Dùng key trong local.properties (BuildConfig). */
    public GeminiRecognitionEngine() {
        this(BuildConfig.GEMINI_API_KEY);
    }

    /** ScanFragment đang dùng constructor này. */
    public GeminiRecognitionEngine(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    // =====================================================================
    // 1. Hàm chính cho ScanFragment
    // =====================================================================
    public CompletableFuture<JSONObject> recognize(Bitmap bitmap) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        EXECUTOR.execute(() -> {
            try {
                future.complete(recognizeBlocking(bitmap));
            } catch (Exception e) {
                Log.e(TAG, "recognize failed", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private JSONObject recognizeBlocking(Bitmap bitmap) throws Exception {
        if (!isConfigured()) {
            throw new IOException("Chưa cấu hình GEMINI_API_KEY");
        }

        String prompt = "You are an object recognition assistant. "
                + "Look at the image and identify ALL distinct physical objects clearly visible. "
                + "Return ONLY a valid JSON object (no markdown, no extra text) with this exact schema: "
                + "{\"object_count\":2,"
                + "\"objects\":[{\"name_en\":\"English name\",\"name_vi\":\"Tên tiếng Việt\","
                + "\"category\":\"electronics\",\"confidence\":0.9}],"
                + "\"main_object\":{\"name_en\":\"English name\",\"name_vi\":\"Tên tiếng Việt\"},"
                + "\"description_en\":\"1-2 sentences in English about the main object and scene "
                + "(color, shape, use).\","
                + "\"description_vi\":\"Vietnamese translation of description_en.\"}. "
                + "Rules: name_en is a short common noun; category is one of "
                + "electronics, food, drink, furniture, clothing, plant, animal, vehicle, tool, "
                + "stationery, other; confidence is between 0.0 and 1.0; "
                + "main_object is the most prominent object and must also appear in objects; "
                + "object_count must equal the number of items in objects; "
                + "do not identify real people by name.";

        JSONObject body = buildBody(bitmap, prompt, true);
        String resp = callGemini(body);
        String text = extractText(resp);

        JSONObject result = new JSONObject(cleanJson(text));
        return normalize(result);
    }

    /** Đảm bảo JSON luôn đủ field mà ScanFragment đọc. */
    private JSONObject normalize(JSONObject result) throws Exception {
        JSONArray objs = result.optJSONArray("objects");
        if (objs == null) {
            objs = new JSONArray();
            result.put("objects", objs);
        }

        // main_object: nếu thiếu thì lấy vật đầu tiên
        JSONObject main = result.optJSONObject("main_object");
        if (main == null || main.optString("name_en", "").isEmpty()) {
            main = new JSONObject();
            if (objs.length() > 0) {
                JSONObject first = objs.getJSONObject(0);
                main.put("name_en", first.optString("name_en", "Unknown"));
                main.put("name_vi", first.optString("name_vi", "Không xác định"));
            } else {
                main.put("name_en", "Unknown");
                main.put("name_vi", "Không xác định");
            }
            result.put("main_object", main);
        }

        // object_count luôn bằng độ dài mảng thực tế
        result.put("object_count", objs.length() > 0
                ? objs.length() : result.optInt("object_count", 0));

        if (result.optString("description_en", "").isEmpty()) {
            result.put("description_en", "No description available.");
        }
        if (result.optString("description_vi", "").isEmpty()) {
            result.put("description_vi", "Không có mô tả.");
        }
        return result;
    }

    // =====================================================================
    // 2. Hàm cũ (giữ lại để các màn khác không bị lỗi biên dịch)
    // =====================================================================
    public List<AIResult> recognize(Bitmap bitmap, List<String> labels) throws Exception {
        if (!isConfigured()) {
            throw new IOException("Chưa cấu hình GEMINI_API_KEY");
        }

        String prompt = "Analyze the image carefully. Identify the main objects. "
                + "Return ONLY a valid JSON object (no markdown, no extra text) with this exact schema: "
                + "{\"objects\":[{\"label\":\"English name of the object\","
                + "\"confidence\":0.9,"
                + "\"ymin\":0.1,\"xmin\":0.2,\"ymax\":0.8,\"xmax\":0.9,"
                + "\"description\":\"Short English description (color, shape, use)\"}]}. "
                + "Note: ymin, xmin, ymax, xmax must be normalized coordinates between 0.0 and 1.0.";

        String resp = callGemini(buildBody(bitmap, prompt, true));
        String json = cleanJson(extractText(resp));

        List<AIResult> list = new ArrayList<>();
        JSONArray arr = new JSONObject(json).optJSONArray("objects");
        if (arr == null) return list;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new AIResult(
                    o.optString("label", "Object"),
                    (float) o.optDouble("confidence", 0.8),
                    "gemini",
                    o.optString("description", ""),
                    (float) o.optDouble("xmin", 0.0),
                    (float) o.optDouble("ymin", 0.0),
                    (float) o.optDouble("xmax", 1.0),
                    (float) o.optDouble("ymax", 1.0)));
        }
        return list;
    }

    // =====================================================================
    // 3. Chat hỏi đáp về ảnh
    // =====================================================================
    public String chat(Bitmap bitmap, String userPrompt) throws Exception {
        if (!isConfigured()) {
            throw new IOException("Chưa cấu hình GEMINI_API_KEY");
        }
        String prompt = "Answer this question about the image naturally in English: " + userPrompt;
        String resp = callGemini(buildBody(bitmap, prompt, false));

        JSONArray cands = new JSONObject(resp).optJSONArray("candidates");
        if (cands == null || cands.length() == 0) return "No response.";
        return extractText(resp).trim();
    }

    // =====================================================================
    // 4. Hỏi AI bằng text thuần (không ảnh), trả về JSON.
    //    Dùng cho phần "gợi ý tìm hiểu" trong ScanFragment.
    //    Tái sử dụng callGemini() nên có sẵn retry + đổi model.
    // =====================================================================
    public CompletableFuture<JSONObject> generateJson(String prompt) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        EXECUTOR.execute(() -> {
            try {
                if (!isConfigured()) {
                    throw new IOException("Chưa cấu hình GEMINI_API_KEY");
                }
                JSONObject body = new JSONObject()
                        .put("contents", new JSONArray().put(new JSONObject()
                                .put("parts", new JSONArray()
                                        .put(new JSONObject().put("text", prompt)))))
                        .put("generationConfig", new JSONObject()
                                .put("temperature", 0.7)
                                .put("responseMimeType", "application/json"));

                String resp = callGemini(body);
                future.complete(new JSONObject(cleanJson(extractText(resp))));
            } catch (Exception e) {
                Log.e(TAG, "generateJson failed", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private JSONObject buildBody(Bitmap bitmap, String prompt, boolean json) throws Exception {
        JSONObject textPart = new JSONObject().put("text", prompt);
        JSONObject imgPart = new JSONObject().put("inline_data",
                new JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", encodeBitmap(bitmap)));

        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("parts", new JSONArray().put(textPart).put(imgPart))));

        if (json) {
            body.put("generationConfig", new JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json"));
        }
        return body;
    }

    /** Thu nhỏ ảnh (cạnh dài tối đa 1024px) để gửi nhanh và đỡ tốn dữ liệu. */
    private String encodeBitmap(Bitmap src) {
        Bitmap bmp = src;
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max > MAX_SIDE) {
            float scale = (float) MAX_SIDE / max;
            bmp = Bitmap.createScaledBitmap(src,
                    Math.round(w * scale), Math.round(h * scale), true);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        if (bmp != src) bmp.recycle();
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }


    /**
     * Dòng gemini-2.5 mặc định "suy nghĩ" trước khi trả lời, làm chậm thêm vài giây.
     * Nhận diện vật thể không cần suy luận sâu nên đặt thinkingBudget = 0.
     * Chỉ áp dụng cho model 2.5 để tránh lỗi 400 ở model không hỗ trợ tham số này.
     */
    private byte[] buildPayload(JSONObject body, String model) throws Exception {
        JSONObject b = body;
        if (model.contains("2.5") && !model.contains("lite")) {
            b = new JSONObject(body.toString());
            JSONObject gc = b.optJSONObject("generationConfig");
            if (gc == null) {
                gc = new JSONObject();
                b.put("generationConfig", gc);
            }
            gc.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String callGemini(JSONObject body) throws Exception {
        IOException lastError = null;
        StringBuilder triedModels = new StringBuilder();

        for (String model : MODELS) {

            triedModels.append(model).append(" ");

            // Payload riêng cho từng model (tắt "thinking" với dòng 2.5 để nhanh hơn)
            byte[] payload = buildPayload(body, model);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

                HttpURLConnection conn = null;

                try {
                    URL url = new URL(String.format(ENDPOINT, model));

                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(25000);
                    conn.setDoOutput(true);

                    conn.setRequestProperty(
                            "Content-Type", "application/json"
                    );

                    conn.setRequestProperty(
                            "x-goog-api-key", apiKey
                    );

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload);
                        os.flush();
                    }

                    int code = conn.getResponseCode();

                    InputStream stream = code >= 400
                            ? conn.getErrorStream()
                            : conn.getInputStream();

                    String response = readAll(stream);

                    // Model không tồn tại: chuyển model tiếp theo.
                    if (code == 404) {
                        lastError = new IOException(
                                "Model không khả dụng: " + model
                        );
                        break;
                    }

                    // Thành công.
                    if (code >= 200 && code < 300) {
                        Log.i(TAG, "Gemini success: " + model);
                        return response;
                    }

                    // Chỉ thử lại với lỗi tạm thời.
                    boolean retryable =
                            code == 408 ||
                                    code == 429 ||
                                    code == 500 ||
                                    code == 502 ||
                                    code == 503 ||
                                    code == 504;

                    lastError = new IOException(
                            "Gemini HTTP " + code + ": " + response
                    );

                    if (!retryable) {
                        throw lastError;
                    }

                    Log.w(TAG,
                            "Gemini HTTP " + code
                                    + ", model=" + model
                                    + ", attempt=" + attempt
                    );

                    // Đã hết số lần thử: chuyển sang model kế tiếp.
                    if (attempt == MAX_RETRIES) {
                        break;
                    }

                    // Ưu tiên Retry-After nếu máy chủ cung cấp.
                    long delay = 1000L * (1L << (attempt - 1));

                    String retryAfter = conn.getHeaderField("Retry-After");

                    if (retryAfter != null) {
                        try {
                            long serverDelay =
                                    Long.parseLong(retryAfter.trim()) * 1000L;

                            if (serverDelay > 0) {
                                delay = Math.min(serverDelay, 10000L);
                            }
                        } catch (NumberFormatException ignored) {
                            // Giữ thời gian chờ mặc định.
                        }
                    }

                    Log.i(TAG, "Retry after " + delay + " ms");

                    Thread.sleep(delay);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "Yêu cầu Gemini đã bị gián đoạn", e
                    );

                } catch (IOException e) {
                    lastError = e;

                    // Lỗi kết nối: thử lại nếu còn lượt.
                    if (attempt == MAX_RETRIES) {
                        break;
                    }

                    long delay = 1000L * (1L << (attempt - 1));

                    Log.w(TAG,
                            "Connection error, retry "
                                    + attempt + "/" + MAX_RETRIES,
                            e
                    );

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(
                                "Yêu cầu Gemini đã bị gián đoạn",
                                interrupted
                        );
                    }

                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }

        throw new IOException(
                "Không thể gọi Gemini sau khi thử các model: "
                        + triedModels.toString().trim()
                        + ". Lỗi cuối: "
                        + (lastError != null
                        ? lastError.getMessage()
                        : "Không rõ nguyên nhân"),
                lastError
        );
    }

    private String extractText(String resp) throws Exception {
        JSONObject root = new JSONObject(resp);
        JSONArray cands = root.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) {
            throw new IOException("Gemini không trả kết quả (có thể bị chặn bởi safety)");
        }
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
        if (parts == null) {
            throw new IOException("Gemini trả về nội dung rỗng");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            text.append(parts.getJSONObject(i).optString("text", ""));
        }
        return text.toString();
    }

    /** Bỏ ```json ... ``` và cắt đúng đoạn từ { đến }. */
    private String cleanJson(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }
        int s = json.indexOf('{');
        int e = json.lastIndexOf('}');
        if (s >= 0 && e > s) json = json.substring(s, e + 1);
        return json;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}