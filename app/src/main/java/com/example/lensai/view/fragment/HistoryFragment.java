package com.example.lensai.view.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lensai.R;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.utils.FirebaseUtils;
import com.example.lensai.view.adapter.HistoryAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private ProgressBar progress;
    private LinearLayout emptyState;
    private TextView tvHistoryCount;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_history, container, false);

        rvHistory = v.findViewById(R.id.rvHistory);
        progress = v.findViewById(R.id.progressHistory);
        emptyState = v.findViewById(R.id.emptyState);
        tvHistoryCount = v.findViewById(R.id.tvHistoryCount);

        adapter = new HistoryAdapter(new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onDelete(ScanHistory item) {
                deleteItem(item);
            }

            @Override
            public void onClick(ScanHistory item) {
                // Có thể mở chi tiết sau
                Toast.makeText(requireContext(), item.labelEn, Toast.LENGTH_SHORT).show();
            }
        });

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        loadHistory();
        return v;
    }

    private void loadHistory() {
        progress.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        FirebaseUtils.db()
                .child("scan_history")
                .child(FirebaseUtils.uid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progress.setVisibility(View.GONE);

                        List<ScanHistory> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ScanHistory item = child.getValue(ScanHistory.class);
                            if (item != null) {
                                item.id = child.getKey();
                                list.add(item);
                            }
                        }

                        // Mới nhất lên đầu
                        Collections.sort(list, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                        adapter.setData(list);
                        tvHistoryCount.setText(list.size() + " mục đã lưu");

                        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                        rvHistory.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(requireContext(),
                                "Lỗi tải lịch sử: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteItem(ScanHistory item) {
        if (item.id == null) return;

        FirebaseUtils.db()
                .child("scan_history")
                .child(FirebaseUtils.uid())
                .child(item.id)
                .removeValue()
                .addOnSuccessListener(unused ->
                        Toast.makeText(requireContext(), "Đã xóa", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Xóa thất bại", Toast.LENGTH_SHORT).show());
    }
}