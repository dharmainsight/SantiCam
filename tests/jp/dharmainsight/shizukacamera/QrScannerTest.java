package jp.dharmainsight.shizukacamera;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.ByteBuffer;

public final class QrScannerTest {
    private static int count;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        count++;
    }
    public static void main(String[] args) throws Exception {
        String url = "https://example.org/scan?id=123";
        check(url.equals(QrLinkPolicy.accept(url)), "Valid HTTPS");
        check("http://example.org/x".equals(QrLinkPolicy.accept(" http://example.org/x ")), "HTTP + trim");
        for (String invalid : new String[] {
            "javascript:alert(1)", "file:///etc/passwd", "intent://scan", "https://",
            "https://user:password@example.org", "https://example.org/a b",
            "https://example.org/\nattack", "not a link", "https://example.org:wrong"
        }) check(QrLinkPolicy.accept(invalid) == null, "Reject " + invalid);
        check(QrLinkPolicy.accept(null) == null, "Reject null");

        int side = 256, stride = side + 8;
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, side, side);
        byte[] padded = new byte[stride * side];
        for (int y = 0; y < side; y++)
            for (int x = 0; x < side; x++)
                padded[y * stride + x] = (byte) (matrix.get(x, y) ? 0 : 255);
        QrFrameSampler.Frame frame = QrFrameSampler.sample(ByteBuffer.wrap(padded), stride, 1, 0, 0, side, side);
        check(frame.width == side && frame.height == side, "Sample geometry");
        check(url.equals(QrScanner.decode(frame)), "Decode QR from padded Y plane");

        byte[] absent = new byte[side * side];
        java.util.Arrays.fill(absent, (byte) 255);
        check(QrScanner.decode(new QrFrameSampler.Frame(absent, side, side)) == null, "No-code frame");
        byte[] pixelStride2 = {10,99,20,99,30,99,40};
        QrFrameSampler.Frame small = QrFrameSampler.sample(ByteBuffer.wrap(pixelStride2), 4, 2, 0, 0, 2, 2);
        check((small.luminance[0] & 255) == 10 && (small.luminance[1] & 255) == 20 &&
              (small.luminance[2] & 255) == 30 && (small.luminance[3] & 255) == 40, "Pixel stride");
        System.out.println("PASS: " + count + " QR/link assertions");
    }
}
