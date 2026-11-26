package com.example.lensai.voice;

/**
 * Chuyển toạ độ chuẩn hoá (0..1) của vật thể trong khung hình thành câu tiếng Việt ngắn.
 * Thuần Java. "Trái/phải/trên/dưới" tính theo khung hình của camera khi người dùng
 * cầm điện thoại dọc, hướng ra phía trước. Không ước lượng khoảng cách chính xác.
 */
public final class PositionDescriber {

    public enum Horizontal { LEFT, CENTER, RIGHT }
    public enum Vertical { TOP, MIDDLE, BOTTOM }

    // Vùng "giữa" rộng 24% mỗi chiều để hướng dẫn không quá nhạy.
    private static final float H_LOW = 0.38f, H_HIGH = 0.62f;
    private static final float V_LOW = 0.35f, V_HIGH = 0.65f;
    // Vật chiếm hơn 25% diện tích khung hình thì nói là "trông khá lớn, có thể đang ở gần".
    private static final float LARGE_AREA = 0.25f;

    private PositionDescriber() {}

    public static Horizontal horizontal(float cx) {
        return cx < H_LOW ? Horizontal.LEFT : cx > H_HIGH ? Horizontal.RIGHT : Horizontal.CENTER;
    }

    public static Vertical vertical(float cy) {
        return cy < V_LOW ? Vertical.TOP : cy > V_HIGH ? Vertical.BOTTOM : Vertical.MIDDLE;
    }

    public static boolean isCentered(float cx, float cy) {
        return horizontal(cx) == Horizontal.CENTER && vertical(cy) == Vertical.MIDDLE;
    }

    /** Mô tả vị trí sau khi đã chụp. */
    public static String describe(String name, float cx, float cy, float areaFraction, int count) {
        Horizontal h = horizontal(cx);
        Vertical v = vertical(cy);
        StringBuilder sb = new StringBuilder();
        if (count > 1) sb.append("Thấy ").append(count).append(' ').append(name)
                .append(". Cái rõ nhất ");
        else sb.append(capitalize(name)).append(' ');

        if (h == Horizontal.CENTER && v == Vertical.MIDDLE) {
            sb.append("ở ngay chính giữa trước camera");
        } else {
            sb.append("ở ");
            if (h == Horizontal.LEFT) sb.append("bên trái");
            else if (h == Horizontal.RIGHT) sb.append("bên phải");
            else sb.append("chính giữa");
            if (v == Vertical.TOP) sb.append(", phía trên");
            else if (v == Vertical.BOTTOM) sb.append(", phía dưới");
        }
        sb.append('.');
        if (areaFraction >= LARGE_AREA) sb.append(" Trông khá lớn trong khung hình, có thể đang ở gần.");
        return sb.toString();
    }

    /** Hướng dẫn xoay camera để đưa vật vào giữa (dùng cho Giai đoạn 2). */
    public static String guide(String name, float cx, float cy) {
        Horizontal h = horizontal(cx);
        if (h == Horizontal.LEFT) return "Đưa camera sang trái.";
        if (h == Horizontal.RIGHT) return "Đưa camera sang phải.";
        Vertical v = vertical(cy);
        if (v == Vertical.TOP) return "Hướng camera lên trên.";
        if (v == Vertical.BOTTOM) return "Hướng camera xuống dưới.";
        return "Đã tìm thấy " + name + ". Hãy đưa tay về phía trước để lấy.";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}