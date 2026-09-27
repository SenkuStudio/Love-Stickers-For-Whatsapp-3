/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.lovestickers;

import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

class StickerPackListItemViewHolder extends RecyclerView.ViewHolder {

    final View container;
    final TextView titleView;
    final TextView publisherView;
    final TextView filesizeView;
    final MaterialButton addButton;
    final ImageButton favoriteButton;
    final ImageView animatedStickerPackIndicator;
    final LinearLayout imageRowView;
    final List<SimpleDraweeView> stickerImages;

    StickerPackListItemViewHolder(final View itemView) {
        super(itemView);
        container = itemView;
        titleView = itemView.findViewById(R.id.sticker_pack_title);
        publisherView = itemView.findViewById(R.id.sticker_pack_publisher);
        filesizeView = itemView.findViewById(R.id.sticker_pack_filesize);
        addButton = itemView.findViewById(R.id.add_button_on_list);
        favoriteButton = itemView.findViewById(R.id.favorite_button);
        imageRowView = itemView.findViewById(R.id.sticker_packs_list_item_image_list);
        animatedStickerPackIndicator = itemView.findViewById(R.id.sticker_pack_animation_indicator);
        
        stickerImages = new ArrayList<>();
        stickerImages.add(itemView.findViewById(R.id.sticker_img_1));
        stickerImages.add(itemView.findViewById(R.id.sticker_img_2));
        stickerImages.add(itemView.findViewById(R.id.sticker_img_3));
        stickerImages.add(itemView.findViewById(R.id.sticker_img_4));
    }
}