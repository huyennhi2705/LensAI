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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeminiRecognitionEngine {

    private static final String TAG = "GeminiEngine";
    // Gặp 404 (model bị gỡ) thì thử model kế tiếp.
    // Cập nhật tại https://ai.google.dev/gemini-api/docs/deprecations
    private static final String[] MODELS = {
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-2.5-flash"
    };
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    public boolean isConfigured() {
        String k = BuildConfig.GEMINI_API_KEY;
        return k != null && !k.trim().isEmpty();
    }

    /** Blocking, gọi từ background thread. Kết quả chỉ chứa lớp có trong labels. */
    public List<AIResult> recognize(Bitmap bitmap, List<String> labels) throws Exception {
        if (!isConfigured()) throw new IOException("Chưa cấu hình GEMINI_API_KEY (local.properties)");

        String body = buildBody(bitmap, labels);
        IOException last = null;

        for (String model : MODELS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(String.format(ENDPOINT, model)).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(40000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 404) {
                    Log.w(TAG, model + " -> 404, thử model khác");
                    last = new IOException("Model " + model + " không còn (404)");
                    continue;
                }
                String resp = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
                if (code >= 400) throw new IOException("Gemini HTTP " + code + ": " + resp);

                Log.d(TAG, "OK với model " + model);
                return parse(resp, labels);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last != null ? last : new IOException("Không gọi được Gemini");
    }

    private String buildBody(Bitmap bitmap, List<String> labels) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String prompt = "The image shows an object the user is pointing a camera at. "
                + "Identify the main object(s) using ONLY names from this class list: "
                + String.join(", ", labels) + ". "
                + "Return JSON only: {\"objects\":[{\"name\":\"<exact name from the list>\","
                + "\"description\":\"<one short English sentence: color, shape, typical use>\","
                + "\"confidence\":<0..1>}]}. "
                + "At most 3 objects, most prominent first. "
                + "If nothing in the image matches the list, return {\"objects\":[]}.";

        JSONObject textPart = new JSONObject().put("text", prompt);
        JSONObject imgPart = new JSONObject().put("inline_data",
                new JSONObject().put("mime_type", "image/jpeg").put("data", b64));

        return new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("parts", new JSONArray().put(textPart).put(imgPart))))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json"))
                .toString();
    }

    private List<AIResult> parse(String resp, List<String> labels) throws Exception {
        JSONObject root = new JSONObject(resp);
        JSONArray cands = root.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) {
            throw new IOException("Gemini không trả kết quả (có thể bị chặn bởi safety)");
        }
        JSONArray parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) text.append(parts.getJSONObject(i).optString("text", ""));

        String json = text.toString().trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }

        // Đưa tên về đúng chính tả trong labels.txt, tên ngoài danh sách thì bỏ
        Map<String, String> canonical = new HashMap<>();
        for (String l : labels) canonical.put(l.toLowerCase(Locale.US), l);

        List<AIResult> list = new ArrayList<>();
        JSONArray arr = new JSONObject(json).optJSONArray("objects");
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = canonical.get(o.optString("name", "").trim().toLowerCase(Locale.US));
            if (name == null) continue;
            AIResult r = new AIResult(name, (float) o.optDouble("confidence", 0.7), "gemini");
            r.description = o.optString("description", "").trim();
            list.add(r);
        }
        return list;
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