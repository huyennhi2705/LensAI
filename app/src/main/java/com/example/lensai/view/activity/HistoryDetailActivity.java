package com.example.lensai.view.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.R;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_scan_id";
    private TextView tvDetailChat;
    private final HistoryRepository repo = new HistoryRepository();

    private ImageView ivDetail;
    private TextView tvDetailLabel, tvDetailTime, tvDetailDesc, tvDetailObjects;
    private Chip chipCategory, chipConfidence, chipCount;
    private Button btnDetailLang;
    private ProgressBar progressDetail;

    private String scanId;
    private ScanHistory current;
    private boolean showingVietnamese = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        ivDetail = findViewById(R.id.ivDetail);
        tvDetailLabel = findViewById(R.id.tvDetailLabel);
        tvDetailTime = findViewById(R.id.tvDetailTime);
        tvDetailDesc = findViewById(R.id.tvDesc);
        tvDetailObjects = findViewById(R.id.tvDetailObjects);
        chipCategory = findViewById(R.id.chipCategory);
        chipConfidence = findViewById(R.id.chipConfidence);
        chipCount = findViewById(R.id.chipCount);
        btnDetailLang = findViewById(R.id.btnDetailLang);
        progressDetail = findViewById(R.id.progressDetail);
        tvDetailChat = findViewById(R.id.tvDetailChat);
        scanId = getIntent().getStringExtra(EXTRA_ID);
        if (scanId == null || scanId.isEmpty()) {
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        btnDetailLang.setOnClickListener(v -> {
            showingVietnamese = !showingVietnamese;
            render();
        });

        load();
    }

    private boolean gone() {
        return isFinishing() || isDestroyed();
    }

    private void load() {
        progressDetail.setVisibility(View.VISIBLE);
        repo.getScan(scanId,
                item -> {
                    if (gone()) return;
                    progressDetail.setVisibility(View.GONE);
                    current = item;
                    bindImage(item.imageBase64);
                    render();
                },
                e -> {
                    if (gone()) return;
                    progressDetail.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Unable to load details: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void bindImage(@Nullable String base64) {
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(bytes)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(ivDetail);

            } catch (Exception e) {
                ivDetail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            ivDetail.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private void render() {
        if (current == null) return;

        String label = pick(showingVietnamese ? current.labelVi : current.labelEn,
                current.labelEn, "Unknown");
        String desc = pick(showingVietnamese ? current.descriptionVi : current.descriptionEn,
                current.description, "—");
        String objects = pick(showingVietnamese ? current.objectsListVi : current.objectsListEn,
                current.objectsListEn, "—");

        tvDetailLabel.setText(label);
        tvDetailDesc.setText(desc);
        tvDetailObjects.setText(objects);
        tvDetailChat.setText(buildChatText());
        btnDetailLang.setText(showingVietnamese ? "English" : "Tiếng Việt");

        if (current.timestamp > 0) {
            tvDetailTime.setText(new SimpleDateFormat("dd MMM yyyy  •  HH:mm",
                    Locale.getDefault()).format(new Date(current.timestamp)));
        } else {
            tvDetailTime.setText("");
        }

        String cat = current.category != null && !current.category.isEmpty()
                ? current.category : "other";
        chipCategory.setText(cap(cat));

        if (current.confidence > 0f) {
            chipConfidence.setVisibility(View.VISIBLE);
            chipConfidence.setText(Math.round(current.confidence * 100) + "% match");
        } else {
            chipConfidence.setVisibility(View.GONE);
        }

        chipCount.setText(current.objectCount + (current.objectCount == 1 ? " object" : " objects"));
    }
    private String buildChatText() {
        if (current.chatHistory == null || current.chatHistory.isEmpty()) {
            return "No conversations yet.";
        }
        StringBuilder sb = new StringBuilder();
        for (com.example.lensai.model.ChatMessage m : current.chatHistory) {
            String ans = pick(showingVietnamese ? m.answerVi : m.answerEn,
                    showingVietnamese ? m.answerEn : m.answerVi, "—");
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("🙋 ").append(m.question).append("\n🤖 ").append(ans);
        }
        return sb.toString();
    }
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this scan?")
                .setMessage("This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        repo.deleteScan(scanId,
                                unused -> {
                                    if (gone()) return;
                                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                                    finish();
                                },
                                e -> {
                                    if (gone()) return;
                                    Toast.makeText(this, "Deletion failed", Toast.LENGTH_SHORT).show();
                                }))
                .show();
    }

    /** Lấy giá trị đầu tiên không rỗng. */
    private static String pick(@Nullable String primary, @Nullable String fallback, String last) {
        if (primary != null && !primary.trim().isEmpty()) return primary.trim();
        if (fallback != null && !fallback.trim().isEmpty()) return fallback.trim();
        return last;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}