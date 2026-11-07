package jp.dharmainsight.shizukacamera;

import java.nio.ByteBuffer;

/** Compact copy of the camera's Y plane for low-cost, asynchronous QR reading. */
final class QrFrameSampler {
    private QrFrameSampler() {}

    static final class Frame {
        final byte[] luminance;
        final int width, height;
        Frame(byte[] luminance, int width, int height) {
            this.luminance = luminance; this.width = width; this.height = height;
        }
    }

    static Frame sample(ByteBuffer y, int rowStride, int pixelStride,
                        int left, int top, int width, int height) {
        if (y == null || rowStride <= 0 || pixelStride <= 0 ||
            left < 0 || top < 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid luminance crop or strides");
        // Limit decoding to ~1 MP even when the photo stream is 12 MP.
        int step = Math.max(1, (Math.max(width, height) + 959) / 960);
        int w = (width + step - 1) / step, h = (height + step - 1) / step;
        byte[] pixels = new byte[Math.multiplyExact(w, h)];
        ByteBuffer source = y.duplicate();
        int origin = y.position();
        for (int row = 0; row < h; row++) {
            int base = origin + (top + row * step) * rowStride + left * pixelStride;
            for (int col = 0; col < w; col++)
                pixels[row * w + col] = source.get(base + col * step * pixelStride);
        }
        return new Frame(pixels, w, h);
    }
}
