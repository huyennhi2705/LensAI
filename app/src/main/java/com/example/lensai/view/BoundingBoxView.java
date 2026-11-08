package com.example.lensai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.example.lensai.model.AIResult;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxView extends View {

    private List<AIResult> results = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private ImageView imageView;

    public BoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
    }

    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
    }

    public void setResults(List<AIResult> results) {
        this.results = results;
        invalidate(); // Vẽ lại
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null || results.isEmpty() || imageView == null || imageView.getDrawable() == null) {
            return;
        }

        // Lấy ma trận scale của ImageView để vẽ khung khớp với ảnh (fitCenter)
        Matrix matrix = imageView.getImageMatrix();
        canvas.save();
        canvas.concat(matrix);

        int imgWidth = imageView.getDrawable().getIntrinsicWidth();
        int imgHeight = imageView.getDrawable().getIntrinsicHeight();

        for (AIResult r : results) {
            if (r.xmax <= 0 || r.ymax <= 0) continue; // Bỏ qua nếu không có tọa độ

            float left = r.xmin * imgWidth;
            float top = r.ymin * imgHeight;
            float right = r.xmax * imgWidth;
            float bottom = r.ymax * imgHeight;

            // Vẽ khung
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Vẽ tên vật thể
            String label = r.label + " (" + Math.round(r.confidence * 100) + "%)";
            canvas.drawText(label, left, Math.max(top - 10f, 36f), textPaint);
        }
        canvas.restore();
    }
}