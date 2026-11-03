package jp.dharmainsight.shizukacamera;
public final class GestureTest {
    static void near(float expected, float actual) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        near(2, GestureMath.dragZoom(1, 120, 8));
        near(1, GestureMath.dragZoom(2, -120, 8));
        near(1, GestureMath.dragZoom(1, -1000, 8));
        near(8, GestureMath.dragZoom(4, 1000, 8));
        near(4, GestureMath.dragZoom(4, 0, 8));
        near(1, GestureMath.sliderZoom(0, 8));
        near(8, GestureMath.sliderZoom(1000, 8));
        near(1, GestureMath.sliderZoom(500, 1));
        near(0, GestureMath.sliderProgress(1, 1));
        for (int p = 0; p <= 1000; p++) near(p, GestureMath.sliderProgress(GestureMath.sliderZoom(p, 8), 8));
        System.out.println("GestureTest: 1010 assertions passed");
    }
}
