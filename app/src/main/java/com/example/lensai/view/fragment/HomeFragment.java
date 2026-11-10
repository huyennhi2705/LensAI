package com.example.lensai.view.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lensai.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

public class HomeFragment extends Fragment {

    private static final String NAV_VIEW_ID = "bottomNav";
    private static final String[] NAV_SCAN = {"nav_scan", "nav_camera"};

    private TextView tvGreeting, tvUserName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Chú ý: Đảm bảo file XML mới của bạn tên là fragment_home.xml
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Ánh xạ các thành phần giao diện cơ bản
        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvUserName = v.findViewById(R.id.tvUserName);

        // 2. Bắt sự kiện click cho nút Bắt đầu quét
        View btnStartScan = v.findViewById(R.id.btnStartScan);
        if (btnStartScan != null) {
            btnStartScan.setOnClickListener(x -> navigateTo(NAV_SCAN));
        }

        // 3. Xử lý sự kiện click vào Avatar (nếu bạn muốn mở Profile)
        View tvAvatar = v.findViewById(R.id.tvAvatar);
        if (tvAvatar != null) {
            tvAvatar.setOnClickListener(x -> {
                // navigateTo(new String[]{"nav_profile"}); // Mở trang cá nhân nếu có
                Toast.makeText(requireContext(), "Tính năng đang phát triển", Toast.LENGTH_SHORT).show();
            });
        }

        bindUser();
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        bindUser(); // Cập nhật lại tên mỗi khi quay lại màn hình
    }

    // Lấy thông tin người dùng từ Firebase
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

    // Tạo lời chào theo thời gian thực
    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 12 ? "Chào buổi sáng," : (hour < 18 ? "Chào buổi chiều," : "Chào buổi tối,");
        return part + " 👋";
    }

    // Điều hướng sang màn hình quét (Camera)
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
        Toast.makeText(requireContext(), "Đang mở Camera...", Toast.LENGTH_SHORT).show();
    }
}