/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.BabyWAstickersApp;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

class Sticker implements Parcelable {
    final String imageFileName;
    final List<String> emojis;
    final String imageUrl;
    long size;
    boolean isFeatured;

    Sticker(String imageFileName, List<String> emojis) {
        this(imageFileName, emojis, null);
    }

    Sticker(String imageFileName, List<String> emojis, String imageUrl) {
        this.imageFileName = imageFileName;
        this.emojis = emojis;
        this.imageUrl = imageUrl;
    }

    private Sticker(Parcel in) {
        imageFileName = in.readString();
        emojis = in.createStringArrayList();
        imageUrl = in.readString();
        size = in.readLong();
        isFeatured = in.readByte() != 0;
    }

    public static final Creator<Sticker> CREATOR = new Creator<Sticker>() {
        @Override
        public Sticker createFromParcel(Parcel in) {
            return new Sticker(in);
        }

        @Override
        public Sticker[] newArray(int size) {
            return new Sticker[size];
        }
    };

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(imageFileName);
        dest.writeStringList(emojis);
        dest.writeString(imageUrl);
        dest.writeLong(size);
        dest.writeByte((byte) (isFeatured ? 1 : 0));
    }
}
