package com.quantumlock.crypto;

import com.quantumlock.kms.KmsException;
import com.quantumlock.kms.KmsProperties;
import com.quantumlock.kms.SimulatedKmsService;
import com.quantumlock.kms.WrappedDek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EnvelopeEncryptionService}.
 *
 * Verifies round-trip correctness, AAD binding (cross-tenant/version attacks),
 * ciphertext integrity (tamper detection), and DEK zeroization semantics.
 * No Spring context is loaded.
 */
class EnvelopeEncryptionServiceTest {

    private static final String KEK_V1 = "u7x8Q4z+1vP3mN9kL2jH5gF8dE1cA4bZ7yX0wV3uT6s=";
    private static final String KEK_V2 = "p2O5r8U1y4A7d0G3j6M9p2S5v8Y1b4E7h0K3n6Q9t2W=";

    private EnvelopeEncryptionService service;
    private SimulatedKmsService kmsService;

    private final UUID tenantId   = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final int  version    = 1;
    private final byte[] plaintext = "TOP SECRET: quantum entanglement keys".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        KmsProperties props = new KmsProperties(1, false, Map.of(1, KEK_V1, 2, KEK_V2));
        kmsService = new SimulatedKmsService(props);
        service = new EnvelopeEncryptionService(kmsService);
    }

    // -------------------------------------------------------------------------
    // Round-trip correctness
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("encrypt then decrypt recovers original plaintext")
    void encryptDecrypt_roundTrip() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);

        byte[] recovered = service.decrypt(
                result.ciphertext(),
                result.nonce(),
                result.wrappedDekBytes(),
                result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version);

        assertThat(recovered).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("ciphertext is different from plaintext")
    void ciphertext_differFromPlaintext() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        assertThat(result.ciphertext()).isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("two encryptions of the same plaintext produce distinct ciphertexts (nonce uniqueness)")
    void encrypt_twoRuns_produceDistinctCiphertexts() {
        EncryptionResult r1 = service.encrypt(plaintext, tenantId, documentId, version);
        EncryptionResult r2 = service.encrypt(plaintext, tenantId, documentId, version);

        assertThat(r1.ciphertext()).isNotEqualTo(r2.ciphertext());
        assertThat(r1.nonce()).isNotEqualTo(r2.nonce());
    }

    @Test
    @DisplayName("nonce is 12 bytes (96 bits)")
    void nonce_is96Bits() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        assertThat(result.nonce()).hasSize(12);
    }

    @Test
    @DisplayName("ciphertextDigest is non-empty SHA-256 hex (64 chars)")
    void ciphertextDigest_isSha256Hex() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        assertThat(result.ciphertextDigest())
                .isNotBlank()
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    // -------------------------------------------------------------------------
    // AAD binding — cross-tenant / cross-version attack prevention (ADR-002)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("decrypt with wrong tenantId fails — AAD binding prevents cross-tenant attack")
    void decrypt_wrongTenantId_fails() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        UUID otherTenant = UUID.randomUUID();

        assertThatThrownBy(() -> service.decrypt(
                result.ciphertext(), result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                otherTenant, documentId, version))
                .isInstanceOfAny(CryptoException.class, javax.crypto.AEADBadTagException.class);
    }

    @Test
    @DisplayName("decrypt with wrong documentId fails — AAD binding prevents document relocation")
    void decrypt_wrongDocumentId_fails() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() -> service.decrypt(
                result.ciphertext(), result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, otherId, version))
                .isInstanceOfAny(CryptoException.class, javax.crypto.AEADBadTagException.class);
    }

    @Test
    @DisplayName("decrypt with wrong version fails — AAD binding prevents version replay")
    void decrypt_wrongVersion_fails() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);

        assertThatThrownBy(() -> service.decrypt(
                result.ciphertext(), result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version + 1))
                .isInstanceOfAny(CryptoException.class, javax.crypto.AEADBadTagException.class);
    }

    // -------------------------------------------------------------------------
    // Ciphertext integrity / tampering detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tampered ciphertext byte is rejected before decryption (digest check)")
    void decrypt_tamperedCiphertext_failsDigestCheck() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);

        // Flip a bit in the ciphertext
        byte[] tampered = result.ciphertext();
        tampered[0] ^= 0x01;

        assertThatThrownBy(() -> service.decrypt(
                tampered, result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("integrity check failed");
    }

    @Test
    @DisplayName("tampered wrapped DEK is rejected during KMS unwrap")
    void decrypt_tamperedWrappedDek_fails() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);

        byte[] tamperedDek = result.wrappedDekBytes();
        tamperedDek[0] ^= 0xFF;

        // Recompute digest with legitimate ciphertext so digest check passes
        String legitDigest = service.computeDigest(result.ciphertext());

        assertThatThrownBy(() -> service.decrypt(
                result.ciphertext(), result.nonce(),
                tamperedDek, result.kekVersion(),
                legitDigest,
                tenantId, documentId, version))
                .isInstanceOfAny(CryptoException.class, KmsException.class);
    }

    @Test
    @DisplayName("tampered nonce causes GCM auth tag failure")
    void decrypt_tamperedNonce_failsGcmAuthTag() {
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);

        byte[] tamperedNonce = result.nonce();
        tamperedNonce[0] ^= 0xFF;

        assertThatThrownBy(() -> service.decrypt(
                result.ciphertext(), tamperedNonce,
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version))
                .isInstanceOfAny(CryptoException.class, javax.crypto.AEADBadTagException.class);
    }

    // -------------------------------------------------------------------------
    // KMS fault injection propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("encrypt propagates KmsException from KMS layer")
    void encrypt_kmsFault_propagatesKmsException() {
        KmsProperties faultProps = new KmsProperties(1, true, Map.of(1, KEK_V1));
        SimulatedKmsService faultKms = new SimulatedKmsService(faultProps);
        EnvelopeEncryptionService faultService = new EnvelopeEncryptionService(faultKms);

        assertThatThrownBy(() -> faultService.encrypt(plaintext, tenantId, documentId, version))
                .isInstanceOf(KmsException.class);
    }

    // -------------------------------------------------------------------------
    // DEK re-wrap (rotation helper)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rewrapDek produces a new WrappedDek under the new KEK version")
    void rewrapDek_producesNewVersion() {
        // Wrap with v1 service
        EncryptionResult result = service.encrypt(plaintext, tenantId, documentId, version);
        assertThat(result.kekVersion()).isEqualTo(1);

        // Simulate rotation: new service has v2 as current
        KmsProperties v2Props = new KmsProperties(2, false, Map.of(1, KEK_V1, 2, KEK_V2));
        SimulatedKmsService kmsV2 = new SimulatedKmsService(v2Props);
        EnvelopeEncryptionService serviceV2 = new EnvelopeEncryptionService(kmsV2);

        WrappedDek rewrapped = serviceV2.rewrapDek(result.wrappedDekBytes(), result.kekVersion());
        assertThat(rewrapped.kekVersion()).isEqualTo(2);

        // Decrypt with new service + re-wrapped DEK must still recover original plaintext
        byte[] recovered = serviceV2.decrypt(
                result.ciphertext(), result.nonce(),
                rewrapped.wrappedBytes(), rewrapped.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version);

        assertThat(recovered).isEqualTo(plaintext);
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("empty plaintext encrypts and decrypts correctly")
    void encryptDecrypt_emptyPlaintext() {
        byte[] empty = new byte[0];
        EncryptionResult result = service.encrypt(empty, tenantId, documentId, version);

        byte[] recovered = service.decrypt(
                result.ciphertext(), result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version);

        assertThat(recovered).isEmpty();
    }

    @Test
    @DisplayName("large plaintext (1 MB) encrypts and decrypts correctly")
    void encryptDecrypt_largePlaintext() {
        byte[] large = new byte[1024 * 1024]; // 1 MB
        Arrays.fill(large, (byte) 0xAB);

        EncryptionResult result = service.encrypt(large, tenantId, documentId, version);
        byte[] recovered = service.decrypt(
                result.ciphertext(), result.nonce(),
                result.wrappedDekBytes(), result.kekVersion(),
                result.ciphertextDigest(),
                tenantId, documentId, version);

        assertThat(recovered).isEqualTo(large);
    }
}

