package com.quantumlock.crypto;

import java.util.Arrays;

/**
 * Immutable result of a successful envelope encryption operation.
 *
 * <p>All byte arrays are defensively copied on construction. Callers should
 * not hold references to the originals after handing this object off.
 *
 * <ul>
 *   <li>{@code ciphertext}     — AES-256-GCM encrypted document bytes (includes GCM auth tag)</li>
 *   <li>{@code nonce}          — 96-bit (12-byte) random IV used during encryption</li>
 *   <li>{@code wrappedDekBytes}— AESWrap-encrypted DEK bytes for persistence</li>
 *   <li>{@code kekVersion}     — KEK version used to wrap the DEK (for future unwrap/rotate)</li>
 *   <li>{@code ciphertextDigest} — SHA-256 hex digest of the ciphertext for integrity verification</li>
 * </ul>
 */
public final class EncryptionResult {

    private final byte[] ciphertext;
    private final byte[] nonce;
    private final byte[] wrappedDekBytes;
    private final int kekVersion;
    private final String ciphertextDigest;

    public EncryptionResult(
            byte[] ciphertext,
            byte[] nonce,
            byte[] wrappedDekBytes,
            int kekVersion,
            String ciphertextDigest) {

        this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        this.nonce = Arrays.copyOf(nonce, nonce.length);
        this.wrappedDekBytes = Arrays.copyOf(wrappedDekBytes, wrappedDekBytes.length);
        this.kekVersion = kekVersion;
        this.ciphertextDigest = ciphertextDigest;
    }

    /** Returns a defensive copy of the GCM ciphertext (including auth tag). */
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    /** Returns a defensive copy of the 96-bit nonce. */
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    /** Returns a defensive copy of the AESWrap-encrypted DEK bytes. */
    public byte[] wrappedDekBytes() {
        return Arrays.copyOf(wrappedDekBytes, wrappedDekBytes.length);
    }

    public int kekVersion() {
        return kekVersion;
    }

    /** SHA-256 hex digest of the ciphertext bytes (integrity anchor). */
    public String ciphertextDigest() {
        return ciphertextDigest;
    }
}

