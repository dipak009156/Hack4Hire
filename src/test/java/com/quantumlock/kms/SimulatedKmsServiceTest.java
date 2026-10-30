package com.quantumlock.kms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SimulatedKmsService}.
 *
 * No Spring context is loaded — all tests run against a manually constructed
 * {@link KmsProperties} to stay fast and self-contained.
 */
class SimulatedKmsServiceTest {

    // Three 256-bit test KEKs (Base64-encoded 32-byte values)
    private static final String KEK_V1 = "u7x8Q4z+1vP3mN9kL2jH5gF8dE1cA4bZ7yX0wV3uT6s=";
    private static final String KEK_V2 = "p2O5r8U1y4A7d0G3j6M9p2S5v8Y1b4E7h0K3n6Q9t2W=";

    private SimulatedKmsService kms;

    @BeforeEach
    void setUp() {
        KmsProperties props = new KmsProperties(1, false, Map.of(1, KEK_V1, 2, KEK_V2));
        kms = new SimulatedKmsService(props);
    }

    // -------------------------------------------------------------------------
    // Basic wrap / unwrap round-trip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("wrap then unwrap returns original DEK bytes")
    void wrapUnwrap_roundTrip() {
        byte[] originalDek = randomAes256Key();

        WrappedDek wrapped = kms.wrapDek(originalDek);
        byte[] recovered = kms.unwrapDek(wrapped);

        assertThat(recovered).isEqualTo(originalDek);
    }

    @Test
    @DisplayName("wrapped DEK differs from plaintext DEK")
    void wrappedBytes_doNotEqualPlaintext() {
        byte[] dek = randomAes256Key();
        WrappedDek wrapped = kms.wrapDek(dek);

        assertThat(wrapped.wrappedBytes()).isNotEqualTo(dek);
    }

    @Test
    @DisplayName("wrapDek assigns current KEK version")
    void wrapDek_assignsCurrentVersion() {
        byte[] dek = randomAes256Key();
        WrappedDek wrapped = kms.wrapDek(dek);

        assertThat(wrapped.kekVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("currentKekVersion returns configured version")
    void currentKekVersion_returnsConfiguredValue() {
        assertThat(kms.currentKekVersion()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Multi-version unwrap (rotation scenario)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unwrap succeeds with older KEK version (rotation scenario)")
    void unwrap_olderKekVersion_succeeds() {
        // Wrap with v1 (the current version)
        byte[] dek = randomAes256Key();
        WrappedDek wrappedWithV1 = kms.wrapDek(dek);
        assertThat(wrappedWithV1.kekVersion()).isEqualTo(1);

        // Now simulate the service has been reconfigured to use v2 as current
        KmsProperties v2Props = new KmsProperties(2, false, Map.of(1, KEK_V1, 2, KEK_V2));
        SimulatedKmsService kmsV2 = new SimulatedKmsService(v2Props);

        // New wraps use v2
        WrappedDek wrappedWithV2 = kmsV2.wrapDek(dek);
        assertThat(wrappedWithV2.kekVersion()).isEqualTo(2);

        // Old v1-wrapped DEK must still unwrap correctly
        byte[] recoveredFromV1 = kmsV2.unwrapDek(wrappedWithV1);
        assertThat(recoveredFromV1).isEqualTo(dek);
    }

    // -------------------------------------------------------------------------
    // Fault injection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("wrapDek throws KmsException when fault is enabled")
    void wrapDek_faultEnabled_throwsKmsException() {
        KmsProperties faultProps = new KmsProperties(1, true, Map.of(1, KEK_V1));
        SimulatedKmsService faultKms = new SimulatedKmsService(faultProps);

        assertThatThrownBy(() -> faultKms.wrapDek(randomAes256Key()))
                .isInstanceOf(KmsException.class)
                .hasMessageContaining("fault injection");
    }

    @Test
    @DisplayName("unwrapDek throws KmsException when fault is enabled")
    void unwrapDek_faultEnabled_throwsKmsException() {
        // Wrap without fault first
        WrappedDek wrapped = kms.wrapDek(randomAes256Key());

        KmsProperties faultProps = new KmsProperties(1, true, Map.of(1, KEK_V1));
        SimulatedKmsService faultKms = new SimulatedKmsService(faultProps);

        assertThatThrownBy(() -> faultKms.unwrapDek(wrapped))
                .isInstanceOf(KmsException.class)
                .hasMessageContaining("fault injection");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unwrap with unknown KEK version throws KmsException")
    void unwrapDek_unknownVersion_throwsKmsException() {
        byte[] dek = randomAes256Key();
        // Manufacture a WrappedDek referencing a non-existent KEK version
        WrappedDek fakeWrapped = new WrappedDek(new byte[40], 99);

        assertThatThrownBy(() -> kms.unwrapDek(fakeWrapped))
                .isInstanceOf(KmsException.class)
                .hasMessageContaining("Unknown KEK version=99");
    }

    @Test
    @DisplayName("configuration with wrong key length throws IllegalStateException at startup")
    void startup_wrongKeyLength_throwsIllegalStateException() {
        // 16 bytes = 128 bits — must be rejected (we require 256 bits)
        String shortKeyBase64 = Base64.getEncoder().encodeToString(new byte[16]);
        KmsProperties badProps = new KmsProperties(1, false, Map.of(1, shortKeyBase64));

        assertThatThrownBy(() -> new SimulatedKmsService(badProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    @DisplayName("configuration with missing current version throws IllegalStateException at startup")
    void startup_missingCurrentVersion_throwsIllegalStateException() {
        // current-version=5 but only version 1 is defined
        KmsProperties badProps = new KmsProperties(5, false, Map.of(1, KEK_V1));

        assertThatThrownBy(() -> new SimulatedKmsService(badProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kms.current-version=5");
    }

    @Test
    @DisplayName("WrappedDek returns defensive copy of bytes")
    void wrappedDek_defensiveCopy() {
        byte[] wrappedBytes = new byte[40];
        WrappedDek wrapped = new WrappedDek(wrappedBytes, 1);

        // Mutating original should not affect the stored copy
        wrappedBytes[0] = (byte) 0xFF;
        assertThat(wrapped.wrappedBytes()[0]).isNotEqualTo((byte) 0xFF);
    }

    @Test
    @DisplayName("unwrapping tampered ciphertext throws KmsException")
    void unwrap_tamperedCiphertext_throwsKmsException() {
        byte[] dek = randomAes256Key();
        WrappedDek wrapped = kms.wrapDek(dek);
        byte[] tampered = wrapped.wrappedBytes();
        tampered[0] ^= 0xFF; // flip bits

        WrappedDek tamperedWrapped = new WrappedDek(tampered, wrapped.kekVersion());
        assertThatThrownBy(() -> kms.unwrapDek(tamperedWrapped))
                .isInstanceOf(KmsException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] randomAes256Key() {
        try {
            javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("AES");
            kg.init(256, new java.security.SecureRandom());
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

