package com.example.lensai.view.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.R;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.example.lensai.utils.TextToSpeechHelper;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "scan_id";

    private ImageView ivDetail;
    private TextView tvName, tvMeta, tvDesc, tvDescTitle;
    private MaterialButton btnSpeak, btnTranslate;

    private TextToSpeechHelper tts;
    private ScanHistory current;
    private boolean showingVietnamese = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        ivDetail = findViewById(R.id.ivDetail);
        tvName = findViewById(R.id.tvDetailName);
        tvMeta = findViewById(R.id.tvDetailMeta);
        tvDesc = findViewById(R.id.tvDetailDesc);
        tvDescTitle = findViewById(R.id.tvDescTitle);
        btnSpeak = findViewById(R.id.btnDetailSpeak);
        btnTranslate = findViewById(R.id.btnDetailTranslate);
        ImageButton btnBack = findViewById(R.id.btnBack);

        tts = new TextToSpeechHelper(this); // American English (Locale.US)

        btnBack.setOnClickListener(v -> finish());

        btnSpeak.setOnClickListener(v -> {
            if (current != null) {
                tts.speak(englishText());
            }
        });

        btnTranslate.setOnClickListener(v -> {
            if (current == null) return;
            showingVietnamese = !showingVietnamese;
            render();
        });

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            Toast.makeText(this, "Record not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new HistoryRepository().getScan(id,
                item -> {
                    current = item;
                    showImage(item.imageBase64);
                    render();
                },
                e -> {
                    Toast.makeText(this, "Could not load details: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private String nameEn() {
        return current.labelEn != null && !current.labelEn.isEmpty() ? current.labelEn : "Unknown";
    }

    private String descEn() {
        if (current.descriptionEn != null && !current.descriptionEn.isEmpty()) return current.descriptionEn;
        if (current.description != null && !current.description.isEmpty()) return current.description;
        return "No description available.";
    }

    /** What the speaker reads: always English. */
    private String englishText() {
        return nameEn() + ". " + descEn();
    }

    private void render() {
        if (showingVietnamese) {
            String nameVi = current.labelVi != null && !current.labelVi.isEmpty()
                    ? current.labelVi : nameEn();
            String descVi = current.descriptionVi != null && !current.descriptionVi.isEmpty()
                    ? current.descriptionVi : "Chưa có bản dịch cho mục này.";
            tvName.setText(nameVi);
            tvDesc.setText(descVi);
            tvDescTitle.setText("Mô tả");
            btnTranslate.setText("English");
        } else {
            tvName.setText(nameEn());
            tvDesc.setText(descEn());
            tvDescTitle.setText("Description");
            btnTranslate.setText("Translate");
        }

        String category = current.category != null && !current.category.isEmpty()
                ? current.category : "other";
        String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)
                .format(new Date(current.timestamp));
        String meta = category + "  •  " + time;
        if (current.confidence > 0f) {
            meta += "  •  " + Math.round(current.confidence * 100) + "%";
        }
        tvMeta.setText(meta);
    }

    private void showImage(String base64) {
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    ivDetail.setImageBitmap(bitmap);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        ivDetail.setImageResource(android.R.drawable.ic_menu_gallery);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.shutdown(); // also stops any speech still playing
        }
    }
}