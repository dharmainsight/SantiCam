package jp.dharmainsight.shizukacamera;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** QR-only ZXing decoder; never uses the network or Android activities. */
final class QrScanner {
    private QrScanner() {}

    static String decode(QrFrameSampler.Frame frame) {
        if (frame == null || frame.width <= 0 || frame.height <= 0) return null;
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                frame.luminance, frame.width, frame.height,
                0, 0, frame.width, frame.height, false);
            return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
        } catch (ReaderException e) {
            return null; // Most frames do not contain a QR code.
        }
    }
}
