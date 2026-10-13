/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.lovestickerss;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class StickerPreviewAdapter extends RecyclerView.Adapter<StickerPreviewViewHolder> {

    private static final float COLLAPSED_STICKER_PREVIEW_BACKGROUND_ALPHA = 1f;
    private static final float EXPANDED_STICKER_PREVIEW_BACKGROUND_ALPHA = 0.2f;

    @NonNull
    private final StickerPack stickerPack;

    private final int cellSize;
    private final int cellLimit;
    private final int cellPadding;
    private final int errorResource;
    private final View expandedStickerOverlay;
    private final SimpleDraweeView expandedStickerPreview;

    private final LayoutInflater layoutInflater;
    private RecyclerView recyclerView;
    private boolean isScrolling = false;
    private final ResizeOptions resizeOptions;

    StickerPreviewAdapter(
            @NonNull final LayoutInflater layoutInflater,
            final int errorResource,
            final int cellSize,
            final int cellPadding,
            @NonNull final StickerPack stickerPack,
            final View expandedStickerOverlay) {
        this.cellSize = cellSize;
        this.cellPadding = cellPadding;
        this.cellLimit = 0;
        this.layoutInflater = layoutInflater;
        this.errorResource = errorResource;
        this.stickerPack = stickerPack;
        this.expandedStickerOverlay = expandedStickerOverlay;
        this.expandedStickerPreview = expandedStickerOverlay.findViewById(R.id.sticker_details_expanded_sticker);
        this.resizeOptions = new ResizeOptions(cellSize, cellSize);
    }

    @NonNull
    @Override
    public StickerPreviewViewHolder onCreateViewHolder(@NonNull final ViewGroup viewGroup, final int i) {
        View itemView = layoutInflater.inflate(R.layout.sticker_image_item, viewGroup, false);
        StickerPreviewViewHolder vh = new StickerPreviewViewHolder(itemView);

        ViewGroup.LayoutParams layoutParams = vh.stickerPreviewView.getLayoutParams();
        layoutParams.height = cellSize;
        layoutParams.width = cellSize;
        vh.stickerPreviewView.setLayoutParams(layoutParams);
        vh.stickerPreviewView.setPadding(cellPadding, cellPadding, cellPadding, cellPadding);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final StickerPreviewViewHolder stickerPreviewViewHolder, final int i) {
        Uri uri = StickerPackLoader.getStickerUri(stickerPreviewViewHolder.stickerPreviewView.getContext(), stickerPack, stickerPack.getStickers().get(i));
        
        Object oldUri = stickerPreviewViewHolder.stickerPreviewView.getTag(R.id.uri_tag);
        if (uri != null && !uri.equals(oldUri)) {
            ImageRequest request = ImageRequestBuilder.newBuilderWithSource(uri)
                    .setResizeOptions(resizeOptions)
                    .build();
            DraweeController controller = Fresco.newDraweeControllerBuilder()
                    .setImageRequest(request)
                    .setAutoPlayAnimations(false)
                    .setOldController(stickerPreviewViewHolder.stickerPreviewView.getController())
                    .build();
            stickerPreviewViewHolder.stickerPreviewView.setController(controller);
            stickerPreviewViewHolder.stickerPreviewView.setTag(R.id.uri_tag, uri);
        }
        
        // Manual animation control based on scroll state
        updateAnimationState(stickerPreviewViewHolder.stickerPreviewView);
        
        stickerPreviewViewHolder.stickerPreviewView.setOnClickListener(v -> expandPreview(i, stickerPreviewViewHolder.stickerPreviewView));
    }

    private void updateAnimationState(SimpleDraweeView view) {
        com.facebook.drawee.interfaces.DraweeController controller = view.getController();
        if (controller != null) {
            android.graphics.drawable.Animatable animatable = controller.getAnimatable();
            if (animatable != null) {
                if (isScrolling) {
                    animatable.stop();
                } else {
                    animatable.start();
                }
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull StickerPreviewViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stickerPreviewView.setController(null);
        holder.stickerPreviewView.setTag(R.id.uri_tag, null);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
        recyclerView.addOnScrollListener(hideExpandedViewScrollListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerView.removeOnScrollListener(hideExpandedViewScrollListener);
        this.recyclerView = null;
    }

    private final RecyclerView.OnScrollListener hideExpandedViewScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    setScrolling(newState != RecyclerView.SCROLL_STATE_IDLE);
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dx != 0 || dy != 0) {
                        hideExpandedStickerPreview();
                    }
                }
            };

    private void expandPreview(int position, View clickedStickerPreview) {
        if (isStickerPreviewExpanded()) {
            hideExpandedStickerPreview();
            return;
        }

        if (expandedStickerOverlay != null && expandedStickerPreview != null) {
            final Uri stickerAssetUri = StickerPackLoader.getStickerUri(expandedStickerPreview.getContext(), stickerPack, stickerPack.getStickers().get(position));
            DraweeController controller = Fresco.newDraweeControllerBuilder()
                    .setUri(stickerAssetUri)
                    .setAutoPlayAnimations(true)
                    .build();
            expandedStickerPreview.setImageResource(errorResource);
            expandedStickerPreview.setController(controller);

            expandedStickerOverlay.setVisibility(View.VISIBLE);
            
            Context context = expandedStickerOverlay.getContext();
            if (context instanceof BaseActivity) {
                ((BaseActivity) context).toggleAdsVisibility(false);
            }

            expandedStickerOverlay.setOnClickListener(v -> hideExpandedStickerPreview());
            expandedStickerPreview.setOnLongClickListener(v -> {
                shareSticker(position);
                return true;
            });
        }
    }

    public void hideExpandedStickerPreview() {
        if (isStickerPreviewExpanded() && expandedStickerOverlay != null) {
            expandedStickerOverlay.setVisibility(View.GONE);
            Context context = expandedStickerOverlay.getContext();
            if (context instanceof BaseActivity) {
                ((BaseActivity) context).toggleAdsVisibility(true);
            }
        }
    }

    private void shareSticker(int position) {
        Context context = expandedStickerPreview.getContext();
        Sticker sticker = stickerPack.getStickers().get(position);
        File stickerFile = new File(context.getFilesDir(), "stickers/" + stickerPack.identifier + "/" + sticker.imageFileName);

        if (stickerFile.exists()) {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", stickerFile);
            shareUri(context, uri, "image/webp");
        } else {
            // If file doesn't exist locally, try to use the cache or captured bitmap as fallback
            Bitmap bitmap = Bitmap.createBitmap(expandedStickerPreview.getWidth(), expandedStickerPreview.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            expandedStickerPreview.draw(canvas);

            File cachePath = new File(context.getCacheDir(), "images");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }
            File file = new File(cachePath, "shared_sticker.png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            shareUri(context, uri, "image/png");
        }
    }

    private void shareUri(Context context, Uri uri, String mimeType) {
        if (uri != null) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(Intent.createChooser(intent, "Share Sticker"));
        }
    }


    private boolean isStickerPreviewExpanded() {
        return expandedStickerOverlay != null && expandedStickerOverlay.getVisibility() == View.VISIBLE;
    }

    public void setScrolling(boolean scrolling) {
        if (this.isScrolling != scrolling) {
            this.isScrolling = scrolling;
            if (scrolling) {
                hideExpandedStickerPreview();
            }
            if (recyclerView != null) {
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
                    if (vh instanceof StickerPreviewViewHolder) {
                        updateAnimationState(((StickerPreviewViewHolder) vh).stickerPreviewView);
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        int numberOfPreviewImagesInPack;
        numberOfPreviewImagesInPack = stickerPack.getStickers().size();
        if (cellLimit > 0) {
            return Math.min(numberOfPreviewImagesInPack, cellLimit);
        }
        return numberOfPreviewImagesInPack;
    }
}
