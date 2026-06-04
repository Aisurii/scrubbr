package com.scrubbr.service;

import com.scrubbr.model.FileKind;
import org.springframework.stereotype.Service;

/** Detects a file's real type from its magic bytes, not the (spoofable) filename. */
@Service
public class FileTypeDetector {

    public FileKind detect(byte[] data) {
        if (data == null || data.length < 4) {
            return FileKind.UNSUPPORTED;
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return FileKind.JPEG;
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47)) {
            return FileKind.PNG;
        }
        if (startsWith(data, 0x25, 0x50, 0x44, 0x46)) {
            return FileKind.PDF;
        }
        return FileKind.UNSUPPORTED;
    }

    private boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
