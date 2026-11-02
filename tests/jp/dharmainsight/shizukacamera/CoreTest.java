package jp.dharmainsight.shizukacamera;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class CoreTest {
    private static int checks;
    private static void equal(byte[] actual, int... expected) {
        if (actual.length != expected.length) throw new AssertionError("Wrong output length");
        for (int i = 0; i < expected.length; i++) if ((actual[i] & 255) != expected[i]) throw new AssertionError("Wrong byte at " + i + ": " + (actual[i] & 255));
        checks++;
    }
    private static void check(boolean condition, String text) { if (!condition) throw new AssertionError(text); checks++; }
    public static void main(String[] args) {
        // Planar 4x2 frame with independent row padding; output must be Y followed by interleaved VU.
        ByteBuffer y = ByteBuffer.wrap(new byte[] {1,2,3,4,99,99,5,6,7,8});
        ByteBuffer u = ByteBuffer.wrap(new byte[] {11,12});
        ByteBuffer v = ByteBuffer.wrap(new byte[] {21,22});
        equal(YuvPacking.nv21(4,2,0,0,y,6,1,u,2,1,v,2,1),1,2,3,4,5,6,7,8,21,11,22,12);
        check(y.position() == 0 && u.position() == 0 && v.position() == 0, "Packing must not mutate plane positions");

        // Camera-style overlapping VU planes, pixel stride two and a short final row.
        ByteBuffer shared = ByteBuffer.wrap(new byte[] {21,11,22,12});
        ByteBuffer vv = shared.duplicate(); vv.limit(3);
        ByteBuffer uu = shared.duplicate(); uu.position(1);
        equal(YuvPacking.nv21(4,2,0,0,y,6,1,uu,4,2,vv,4,2),1,2,3,4,5,6,7,8,21,11,22,12);
        check(uu.position() == 1, "Plane base position must be preserved");

        // Non-zero crop on a padded 4x4 frame.
        ByteBuffer yy = ByteBuffer.wrap(new byte[] {1,2,3,4,99,99,5,6,7,8,99,99,9,10,11,12,99,99,13,14,15,16});
        equal(YuvPacking.nv21(2,2,2,2,yy,6,1,ByteBuffer.wrap(new byte[] {31,32,33,34}),2,1,ByteBuffer.wrap(new byte[] {41,42,43,44}),2,1),11,12,15,16,44,34);

        // Pixel stride two can also occur in a generic plane view.
        equal(YuvPacking.nv21(2,2,0,0,ByteBuffer.wrap(new byte[] {1,99,2,99,3,99,4}),4,2,ByteBuffer.wrap(new byte[] {7}),1,1,ByteBuffer.wrap(new byte[] {8}),1,1),1,2,3,4,8,7);
        boolean rejected = false;
        try { YuvPacking.nv21(3,2,0,0,y,6,1,u,2,1,v,2,1); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Odd YUV dimensions must be rejected");

        int[] device = {0,90,180,270,359};
        int[] backExpected = {90,180,270,0,90}, frontExpected = {270,180,90,0,270};
        for (int i=0;i<device.length;i++) {
            check(CameraMath.jpegRotation(90,device[i],false)==backExpected[i], "Back JPEG orientation");
            check(CameraMath.jpegRotation(270,device[i],true)==frontExpected[i], "Front JPEG orientation");
        }
        float[] center = CameraMath.sensorPoint(.5f,.5f,90,false);
        check(center[0]==.5f && center[1]==.5f,"Center focus mapping");
        float[] corner = CameraMath.sensorPoint(0,0,90,false);
        check(corner[0]==0 && corner[1]==1,"Back focus mapping");
        float[] front = CameraMath.sensorPoint(0,0,270,true);
        check(front[0]==1 && front[1]==1,"Mirrored front focus mapping");
        check(CameraMath.clamp(20,1,8)==8 && CameraMath.clamp(.2f,1,8)==1,"Zoom boundaries");
        for (int mask = 0; mask < 16; mask++) {
            boolean prior = (mask & 1) != 0, entry = (mask & 2) != 0;
            boolean secureAction = (mask & 4) != 0, locked = (mask & 8) != 0;
            String action = secureAction ? "android.media.action.STILL_IMAGE_CAMERA_SECURE" : "android.media.action.STILL_IMAGE_CAMERA";
            check(LaunchPolicy.restricted(prior,entry,action,locked) == (mask != 0), "Launch privacy matrix " + mask);
        }
        check(!LaunchPolicy.restricted(false,false,null,false), "Unlocked launcher works");
        check(LaunchPolicy.restricted(false,true,null,false), "Secure Activity cannot be downgraded");
        check(LaunchPolicy.restricted(true,false,null,false), "Unlock keeps session restricted");
        check(DiagnosticRules.verdict(false, true, false, false).contains("Manifest"), "Missing standard handler");
        check(DiagnosticRules.verdict(true, false, false, false).contains("ロック画面"), "Missing secure handler");
        check(DiagnosticRules.verdict(true, true, false, false).contains("起動テスト"), "Pending self test");
        check(DiagnosticRules.verdict(true, true, true, false).contains("OSの起動先制限"), "Working intent but no outside delivery");
        check(DiagnosticRules.verdict(true, true, true, true).contains("断定できません"), "Outside intent is not proof of button event");
        System.out.println("PASS: " + checks + " assertions (YUV layouts, crop, strides, orientations, focus, zoom, secure launch policy, diagnostic rules)");
    }
}
