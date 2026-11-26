package com.example.lensai.view.adapter;

import static android.text.TextUtils.isEmpty;

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
import com.squareup.picasso.Picasso;

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
        if (data != null) {
            list.addAll(data);
        }
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

        // Tên vật thể
        h.tvLabel.setText(item.labelEn != null ? item.labelEn : "Unknown");

        // Mô tả vật thể: ưu tiên tiếng Anh
        String desc = !isEmpty(item.descriptionEn) ? item.descriptionEn
                : !isEmpty(item.descriptionVi) ? item.descriptionVi
                  : !isEmpty(item.description) ? item.description : "";
        h.tvDesc.setText(desc);
        // Thời gian nhận diện
        h.tvTime.setText(sdf.format(new Date(item.timestamp)));

        // Hiển thị ảnh (Giải mã trực tiếp từ Base64)
        if (item.imageBase64 != null && !item.imageBase64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(item.imageBase64, Base64.DEFAULT);

                // Tự giải mã byte[] thành Bitmap
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

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

        // Xóa lịch sử
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });

        // Xem chi tiết lịch sử
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }
    @Override
    public int getItemCount() {
        return list.size();
    }
    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvLabel, tvDesc, tvTime;
        ImageView btnDelete;

        ViewHolder(View v) {
            super(v);
            ivThumb = v.findViewById(R.id.imgThumbnail);
            tvLabel = v.findViewById(R.id.tvObjectName);
            tvDesc = v.findViewById(R.id.tvDesc);
            tvTime = v.findViewById(R.id.tvDate);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}