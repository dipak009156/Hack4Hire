package com.quantumlock.kms;

/**
 * KMS abstraction for wrapping and unwrapping Data Encryption Keys (DEKs).
 *
 * <p>Implementations use {@code AESWrap} (RFC 3394) with a 256-bit Key Encryption
 * Key (KEK) identified by a version number. The version is persisted alongside the
 * wrapped DEK so that rotation can unwrap with the old KEK and re-wrap with the new
 * one without touching plaintext document content.
 *
 * <p><strong>Security contract:</strong>
 * <ul>
 *   <li>Callers MUST zero the returned plaintext DEK bytes immediately after use.</li>
 *   <li>Wrapped DEK bytes are safe to persist and are opaque to the caller.</li>
 * </ul>
 */
public interface KmsService {

    /**
     * Wraps (encrypts) a plaintext DEK using the current KEK version.
     *
     * @param plaintextDek raw DEK bytes (must be a valid AES key length: 16, 24, or 32 bytes)
     * @return {@link WrappedDek} containing the ciphertext and the KEK version used
     * @throws KmsException if wrapping fails or fault injection is active
     */
    WrappedDek wrapDek(byte[] plaintextDek);

    /**
     * Unwraps (decrypts) a previously wrapped DEK.
     *
     * @param wrappedDek the wrapped DEK and the KEK version it was wrapped with
     * @return plaintext DEK bytes — caller MUST zero these after use
     * @throws KmsException if the KEK version is unknown, unwrapping fails, or fault injection is active
     */
    byte[] unwrapDek(WrappedDek wrappedDek);

    /**
     * Returns the version number of the currently active KEK used for new wrap operations.
     */
    int currentKekVersion();

    /**
     * Wraps a plaintext DEK using a specific KEK version (used during key rotation
     * to re-wrap DEKs with a new target KEK without changing the current-version default).
     *
     * @param plaintextDek raw DEK bytes
     * @param kekVersion   the target KEK version to wrap with
     * @return {@link WrappedDek} wrapped with the specified version
     */
    WrappedDek wrapDekWithVersion(byte[] plaintextDek, int kekVersion);
}

