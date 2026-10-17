package com.estebanmm13.pytra_api.covers.service;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes a source image and downscales it to a JPEG of the requested width, keeping the aspect
 * ratio (never upscaling). Dimensions are read from the header before decoding, so decompression
 * bombs are refused without allocating their pixels. Downscaling halves the image step by step
 * (bilinear) before a final bicubic pass: a single big step aliases badly on 600 -> 120 px.
 */
final class CoverThumbnailResizer {

    static final int MAX_SOURCE_DIMENSION = 4096;
    static final long MAX_SOURCE_PIXELS = 4096L * 4096L;
    private static final float JPEG_QUALITY = 0.85f;

    private CoverThumbnailResizer() {
    }

    static byte[] resizeToJpeg(byte[] source, int targetWidth) {
        try {
            return encodeJpeg(downscale(decode(source), targetWidth));
        } catch (IOException | RuntimeException e) {
            if (e instanceof SteamIntegrationException steamException) {
                throw steamException;
            }
            // Corrupt or unsupported image data (ImageIO may also throw unchecked exceptions).
            throw SteamIntegrationException.coverUnavailable();
        }
    }

    private static BufferedImage decode(byte[] source) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw SteamIntegrationException.coverUnavailable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw SteamIntegrationException.coverUnavailable();
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    static BufferedImage downscale(BufferedImage source, int targetWidth) {
        int width = source.getWidth();
        int height = source.getHeight();
        int finalWidth = Math.min(targetWidth, width);
        int finalHeight = Math.max(1, (int) Math.round((double) height * finalWidth / width));

        BufferedImage current = toRgb(source);
        while (width / 2 >= finalWidth * 2) {
            width /= 2;
            height = Math.max(1, height / 2);
            current = draw(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, finalWidth, finalHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** JPEG has no alpha: transparent pixels are flattened onto black. */
    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        return draw(source, source.getWidth(), source.getHeight(), RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
