package com.example.lensai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxView extends View {

    public static class BoxItem {
        public RectF normalizedBox; // 0–1
        public String label;
        public boolean confident;

        public BoxItem(RectF normalizedBox, String label, boolean confident) {
            this.normalizedBox = normalizedBox;
            this.label = label;
            this.confident = confident;
        }
    }

    private final List<BoxItem> boxes = new ArrayList<>();
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setBoxes(List<BoxItem> items) {
        boxes.clear();
        if (items != null) boxes.addAll(items);
        invalidate();
    }

    public void clear() {
        boxes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        for (BoxItem item : boxes) {
            if (item.normalizedBox == null) continue;
            RectF r = new RectF(
                    item.normalizedBox.left * w,
                    item.normalizedBox.top * h,
                    item.normalizedBox.right * w,
                    item.normalizedBox.bottom * h
            );
            boxPaint.setColor(item.confident ? Color.parseColor("#22D3EE") : Color.parseColor("#F87171"));
            bgPaint.setColor(item.confident ? Color.parseColor("#CC0F172A") : Color.parseColor("#CC7F1D1D"));
            canvas.drawRect(r, boxPaint);

            String label = item.label != null ? item.label : "";
            float tw = textPaint.measureText(label);
            float th = textPaint.getTextSize() + 12;
            canvas.drawRect(r.left, r.top - th, r.left + tw + 16, r.top, bgPaint);
            canvas.drawText(label, r.left + 8, r.top - 10, textPaint);
        }
    }
}