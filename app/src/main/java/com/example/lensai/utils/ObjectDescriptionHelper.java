package com.example.lensai.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.lensai.ml.MaterialClassifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ObjectDescriptionHelper {

    private static final Map<String, String> FIX_NAME = new HashMap<>();
    private static final Map<String, String> SHAPE_MAP = new HashMap<>();
    private static MaterialClassifier materialClassifier;

    static {
        FIX_NAME.put("mouse", "Computer Mouse");
        FIX_NAME.put("computer mouse", "Computer Mouse");
        FIX_NAME.put("helmet", "Computer Mouse");
        FIX_NAME.put("hat", "Computer Mouse");
        FIX_NAME.put("dau", "Computer Mouse");
        SHAPE_MAP.put("computer mouse", "oval-shaped");
        SHAPE_MAP.put("mouse", "oval-shaped");
        SHAPE_MAP.put("keyboard", "rectangular");
        SHAPE_MAP.put("cell phone", "rectangular");
        SHAPE_MAP.put("laptop", "rectangular");
        SHAPE_MAP.put("bottle", "cylindrical");
        SHAPE_MAP.put("cup", "cylindrical");
        SHAPE_MAP.put("book", "rectangular");
        SHAPE_MAP.put("chair", "rectangular");
        SHAPE_MAP.put("tv", "rectangular");
        SHAPE_MAP.put("remote", "rectangular");
    }
    public static void initMaterialClassifier(MaterialClassifier classifier) {
        materialClassifier = classifier;
    }
    public static void initMaterialClassifier(Context context) {
        if (materialClassifier == null) {
            materialClassifier = new MaterialClassifier(context);
        }
    }
    public static String getEnglishName(String rawLabel) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return "Unknown Object";
        }

        String key = rawLabel.toLowerCase(Locale.US).trim();

        if (FIX_NAME.containsKey(key)) {
            return FIX_NAME.get(key);
        }
        String[] words = key.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }
    public static String getDescription(String englishName, Bitmap bitmap) {
        if (englishName == null || englishName.trim().isEmpty()) {
            return "Don't have description for this object.";
        }
        String nameLower = englishName.toLowerCase(Locale.US);
        String color = getDominantColor(bitmap);
        String shape = SHAPE_MAP.getOrDefault(nameLower, "Shape not clearly defined");
        String size = estimateSize(nameLower);
        String material;
        if (materialClassifier != null) {
            material = materialClassifier.predict(bitmap);
        } else {material = predictMaterialRuleBased(nameLower);
        }

        return String.format(
                "Object: %s.\nQuantity: 1.\nShape: %s, estimated size: %s.\nMDominant color: %s.\nPredictive material: %s.",
                englishName, shape, size, color, material
        );
    }
    public static String getDescription(String englishName) {
        return "This is a " + (englishName != null ? englishName.toLowerCase() : "object") + ".";
    }
    public static String getDominantColor(Bitmap bitmap) {
        if (bitmap == null) return "unknown-colored";
        long r = 0, g = 0, b = 0;
        int count = 0;
        int step = 6;

        for (int y = 0; y < bitmap.getHeight(); y += step) {
            for (int x = 0; x < bitmap.getWidth(); x += step) {
                int pixel = bitmap.getPixel(x, y);
                r += Color.red(pixel);
                g += Color.green(pixel);
                b += Color.blue(pixel);
                count++;
            }
        }

        if (count == 0) return "unknown-colored";
        r /= count;
        g /= count;
        b /= count;
        if (r < 50 && g < 50 && b < 50) return "black";
        if (r > 210 && g > 210 && b > 210) return "white";
        if (r > 160 && g < 90 && b < 90) return "red";
        if (r < 90 && g > 150 && b < 90) return "green";
        if (r < 90 && g < 90 && b > 160) return "blue";
        if (r > 180 && g > 180 && b < 100) return "yellow";
        if (r > 150 && g > 100 && b < 80) return "orange";
        if (r > 100 && g < 80 && b > 100) return "purple";
        if (r > 120 && g > 80 && b < 60) return "brown";
        if (Math.abs(r - g) < 30 && Math.abs(g - b) < 30) {
            if (r > 140) return "gray";
            return "dark gray";
        }

        return "dark";
    }
    private static String estimateSize(String name) {
        if (name.contains("phone") || name.contains("mouse") || name.contains("cup")
                || name.contains("bottle") || name.contains("remote") || name.contains("key")) {
            return "small";
        } else if (name.contains("laptop") || name.contains("keyboard") || name.contains("book")
                || name.contains("tablet")) {
            return "Predictive material";
        } else if (name.contains("chair") || name.contains("table") || name.contains("tv")
                || name.contains("monitor") || name.contains("refrigerator")) {
            return "large";
        } else if (name.contains("car") || name.contains("bus") || name.contains("truck")
                || name.contains("motorcycle")) {
            return "very large";
        }
        return "undefined";
    }
    private static String predictMaterialRuleBased(String name) {
        if (name.contains("phone") || name.contains("laptop") || name.contains("keyboard")
                || name.contains("mouse") || name.contains("remote") || name.contains("tv")) {
            return "plastic + metal / glass";
        } else if (name.contains("bottle") || name.contains("cup") || name.contains("glass")) {
            return "plastic or glass";
        } else if (name.contains("book")) {
            return "paper";
        } else if (name.contains("chair") || name.contains("table") || name.contains("bench")) {
                return "wood or metal / plastic";
        } else if (name.contains("car") || name.contains("motorcycle") || name.contains("bicycle")) {
            return "metal + plastic + rubber";
        } else if (name.contains("person") || name.contains("dog") || name.contains("cat")) {
                return "living organism";
        }
        return "undefined";
    }
}