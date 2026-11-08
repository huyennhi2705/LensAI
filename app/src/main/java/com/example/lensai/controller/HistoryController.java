package com.example.lensai.controller;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryController {

    private static final String TAG = "HistoryController";

    public interface Callback {
        void onData(List<ScanHistory> list);
        void onError(Exception e);
    }

    private final HistoryRepository repo = new HistoryRepository();

    private Query query;
    private ValueEventListener listener;
    // Tăng mỗi lần start/stop để bỏ qua kết quả đăng nhập trễ sau khi đã stop()
    private int generation = 0;

    /** Lắng nghe lịch sử theo thời gian thực (mới nhất trước). */
    public void start(Callback callback) {
        stop();
        final int gen = ++generation;

        repo.withUid(uid -> {
            if (gen != generation) return; // đã stop() hoặc start() lại

            query = repo.historyQuery(uid);
            listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<ScanHistory> list = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            ScanHistory item = child.getValue(ScanHistory.class);
                            if (item != null) {
                                item.id = child.getKey();
                                list.add(item);
                            }
                        } catch (Exception e) {
                            // Bản ghi sai định dạng thì bỏ qua, không làm crash cả danh sách
                            Log.w(TAG, "Skip invalid record: " + child.getKey(), e);
                        }
                    }
                    // Truy vấn trả về tăng dần theo created_at -> đảo lại để mới nhất lên đầu
                    Collections.reverse(list);
                    callback.onData(list);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onError(error.toException());
                }
            };
            query.addValueEventListener(listener);
        }, callback::onError);
    }

    public void stop() {
        generation++;
        if (query != null && listener != null) {
            query.removeEventListener(listener);
        }
        query = null;
        listener = null;
    }

    public void delete(ScanHistory item,
                       OnSuccessListener<Void> onSuccess,
                       OnFailureListener onFailure) {
        if (item == null || item.id == null) return;
        repo.deleteScan(item.id, onSuccess, onFailure);
    }
}