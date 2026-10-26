package com.example.lensai.ml;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiRecognitionEngine {

    private static final String TAG = "GeminiRecognition";
    // Đổi sang model đang có trên Google AI Studio nếu cần
    private static final String MODEL = "gemini-3.8-flash";
    private static final int MAX_ATTEMPTS = 5;

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final OkHttpClient client;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public GeminiRecognitionEngine(String apiKey) {
        this.apiKey = apiKey;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)   // 30 → 15
                .readTimeout(60, TimeUnit.SECONDS)      // 120 → 60
                .writeTimeout(30, TimeUnit.SECONDS)     // 120 → 30
                .build();
    }

    public CompletableFuture<JSONObject> recognize(Bitmap bitmap) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        executor.execute(() -> {
            try {
                String base64Image = bitmapToBase64(bitmap);
                String prompt = buildPrompt();
                JSONObject body = buildRequestBody(prompt, base64Image);
                JSONObject result = sendWithRetry(body);
                future.complete(result);
            } catch (Exception e) {
                Log.e(TAG, "Gemini recognition failed", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private JSONObject sendWithRetry(JSONObject body) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL
                + ":generateContent?key="
                + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Response response = client.newCall(request).execute()) {

                if (response.isSuccessful()) {
                    if (response.body() == null) {
                        throw new IOException("Gemini returned an empty response body");
                    }

                    String responseText = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseText);

                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                    if (candidates == null || candidates.length() == 0) {
                        throw new Exception("Gemini returned no candidates");
                    }

                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject content = candidate.getJSONObject("content");
                    JSONArray parts = content.getJSONArray("parts");

                    String rawText = null;
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        if (part.has("text")) {
                            rawText = part.getString("text");
                            break;
                        }
                    }

                    if (rawText == null || rawText.trim().isEmpty()) {
                        throw new Exception("Gemini returned no text result");
                    }

                    return new JSONObject(cleanJson(rawText));
                }

                int code = response.code();
                String errorBody = response.body() != null
                        ? response.body().string()
                        : "No error details";

                boolean retryable = code == 429 || code == 500
                        || code == 502 || code == 503 || code == 504;

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw new Exception("Gemini HTTP " + code + ": " + errorBody);
                }

                long waitSeconds = 1L << attempt;
                Log.w(TAG, "Temporary Gemini error HTTP " + code
                        + ". Retry " + (attempt + 1) + "/" + MAX_ATTEMPTS
                        + " after " + waitSeconds + "s");
                Thread.sleep(waitSeconds * 1000);

            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) throw e;
                long waitSeconds = 1L << attempt;
                Log.w(TAG, "Network error. Retry " + (attempt + 1) + "/" + MAX_ATTEMPTS, e);
                Thread.sleep(waitSeconds * 1000);
            }
        }

        throw new Exception("Gemini request failed");
    }

    private JSONObject buildRequestBody(String prompt, String base64Image) throws Exception {
        JSONObject inlineData = new JSONObject();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64Image);

        JSONObject imagePart = new JSONObject();
        imagePart.put("inline_data", inlineData);

        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);

        JSONArray parts = new JSONArray();
        parts.put(textPart);
        parts.put(imagePart);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(content);

        JSONObject body = new JSONObject();
        body.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.15);
        generationConfig.put("response_mime_type", "application/json");
        body.put("generationConfig", generationConfig);

        return body;
    }

    private String bitmapToBase64(Bitmap bitmap) {

        Bitmap resized = resizeBitmap(bitmap, 1024);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);

        byte[] bytes = baos.toByteArray();
        if (resized != bitmap) resized.recycle();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        if (ratio >= 1.0f) {
            return original;
        }
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    /**
     * Prompt mới: đếm số lượng + liệt kê tất cả + mô tả bao quát.
     * Vẫn giữ main_object, description_en/vi, objects, category, confidence.
     */
    private String buildPrompt() {
        return """
            Analyze the image. Count all clearly visible objects, list them, pick the main one, and write an overall scene description.

            Return ONLY this JSON (no markdown):
            {
              "object_count": 0,
              "objects": [
                {"name_en":"", "name_vi":"", "category":"object", "confidence":0.0}
              ],
              "main_object": {"name_en":"", "name_vi":""},
              "description_en":"",
              "description_vi":""
            }

            category: object|animal|plant|food|person|vehicle|furniture|electronics|other
            confidence: 0–1. Do not invent objects.
            """;
    }

    private String cleanJson(String text) {
        if (text == null) {
            return "{}";
        }
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}