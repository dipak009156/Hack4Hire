package com.quantumlock.kms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process KMS simulation backed by Base64-encoded AES-256 KEKs configured
 * in {@code application.yml} under {@code kms.keys}.
 *
 * <p>Wrapping uses the standard {@code AESWrap} (RFC 3394) algorithm via JCA,
 * satisfying ADR-002's requirement for JCA-only algorithms. Key material is
 * loaded once at startup and cached as {@link SecretKey} objects — raw byte
 * arrays are never stored in fields.
 *
 * <p>If {@code kms.fault-enabled=true} every operation throws {@link KmsException}
 * to allow resilience testing of callers.
 */
@Service
@EnableConfigurationProperties(KmsProperties.class)
public class SimulatedKmsService implements KmsService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedKmsService.class);
    private static final String WRAP_ALGORITHM = "AESWrap";
    private static final String KEY_ALGORITHM = "AES";

    private final KmsProperties props;
    /** Cache of pre-loaded KEKs keyed by version; avoids repeated Base64 decoding. */
    private final Map<Integer, SecretKey> kekCache = new ConcurrentHashMap<>();

    public SimulatedKmsService(KmsProperties props) {
        this.props = props;
        loadAndValidateKeys();
    }

    // -------------------------------------------------------------------------
    // KmsService implementation
    // -------------------------------------------------------------------------

    @Override
    public WrappedDek wrapDek(byte[] plaintextDek) {
        checkFault("wrapDek");
        int version = props.currentVersion();
        SecretKey kek = resolveKek(version);

        try {
            Cipher cipher = Cipher.getInstance(WRAP_ALGORITHM);
            cipher.init(Cipher.WRAP_MODE, kek);
            SecretKey dekKey = new SecretKeySpec(plaintextDek, KEY_ALGORITHM);
            byte[] wrapped = cipher.wrap(dekKey);
            log.debug("DEK wrapped with KEK version={}", version);
            return new WrappedDek(wrapped, version);
        } catch (Exception e) {
            throw new KmsException("Failed to wrap DEK with KEK version=" + version, e);
        }
    }

    @Override
    public byte[] unwrapDek(WrappedDek wrappedDek) {
        checkFault("unwrapDek");
        int version = wrappedDek.kekVersion();
        SecretKey kek = resolveKek(version);

        try {
            Cipher cipher = Cipher.getInstance(WRAP_ALGORITHM);
            cipher.init(Cipher.UNWRAP_MODE, kek);
            SecretKey unwrapped = (SecretKey) cipher.unwrap(wrappedDek.wrappedBytes(), KEY_ALGORITHM, Cipher.SECRET_KEY);
            byte[] plaintext = unwrapped.getEncoded();
            log.debug("DEK unwrapped with KEK version={}", version);
            return plaintext;
        } catch (Exception e) {
            throw new KmsException("Failed to unwrap DEK with KEK version=" + version, e);
        }
    }

    @Override
    public int currentKekVersion() {
        return props.currentVersion();
    }

    @Override
    public WrappedDek wrapDekWithVersion(byte[] plaintextDek, int kekVersion) {
        checkFault("wrapDekWithVersion");
        SecretKey kek = resolveKek(kekVersion);
        try {
            Cipher cipher = Cipher.getInstance(WRAP_ALGORITHM);
            cipher.init(Cipher.WRAP_MODE, kek);
            SecretKey dekKey = new SecretKeySpec(plaintextDek, KEY_ALGORITHM);
            byte[] wrapped = cipher.wrap(dekKey);
            log.debug("DEK wrapped with target KEK version={}", kekVersion);
            return new WrappedDek(wrapped, kekVersion);
        } catch (Exception e) {
            throw new KmsException("Failed to wrap DEK with KEK version=" + kekVersion, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void loadAndValidateKeys() {
        Map<Integer, String> rawKeys = props.keys();
        if (rawKeys == null || rawKeys.isEmpty()) {
            throw new IllegalStateException("KMS configuration error: no KEK keys defined under kms.keys");
        }
        if (!rawKeys.containsKey(props.currentVersion())) {
            throw new IllegalStateException(
                    "KMS configuration error: kms.current-version=" + props.currentVersion()
                    + " has no corresponding entry in kms.keys");
        }
        for (Map.Entry<Integer, String> entry : rawKeys.entrySet()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(entry.getValue());
                if (keyBytes.length != 32) {
                    throw new IllegalStateException(
                            "KMS configuration error: KEK version=" + entry.getKey()
                            + " must be 256 bits (32 bytes), got " + keyBytes.length);
                }
                kekCache.put(entry.getKey(), new SecretKeySpec(keyBytes, KEY_ALGORITHM));
                log.info("Loaded KEK version={}", entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "KMS configuration error: KEK version=" + entry.getKey()
                        + " is not valid Base64", e);
            }
        }
    }

    private SecretKey resolveKek(int version) {
        SecretKey kek = kekCache.get(version);
        if (kek == null) {
            throw new KmsException("Unknown KEK version=" + version
                    + ". Available versions: " + kekCache.keySet());
        }
        return kek;
    }

    private void checkFault(String operation) {
        if (props.faultEnabled()) {
            log.warn("KMS fault injection triggered for operation={}", operation);
            throw new KmsException("KMS fault injection is enabled — " + operation + " rejected");
        }
    }
}

