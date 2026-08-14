package com.skstudio.BabyWAstickersApp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
    private final List<StickerPack> stickerPacks;
    private final OnStickerPackClickListener listener;
    private String selectedPackId = "__ALL__";

    public CategoryAdapter(List<StickerPack> stickerPacks, OnStickerPackClickListener listener) {
        this.stickerPacks = stickerPacks;
        this.listener = listener;
    }

    public void setSelectedPackId(String packId) {
        if (selectedPackId != null && selectedPackId.equals(packId)) return;
        
        int oldIndex = getIndexForPackId(selectedPackId);
        int newIndex = getIndexForPackId(packId);
        
        this.selectedPackId = packId;

        if (oldIndex != -1) notifyItemChanged(oldIndex);
        if (newIndex != -1) notifyItemChanged(newIndex);
    }

    private int getIndexForPackId(String packId) {
        if ("__ALL__".equals(packId)) return 0;
        for (int i = 0; i < stickerPacks.size(); i++) {
            if (stickerPacks.get(i).identifier != null && stickerPacks.get(i).identifier.equals(packId)) {
                return i + 1; // +1 because of "ALL" at index 0
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        boolean isSelected;
        if (position == 0) {
            holder.categoryName.setText(R.string.all_packs);
            isSelected = "__ALL__".equals(selectedPackId);
            holder.itemView.setOnClickListener(v -> {
                setSelectedPackId("__ALL__");
                if (listener != null) {
                    listener.onAllClick();
                }
            });
        } else {
            StickerPack pack = stickerPacks.get(position - 1);
            holder.categoryName.setText(pack.name);
            isSelected = selectedPackId != null && selectedPackId.equals(pack.identifier);
            holder.itemView.setOnClickListener(v -> {
                // We don't necessarily select it if it's opening a new activity, 
                // but let's follow the user's intent.
                if (listener != null) {
                    listener.onStickerPackClick(pack);
                }
            });
        }
        
        if (isSelected) {
            holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorPrimary));
            holder.categoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorPrimary));
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.category_chip_background));
        } else {
            holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.divider));
            holder.categoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textSecondary));
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.surface));
        }
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size() + 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView categoryName;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView.findViewById(R.id.category_card);
            categoryName = itemView.findViewById(R.id.category_name);
        }
    }

    public interface OnStickerPackClickListener {
        void onStickerPackClick(StickerPack pack);
        void onAllClick();
    }
}
