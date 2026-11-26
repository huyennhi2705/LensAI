package com.example.lensai.voice;

import java.util.List;

/**
 * Máy trạng thái cho luồng: nói tên đồ vật -> quét xung quanh -> thấy ổn định -> chụp -> mô tả.
 * Thuần Java, không giữ Context/camera. Fragment gọi onFrame() cho mỗi kết quả phát hiện
 * (trên luồng nền hoặc main, nhưng chỉ một luồng cùng lúc) và làm theo Action trả về.
 */
public final class SearchSession {

    public enum State { IDLE, SEARCHING, CONFIRMED, DONE }
    public enum ActionType { NONE, SPEAK, CAPTURE }

    /** Một phát hiện đã rút gọn từ MLResult (toạ độ chuẩn hoá 0..1). */
    public static final class Hit {
        public final String label;
        public final float confidence, cx, cy, area;
        public Hit(String label, float confidence, float cx, float cy, float area) {
            this.label = label; this.confidence = confidence;
            this.cx = cx; this.cy = cy; this.area = area;
        }
    }

    public static final class Action {
        public final ActionType type;
        public final String text;     // câu cần đọc (SPEAK) hoặc câu báo trước khi chụp (CAPTURE)
        public final Hit best;        // phát hiện tốt nhất (CAPTURE)
        public final int count;       // số vật cùng loại trong frame xác nhận
        Action(ActionType type, String text, Hit best, int count) {
            this.type = type; this.text = text; this.best = best; this.count = count;
        }
    }

    private static final Action NONE = new Action(ActionType.NONE, null, null, 0);

    /** Số frame liên tiếp phải thấy vật trước khi chụp (tránh chụp vì nhận nhầm 1 frame). */
    private final int framesToConfirm;
    /** Khoảng cách tối thiểu giữa hai lần nhắc "chưa thấy". */
    private final long reminderIntervalMs;

    private State state = State.IDLE;
    private String targetLabel, spokenName;
    private int streak;
    private Hit streakBest;
    private int streakCount;
    private long lastReminderMs;

    public SearchSession(int framesToConfirm, long reminderIntervalMs) {
        this.framesToConfirm = framesToConfirm;
        this.reminderIntervalMs = reminderIntervalMs;
    }

    public State state() { return state; }
    public String spokenName() { return spokenName; }

    public Action start(String cocoLabel, String spokenName, long nowMs) {
        this.targetLabel = cocoLabel;
        this.spokenName = spokenName;
        this.state = State.SEARCHING;
        this.streak = 0; this.streakBest = null; this.streakCount = 0;
        this.lastReminderMs = nowMs;
        return new Action(ActionType.SPEAK,
                "Đang tìm " + spokenName + ". Hãy cầm điện thoại thẳng đứng và xoay chậm xung quanh.",
                null, 0);
    }

    public void stop() { state = State.IDLE; streak = 0; streakBest = null; }
    public void finish() { state = State.DONE; }

    public Action onFrame(List<Hit> hits, long nowMs) {
        if (state != State.SEARCHING) return NONE;

        Hit best = null;
        int count = 0;
        for (Hit h : hits) {
            if (!h.label.equals(targetLabel)) continue;
            count++;
            if (best == null || h.confidence > best.confidence) best = h;
        }

        if (best == null) {
            streak = 0; streakBest = null; streakCount = 0;
            if (nowMs - lastReminderMs >= reminderIntervalMs) {
                lastReminderMs = nowMs;
                return new Action(ActionType.SPEAK,
                        "Chưa thấy " + spokenName + ". Hãy tiếp tục xoay chậm.", null, 0);
            }
            return NONE;
        }

        streak++;
        if (streakBest == null || best.confidence >= streakBest.confidence) {
            streakBest = best; streakCount = count;
        }
        if (streak >= framesToConfirm) {
            state = State.CONFIRMED;
            return new Action(ActionType.CAPTURE,
                    "Đã thấy " + spokenName + ". Giữ yên điện thoại.", streakBest, streakCount);
        }
        return NONE;
    }
}