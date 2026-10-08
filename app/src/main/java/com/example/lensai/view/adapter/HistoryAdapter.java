package com.example.lensai.view.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lensai.R;
import com.example.lensai.model.ScanHistory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onDelete(ScanHistory item);
        void onClick(ScanHistory item);
    }

    private final List<ScanHistory> list = new ArrayList<>();
    private final OnItemClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public HistoryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ScanHistory> data) {
        list.clear();
        if (data != null) list.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ScanHistory item = list.get(position);

        h.tvLabel.setText(item.labelEn != null ? item.labelEn : "Unknown");
        h.tvDesc.setText(item.description != null ? item.description : "");
        h.tvTime.setText(sdf.format(new Date(item.timestamp)));

        // Hiện ảnh từ Base64 (không còn imageUrl)
        if (item.imageBase64 != null && !item.imageBase64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(item.imageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    h.ivThumb.setImageBitmap(bitmap);
                } else {
                    h.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } catch (Exception e) {
                h.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            h.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvLabel, tvDesc, tvTime;
        ImageButton btnDelete;

        ViewHolder(View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivThumb);
            tvLabel = v.findViewById(R.id.tvLabel);
            tvDesc = v.findViewById(R.id.tvDesc);
            tvTime = v.findViewById(R.id.tvTime);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}