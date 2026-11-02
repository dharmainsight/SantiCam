package jp.dharmainsight.shizukacamera;

final class CameraMath {
    private CameraMath() {}

    static int jpegRotation(int sensor, int deviceDegrees, boolean front) {
        int rounded = ((Math.max(0, deviceDegrees) + 45) / 90 * 90) % 360;
        return (sensor + (front ? -rounded : rounded) + 360) % 360;
    }

    // Undo the mirrored, rotated preview to obtain normalized sensor coordinates.
    static float[] sensorPoint(float x, float y, int rotation, boolean mirrored) {
        x = clamp(x, 0, 1); y = clamp(y, 0, 1);
        if (mirrored) x = 1 - x;
        switch (rotation) {
            case 90: return new float[] {y, 1 - x};
            case 180: return new float[] {1 - x, 1 - y};
            case 270: return new float[] {1 - y, x};
            default: return new float[] {x, y};
        }
    }

    static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
