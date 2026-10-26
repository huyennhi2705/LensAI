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
import com.example.lensai.controller.HistoryController;
import com.example.lensai.model.ScanHistory;
import com.example.lensai.view.adapter.HistoryAdapter;

import java.util.List;
import android.content.Intent;
import com.example.lensai.view.activity.HistoryDetailActivity;
public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private ProgressBar progress;
    private LinearLayout emptyState;
    private TextView tvHistoryCount;
    private HistoryAdapter adapter;
    private final HistoryController controller = new HistoryController();

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
                if (item.id == null) return;
                Intent intent = new Intent(requireContext(), HistoryDetailActivity.class);
                intent.putExtra(HistoryDetailActivity.EXTRA_ID, item.id);
                startActivity(intent);
            }

        });

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadHistory();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.stop();
    }

    private void loadHistory() {
        progress.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        controller.start(new HistoryController.Callback() {
            @Override
            public void onData(List<ScanHistory> list) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);

                adapter.setData(list);
                tvHistoryCount.setText(list.size() + " mục đã lưu");

                emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                rvHistory.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(),
                        "Lỗi tải lịch sử: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteItem(ScanHistory item) {
        controller.delete(item,
                unused -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Đã xóa", Toast.LENGTH_SHORT).show();
                    }
                },
                e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Xóa thất bại", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}