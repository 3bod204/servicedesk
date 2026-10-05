package com.tcc.servicedesk.ticket.attachment;

import org.springframework.stereotype.Component;

@Component
public class FileContentValidator {

    public record DetectedType(String contentType, String category) {}

    public DetectedType detect(byte[] content) {
        if (matches(content, 0x25, 0x50, 0x44, 0x46)) {
            return new DetectedType("application/pdf", "pdf");
        }
        if (matches(content, 0x89, 0x50, 0x4E, 0x47)) {
            return new DetectedType("image/png", "png");
        }
        if (matches(content, 0xFF, 0xD8, 0xFF)) {
            return new DetectedType("image/jpeg", "jpg");
        }
        if (matches(content, 0x50, 0x4B, 0x03, 0x04)) {
            return new DetectedType("application/zip", "zip");
        }
        if (isPlainText(content)) {
            return new DetectedType("text/plain", "text");
        }
        return null;
    }

    private boolean matches(byte[] content, int... signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private boolean isPlainText(byte[] content) {
        int sampleSize = Math.min(content.length, 8000);
        for (int i = 0; i < sampleSize; i++) {
            int b = content[i] & 0xFF;
            boolean printableAscii =
                    (b >= 0x20 && b <= 0x7E) || b == 0x09 || b == 0x0A || b == 0x0D;
            boolean utf8Continuation = b >= 0x80;
            if (!printableAscii && !utf8Continuation) {
                return false;
            }
        }
        return true;
    }
}
