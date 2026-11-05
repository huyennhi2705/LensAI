package com.example.lensai.service;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.example.lensai.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudAIService {
    // Đổi tên biến để khó bị phát hiện hơn
    private static final String API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private static final String TAG = "CloudAIService";
    private final OkHttpClient client;

    public CloudAIService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public interface CloudCallback {
        void onSuccess(String name, String description);
        void onError(String error);
    }

    public void processImage(Bitmap bitmap, String prompt, CloudCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("Missing configuration");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        try {
            JSONObject jsonBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();

            JSONObject textPart = new JSONObject().putOpt("text", prompt);
            parts.put(textPart);

            JSONObject imagePart = new JSONObject();
            JSONObject inlineData = new JSONObject()
                    .putOpt("mime_type", "image/jpeg")
                    .putOpt("data", base64Image);
            imagePart.putOpt("inline_data", inlineData);
            parts.put(imagePart);

            content.putOpt("parts", parts);
            contents.put(content);
            jsonBody.putOpt("contents", contents);

            RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(API_ENDPOINT + apiKey).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String resBody = response.body().string();
                            String rawText = new JSONObject(resBody)
                                    .getJSONArray("candidates").getJSONObject(0)
                                    .getJSONObject("content").getJSONArray("parts")
                                    .getJSONObject(0).getString("text");

                            rawText = rawText.replace("```json", "").replace("```", "").trim();

                            if (!rawText.startsWith("{")) {
                                callback.onSuccess("Trả lời", rawText);
                            } else {
                                JSONObject json = new JSONObject(rawText);
                                callback.onSuccess(json.optString("name", "Vật thể"), json.optString("description", rawText));
                            }
                        } catch (Exception e) {
                            callback.onError("Parsing error: " + e.getMessage());
                        }
                    } else {
                        callback.onError("API Error: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}