// Ported from user-supplied 26.0928 DEX; transforms retained for protocol compatibility.
package li.gkd.app.feature.vehicle.auto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public final class WbskTables {
    public static final Pattern q = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");

    
    public final int[] f701a;
    public final int[] b;
    public final int[] c;
    public final int[] d;
    public final int[] e;
    public final int[] f;
    public final int[] g;
    public final int[] h;
    public final int[] i;
    public final int[] j;
    public final int[] k;
    public final int[] l;
    public final int[] m;
    public final int[] n;
    public final int[] o;
    public final int[] p;

    public WbskTables(HashMap map) {
        this.f701a = a(map, "encInitXor");
        this.b = a(map, "encRoundXor");
        this.c = a(map, "encSbox");
        this.d = a(map, "encFinalXor");
        this.e = d(map, "encTe0");
        this.f = d(map, "encTe1");
        this.g = d(map, "encTe2");
        this.h = d(map, "encTe3");
        this.i = a(map, "decInitXor");
        this.j = a(map, "decRoundXor");
        this.k = a(map, "decInvSbox");
        this.l = a(map, "decFinalXor");
        this.m = d(map, "decTd0");
        this.n = d(map, "decTd1");
        this.o = d(map, "decTd2");
        this.p = d(map, "decTd3");
    }

    public static int[] a(HashMap map, String str) {
        byte[] bArrB = b(map, str);
        if (bArrB.length != 256) {
            StringBuilder sbO = new StringBuilder("WBSK table " + str + " size ");
            sbO.append(bArrB.length);
            throw new IllegalArgumentException(sbO.toString());
        }
        int[] iArr = new int[256];
        for (int i = 0; i < 256; i++) {
            iArr[i] = bArrB[i] & 255;
        }
        return iArr;
    }

    public static byte[] b(HashMap map, String str) {
        String str2 = (String) map.get(str);
        if (str2 == null || str2.isEmpty()) {
            throw new IllegalArgumentException("Missing WBSK table: ".concat(str));
        }
        return WbskTransform.b(str2);
    }

    public static WbskTables c(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(16384);
        byte[] bArr = new byte[8192];
        while (true) {
            int i = inputStream.read(bArr);
            if (i == -1) {
                break;
            }
            byteArrayOutputStream.write(bArr, 0, i);
        }
        String str = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        HashMap map = new HashMap();
        Matcher matcher = q.matcher(str);
        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2));
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("wbsk_tables.json empty");
        }
        return new WbskTables(map);
    }

    public static int[] d(HashMap map, String str) {
        byte[] bArrB = b(map, str);
        if (bArrB.length != 1024) {
            StringBuilder sbO = new StringBuilder("WBSK table " + str + " size ");
            sbO.append(bArrB.length);
            throw new IllegalArgumentException(sbO.toString());
        }
        int[] iArr = new int[256];
        for (int i = 0; i < 256; i++) {
            int i2 = i * 4;
            iArr[i] = ((bArrB[i2 + 3] & 255) << 24) | (bArrB[i2] & 255) | ((bArrB[i2 + 1] & 255) << 8) | ((bArrB[i2 + 2] & 255) << 16);
        }
        return iArr;
    }
}
