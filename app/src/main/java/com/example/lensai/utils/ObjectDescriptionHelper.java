package com.example.lensai.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ObjectDescriptionHelper {

    private static final Map<String, String> FIX_NAME = new HashMap<>();
    private static final Map<String, String> SHAPE_MAP = new HashMap<>();

    static {
        // Sửa tên hay bị ML Kit nhận sai
        FIX_NAME.put("helmet", "Computer Mouse");
        FIX_NAME.put("hat", "Computer Mouse");
        FIX_NAME.put("mouse", "Computer Mouse");
        FIX_NAME.put("computer mouse", "Computer Mouse");
        FIX_NAME.put("dau", "Computer Mouse"); // nếu model cũ trả về "Dau"

        // Hình dạng theo class
        SHAPE_MAP.put("computer mouse", "oval-shaped");
        SHAPE_MAP.put("mouse", "oval-shaped");
        SHAPE_MAP.put("keyboard", "rectangular");
        SHAPE_MAP.put("cell phone", "rectangular");
        SHAPE_MAP.put("laptop", "rectangular");
        SHAPE_MAP.put("bottle", "cylindrical");
        SHAPE_MAP.put("cup", "cylindrical");
        SHAPE_MAP.put("book", "rectangular");
    }

    public static String getEnglishName(String rawLabel) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return "Unknown Object";
        }

        String key = rawLabel.toLowerCase(Locale.US).trim();

        if (FIX_NAME.containsKey(key)) {
            return FIX_NAME.get(key);
        }

        // Viết hoa chữ cái đầu
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

    /**
     * Mô tả có màu + hình dạng
     */
    public static String getDescription(String englishName, Bitmap bitmap) {
        if (englishName == null || englishName.trim().isEmpty()) {
            return "No description available.";
        }

        String color = getDominantColor(bitmap);
        String shape = SHAPE_MAP.getOrDefault(englishName.toLowerCase(), "");

        if (!shape.isEmpty()) {
            return "A " + color + " " + shape + " " + englishName.toLowerCase() + ".";
        } else {
            return "A " + color + " " + englishName.toLowerCase() + ".";
        }
    }

    // Giữ lại version cũ nếu cần
    public static String getDescription(String englishName) {
        return "This is a " + (englishName != null ? englishName.toLowerCase() : "object") + ".";
    }

    /**
     * Lấy màu chủ đạo đơn giản
     */
    public static String getDominantColor(Bitmap bitmap) {
        if (bitmap == null) return "unknown-colored";

        long r = 0, g = 0, b = 0;
        int count = 0;
        int step = 6; // lấy mẫu thưa để nhanh

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

        // Phân loại màu đơn giản
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
}