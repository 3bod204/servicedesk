package com.tcc.servicedesk.ticket.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileContentValidatorTest {

    private final FileContentValidator validator = new FileContentValidator();

    @Test
    void detectsPdfBySignature() {
        byte[] content = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.contentType()).isEqualTo("application/pdf");
        assertThat(detected.category()).isEqualTo("pdf");
    }

    @Test
    void detectsPngBySignature() {
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.contentType()).isEqualTo("image/png");
        assertThat(detected.category()).isEqualTo("png");
    }

    @Test
    void detectsJpegBySignature() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.contentType()).isEqualTo("image/jpeg");
        assertThat(detected.category()).isEqualTo("jpg");
    }

    @Test
    void detectsZipBySignature() {
        byte[] content = {0x50, 0x4B, 0x03, 0x04};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.contentType()).isEqualTo("application/zip");
        assertThat(detected.category()).isEqualTo("zip");
    }

    @Test
    void detectsPlainTextWhenNoBinarySignatureMatches() {
        byte[] content = "hello world\nline two\r\n".getBytes();

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.contentType()).isEqualTo("text/plain");
        assertThat(detected.category()).isEqualTo("text");
    }

    @Test
    void returnsNullForUnrecognizedBinaryContent() {
        byte[] content = {0x00, 0x01, 0x02, 0x7F, (byte) 0xFE, 0x03};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNull();
    }

    @Test
    void emptyContentIsVacuouslyDetectedAsText() {
        // isPlainText's scan loop never runs on empty input, so it is treated as
        // "plain text" by default. AttachmentService guards against this by
        // rejecting empty uploads before content detection ever runs.
        FileContentValidator.DetectedType detected = validator.detect(new byte[0]);

        assertThat(detected).isNotNull();
        assertThat(detected.category()).isEqualTo("text");
    }

    @Test
    void treatsUtf8ContinuationBytesAsText() {
        byte[] content = "café — naïve".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.category()).isEqualTo("text");
    }

    @Test
    void contentTooShortForPdfSignatureFallsBackToTextWhenPrintable() {
        byte[] content = {0x25, 0x50};

        FileContentValidator.DetectedType detected = validator.detect(content);

        assertThat(detected).isNotNull();
        assertThat(detected.category()).isEqualTo("text");
    }
}
