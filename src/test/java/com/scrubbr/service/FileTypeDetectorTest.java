package com.scrubbr.service;

import com.scrubbr.model.FileKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsByMagicBytesNotExtension() {
        assertThat(detector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .isEqualTo(FileKind.JPEG);
        assertThat(detector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G'}))
                .isEqualTo(FileKind.PNG);
        assertThat(detector.detect(new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'}))
                .isEqualTo(FileKind.PDF);
    }

    @Test
    void unknownOrTooShortIsUnsupported() {
        assertThat(detector.detect(new byte[]{'h', 'i'})).isEqualTo(FileKind.UNSUPPORTED);
        assertThat(detector.detect(new byte[]{1, 2, 3, 4, 5})).isEqualTo(FileKind.UNSUPPORTED);
        assertThat(detector.detect(null)).isEqualTo(FileKind.UNSUPPORTED);
    }
}
