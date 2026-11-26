package com.example.lensai.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ObjectCategory {
    public static final String FURNITURE = "Furniture";
    public static final String ELECTRONICS = "Electronics";
    public static final String KITCHEN = "Kitchen";
    public static final String FOOD = "Food";
    public static final String ANIMALS = "Animals";
    public static final String VEHICLES = "Vehicles";
    public static final String SPORTS = "Sports";
    public static final String PERSONAL = "Personal";
    public static final String OTHER = "Other";

    private static final Map<String, String> MAP = new HashMap<>();

    static {
        put(PERSONAL, "person", "backpack", "umbrella", "handbag", "tie", "suitcase");
        put(VEHICLES, "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat");
        put(ANIMALS, "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe");
        put(SPORTS, "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
                "baseball glove", "skateboard", "surfboard", "tennis racket");
        put(KITCHEN, "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl",
                "microwave", "oven", "toaster", "sink", "refrigerator");
        put(FOOD, "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog",
                "pizza", "donut", "cake");
        put(FURNITURE, "chair", "couch", "potted plant", "bed", "dining table", "toilet",
                "bench", "vase", "clock");
        put(ELECTRONICS, "tv", "laptop", "mouse", "computer mouse", "remote", "keyboard",
                "cell phone", "smartphone");
    }

    private ObjectCategory() {}

    private static void put(String category, String... names) {
        for (String n : names) MAP.put(n, category);
    }

    public static String of(String label) {
        if (label == null) return OTHER;
        String c = MAP.get(label.trim().toLowerCase(Locale.ROOT));
        return c != null ? c : OTHER;
    }
}