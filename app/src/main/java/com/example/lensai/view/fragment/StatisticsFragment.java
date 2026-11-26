package com.example.lensai.view.fragment;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lensai.R;
import com.example.lensai.controller.HistoryController;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.utils.ObjectCategory;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatisticsFragment extends Fragment {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final Pattern LINE =
            Pattern.compile("^(\\d{1,4})\\s*x\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final int C_TEXT = Color.parseColor("#000000");

    // Màu chữ phụ (Xám đậm - Dùng cho số lượng, phần trăm)
    private static final int C_MUTED = Color.parseColor("#000000");

    // Màu thanh nền của ProgressBar (Xám rất nhạt)
    private static final int C_TRACK = Color.parseColor("#000000");

    // Màu thanh chạy của ProgressBar (Màu xanh dương)
    private static final int C_BAR = Color.parseColor("#3B82F6");

    // Màu nhấn (Điểm xuyết)
    private static final int C_ACCENT = Color.parseColor("#2563EB");
    private static final int[] RANK_COLORS = {
            Color.parseColor("#22D3EE"), Color.parseColor("#A78BFA"),
            Color.parseColor("#FBBF24"), Color.parseColor("#34D399"),
            Color.parseColor("#FB7185")
    };
    private static final Map<String, Integer> CAT_COLORS = new HashMap<>();

    static {
        CAT_COLORS.put(ObjectCategory.FURNITURE, Color.parseColor("#F59E0B"));
        CAT_COLORS.put(ObjectCategory.ELECTRONICS, Color.parseColor("#22D3EE"));
        CAT_COLORS.put(ObjectCategory.KITCHEN, Color.parseColor("#A78BFA"));
        CAT_COLORS.put(ObjectCategory.FOOD, Color.parseColor("#34D399"));
        CAT_COLORS.put(ObjectCategory.ANIMALS, Color.parseColor("#FB7185"));
        CAT_COLORS.put(ObjectCategory.VEHICLES, Color.parseColor("#60A5FA"));
        CAT_COLORS.put(ObjectCategory.SPORTS, Color.parseColor("#F472B6"));
        CAT_COLORS.put(ObjectCategory.PERSONAL, Color.parseColor("#FBBF24"));
        CAT_COLORS.put(ObjectCategory.OTHER, Color.parseColor("#64748B"));
    }

    private final HistoryController controller = new HistoryController();

    private TextView tvHeroTotal, tvHeroObjects, tvHeroTypes, tvStreak;
    private TextView tvKpiAccuracy, tvKpiSpeed, tvKpiChats, tvWeekSummary;
    private View layoutEmpty, layoutContent;
    private LinearLayout layoutWeekBars, layoutWeekLabels;
    private LinearLayout layoutCategoryBar, layoutCategoryLegend;
    private LinearLayout layoutTopObjects;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_statistics, container, false);

        tvHeroTotal = v.findViewById(R.id.tvHeroTotal);
        tvHeroObjects = v.findViewById(R.id.tvHeroObjects);
        tvHeroTypes = v.findViewById(R.id.tvHeroTypes);
        tvStreak = v.findViewById(R.id.tvStreak);
        tvKpiAccuracy = v.findViewById(R.id.tvKpiAccuracy);
        tvKpiSpeed = v.findViewById(R.id.tvKpiSpeed);
        tvKpiChats = v.findViewById(R.id.tvKpiChats);
        tvWeekSummary = v.findViewById(R.id.tvWeekSummary);
        layoutEmpty = v.findViewById(R.id.layoutEmpty);
        layoutContent = v.findViewById(R.id.layoutContent);
        layoutWeekBars = v.findViewById(R.id.layoutWeekBars);
        layoutWeekLabels = v.findViewById(R.id.layoutWeekLabels);
        layoutCategoryBar = v.findViewById(R.id.layoutCategoryBar);
        layoutCategoryLegend = v.findViewById(R.id.layoutCategoryLegend);
        layoutTopObjects = v.findViewById(R.id.layoutTopObjects);


        render(new ArrayList<>());
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        controller.start(new HistoryController.Callback() {
            @Override
            public void onData(List<ScanHistory> list) {
                if (!isAdded()) return;
                render(list);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvWeekSummary.setText("Could not load stats: " + e.getMessage());
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.stop();
    }

    // ==================================================================
    // TÍNH TOÁN + HIỂN THỊ
    // ==================================================================

    private void render(List<ScanHistory> list) {
        int total = list.size();
        layoutEmpty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        layoutContent.setVisibility(total == 0 ? View.GONE : View.VISIBLE);

        long startToday = startOfDay(System.currentTimeMillis());

        long totalObjects = 0, msSum = 0, chats = 0, voice = 0;
        double confSum = 0;
        int confCount = 0, msCount = 0, camera = 0, gallery = 0;
        int thisWeek = 0, lastWeek = 0;
        int[] perDay = new int[7];          // [0] = 6 ngày trước ... [6] = hôm nay
        int[] perWeekday = new int[8];      // Calendar.DAY_OF_WEEK = 1..7
        int[] perHour = new int[24];
        Set<Long> activeDays = new HashSet<>();
        Map<String, Integer> objectCounts = new HashMap<>();
        ScanHistory best = null;

        Calendar cal = Calendar.getInstance();
        for (ScanHistory s : list) {
            totalObjects += s.objectCount;

            if (s.confidence > 0f) {
                confSum += s.confidence;
                confCount++;
                if (best == null || s.confidence > best.confidence) best = s;
            }
            if (s.detectMs > 0) {
                msSum += s.detectMs;
                msCount++;
            }
            chats += s.chatCount;
            voice += s.speakCount;
            if ("gallery".equals(s.inputType)) gallery++;
            else if ("camera".equals(s.inputType)) camera++;

            if (s.timestamp > 0) {
                cal.setTimeInMillis(s.timestamp);
                perWeekday[cal.get(Calendar.DAY_OF_WEEK)]++;
                perHour[cal.get(Calendar.HOUR_OF_DAY)]++;

                long day = startOfDay(s.timestamp);
                activeDays.add(day);
                long daysAgo = Math.max(0, Math.round((startToday - day) / (double) DAY_MS));
                if (daysAgo <= 6) {
                    thisWeek++;
                    perDay[6 - (int) daysAgo]++;
                } else if (daysAgo <= 13) {
                    lastWeek++;
                }
            }
            countObjects(s, objectCounts);
        }

        // Nhóm theo loại
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (Map.Entry<String, Integer> e : objectCounts.entrySet()) {
            add(categoryCounts, ObjectCategory.of(e.getKey()), e.getValue());
        }

        // ---- Hero ----
        tvHeroTotal.setText(String.valueOf(total));
        tvHeroObjects.setText("🔍 " + totalObjects + (totalObjects == 1 ? " object" : " objects"));
        int types = objectCounts.size();
        tvHeroTypes.setText("🧩 " + types + (types == 1 ? " type" : " types"));
        tvStreak.setText("🔥 " + computeStreak(activeDays, startToday) + "-day streak");

        // ---- KPI ----
        tvKpiAccuracy.setText(confCount == 0 ? "—"
                : String.format(Locale.US, "%.0f%%", confSum / confCount * 100));
        tvKpiSpeed.setText(msCount == 0 ? "—" : (msSum / msCount) + " ms");
        tvKpiChats.setText(String.valueOf(chats));

        // ---- Activity ----
        String summary = thisWeek + (thisWeek == 1 ? " scan" : " scans");
        if (lastWeek > 0) {
            int diff = Math.round((thisWeek - lastWeek) * 100f / lastWeek);
            summary += "  ·  " + (diff >= 0 ? "+" : "") + diff + "% vs previous week";
        }
        tvWeekSummary.setText(summary);
        renderWeek(perDay, startToday);

        renderCategories(categoryCounts);
        renderTopObjects(objectCounts);


    }

    private void renderWeek(int[] perDay, long startToday) {
        layoutWeekBars.removeAllViews();
        layoutWeekLabels.removeAllViews();

        int max = 1;
        for (int c : perDay) max = Math.max(max, c);
        int maxBarPx = dp(100);
        SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH, 1f));

            col.addView(makeText(String.valueOf(perDay[i]), 12, C_MUTED, false),
                    new LinearLayout.LayoutParams(WRAP, WRAP));

            int h = perDay[i] == 0
                    ? dp(4)
                    : Math.max(dp(8), Math.round(maxBarPx * perDay[i] / (float) max));
            View bar = new View(requireContext());
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(i == 6 ? C_ACCENT : C_BAR);   // hôm nay nổi bật
            bg.setCornerRadius(dp(6));
            bar.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), h);
            lp.topMargin = dp(4);
            col.addView(bar, lp);
            layoutWeekBars.addView(col);

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(startToday);
            c.add(Calendar.DAY_OF_YEAR, -(6 - i));
            TextView label = makeText(fmt.format(c.getTime()), 11, C_MUTED, false);
            label.setGravity(Gravity.CENTER);
            layoutWeekLabels.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
    }

    private void renderCategories(Map<String, Integer> counts) {
        layoutCategoryLegend.removeAllViews();

        int sum = 0;
        for (int v : counts.values()) sum += v;
        if (sum == 0) return;

        List<Map.Entry<String, Integer>> es = sorted(counts);

        for (int i = 0; i < es.size(); i++) {
            Map.Entry<String, Integer> e = es.get(i);
            int color = CAT_COLORS.getOrDefault(e.getKey(), C_MUTED);

            // Tạo Thẻ (Card) cho từng danh mục
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(MATCH, WRAP);
            cardLp.bottomMargin = dp(10);
            card.setLayoutParams(cardLp);
            card.setRadius(dp(16));
            card.setCardElevation(0f);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));

            // Chấm tròn màu
            View dot = new View(requireContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            dot.setBackground(g);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
            dotLp.setMarginEnd(dp(14));
            row.addView(dot, dotLp);

            // Tên danh mục
            TextView tvName = makeText(e.getKey(), 15, C_TEXT, true);
            row.addView(tvName, new LinearLayout.LayoutParams(0, WRAP, 1f));

            // Số lượng và %
            String statText = e.getValue() + " · " + Math.round(e.getValue() * 100f / sum) + "%";
            row.addView(makeText(statText, 14, C_MUTED, true));

            card.addView(row);
            layoutCategoryLegend.addView(card);
        }
    }

    private void renderTopObjects(Map<String, Integer> counts) {
        layoutTopObjects.removeAllViews();
        if (counts.isEmpty()) {
            layoutTopObjects.addView(makeText("No objects yet", 14, C_MUTED, false));
            return;
        }

        List<Map.Entry<String, Integer>> es = sorted(counts);
        int max = es.get(0).getValue();
        int n = Math.min(5, es.size());

        for (int i = 0; i < n; i++) {
            Map.Entry<String, Integer> e = es.get(i);
            int color = RANK_COLORS[i % RANK_COLORS.length];

            // Tạo Thẻ (Card) cho từng Object
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(MATCH, WRAP);
            cardLp.bottomMargin = dp(12);
            card.setLayoutParams(cardLp);
            card.setRadius(dp(18));
            card.setCardElevation(dp(1));
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout block = new LinearLayout(requireContext());
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(16), dp(16), dp(16), dp(16));

            // Phần đầu thẻ: Rank + Name + Count
            LinearLayout head = new LinearLayout(requireContext());
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            // Số thứ tự
            TextView tvRank = makeText("#" + (i + 1), 14, color, true);
            LinearLayout.LayoutParams rankLp = new LinearLayout.LayoutParams(WRAP, WRAP);
            rankLp.setMarginEnd(dp(10));
            head.addView(tvRank, rankLp);

            // Tên vật thể
            head.addView(makeText(e.getKey(), 16, C_TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1f));

            // Số lượng (e.g. 11x)
            head.addView(makeText(e.getValue() + "x", 15, C_MUTED, true));

            block.addView(head);

            // Thanh ProgressBar
            LinearProgressIndicator bar = new LinearProgressIndicator(requireContext());
            bar.setIndicatorColor(color);
            bar.setTrackColor(C_TRACK);
            bar.setTrackThickness(dp(12));
            bar.setTrackCornerRadius(dp(6));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(MATCH, WRAP);
            barLp.topMargin = dp(14);
            block.addView(bar, barLp);
            bar.setProgressCompat(Math.round(e.getValue() * 100f / max), false);

            card.addView(block);
            layoutTopObjects.addView(card);
        }
    }
    private void addHighlight(String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        row.addView(makeText(label, 14, C_MUTED, false),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView v = makeText(value, 14, C_TEXT, true);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(WRAP, WRAP));


    }

    // ==================================================================
    // HÀM PHỤ
    // ==================================================================

    /** Cộng dồn số lượng từ chuỗi "8x chair\n2x table". */
    private void countObjects(ScanHistory s, Map<String, Integer> map) {
        boolean added = false;
        if (s.objectsListEn != null && !s.objectsListEn.trim().isEmpty()) {
            for (String line : s.objectsListEn.split("\\r?\\n")) {
                Matcher m = LINE.matcher(line.trim());
                if (m.matches()) {
                    add(map, title(m.group(2)), Integer.parseInt(m.group(1)));
                    added = true;
                }
            }
        }
        if (!added && s.labelEn != null && !s.labelEn.trim().isEmpty()) {
            add(map, title(s.labelEn), 1);
        }
    }

    private static void add(Map<String, Integer> map, String key, int n) {
        Integer old = map.get(key);
        map.put(key, old == null ? n : old + n);
    }

    private static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> es = new ArrayList<>(m.entrySet());
        es.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return es;
    }

    private static String title(String s) {
        String[] words = s.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Số ngày liên tiếp có quét (tính đến hôm nay, hoặc hôm qua nếu hôm nay chưa quét). */
    private static int computeStreak(Set<Long> days, long startToday) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startToday);
        if (!days.contains(c.getTimeInMillis())) c.add(Calendar.DAY_OF_YEAR, -1);
        int streak = 0;
        while (days.contains(c.getTimeInMillis())) {
            streak++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private static String busiestDay(int[] perWeekday) {
        int bestDow = 0, bestCount = 0;
        for (int i = 1; i <= 7; i++) {
            if (perWeekday[i] > bestCount) {
                bestCount = perWeekday[i];
                bestDow = i;
            }
        }
        if (bestDow == 0) return "—";
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, bestDow);
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(c.getTime())
                + " (" + bestCount + ")";
    }

    private static String peakHour(int[] perHour) {
        int bestHour = -1, bestCount = 0;
        for (int h = 0; h < 24; h++) {
            if (perHour[h] > bestCount) {
                bestCount = perHour[h];
                bestHour = h;
            }
        }
        if (bestHour < 0) return "—";
        return String.format(Locale.US, "%02d:00 – %02d:00", bestHour, (bestHour + 1) % 24);
    }

    private TextView makeText(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}