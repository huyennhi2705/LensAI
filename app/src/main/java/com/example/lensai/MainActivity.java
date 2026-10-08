package com.example.lensai;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.lensai.view.fragment.HistoryFragment;
import com.example.lensai.view.fragment.HomeFragment;
import com.example.lensai.view.fragment.ScanFragment;
import com.example.lensai.view.fragment.SettingsFragment;
import com.example.lensai.view.fragment.StatisticsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);

        nav.setOnItemSelectedListener(item -> {

            Fragment f;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                f = new HomeFragment();

            } else if (id == R.id.nav_scan) {
                f = new ScanFragment();

            } else if (id == R.id.nav_statistics) {
                f = new StatisticsFragment();

            } else if (id == R.id.nav_history) {
                f = new HistoryFragment();

            } else if (id == R.id.nav_settings) {
                f = new SettingsFragment();

            } else {
                f = new HomeFragment();
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .commit();

            return true;
        });

        // Mặc định mở Home
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_home);
        }
    }
}