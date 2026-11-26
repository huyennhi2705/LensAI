// Đường dẫn: app/src/main/java/com/example/lensai/service/TranslationService.java
package com.example.lensai.service;

import java.util.HashMap;
import java.util.Map;

public class TranslationService {
    private static final Map<String, String> DICTIONARY = new HashMap<>();

    static {
        // 80 lớp COCO – key phải khớp CHÍNH XÁC với labels.txt (chữ thường, có dấu cách)
        DICTIONARY.put("person", "Người");
        DICTIONARY.put("bicycle", "Xe đạp");
        DICTIONARY.put("car", "Ô tô");
        DICTIONARY.put("motorcycle", "Xe máy");
        DICTIONARY.put("airplane", "Máy bay");
        DICTIONARY.put("bus", "Xe buýt");
        DICTIONARY.put("train", "Tàu hỏa");
        DICTIONARY.put("truck", "Xe tải");
        DICTIONARY.put("boat", "Thuyền");
        DICTIONARY.put("traffic light", "Đèn giao thông");
        DICTIONARY.put("fire hydrant", "Trụ nước cứu hỏa");
        DICTIONARY.put("stop sign", "Biển dừng");
        DICTIONARY.put("parking meter", "Đồng hồ đỗ xe");
        DICTIONARY.put("bench", "Ghế băng");
        DICTIONARY.put("bird", "Con chim");
        DICTIONARY.put("cat", "Con mèo");
        DICTIONARY.put("dog", "Con chó");
        DICTIONARY.put("horse", "Con ngựa");
        DICTIONARY.put("sheep", "Con cừu");
        DICTIONARY.put("cow", "Con bò");
        DICTIONARY.put("elephant", "Con voi");
        DICTIONARY.put("bear", "Con gấu");
        DICTIONARY.put("zebra", "Con ngựa vằn");
        DICTIONARY.put("giraffe", "Hươu cao cổ");
        DICTIONARY.put("backpack", "Ba lô");
        DICTIONARY.put("umbrella", "Ô");
        DICTIONARY.put("handbag", "Túi xách");
        DICTIONARY.put("tie", "Cà vạt");
        DICTIONARY.put("suitcase", "Va li");
        DICTIONARY.put("frisbee", "Đĩa bay");
        DICTIONARY.put("skis", "Ván trượt tuyết");
        DICTIONARY.put("snowboard", "Ván trượt tuyết");
        DICTIONARY.put("sports ball", "Bóng thể thao");
        DICTIONARY.put("kite", "Cánh diều");
        DICTIONARY.put("baseball bat", "Gậy bóng chày");
        DICTIONARY.put("baseball glove", "Găng tay bóng chày");
        DICTIONARY.put("skateboard", "Ván trượt");
        DICTIONARY.put("surfboard", "Ván lướt sóng");
        DICTIONARY.put("tennis racket", "Vợt tennis");
        DICTIONARY.put("bottle", "Chai");
        DICTIONARY.put("wine glass", "Ly rượu");
        DICTIONARY.put("cup", "Cốc");
        DICTIONARY.put("fork", "Nĩa");
        DICTIONARY.put("knife", "Dao");
        DICTIONARY.put("spoon", "Thìa");
        DICTIONARY.put("bowl", "Bát");
        DICTIONARY.put("banana", "Chuối");
        DICTIONARY.put("apple", "Táo");
        DICTIONARY.put("sandwich", "Bánh sandwich");
        DICTIONARY.put("orange", "Cam");
        DICTIONARY.put("broccoli", "Súp lơ");
        DICTIONARY.put("carrot", "Cà rốt");
        DICTIONARY.put("hot dog", "Xúc xích");
        DICTIONARY.put("pizza", "Pizza");
        DICTIONARY.put("donut", "Bánh vòng");
        DICTIONARY.put("cake", "Bánh ngọt");
        DICTIONARY.put("chair", "Ghế");
        DICTIONARY.put("couch", "Ghế sofa");
        DICTIONARY.put("potted plant", "Cây cảnh");
        DICTIONARY.put("bed", "Giường");
        DICTIONARY.put("dining table", "Bàn ăn");
        DICTIONARY.put("toilet", "Nhà vệ sinh");
        DICTIONARY.put("tv", "Tivi");
        DICTIONARY.put("laptop", "Máy tính xách tay");
        DICTIONARY.put("mouse", "Chuột máy tính");
        DICTIONARY.put("remote", "Điều khiển");
        DICTIONARY.put("keyboard", "Bàn phím");
        DICTIONARY.put("cell phone", "Điện thoại");
        DICTIONARY.put("microwave", "Lò vi sóng");
        DICTIONARY.put("oven", "Lò nướng");
        DICTIONARY.put("toaster", "Máy nướng bánh");
        DICTIONARY.put("sink", "Bồn rửa");
        DICTIONARY.put("refrigerator", "Tủ lạnh");
        DICTIONARY.put("book", "Sách");
        DICTIONARY.put("clock", "Đồng hồ");
        DICTIONARY.put("vase", "Bình hoa");
        DICTIONARY.put("scissors", "Kéo");
        DICTIONARY.put("teddy bear", "Gấu bông");
        DICTIONARY.put("hair drier", "Máy sấy tóc");
        DICTIONARY.put("toothbrush", "Bàn chải đánh răng");
    }

    /** Trả về tên tiếng Việt nếu có, ngược lại trả về nguyên bản tiếng Anh. */
    public static String toVietnamese(String englishLabel) {
        if (englishLabel == null) return "";
        String vi = DICTIONARY.get(englishLabel.toLowerCase().trim());
        return vi != null ? vi : englishLabel;
    }
}