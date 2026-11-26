package com.example.lensai.controller;
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
import java.util.List;
public class HistoryController {
    public interface Callback {
        void onData(List<ScanHistory> list);
        void onError(Exception e);
    }

    private final HistoryRepository repo = new HistoryRepository();
    private Query query;
    private ValueEventListener listener;
    private int generation = 0;
    public void start(Callback callback) {
        stop();
        final int gen = ++generation;
        repo.withUid(uid -> {
            if (gen != generation) return;
            query = repo.historyQuery(uid);
            listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<ScanHistory> list = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        ScanHistory item = HistoryRepository.parseSnapshot(child);
                        if (item != null) list.add(item);
                    }
                    list.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));

                    if (callback != null) {
                        callback.onData(list);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (callback != null) {
                        callback.onError(error.toException());
                    }
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
        query = null; listener = null;
    }
    public void delete(ScanHistory item,
                       OnSuccessListener<Void> onSuccess,
                       OnFailureListener onFailure) {
        if (item == null || item.id == null) return;
        repo.deleteScan(item.id, onSuccess, onFailure);
    }
}