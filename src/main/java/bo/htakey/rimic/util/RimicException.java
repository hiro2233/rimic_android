/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package bo.htakey.rimic.util;

import android.os.Parcel;
import android.os.Parcelable;

import bo.htakey.rimic.protobuf.Mumble;

/**
 * Created by andrew on 14/07/13.
 */
@SuppressWarnings("serial")
public class RimicException extends Exception implements Parcelable {

    public static final Creator<RimicException> CREATOR = new Creator<RimicException>() {
        @Override
        public RimicException createFromParcel(Parcel source) {
            return new RimicException(source);
        }

        @Override
        public RimicException[] newArray(int size) {
            return new RimicException[size];
        }
    };

    private RimicDisconnectReason mReason;
    /** Indicates that this exception was caused by a reject from the server. */
    private Mumble.Reject mReject;
    /** Indicates that this exception was caused by being kicked/banned from the server. */
    private Mumble.UserRemove mUserRemove;

    public RimicException(String message, Throwable e, RimicDisconnectReason reason) {
        super(message, e);
        mReason = reason;
    }

    public RimicException(String message, RimicDisconnectReason reason) {
        super(message);
        mReason = reason;
    }

    public RimicException(Throwable e, RimicDisconnectReason reason) {
        super(e);
        mReason = reason;
    }

    public RimicException(Mumble.Reject reject) {
        super("Rejected: " + reject.getReason());
        mReject = reject;
        mReason = RimicDisconnectReason.REJECT;
    }

    public RimicException(Mumble.UserRemove userRemove) {
        super((userRemove.getBan() ? "Banned: " : "Kicked: ")+userRemove.getReason());
        mUserRemove = userRemove;
        mReason = RimicDisconnectReason.USER_REMOVE;
    }

    private RimicException(Parcel in) {
        super(in.readString(), (Throwable) in.readSerializable());
        mReason = RimicDisconnectReason.values()[in.readInt()];
        mReject = (Mumble.Reject) in.readSerializable();
        mUserRemove = (Mumble.UserRemove) in.readSerializable();
    }

    public RimicDisconnectReason getReason() {
        return mReason;
    }

    public Mumble.Reject getReject() {
        return mReject;
    }

    public Mumble.UserRemove getUserRemove() {
        return mUserRemove;
    }
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        dest.writeSerializable(getCause());
        dest.writeInt(mReason.ordinal());
        dest.writeSerializable(mReject);
        dest.writeSerializable(mUserRemove);
    }

    public enum RimicDisconnectReason {
        REJECT,
        USER_REMOVE,
        CONNECTION_ERROR,
        OTHER_ERROR
    }
}
