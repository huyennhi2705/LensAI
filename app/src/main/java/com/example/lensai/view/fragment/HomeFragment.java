package com.example.lensai.view.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lensai.R;
import com.example.lensai.controller.HistoryController;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.view.activity.HistoryDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {
    private static final int SAMPLE_SIZE = 50;   // số lần quét dùng để thống kê
    private static final int RECENT_COUNT = 8;   // số thẻ hiển thị

    private static final String NAV_VIEW_ID = "bottomNav";
    private static final String[] NAV_SCAN = {"nav_scan", "nav_camera"};
    private static final String[] NAV_HISTORY = {"nav_history"};
    private static final String[] NAV_PROFILE = {"nav_profile", "nav_settings", "nav_account"};

    private ScrollView scrollHome;
    private TextView tvGreeting, tvUserName;
    private TextView tvSeeAll, tvOverviewHint, tvHowTitle;
    private RecyclerView rvRecent;
    private View cardRecentEmpty;
    private RecentAdapter recentAdapter;
    private final HistoryController historyController = new HistoryController();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Ánh xạ các thành phần giao diện từ fragment_home.xml
        scrollHome = v.findViewById(R.id.scrollHome);
        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvUserName = v.findViewById(R.id.tvUserName);
        tvSeeAll = v.findViewById(R.id.tvSeeAll);
        tvOverviewHint = v.findViewById(R.id.tvOverviewHint);
        tvHowTitle = v.findViewById(R.id.tvHowTitle);
        rvRecent = v.findViewById(R.id.rvRecent);
        cardRecentEmpty = v.findViewById(R.id.cardRecentEmpty);

        // 2. Khởi tạo RecyclerView
        if (rvRecent != null) {
            recentAdapter = new RecentAdapter(item -> {
                if (item.id == null) return;
                Intent intent = new Intent(requireContext(), HistoryDetailActivity.class);
                intent.putExtra(HistoryDetailActivity.EXTRA_ID, item.id);
                startActivity(intent);
            });

            rvRecent.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            rvRecent.setAdapter(recentAdapter);
        }

        // 3. Bắt sự kiện click
        View btnStartScan = v.findViewById(R.id.btnStartScan);
        if (btnStartScan != null) {
            btnStartScan.setOnClickListener(x -> navigateTo(NAV_SCAN)); // Đã thêm sự kiện click
        }

        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(x -> navigateTo(NAV_HISTORY));
        }

        ViewGroup showcase = v.findViewById(R.id.layoutShowcase);
        if (showcase != null) {
            for (int i = 0; i < showcase.getChildCount(); i++) {
                showcase.getChildAt(i).setOnClickListener(x -> navigateTo(NAV_SCAN));
            }
        }

        View btnLearnMore = v.findViewById(R.id.btnLearnMore);
        if (btnLearnMore != null && scrollHome != null && tvHowTitle != null) {
            btnLearnMore.setOnClickListener(
                    x -> scrollHome.smoothScrollTo(0, tvHowTitle.getTop())
            );
        }

        bindUser();
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        bindUser();
        startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        historyController.stop();
    }

    private void bindUser() {
        if (tvGreeting != null) tvGreeting.setText(greeting());
        if (tvUserName == null) return;

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            tvUserName.setText("Explorer");
            return;
        }

        String email = firebaseUser.getEmail();
        String fallbackName = "Explorer";
        if (email != null && email.contains("@")) {
            fallbackName = email.substring(0, email.indexOf("@"));
        }
        tvUserName.setText(fallbackName);

        String uid = firebaseUser.getUid();
        DatabaseReference userRef = FirebaseDatabase
                .getInstance("https://lensai-97dc7-default-rtdb.firebaseio.com/")
                .getReference("users")
                .child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || tvUserName == null) return;
                String fullName = snapshot.child("fullName").getValue(String.class);
                if (fullName == null || fullName.trim().isEmpty()) {
                    fullName = snapshot.child("name").getValue(String.class);
                }
                if (fullName != null && !fullName.trim().isEmpty()) {
                    tvUserName.setText(fullName.trim());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("HomeFragment", "Không thể lấy tên người dùng: " + error.getMessage());
            }
        });
    }

    // ---------- Dữ liệu từ Realtime Database ----------
    private void startListening() {
        historyController.start(new HistoryController.Callback() {
            @Override
            public void onData(List<ScanHistory> list) {
                if (!isAdded()) return;
                // Controller đã sắp xếp mới nhất trước
                List<ScanHistory> sample = list.size() > SAMPLE_SIZE
                        ? new ArrayList<>(list.subList(0, SAMPLE_SIZE)) : list;
                bindSampleStats(sample);
                bindRecent(sample);
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("HomeFragment", "Không tải được lịch sử: " + e.getMessage());
            }
        });
    }

    private void bindSampleStats(List<ScanHistory> list) {
        Set<String> unique = new HashSet<>();
        int object = 0, animal = 0, plant = 0, food = 0, other = 0;
        float sum = 0f;
        int counted = 0;

        for (ScanHistory h : list) {
            if (h.labelEn != null && !h.labelEn.trim().isEmpty()) {
                unique.add(h.labelEn.trim().toLowerCase(Locale.US));
            }
            if (h.confidence > 0f) {
                sum += h.confidence;
                counted++;
            }

            String c = h.category != null ? h.category.toLowerCase(Locale.US) : "other";
            switch (c) {
                case "object": object++; break;
                case "animal": animal++; break;
                case "plant":  plant++;  break;
                case "food":   food++;   break;
                default:       other++;  break;
            }
        }
        // Bạn có thể cập nhật lên UI ở đây nếu có các TextView tương ứng
    }

    private void bindRecent(List<ScanHistory> list) {
        if (recentAdapter == null || rvRecent == null || cardRecentEmpty == null) return;

        List<ScanHistory> recent = list.size() > RECENT_COUNT
                ? new ArrayList<>(list.subList(0, RECENT_COUNT)) : list;
        recentAdapter.setData(recent);

        boolean empty = recent.isEmpty();
        rvRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        cardRecentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (tvOverviewHint != null) {
            tvOverviewHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
        return part + " 👋";
    }

    // ---------- Điều hướng ----------
    private void navigateTo(String[] menuIdNames) {
        String pkg = requireContext().getPackageName();
        int navId = getResources().getIdentifier(NAV_VIEW_ID, "id", pkg);
        BottomNavigationView nav = navId != 0 ? requireActivity().findViewById(navId) : null;

        if (nav != null) {
            for (String name : menuIdNames) {
                int id = getResources().getIdentifier(name, "id", pkg);
                if (id != 0 && nav.getMenu().findItem(id) != null) {
                    nav.setSelectedItemId(id);
                    return;
                }
            }
        }
        Toast.makeText(requireContext(), "Coming soon", Toast.LENGTH_SHORT).show();
    }

    // ---------- Adapter "Recent scans" ----------
    private static class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.VH> {
        interface OnClick { void onClick(ScanHistory item); }
        private static final int TARGET_PX = 300; // đủ cho thẻ 150dp
        private final List<ScanHistory> data = new ArrayList<>();
        private final OnClick listener;

        RecentAdapter(OnClick listener) {
            this.listener = listener;
        }

        void setData(List<ScanHistory> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recent_scan, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ScanHistory item = data.get(position);

            h.tvName.setText(item.labelEn != null && !item.labelEn.isEmpty() ? item.labelEn : "Unknown");
            h.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                    item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));

            Bitmap bmp = decodeScaled(item.imageBase64);
            if (bmp != null) {
                h.iv.setImageBitmap(bmp);
            } else {
                h.iv.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            h.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Nullable
        private static Bitmap decodeScaled(@Nullable String base64) {
            if (base64 == null || base64.isEmpty()) return null;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

                int sample = 1;
                while (opts.outWidth / (sample * 2) >= TARGET_PX) sample *= 2;

                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView iv;
            final TextView tvName, tvTime;

            VH(@NonNull View v) {
                super(v);
                iv = v.findViewById(R.id.ivRecent);
                tvName = v.findViewById(R.id.tvRecentName);
                tvTime = v.findViewById(R.id.tvRecentTime);
            }
        }
    }
}