package jp.dharmainsight.shizukacamera;

final class GestureMath {
    private GestureMath() {}
    static float dragZoom(float initial, float distanceDp, float maximum) {
        return CameraMath.clamp((float) (initial * Math.pow(2, distanceDp / 120f)), 1, maximum);
    }
    static float sliderZoom(int progress, float maximum) {
        return (float) Math.pow(Math.max(1, maximum), CameraMath.clamp(progress, 0, 1000) / 1000f);
    }
    static int sliderProgress(float zoom, float maximum) {
        return maximum <= 1 ? 0 : Math.round((float) (Math.log(CameraMath.clamp(zoom, 1, maximum)) / Math.log(maximum)) * 1000);
    }
}
