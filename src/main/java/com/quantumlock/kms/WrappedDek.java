package com.quantumlock.kms;

import java.util.Arrays;

/**
 * Value object pairing a wrapped (AESWrap-encrypted) DEK with the KEK version
 * that was used to produce it.
 *
 * <p>Both fields are required for correct unwrapping; the version is persisted
 * in the {@code document_version.kek_version} column.
 *
 * <p>The wrapped bytes are a defensive copy on construction to prevent mutation
 * by callers.
 */
public final class WrappedDek {

    private final byte[] wrappedBytes;
    private final int kekVersion;

    public WrappedDek(byte[] wrappedBytes, int kekVersion) {
        if (wrappedBytes == null || wrappedBytes.length == 0) {
            throw new IllegalArgumentException("wrappedBytes must not be null or empty");
        }
        this.wrappedBytes = Arrays.copyOf(wrappedBytes, wrappedBytes.length);
        this.kekVersion = kekVersion;
    }

    /** Returns a defensive copy of the wrapped DEK bytes. */
    public byte[] wrappedBytes() {
        return Arrays.copyOf(wrappedBytes, wrappedBytes.length);
    }

    public int kekVersion() {
        return kekVersion;
    }
}

