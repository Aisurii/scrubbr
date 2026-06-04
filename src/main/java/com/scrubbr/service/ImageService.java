package com.scrubbr.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;
import com.scrubbr.model.FileKind;
import com.scrubbr.model.MetadataEntry;
import com.scrubbr.model.Severity;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads metadata from JPEG/PNG images and returns a metadata-free copy.
 * Stripping works by re-encoding the decoded pixels, which carry no EXIF/IPTC/XMP.
 */
@Service
public class ImageService {

    private static final float JPEG_QUALITY = 0.92f;

    public List<MetadataEntry> reveal(byte[] data) {
        List<MetadataEntry> entries = new ArrayList<>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));

            for (GpsDirectory gps : metadata.getDirectoriesOfType(GpsDirectory.class)) {
                var location = gps.getGeoLocation();
                if (location != null && !location.isZero()) {
                    entries.add(new MetadataEntry("GPS", "Capture location",
                            String.format(Locale.US, "%.6f, %.6f (lat, lon)",
                                    location.getLatitude(), location.getLongitude()),
                            Severity.HIGH));
                }
            }

            for (Directory directory : metadata.getDirectories()) {
                if (directory instanceof GpsDirectory) {
                    continue;
                }
                for (Tag tag : directory.getTags()) {
                    String description = tag.getDescription();
                    if (description != null && !description.isBlank()) {
                        entries.add(new MetadataEntry(directory.getName(), tag.getTagName(),
                                description, classify(tag.getTagName())));
                    }
                }
            }
        } catch (Exception e) {
            entries.add(new MetadataEntry("Notice", "Metadata",
                    "Could not parse embedded metadata (" + e.getMessage() + ")", Severity.LOW));
        }
        return entries;
    }

    public byte[] strip(byte[] data, FileKind kind) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Unsupported or corrupt image data");
        }
        return kind == FileKind.PNG ? encodePng(image) : encodeJpeg(flattenAlpha(image));
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG encoder available");
        }
        return out.toByteArray();
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** JPEG has no alpha channel, so flatten transparency onto white first. */
    private BufferedImage flattenAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    private Severity classify(String tagName) {
        String name = tagName.toLowerCase(Locale.US);
        if (name.contains("date") || name.contains("time") || name.contains("gps")
                || name.contains("serial") || name.contains("owner") || name.contains("artist")) {
            return Severity.HIGH;
        }
        if (name.contains("make") || name.contains("model") || name.contains("software")
                || name.contains("lens") || name.contains("author")) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}
