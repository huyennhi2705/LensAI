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

import com.example.lensai.model.ScanHistory;
import com.example.lensai.R;
import com.example.lensai.repository.HistoryRepository;
import com.example.lensai.view.activity.HistoryDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {

    private static final int SAMPLE_SIZE = 50;   // số lần quét dùng để thống kê
    private static final int RECENT_COUNT = 8;   // số thẻ hiển thị

    // Tên id trong MainActivity / menu bottom navigation. Sửa nếu app bạn đặt tên khác.
    private static final String NAV_VIEW_ID = "bottomNav";
    private static final String[] NAV_SCAN = {"nav_scan", "nav_camera"};
    private static final String[] NAV_HISTORY = {"nav_history"};
    private static final String[] NAV_PROFILE = {"nav_profile", "nav_settings", "nav_account"};

    private ScrollView scrollHome;
    private TextView tvGreeting, tvUserName, tvScanToday, tvScanTotal, tvObjects, tvAccuracy;
    private TextView tvCatObject, tvCatAnimal, tvCatPlant, tvCatFood, tvCatOther;
    private TextView tvSeeAll, tvOverviewHint, tvHowTitle;
    private RecyclerView rvRecent;
    private View cardRecentEmpty;

    private RecentAdapter recentAdapter;
    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        scrollHome = v.findViewById(R.id.scrollHome);
        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvUserName = v.findViewById(R.id.tvUserName);
        tvScanToday = v.findViewById(R.id.tvScanToday);
        tvScanTotal = v.findViewById(R.id.tvScanTotal);
        tvObjects = v.findViewById(R.id.tvObjects);
        tvAccuracy = v.findViewById(R.id.tvAccuracy);
        tvCatObject = v.findViewById(R.id.tvCatObject);
        tvCatAnimal = v.findViewById(R.id.tvCatAnimal);
        tvCatPlant = v.findViewById(R.id.tvCatPlant);
        tvCatFood = v.findViewById(R.id.tvCatFood);
        tvCatOther = v.findViewById(R.id.tvCatOther);
        tvSeeAll = v.findViewById(R.id.tvSeeAll);
        tvOverviewHint = v.findViewById(R.id.tvOverviewHint);
        tvHowTitle = v.findViewById(R.id.tvHowTitle);
        rvRecent = v.findViewById(R.id.rvRecent);
        cardRecentEmpty = v.findViewById(R.id.cardRecentEmpty);

        // Danh sách cuộn ngang cần LayoutManager
        recentAdapter = new RecentAdapter(item -> {
            if (item.id == null) return;
            Intent intent = new Intent(requireContext(), HistoryDetailActivity.class);
            intent.putExtra(HistoryDetailActivity.EXTRA_ID, item.id);
            startActivity(intent);
        });
        rvRecent.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecent.setAdapter(recentAdapter);

        // ----- Liên kết sang các trang khác -----
        v.findViewById(R.id.btnStartScan).setOnClickListener(x -> navigateTo(NAV_SCAN));
        v.findViewById(R.id.cardQuickScan).setOnClickListener(x -> navigateTo(NAV_SCAN));
        v.findViewById(R.id.cardQuickHistory).setOnClickListener(x -> navigateTo(NAV_HISTORY));
        v.findViewById(R.id.cardQuickProfile).setOnClickListener(x -> navigateTo(NAV_PROFILE));
        tvSeeAll.setOnClickListener(x -> navigateTo(NAV_HISTORY));

        // Bấm vào thẻ nhóm (Objects / Animals / Plants / Food) -> mở trang quét
        ViewGroup showcase = v.findViewById(R.id.layoutShowcase);
        for (int i = 0; i < showcase.getChildCount(); i++) {
            showcase.getChildAt(i).setOnClickListener(x -> navigateTo(NAV_SCAN));
        }

        // "How it works" -> cuộn xuống phần hướng dẫn
        v.findViewById(R.id.btnLearnMore).setOnClickListener(x ->
                scrollHome.smoothScrollTo(0, tvHowTitle.getTop()));

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
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    // ---------- Người dùng ----------

    private void bindUser() {
        tvGreeting.setText(greeting());
        // TODO: nếu có đăng nhập, hiển thị tên thật:
        // FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        // if (u != null && u.getDisplayName() != null) tvUserName.setText(u.getDisplayName());
    }

    // ---------- Dữ liệu từ Firestore ----------

    private void startListening() {
        if (registration != null) registration.remove();

        registration = new HistoryRepository().getHistoryQuery()
                .limit(SAMPLE_SIZE)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || error != null || snapshot == null) return;

                    List<ScanHistory> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ScanHistory item = doc.toObject(ScanHistory.class);
                        if (item != null) {
                            item.id = doc.getId();
                            list.add(item);
                        }
                    }

                    bindSampleStats(list);
                    bindRecent(list);
                    loadCounts();
                });
    }

    /** Số vật thể khác nhau, số lượng theo nhóm và độ chính xác trung bình. */
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

        tvObjects.setText(String.valueOf(unique.size()));
        tvAccuracy.setText(counted > 0 ? Math.round(sum / counted * 100) + "%" : "--");
        tvCatObject.setText(String.valueOf(object));
        tvCatAnimal.setText(String.valueOf(animal));
        tvCatPlant.setText(String.valueOf(plant));
        tvCatFood.setText(String.valueOf(food));
        tvCatOther.setText(String.valueOf(other));
    }

    private void bindRecent(List<ScanHistory> list) {
        List<ScanHistory> recent = list.size() > RECENT_COUNT
                ? new ArrayList<>(list.subList(0, RECENT_COUNT)) : list;
        recentAdapter.setData(recent);

        boolean empty = recent.isEmpty();
        rvRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        cardRecentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvOverviewHint.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** Tổng số chính xác bằng count query (không tải document). */
    private void loadCounts() {
        CollectionReference col = FirebaseFirestore.getInstance()
                .collection(HistoryRepository.COLLECTION);

        col.count().get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> {
                    if (isAdded()) tvScanTotal.setText(String.valueOf(r.getCount()));
                });

        col.whereGreaterThanOrEqualTo("timestamp", startOfToday()).count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> {
                    if (isAdded()) tvScanToday.setText(String.valueOf(r.getCount()));
                });
    }

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
        return part + " 👋";
    }

    // ---------- Điều hướng ----------

    /**
     * Chuyển tab bottom navigation theo tên id. Thử lần lượt từng tên trong danh sách,
     * nên không bị lỗi biên dịch nếu app bạn đặt tên khác.
     */
    private void navigateTo(String[] menuIdNames) {
        String pkg = requireContext().getPackageName();
        int navId = getResources().getIdentifier(NAV_VIEW_ID, "id", pkg);
        BottomNavigationView nav = navId != 0
                ? requireActivity().findViewById(navId) : null;

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

            h.tvName.setText(item.labelEn != null && !item.labelEn.isEmpty()
                    ? item.labelEn : "Unknown");
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

        /** Decode ảnh Base64 đã thu nhỏ để tránh giật / OOM. */
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