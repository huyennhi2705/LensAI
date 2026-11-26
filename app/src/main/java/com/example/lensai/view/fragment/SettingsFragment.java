package com.example.lensai.view.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.lensai.BuildConfig;
import com.example.lensai.R;
import com.example.lensai.utils.SettingsManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SettingsManager settings;

    private MaterialSwitch switchAutoSpeak, switchAutoSave, switchHighAccuracy;
    private Slider sliderSpeechRate;
    private TextView tvSpeechRateValue, tvAppVersion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        settings = new SettingsManager(requireContext());
        switchAutoSpeak = v.findViewById(R.id.switchAutoSpeak);
        switchAutoSave = v.findViewById(R.id.switchAutoSave);
        switchHighAccuracy = v.findViewById(R.id.switchHighAccuracy);
        sliderSpeechRate = v.findViewById(R.id.sliderSpeechRate);
        tvSpeechRateValue = v.findViewById(R.id.tvSpeechRateValue);
        tvAppVersion = v.findViewById(R.id.tvAppVersion);
        View btnClearHistory = v.findViewById(R.id.btnClearHistory);
        View btnSyncFirebase = v.findViewById(R.id.btnSyncFirebase);
        View btnPrivacy = v.findViewById(R.id.btnPrivacy);
        View btnRateApp = v.findViewById(R.id.btnRateApp);
        if (tvAppVersion != null) {
            tvAppVersion.setText("LensAI v" + BuildConfig.VERSION_NAME);
        }
        switchAutoSpeak.setChecked(settings.isAutoSpeak());
        switchAutoSave.setChecked(settings.isAutoSave());
        switchHighAccuracy.setChecked(settings.isHighAccuracy());
        float currentRate = settings.getSpeechRate();
        sliderSpeechRate.setValue(currentRate);
        updateSpeechRateText(currentRate);
        switchAutoSpeak.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setAutoSpeak(isChecked));
        switchAutoSave.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setAutoSave(isChecked));
        switchHighAccuracy.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setHighAccuracy(isChecked));
        sliderSpeechRate.addOnChangeListener((slider, value, fromUser) -> {
            settings.setSpeechRate(value);
            updateSpeechRateText(value);
        });
        if (btnClearHistory != null) {
            btnClearHistory.setOnClickListener(view -> showClearHistoryDialog());
        }

        if (btnSyncFirebase != null) {
            btnSyncFirebase.setOnClickListener(view ->
                    Toast.makeText(requireContext(), "Data has been automatically synced to Firebase.", Toast.LENGTH_SHORT).show()
            );
        }
        if (btnPrivacy != null) {
            btnPrivacy.setOnClickListener(view -> showPrivacyPolicy());
        }
        if (btnRateApp != null) {
            btnRateApp.setOnClickListener(view -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + requireContext().getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + requireContext().getPackageName())));
                }
            });
        }
        return v;
    }
    private void updateSpeechRateText(float rate) {
        String text;
        if (rate < 0.8f) text = "Slow";
        else if (rate < 1.2f) text = "Normal";
        else text = "Fast";

        tvSpeechRateValue.setText(String.format(Locale.US, "%s (%.1fx)", text, rate));
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear history")
                .setMessage("Are you sure you want to delete your entire scan history? This action cannot be undone..")
                .setPositiveButton("Delete", (dialog, which) -> clearHistory())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";

        FirebaseDatabase.getInstance("https://lensai-97dc7-default-rtdb.firebaseio.com/")
                .getReference("scan_history").child(uid)
                .removeValue()
                .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Entire history deleted!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Deletion error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showPrivacyPolicy() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Privacy Policy")
                .setMessage("LensAI is committed to protecting your data. Images and scan results are stored securely within your own personal Firebase instance and are never shared with third parties.")
                .setPositiveButton("Understood", null)
                .show();
    }
}