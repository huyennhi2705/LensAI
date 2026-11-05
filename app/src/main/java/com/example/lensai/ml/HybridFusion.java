package com.example.lensai.ml;

import android.graphics.RectF;
import android.util.Log;

import com.example.lensai.model.AIResult;
import com.example.lensai.model.DetectedObjectInfo;
import com.example.lensai.model.HybridResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public final class HybridFusion {

    public static final float MIN_CONFIDENCE = 0.55f;
    private static final String TAG = "HybridFusion";

    private HybridFusion() {}

    /**
     * Entry point cho AIService của bạn:
     * yoloResults = model.tflite + labels.txt (có thể kèm box)
     * geminiJsonText = raw JSON string từ CloudAIService
     */
    public static HybridResult fuseFromYoloAndGeminiJson(
            List<AIResult> yoloResults,
            String geminiJsonText) {

        JSONObject geminiJson = null;
        if (geminiJsonText != null && !geminiJsonText.trim().isEmpty()) {
            try {
                String cleaned = cleanJson(geminiJsonText);
                geminiJson = new JSONObject(cleaned);
            } catch (Exception e) {
                Log.w(TAG, "Cannot parse Gemini JSON", e);
            }
        }

        HybridResult h = fuse(geminiJson, yoloResults);

        // Gắn / bổ sung bbox từ YOLO nếu Gemini thiếu
        if (yoloResults != null && !yoloResults.isEmpty()) {
            for (int i = 0; i < yoloResults.size(); i++) {
                AIResult y = yoloResults.get(i);
                RectF box = extractBox(y);
                if (box == null) continue;

                if (i < h.objects.size()) {
                    if (h.objects.get(i).box == null) {
                        h.objects.get(i).box = box;
                    }
                } else {
                    DetectedObjectInfo info = new DetectedObjectInfo();
                    String label = y.label != null ? y.label : "Unknown";
                    info.confidence = y.confidence;
                    info.box = box;
                    info.category = "other";
                    if (y.confidence < MIN_CONFIDENCE) {
                        info.nameEn = "Not sure";
                        info.nameVi = "Không chắc chắn";
                    } else {
                        info.nameEn = label;
                        info.nameVi = label;
                    }
                    h.objects.add(info);
                }
            }
            if (h.objectCount <= 0) {
                h.objectCount = h.objects.size();
            }
        }

        return h;
    }

    /**
     * Core fusion: Gemini JSON + danh sách YOLO/TFLite.
     */
    public static HybridResult fuse(JSONObject geminiJson, List<AIResult> tfliteOrYolo) {
        HybridResult h = new HybridResult();
        h.rawGeminiJson = geminiJson != null ? geminiJson.toString() : "";

        // Top YOLO/TFLite
        AIResult topTf = null;
        if (tfliteOrYolo != null) {
            for (AIResult r : tfliteOrYolo) {
                if (r == null) continue;
                if (topTf == null || r.confidence > topTf.confidence) {
                    topTf = r;
                }
            }
        }
        boolean tfliteSure = topTf != null && topTf.confidence >= MIN_CONFIDENCE;

        String gNameEn = "Unknown";
        String gNameVi = "Không xác định";
        float gConf = 0f;
        StringBuilder listEn = new StringBuilder();
        StringBuilder listVi = new StringBuilder();

        if (geminiJson != null) {
            // main_object (format mới) hoặc fallback
            JSONObject main = geminiJson.optJSONObject("main_object");
            if (main != null) {
                gNameEn = main.optString("name_en",
                        main.optString("name", "Unknown"));
                gNameVi = main.optString("name_vi", "Không xác định");
            }

            h.descEn = firstNonEmpty(
                    geminiJson.optString("description_en", null),
                    geminiJson.optString("description", ""),
                    "");
            h.descVi = firstNonEmpty(
                    geminiJson.optString("description_vi", null),
                    "");

            JSONArray objs = geminiJson.optJSONArray("objects");
            if (objs != null && objs.length() > 0) {
                h.objectCount = objs.length();
                for (int i = 0; i < objs.length(); i++) {
                    JSONObject o = objs.optJSONObject(i);
                    if (o == null) continue;

                    String nEn = firstNonEmpty(
                            o.optString("name_en", null),
                            o.optString("name", "Unknown"),
                            "Unknown");
                    String nVi = o.optString("name_vi", "Không xác định");
                    float conf = (float) o.optDouble("confidence", 0.0);
                    String cat = o.optString("category", "other");

                    if (conf < MIN_CONFIDENCE) {
                        nEn = "Not sure";
                        nVi = "Không chắc chắn";
                    }

                    DetectedObjectInfo info = new DetectedObjectInfo();
                    info.nameEn = nEn;
                    info.nameVi = nVi;
                    info.confidence = conf;
                    info.category = cat;

                    JSONObject bbox = o.optJSONObject("bbox");
                    if (bbox != null) {
                        float x = (float) bbox.optDouble("x", 0);
                        float y = (float) bbox.optDouble("y", 0);
                        float w = (float) bbox.optDouble("w", 0);
                        float hh = (float) bbox.optDouble("h", 0);
                        info.box = new RectF(x, y, x + w, y + hh);
                    }

                    h.objects.add(info);

                    if (i > 0) {
                        listEn.append(", ");
                        listVi.append(", ");
                    }
                    listEn.append(nEn)
                            .append(" (")
                            .append(String.format(Locale.US, "%.0f%%", conf * 100))
                            .append(")");
                    listVi.append(nVi)
                            .append(" (")
                            .append(String.format(Locale.US, "%.0f%%", conf * 100))
                            .append(")");

                    if (i == 0) {
                        gConf = conf;
                        h.category = cat;
                        if (main == null) {
                            gNameEn = nEn;
                            gNameVi = nVi;
                        }
                    }
                }
            } else {
                // format cũ: count
                h.objectCount = geminiJson.optInt("object_count",
                        geminiJson.optInt("count", 0));
            }
        }

        h.objectsListEn = listEn.toString();
        h.objectsListVi = listVi.toString();

        boolean geminiSure = gConf >= MIN_CONFIDENCE
                && !"Unknown".equalsIgnoreCase(gNameEn)
                && !"Not sure".equalsIgnoreCase(gNameEn);

        if (geminiSure) {
            h.nameEn = gNameEn;
            h.nameVi = gNameVi;
            h.confidence = gConf;
            h.source = tfliteSure ? "hybrid" : "gemini";
        } else if (tfliteSure) {
            h.nameEn = topTf.label != null ? topTf.label : "Unknown";
            h.nameVi = h.nameEn;
            h.confidence = topTf.confidence;
            h.source = "tflite";
            if (isEmpty(h.descEn)) {
                h.descEn = "Detected by on-device model: " + h.nameEn;
            }
            if (isEmpty(h.descVi)) {
                h.descVi = "Nhận diện bởi model trên thiết bị: " + h.nameEn;
            }
            if (h.objectCount == 0) {
                h.objectCount = 1;
                h.objectsListEn = h.nameEn + " (" +
                        String.format(Locale.US, "%.0f%%", h.confidence * 100) + ")";
                h.objectsListVi = h.objectsListEn;
            }
            if (h.objects.isEmpty()) {
                DetectedObjectInfo info = new DetectedObjectInfo();
                info.nameEn = h.nameEn;
                info.nameVi = h.nameVi;
                info.confidence = h.confidence;
                info.box = extractBox(topTf);
                h.objects.add(info);
            }
        } else {
            h.nameEn = "Not sure";
            h.nameVi = "Không chắc chắn";
            h.confidence = Math.max(gConf, topTf != null ? topTf.confidence : 0f);
            h.source = "none";
            if (isEmpty(h.descEn)) {
                h.descEn = "Not confident enough. Try closer or better light.";
            }
            if (isEmpty(h.descVi)) {
                h.descVi = "Không đủ chắc chắn. Hãy lại gần hoặc đủ sáng hơn.";
            }
        }

        // Gợi ý TFLite/YOLO để hiển thị
        if (tfliteOrYolo != null && !tfliteOrYolo.isEmpty()) {
            StringBuilder tip = new StringBuilder(" | On-device top: ");
            int n = Math.min(3, tfliteOrYolo.size());
            for (int i = 0; i < n; i++) {
                AIResult r = tfliteOrYolo.get(i);
                if (r == null) continue;
                if (i > 0) tip.append(", ");
                tip.append(r.label)
                        .append(String.format(Locale.US, " %.0f%%", r.confidence * 100));
            }
            h.tfliteHint = tip.toString();
        }

        return h;
    }

    // ---------- helpers ----------

    private static String cleanJson(String text) {
        if (text == null) return "{}";
        text = text.trim();
        if (text.startsWith("```json")) text = text.substring(7);
        else if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        return text.trim();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }


    private static RectF extractBox(AIResult r) {
        if (r == null) return null;
        try {
            // Ưu tiên field public thường gặp
            if (r.box != null) return r.box;
        } catch (Exception ignored) {
        }
        return null;
    }
}
