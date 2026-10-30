package com.quantumlock.crypto;

import com.quantumlock.kms.KmsService;
import com.quantumlock.kms.WrappedDek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Envelope encryption service implementing ADR-002.
 *
 * <h3>Encryption flow</h3>
 * <ol>
 *   <li>Generate a fresh 256-bit DEK via {@link KeyGenerator} / {@link SecureRandom#getInstanceStrong()}.</li>
 *   <li>Build AAD = {@code tenantId|documentId|version} to bind ciphertext to its identity.</li>
 *   <li>Encrypt plaintext with AES-256-GCM ({@code AES/GCM/NoPadding}), 96-bit random nonce, 128-bit auth tag.</li>
 *   <li>Compute SHA-256 digest of the ciphertext for an additional integrity check.</li>
 *   <li>Wrap the DEK via {@link KmsService#wrapDek(byte[])} and zero the plaintext DEK bytes.</li>
 *   <li>Return an {@link EncryptionResult} — no plaintext key material escapes this method.</li>
 * </ol>
 *
 * <h3>Decryption flow</h3>
 * <ol>
 *   <li>Verify the SHA-256 ciphertext digest before decrypting (fast fail on tampering).</li>
 *   <li>Unwrap the DEK via {@link KmsService#unwrapDek(WrappedDek)}.</li>
 *   <li>Decrypt with AES-256-GCM; GCM authentication tag verification is implicit.</li>
 *   <li>Zero the plaintext DEK bytes immediately after decryption.</li>
 * </ol>
 *
 * <p>All algorithms are JCA standard: {@code AES/GCM/NoPadding}, {@code AES}, {@code SHA-256}.
 */
@Service
public class EnvelopeEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEncryptionService.class);

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int DEK_KEY_SIZE_BITS = 256;
    private static final int NONCE_LENGTH_BYTES = 12;  // 96 bits
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final KmsService kmsService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EnvelopeEncryptionService(KmsService kmsService) {
        this.kmsService = kmsService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Encrypts {@code plaintext} and returns an {@link EncryptionResult} containing
     * the ciphertext, nonce, wrapped DEK, KEK version, and ciphertext digest.
     *
     * @param plaintext  raw document bytes to encrypt
     * @param tenantId   tenant UUID — bound into AAD
     * @param documentId document UUID — bound into AAD
     * @param version    document version number — bound into AAD
     * @return encryption result; all byte arrays are defensive copies
     * @throws CryptoException on any JCA failure
     */
    public EncryptionResult encrypt(byte[] plaintext, UUID tenantId, UUID documentId, int version) {
        byte[] dek = null;
        try {
            // 1. Generate DEK
            dek = generateDek();

            // 2. Generate 96-bit nonce
            byte[] nonce = generateNonce();

            // 3. Build AAD
            byte[] aad = buildAad(tenantId, documentId, version);

            // 4. Encrypt
            byte[] ciphertext = gcmEncrypt(plaintext, dek, nonce, aad);

            // 5. Compute ciphertext digest
            String digest = computeDigest(ciphertext);

            // 6. Wrap DEK
            WrappedDek wrappedDek = kmsService.wrapDek(dek);

            log.debug("Encrypted document tenantId={} documentId={} version={} digestPrefix={}",
                    tenantId, documentId, version, digest.substring(0, 8));

            return new EncryptionResult(
                    ciphertext,
                    nonce,
                    wrappedDek.wrappedBytes(),
                    wrappedDek.kekVersion(),
                    digest);

        } catch (CryptoException | com.quantumlock.kms.KmsException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Encryption failed for documentId=" + documentId, e);
        } finally {
            // Zero the plaintext DEK bytes immediately — even if an exception occurred
            zeroize(dek);
        }
    }

    /**
     * Decrypts a document version, verifying both the SHA-256 digest and the
     * GCM authentication tag before returning plaintext.
     *
     * @param ciphertext      encrypted document bytes (includes GCM auth tag)
     * @param nonce           the 96-bit IV used during encryption
     * @param wrappedDekBytes AESWrap-encrypted DEK
     * @param kekVersion      KEK version used to wrap the DEK
     * @param expectedDigest  SHA-256 hex digest stored at upload time
     * @param tenantId        tenant UUID — must match the one used at encryption time
     * @param documentId      document UUID — must match
     * @param version         document version — must match
     * @return decrypted plaintext bytes
     * @throws CryptoException if digest verification fails, GCM auth tag is invalid, or any JCA error
     */
    public byte[] decrypt(
            byte[] ciphertext,
            byte[] nonce,
            byte[] wrappedDekBytes,
            int kekVersion,
            String expectedDigest,
            UUID tenantId,
            UUID documentId,
            int version) {

        byte[] dek = null;
        try {
            // 1. Verify ciphertext integrity before touching the DEK
            verifyDigest(ciphertext, expectedDigest, tenantId, documentId, version);

            // 2. Unwrap DEK
            WrappedDek wrappedDek = new WrappedDek(wrappedDekBytes, kekVersion);
            dek = kmsService.unwrapDek(wrappedDek);

            // 3. Build AAD (must match exactly what was used at encryption)
            byte[] aad = buildAad(tenantId, documentId, version);

            // 4. Decrypt — GCM authentication tag is verified implicitly by the JCA provider
            byte[] plaintext = gcmDecrypt(ciphertext, dek, nonce, aad);

            log.debug("Decrypted document tenantId={} documentId={} version={}", tenantId, documentId, version);
            return plaintext;

        } catch (CryptoException | com.quantumlock.kms.KmsException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Decryption failed for documentId=" + documentId, e);
        } finally {
            zeroize(dek);
        }
    }

    /**
     * Re-wraps an existing wrapped DEK from one KEK version to the current KEK version,
     * used by the key rotation subsystem. Plaintext DEK bytes are zeroed immediately.
     *
     * @param wrappedDekBytes wrapped DEK bytes to re-wrap
     * @param kekVersion      the KEK version the DEK is currently wrapped with
     * @return a new {@link WrappedDek} wrapped under the current KEK version
     */
    public WrappedDek rewrapDek(byte[] wrappedDekBytes, int kekVersion) {
        byte[] plaintextDek = null;
        try {
            WrappedDek old = new WrappedDek(wrappedDekBytes, kekVersion);
            plaintextDek = kmsService.unwrapDek(old);
            return kmsService.wrapDek(plaintextDek);
        } finally {
            zeroize(plaintextDek);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private byte[] generateDek() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
            kg.init(DEK_KEY_SIZE_BITS, secureRandom);
            return kg.generateKey().getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("AES KeyGenerator not available", e);
        }
    }

    private byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    /**
     * Builds Additional Authenticated Data: {@code tenantId|documentId|version}
     * encoded as UTF-8. This binds the ciphertext cryptographically to its
     * document identity, preventing relocation or replay across tenants/versions.
     */
    private byte[] buildAad(UUID tenantId, UUID documentId, int version) {
        String aadString = tenantId.toString() + "|" + documentId.toString() + "|" + version;
        return aadString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] gcmEncrypt(byte[] plaintext, byte[] dek, byte[] nonce, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        SecretKey key = new SecretKeySpec(dek, KEY_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private byte[] gcmDecrypt(byte[] ciphertext, byte[] dek, byte[] nonce, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        SecretKey key = new SecretKeySpec(dek, KEY_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    /** Computes a SHA-256 hex digest of the provided bytes. */
    public String computeDigest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = md.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-256 not available", e);
        }
    }

    private void verifyDigest(byte[] ciphertext, String expectedDigest,
                               UUID tenantId, UUID documentId, int version) {
        String actualDigest = computeDigest(ciphertext);
        if (!MessageDigest.isEqual(
                HexFormat.of().parseHex(expectedDigest),
                HexFormat.of().parseHex(actualDigest))) {
            throw new CryptoException(
                    "Ciphertext integrity check failed for documentId=" + documentId
                    + " version=" + version + " tenantId=" + tenantId
                    + ". Possible tampering or corruption detected.");
        }
    }

    /**
     * Zeros a byte array to remove key material from heap memory.
     * Safe to call with {@code null} (no-op).
     */
    private void zeroize(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}

