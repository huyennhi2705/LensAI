package com.example.lensai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxView extends View {

    public static class DetectionBox {
        public RectF rect; // Tọa độ đã được scale theo kích thước View
        public String label;
        public float confidence;
        public int color;

        public DetectionBox(RectF rect, String label, float confidence, int color) {
            this.rect = rect;
            this.label = label;
            this.confidence = confidence;
            this.color = color;
        }
    }

    private List<DetectionBox> boxes = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private Paint bgPaint;

    public BoundingBoxView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f); // Độ dày của khung
        boxPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);
    }

    public void setBoxes(List<DetectionBox> boxes) {
        this.boxes = boxes;
        invalidate(); // Vẽ lại
    }

    public void clear() {
        this.boxes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DetectionBox box : boxes) {
            // Vẽ khung
            boxPaint.setColor(box.color);
            canvas.drawRect(box.rect, boxPaint);

            // Vẽ nhãn (label + confidence)
            String text = box.label + " " + String.format("%.2f", box.confidence);
            float textWidth = textPaint.measureText(text);
            float textHeight = textPaint.descent() - textPaint.ascent();

            // Vẽ nền cho text
            bgPaint.setColor(box.color);
            bgPaint.setAlpha(200);
            canvas.drawRect(
                    box.rect.left,
                    box.rect.top - textHeight - 10,
                    box.rect.left + textWidth + 20,
                    box.rect.top,
                    bgPaint
            );

            // Vẽ chữ
            canvas.drawText(
                    text,
                    box.rect.left + 10,
                    box.rect.top - 10,
                    textPaint
            );
        }
    }
}