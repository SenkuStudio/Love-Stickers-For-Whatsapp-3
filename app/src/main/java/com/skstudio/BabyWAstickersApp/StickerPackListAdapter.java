/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StickerPackListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    @NonNull
    private List<StickerPack> stickerPacks;
    @NonNull
    private List<StickerPack> stickerPacksFull;
    @NonNull
    private final OnAddButtonClickedListener onAddButtonClickedListener;
    private FavoriteManager favoriteManager;
    private int maxNumberOfStickersInARow;
    private int minMarginBetweenImages;
    private int previewSize;
    private boolean showLoadingFooter = false;
    private boolean isScrolling = false;
    private ResizeOptions resizeOptions;

    StickerPackListAdapter(@NonNull List<StickerPack> stickerPacks, @NonNull OnAddButtonClickedListener onAddButtonClickedListener) {
        this.stickerPacks = new ArrayList<>(stickerPacks);
        this.stickerPacksFull = new ArrayList<>(stickerPacks);
        this.onAddButtonClickedListener = onAddButtonClickedListener;
    }

    public void updateData(List<StickerPack> newList) {
        if (newList == null) {
            int size = this.stickerPacks.size();
            this.stickerPacks = new ArrayList<>();
            this.stickerPacksFull = new ArrayList<>();
            notifyItemRangeRemoved(0, size);
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new StickerPackDiffCallback(this.stickerPacks, newList));
        this.stickerPacks.clear();
        this.stickerPacks.addAll(newList);
        this.stickerPacksFull = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    private static class StickerPackDiffCallback extends DiffUtil.Callback {
        private final List<StickerPack> oldList;
        private final List<StickerPack> newList;

        StickerPackDiffCallback(List<StickerPack> oldList, List<StickerPack> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldList.get(oldItemPosition).identifier, newList.get(newItemPosition).identifier);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            StickerPack oldPack = oldList.get(oldItemPosition);
            StickerPack newPack = newList.get(newItemPosition);
            return Objects.equals(oldPack.identifier, newPack.identifier) &&
                    Objects.equals(oldPack.name, newPack.name) &&
                    oldPack.getIsWhitelisted() == newPack.getIsWhitelisted() &&
                    oldPack.isDownloaded() == newPack.isDownloaded() &&
                    oldPack.animatedStickerPack == newPack.animatedStickerPack;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (showLoadingFooter && position == stickerPacks.size()) {
            return VIEW_TYPE_LOADING;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup viewGroup, final int viewType) {
        final Context context = viewGroup.getContext();
        if (favoriteManager == null) {
            favoriteManager = new FavoriteManager(context);
        }
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_LOADING) {
            View view = layoutInflater.inflate(R.layout.layout_loading_footer, viewGroup, false);
            return new LoadingViewHolder(view);
        }
        final View stickerPackRow = layoutInflater.inflate(R.layout.sticker_packs_list_item, viewGroup, false);
        return new StickerPackListItemViewHolder(stickerPackRow);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int index) {
        if (holder instanceof StickerPackListItemViewHolder) {
            StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) holder;
            StickerPack pack = stickerPacks.get(index);
            final Context context = viewHolder.publisherView.getContext();
            viewHolder.publisherView.setText(pack.publisher);
            viewHolder.filesizeView.setText(Formatter.formatShortFileSize(context, pack.getTotalSize()));

            viewHolder.titleView.setText(pack.name);
            viewHolder.container.setOnClickListener(view -> {
                Intent intent = new Intent(view.getContext(), StickerPackDetailsActivity.class);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_SHOW_UP_BUTTON, true);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_DATA, pack);
                view.getContext().startActivity(intent);
            });
            //if this sticker pack contains less stickers than the max, then take the smaller size.
            int actualNumberOfStickersToShow = Math.min(maxNumberOfStickersInARow, pack.getStickers().size());
            List<Sticker> stickers = pack.getStickers();
            for (int i = 0; i < viewHolder.stickerImages.size(); i++) {
                SimpleDraweeView rowImage = viewHolder.stickerImages.get(i);
                if (i < actualNumberOfStickersToShow) {
                    if (rowImage.getVisibility() != View.VISIBLE) {
                        rowImage.setVisibility(View.VISIBLE);
                    }
                    Uri uri = StickerPackLoader.getStickerUri(context, pack, stickers.get(i));
                    
                    Object oldUri = rowImage.getTag(R.id.uri_tag);
                    if (uri != null && !uri.equals(oldUri)) {
                        ImageRequestBuilder requestBuilder = ImageRequestBuilder.newBuilderWithSource(uri);
                        if (resizeOptions != null) {
                            requestBuilder.setResizeOptions(resizeOptions);
                        }
                        // Add this to reduce priority of off-screen items
                        requestBuilder.setRequestPriority(com.facebook.imagepipeline.common.Priority.MEDIUM);

                        ImageRequest request = requestBuilder.build();

                        DraweeController controller = Fresco.newDraweeControllerBuilder()
                                .setImageRequest(request)
                                .setAutoPlayAnimations(false)
                                .setOldController(rowImage.getController())
                                .build();
                        rowImage.setController(controller);
                        rowImage.setTag(R.id.uri_tag, uri);
                    }
                    
                    // Apply dynamic margins - only if changed to avoid layout requests
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rowImage.getLayoutParams();
                    int targetMargin = (i == 0) ? 0 : minMarginBetweenImages;
                    if (lp.leftMargin != targetMargin) {
                        lp.leftMargin = targetMargin;
                        rowImage.setLayoutParams(lp);
                    }
                } else {
                    if (rowImage.getVisibility() != View.GONE) {
                        rowImage.setVisibility(View.GONE);
                    }
                }
            }
            setAddButtonAppearance(viewHolder.addButton, pack);
            viewHolder.animatedStickerPackIndicator.setVisibility(pack.animatedStickerPack ? View.VISIBLE : View.GONE);
            updateFavoriteUI(viewHolder.favoriteButton, pack);
            viewHolder.favoriteButton.setOnClickListener(v -> {
                favoriteManager.toggleFavorite(pack.identifier);
                updateFavoriteUI(viewHolder.favoriteButton, pack);
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof StickerPackListItemViewHolder) {
            StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) holder;
            for (SimpleDraweeView rowImage : viewHolder.stickerImages) {
                rowImage.setController(null);
                rowImage.setTag(R.id.uri_tag, null);
            }
        }
    }

    private void updateFavoriteUI(ImageButton button, StickerPack pack) {
        if (favoriteManager.isFavorite(pack.identifier)) {
            button.setImageResource(R.drawable.ic_favorite_filled);
        } else {
            button.setImageResource(R.drawable.ic_favorite_border);
        }
    }

    private void setAddButtonAppearance(MaterialButton addButton, StickerPack pack) {
        if (pack.getIsWhitelisted()) {
            addButton.setIconResource(R.drawable.sticker_3rdparty_added);
            addButton.setClickable(false);
            addButton.setOnClickListener(null);
            setBackground(addButton, null);
        } else {
            addButton.setIconResource(R.drawable.sticker_3rdparty_add);
            addButton.setOnClickListener(v -> onAddButtonClickedListener.onAddButtonClicked(pack));
            TypedValue outValue = new TypedValue();
            addButton.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            addButton.setBackgroundResource(outValue.resourceId);
        }
    }

    private void setBackground(View view, Drawable background) {
        if (Build.VERSION.SDK_INT >= 16) {
            view.setBackground(background);
        } else {
            view.setBackgroundDrawable(background);
        }
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size() + (showLoadingFooter ? 1 : 0);
    }

    void setImageRowSpec(int maxNumberOfStickersInARow, int minMarginBetweenImages, int previewSize) {
        this.minMarginBetweenImages = minMarginBetweenImages;
        this.previewSize = previewSize;
        if (previewSize > 0) {
            this.resizeOptions = new ResizeOptions(previewSize, previewSize);
        }
        if (this.maxNumberOfStickersInARow != maxNumberOfStickersInARow) {
            this.maxNumberOfStickersInARow = maxNumberOfStickersInARow;
            notifyDataSetChanged();
        }
    }

    void setStickerPackList(List<StickerPack> stickerPackList) {
        this.stickerPacks = new ArrayList<>(stickerPackList);
        this.stickerPacksFull = new ArrayList<>(stickerPackList);
        notifyDataSetChanged();
    }

    public void showLoadingFooter(boolean show) {
        if (this.showLoadingFooter != show) {
            this.showLoadingFooter = show;
            if (show) {
                notifyItemInserted(stickerPacks.size());
            } else {
                notifyItemRemoved(stickerPacks.size());
            }
        }
    }

    public void setData(List<StickerPack> newList) {
        updateData(newList);
    }

    public void setScrolling(boolean scrolling) {
        if (this.isScrolling != scrolling) {
            this.isScrolling = scrolling;
            // No notifyDataSetChanged here to avoid stutter
        }
    }

    public void updateAnimationState(RecyclerView recyclerView) {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            if (holder instanceof StickerPackListItemViewHolder) {
                StickerPackListItemViewHolder vh = (StickerPackListItemViewHolder) holder;
                for (SimpleDraweeView dv : vh.stickerImages) {
                    if (dv.getController() != null && dv.getController().getAnimatable() != null) {
                        if (isScrolling) {
                            dv.getController().getAnimatable().stop();
                        } else {
                            dv.getController().getAnimatable().start();
                        }
                    }
                }
            }
        }
    }

    // Removed redundant updateData method to keep only the DiffUtil version


    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void filter(String text) {
        stickerPacks = new ArrayList<>();
        if (text.isEmpty()) {
            stickerPacks.addAll(stickerPacksFull);
        } else {
            text = text.toLowerCase();
            for (StickerPack pack : stickerPacksFull) {
                boolean emojiMatch = false;
                if (pack.getStickers() != null) {
                    for (Sticker sticker : pack.getStickers()) {
                        if (sticker.emojis != null) {
                            for (String emoji : sticker.emojis) {
                                if (emoji.contains(text)) {
                                    emojiMatch = true;
                                    break;
                                }
                            }
                        }
                        if (emojiMatch) break;
                    }
                }
                if (pack.name.toLowerCase().contains(text) || pack.publisher.toLowerCase().contains(text) || emojiMatch) {
                    stickerPacks.add(pack);
                }
            }
        }
        notifyDataSetChanged();
    }

    public interface OnAddButtonClickedListener {
        void onAddButtonClicked(StickerPack stickerPack);
    }
}
