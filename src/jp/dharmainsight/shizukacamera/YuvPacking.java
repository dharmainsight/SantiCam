package jp.dharmainsight.shizukacamera;

import java.nio.ByteBuffer;

final class YuvPacking {
    private YuvPacking() {}

    static byte[] nv21(int width, int height, int left, int top,
                       ByteBuffer y, int yRow, int yPixel,
                       ByteBuffer u, int uRow, int uPixel,
                       ByteBuffer v, int vRow, int vPixel) {
        if (width <= 0 || height <= 0 || ((width | height | left | top) & 1) != 0 || left < 0 || top < 0) {
            throw new IllegalArgumentException("An even, positive YUV crop is required");
        }
        byte[] out = new byte[Math.multiplyExact(width, height) * 3 / 2];
        int index = 0;
        ByteBuffer yy = y.duplicate();
        for (int row = 0; row < height; row++) {
            int start = y.position() + (row + top) * yRow + left * yPixel;
            if (yPixel == 1) {
                yy.position(start); yy.get(out, index, width); index += width;
            } else {
                for (int col = 0; col < width; col++) out[index++] = y.get(start + col * yPixel);
            }
        }
        for (int row = 0; row < height / 2; row++) {
            int uStart = u.position() + (row + top / 2) * uRow + left / 2 * uPixel;
            int vStart = v.position() + (row + top / 2) * vRow + left / 2 * vPixel;
            for (int col = 0; col < width / 2; col++) {
                out[index++] = v.get(vStart + col * vPixel);
                out[index++] = u.get(uStart + col * uPixel);
            }
        }
        return out;
    }
}
