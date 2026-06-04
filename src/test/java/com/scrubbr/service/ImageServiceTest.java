package com.scrubbr.service;

import com.scrubbr.model.FileKind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageServiceTest {

    private final ImageService imageService = new ImageService();

    @Test
    void stripProducesAValidReadableImage() throws Exception {
        byte[] png = solidColourPng(32, 32);

        byte[] cleaned = imageService.strip(png, FileKind.PNG);

        BufferedImage reread = ImageIO.read(new ByteArrayInputStream(cleaned));
        assertThat(reread).isNotNull();
        assertThat(reread.getWidth()).isEqualTo(32);
        assertThat(reread.getHeight()).isEqualTo(32);
    }

    @Test
    void revealNeverThrowsOnImagesWithoutMetadata() {
        // A plain generated PNG has no EXIF; reveal should simply return little/nothing,
        // and must never blow up.
        byte[] png = solidColourPng(8, 8);
        assertThat(imageService.reveal(png)).isNotNull();
    }

    private byte[] solidColourPng(int w, int h) {
        try {
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, w, h);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
