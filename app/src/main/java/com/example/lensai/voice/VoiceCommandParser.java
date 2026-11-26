package com.example.lensai.voice;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Phân tích lệnh tiếng Việt (thuần Java, không phụ thuộc Android) thành:
 * SEARCH <nhãn COCO> / STOP / UNKNOWN. So khớp không dấu để chịu được sai khác dấu của bộ nhận dạng giọng nói.
 */
public final class VoiceCommandParser {

    public enum Type { SEARCH, STOP, UNKNOWN }

    public static final class Command {
        public final Type type;
        public final String cocoLabel;   // nhãn trong labels.txt, null nếu không phải SEARCH
        public final String spokenName;  // tên tiếng Việt để đọc lại cho người dùng

        Command(Type type, String cocoLabel, String spokenName) {
            this.type = type;
            this.cocoLabel = cocoLabel;
            this.spokenName = spokenName;
        }
    }

    // Cụm dài đặt trước cụm ngắn (LinkedHashMap giữ thứ tự duyệt).
    private static final Map<String, String[]> OBJECTS = new LinkedHashMap<>();
    static {
        put("chai nuoc", "bottle", "chai nước");
        put("chai", "bottle", "chai");
        put("dien thoai", "cell phone", "điện thoại");
        put("cai coc", "cup", "cái cốc");
        put("coc", "cup", "cái cốc");
        put("ly", "cup", "cái ly");
        put("tach", "cup", "cái tách");
        put("chiec ghe", "chair", "chiếc ghế");
        put("ghe sofa", "couch", "ghế sofa");
        put("ghe", "chair", "chiếc ghế");
        put("sofa", "couch", "ghế sofa");
        put("ban an", "dining table", "cái bàn");
        put("cai ban", "dining table", "cái bàn");
        put("ban phim", "keyboard", "bàn phím");
        put("ban", "dining table", "cái bàn");
        put("may tinh xach tay", "laptop", "máy tính xách tay");
        put("laptop", "laptop", "laptop");
        put("chuot", "mouse", "con chuột máy tính");
        put("dieu khien", "remote", "điều khiển");
        put("remote", "remote", "điều khiển");
        put("quyen sach", "book", "quyển sách");
        put("sach", "book", "quyển sách");
        put("keo", "scissors", "cái kéo");
        put("cai bat", "bowl", "cái bát");
        put("bat", "bowl", "cái bát");
        put("thia", "spoon", "cái thìa");
        put("muong", "spoon", "cái thìa");
        put("dao", "knife", "con dao");
        put("nia", "fork", "cái dĩa");
        put("ba lo", "backpack", "ba lô");
        put("tui xach", "handbag", "túi xách");
        put("dong ho", "clock", "đồng hồ");
        put("binh hoa", "vase", "bình hoa");
        put("cay canh", "potted plant", "chậu cây");
        put("tivi", "tv", "tivi");
        put("ti vi", "tv", "tivi");
        put("giuong", "bed", "cái giường");
        put("tu lanh", "refrigerator", "tủ lạnh");
        put("lo vi song", "microwave", "lò vi sóng");
        put("ban chai danh rang", "toothbrush", "bàn chải đánh răng");
        put("con nguoi", "person", "người");
        put("nguoi", "person", "người");
    }

    private static void put(String keyNoAccent, String coco, String spoken) {
        OBJECTS.put(keyNoAccent, new String[]{coco, spoken});
    }

    private VoiceCommandParser() {}

    public static Command parse(String raw) {
        String t = normalize(raw);
        if (t.isEmpty()) return new Command(Type.UNKNOWN, null, null);

        if (matchesWord(t, "dung") || matchesWord(t, "thoi") || t.contains("ngung") || t.contains("huy")) {
            return new Command(Type.STOP, null, null);
        }

        boolean hasVerb = matchesWord(t, "tim") || t.contains("tim kiem") || t.contains("o dau");
        if (hasVerb) {
            for (Map.Entry<String, String[]> e : OBJECTS.entrySet()) {
                if (matchesWord(t, e.getKey())) {
                    return new Command(Type.SEARCH, e.getValue()[0], e.getValue()[1]);
                }
            }
        }
        return new Command(Type.UNKNOWN, null, null);
    }

    /** Tên tiếng Việt để đọc cho một nhãn COCO; trả lại chính nhãn nếu chưa có bản dịch. */
    public static String spokenNameFor(String cocoLabel) {
        for (String[] v : OBJECTS.values()) {
            if (v[0].equals(cocoLabel)) return v[1];
        }
        return cocoLabel;
    }

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    private static boolean matchesWord(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }
}