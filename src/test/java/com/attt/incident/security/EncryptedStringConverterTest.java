package com.attt.incident.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        String key = Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(converter, "base64Key", key);
    }

    @Test
    void roundTripPreservesUnicodePlaintextWithoutStoringIt() {
        String plaintext = "Sự cố rò rỉ dữ liệu — máy chủ lõi";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(ciphertext).doesNotContain("rò rỉ");
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void randomIvProducesDifferentCiphertextForSamePlaintext() {
        String first = converter.convertToDatabaseColumn("same incident details");
        String second = converter.convertToDatabaseColumn("same incident details");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tamperingIsDetectedByGcmAuthenticationTag() {
        byte[] encoded = Base64.getDecoder().decode(
                converter.convertToDatabaseColumn("evidence that must remain authentic"));
        encoded[encoded.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(encoded);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("giải mã");
    }

    @Test
    void nullAndEmptyValuesRemainUnchanged() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }
}
