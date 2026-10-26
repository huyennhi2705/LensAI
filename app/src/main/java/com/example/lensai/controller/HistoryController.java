package com.example.lensai.controller;

import com.example.lensai.model.ScanHistory;
import com.example.lensai.repository.HistoryRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class HistoryController {

    public interface Callback {
        void onData(List<ScanHistory> list);
        void onError(Exception e);
    }

    private final HistoryRepository repo = new HistoryRepository();
    private ListenerRegistration registration;

    /** Starts listening to history in real time (newest first). */
    public void start(Callback callback) {
        stop();
        registration = repo.getHistoryQuery().addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot == null) return;

            List<ScanHistory> list = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ScanHistory item = doc.toObject(ScanHistory.class);
                if (item != null) {
                    item.id = doc.getId();
                    list.add(item);
                }
            }
            callback.onData(list);
        });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public void delete(ScanHistory item,
                       OnSuccessListener<Void> onSuccess,
                       OnFailureListener onFailure) {
        if (item == null || item.id == null) return;
        repo.deleteScan(item.id, onSuccess, onFailure);
    }
}