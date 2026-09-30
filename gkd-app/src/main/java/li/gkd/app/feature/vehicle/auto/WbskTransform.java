// Ported from user-supplied 26.0928 DEX; transforms retained for protocol compatibility.
package li.gkd.app.feature.vehicle.auto;

public abstract class WbskTransform {

    
    public static final int[] f595a = {0, 8, 4, 12, 1, 9, 5, 13, 2, 10, 6, 14, 3, 11, 7, 15};
    public static final int[] b = {0, 4, 8, 12, 2, 6, 10, 14, 1, 5, 9, 13, 3, 7, 11, 15};
    public static final int[] c = new int[16];
    public static final int[] d;
    public static final int[] e;
    public static final int[] f;
    public static final int[] g;
    public static final int[] h;
    public static final int[] i;
    public static final int[] j;
    public static final int[] k;
    public static final char[] l;
    public static final int[] m;

    static {
        int i2 = 0;
        for (int i3 = 0; i3 < 16; i3++) {
            int[] iArr = c;
            int[] iArr2 = f595a;
            iArr[i3] = iArr2[iArr2[i3 ^ 8]];
        }
        d = new int[]{0, 5, 10, 15, 4, 9, 14, 3, 8, 13, 2, 7, 12, 1, 6, 11};
        e = new int[]{0, 13, 10, 7, 4, 1, 14, 11, 8, 5, 2, 15, 12, 9, 6, 3};
        f = new int[]{5, 9, 13, 1};
        g = new int[]{10, 14, 2, 6};
        h = new int[]{15, 3, 7, 11};
        i = new int[]{13, 1, 5, 9};
        j = new int[]{10, 14, 2, 6};
        k = new int[]{7, 11, 15, 3};
        l = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        m = new int[128];
        int i4 = 0;
        while (true) {
            int[] iArr3 = m;
            if (i4 >= iArr3.length) {
                break;
            }
            iArr3[i4] = 0;
            i4++;
        }
        while (true) {
            char[] cArr = l;
            if (i2 >= cArr.length) {
                break;
            }
            m[cArr[i2]] = i2;
            i2++;
        }
    }

    public static byte[] a(byte[] bArr) {
        int length = bArr.length % 16;
        int i2 = length != 0 ? 16 - length : 16;
        int[] iArr = c;
        int i3 = (iArr[i2 >> 4] << 4) | iArr[i2 & 15];
        int length2 = bArr.length + i2;
        byte[] bArr2 = new byte[length2];
        System.arraycopy(bArr, 0, bArr2, 0, bArr.length);
        for (int length3 = bArr.length; length3 < length2; length3++) {
            bArr2[length3] = (byte) i3;
        }
        return bArr2;
    }

    public static byte[] b(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        for (int i2 = 0; i2 < str.length(); i2++) {
            char cCharAt = str.charAt(i2);
            if (cCharAt != '\n' && cCharAt != '\r' && cCharAt != ' ' && cCharAt != '\t') {
                if (cCharAt == '-') {
                    cCharAt = '+';
                } else if (cCharAt == '_') {
                    cCharAt = '/';
                }
                sb.append(cCharAt);
            }
        }
        int length = sb.length() % 4;
        if (length != 0) {
            for (int i3 = 0; i3 < 4 - length; i3++) {
                sb.append('=');
            }
        }
        String string = sb.toString();
        int length2 = string.length();
        int i4 = (length2 < 1 || string.charAt(length2 + (-1)) != '=') ? 0 : 1;
        if (length2 >= 2 && string.charAt(length2 - 2) == '=') {
            i4++;
        }
        int i5 = ((length2 / 4) * 3) - i4;
        byte[] bArr = new byte[i5];
        int i6 = 0;
        for (int i7 = 0; i7 < length2; i7 += 4) {
            char cCharAt2 = string.charAt(i7);
            int[] iArr = m;
            int i8 = i7 + 2;
            int i9 = i7 + 3;
            int i10 = (iArr[cCharAt2] << 18) | (iArr[string.charAt(i7 + 1)] << 12) | ((string.charAt(i8) == '=' ? 0 : iArr[string.charAt(i8)]) << 6) | (string.charAt(i9) == '=' ? 0 : iArr[string.charAt(i9)]);
            if (i6 < i5) {
                bArr[i6] = (byte) ((i10 >> 16) & 255);
                i6++;
            }
            if (i6 < i5) {
                bArr[i6] = (byte) ((i10 >> 8) & 255);
                i6++;
            }
            if (i6 < i5) {
                bArr[i6] = (byte) (i10 & 255);
                i6++;
            }
        }
        return bArr;
    }

    public static String c(byte[] bArr) {
        char[] cArr;
        StringBuilder sb = new StringBuilder(((bArr.length + 2) / 3) * 4);
        int length = bArr.length;
        int i2 = 0;
        while (true) {
            int i3 = i2 + 3;
            cArr = l;
            if (i3 > length) {
                break;
            }
            int i4 = (bArr[i2 + 2] & 255) | ((bArr[i2] & 255) << 16) | ((bArr[i2 + 1] & 255) << 8);
            sb.append(cArr[(i4 >> 18) & 63]);
            sb.append(cArr[(i4 >> 12) & 63]);
            sb.append(cArr[(i4 >> 6) & 63]);
            sb.append(cArr[i4 & 63]);
            i2 = i3;
        }
        int i5 = length - i2;
        if (i5 == 1) {
            int i6 = (bArr[i2] & 255) << 16;
            sb.append(cArr[(i6 >> 18) & 63]);
            sb.append(cArr[(i6 >> 12) & 63]);
            sb.append("==");
        } else if (i5 == 2) {
            int i7 = ((bArr[i2 + 1] & 255) << 8) | ((bArr[i2] & 255) << 16);
            sb.append(cArr[(i7 >> 18) & 63]);
            sb.append(cArr[(i7 >> 12) & 63]);
            sb.append(cArr[(i7 >> 6) & 63]);
            sb.append("=");
        }
        return sb.toString();
    }

    public static byte[] d(byte[] bArr, byte[] bArr2) {
        byte[] bArr3 = new byte[bArr.length + bArr2.length];
        System.arraycopy(bArr, 0, bArr3, 0, bArr.length);
        System.arraycopy(bArr2, 0, bArr3, bArr.length, bArr2.length);
        return bArr3;
    }

    public static byte[] e(WbskTables mb3Var, byte[] bArr, int[] iArr, int i2, byte[] bArr2) {
        int i3;
        byte[] bArr3 = bArr;
        int[] iArr2 = mb3Var.j;
        int i4 = 16;
        int length = bArr3.length / 16;
        byte[] bArr4 = new byte[bArr3.length];
        int i5 = 0;
        byte[] bArr5 = bArr2;
        int i6 = 0;
        while (i6 < length) {
            byte[] bArr6 = new byte[i4];
            int i7 = i6 * 16;
            System.arraycopy(bArr3, i7, bArr6, i5, i4);
            int[] iArr3 = new int[i4];
            int[] iArr4 = new int[i4];
            int[] iArr5 = new int[i4];
            int i8 = i5;
            while (i8 < i4) {
                iArr3[i8] = k(bArr6[i8] & 255, iArr[i8], mb3Var.i);
                i8++;
                i4 = 16;
            }
            int i9 = 1;
            while (i9 < i2) {
                int i10 = 0;
                while (true) {
                    if (i10 >= 4) {
                        break;
                    }
                    int i11 = i10 * 4;
                    int i12 = mb3Var.m[iArr3[i11]];
                    iArr4[i11] = (i12 >>> 24) & 255;
                    iArr4[i11 + 1] = (i12 >>> 16) & 255;
                    iArr4[i11 + 2] = (i12 >>> 8) & 255;
                    iArr4[i11 + 3] = i12 & 255;
                    i10++;
                    i9 = i9;
                }
                int i13 = i9;
                int i14 = 0;
                for (i3 = 4; i14 < i3; i3 = 4) {
                    int i15 = mb3Var.n[iArr3[i[i14]]];
                    int i16 = i14 * 4;
                    iArr5[i16] = (i15 >>> 24) & 255;
                    iArr5[i16 + 1] = (i15 >>> 16) & 255;
                    iArr5[i16 + 2] = (i15 >>> 8) & 255;
                    iArr5[i16 + 3] = i15 & 255;
                    i14++;
                }
                for (int i17 = 0; i17 < 16; i17++) {
                    iArr4[i17] = k(iArr4[i17], iArr5[i17], iArr2);
                }
                for (int i18 = 0; i18 < 4; i18++) {
                    int i19 = mb3Var.o[iArr3[j[i18]]];
                    int i20 = i18 * 4;
                    iArr5[i20] = (i19 >>> 24) & 255;
                    iArr5[i20 + 1] = (i19 >>> 16) & 255;
                    iArr5[i20 + 2] = (i19 >>> 8) & 255;
                    iArr5[i20 + 3] = i19 & 255;
                }
                for (int i21 = 0; i21 < 16; i21++) {
                    iArr4[i21] = k(iArr4[i21], iArr5[i21], iArr2);
                }
                for (int i22 = 0; i22 < 4; i22++) {
                    int i23 = mb3Var.p[iArr3[k[i22]]];
                    int i24 = i22 * 4;
                    iArr5[i24] = (i23 >>> 24) & 255;
                    iArr5[i24 + 1] = (i23 >>> 16) & 255;
                    iArr5[i24 + 2] = (i23 >>> 8) & 255;
                    iArr5[i24 + 3] = i23 & 255;
                }
                for (int i25 = 0; i25 < 16; i25++) {
                    iArr4[i25] = k(iArr4[i25], iArr5[i25], iArr2);
                }
                int i26 = i13 * 16;
                int i27 = 0;
                for (int i28 = 16; i27 < i28; i28 = 16) {
                    iArr3[i27] = k(iArr4[i27], iArr[i26 + i27], iArr2);
                    i27++;
                }
                i9 = i13 + 1;
            }
            int i29 = 16;
            for (int i30 = 0; i30 < 16; i30++) {
                iArr4[i30] = mb3Var.k[iArr3[e[i30]]];
            }
            byte[] bArr7 = new byte[16];
            int i31 = i2 * 16;
            int i32 = 0;
            while (i32 < i29) {
                bArr7[i32] = (byte) k(iArr4[i32], iArr[i31 + i32], mb3Var.l);
                i32++;
                i29 = 16;
            }
            int i33 = i29;
            for (int i34 = 0; i34 < i33; i34++) {
                bArr4[i7 + i34] = (byte) ((bArr7[i34] & 255) ^ (bArr5[i34] & 255));
            }
            i6++;
            bArr3 = bArr;
            i4 = i33;
            bArr5 = bArr6;
            i5 = 0;
        }
        return bArr4;
    }

    public static byte[] f(WbskTables mb3Var, byte[] bArr, int[] iArr, int i2, byte[] bArr2) {
        int i3;
        int[] iArr2 = mb3Var.b;
        int i4 = 16;
        int length = bArr.length / 16;
        byte[] bArr3 = new byte[bArr.length];
        byte[] bArr4 = bArr2;
        int i5 = 0;
        while (i5 < length) {
            byte[] bArr5 = new byte[i4];
            for (int i6 = 0; i6 < i4; i6++) {
                bArr5[i6] = (byte) ((bArr[(i5 * 16) + i6] & 255) ^ (bArr4[i6] & 255));
            }
            int[] iArr3 = new int[i4];
            int[] iArr4 = new int[i4];
            int[] iArr5 = new int[i4];
            for (int i7 = 0; i7 < i4; i7++) {
                iArr3[i7] = k(bArr5[i7] & 255, iArr[i7], mb3Var.f701a);
            }
            int i8 = 1;
            while (i8 < i2) {
                int i9 = 0;
                while (true) {
                    if (i9 >= 4) {
                        break;
                    }
                    int i10 = i9 * 4;
                    int i11 = mb3Var.e[iArr3[i10]];
                    iArr4[i10] = (i11 >>> 24) & 255;
                    iArr4[i10 + 1] = (i11 >>> 16) & 255;
                    iArr4[i10 + 2] = (i11 >>> 8) & 255;
                    iArr4[i10 + 3] = i11 & 255;
                    i9++;
                }
                int i12 = 0;
                for (i3 = 4; i12 < i3; i3 = 4) {
                    int i13 = mb3Var.f[iArr3[f[i12]]];
                    int i14 = i12 * 4;
                    iArr5[i14] = (i13 >>> 24) & 255;
                    iArr5[i14 + 1] = (i13 >>> 16) & 255;
                    iArr5[i14 + 2] = (i13 >>> 8) & 255;
                    iArr5[i14 + 3] = i13 & 255;
                    i12++;
                }
                for (int i15 = 0; i15 < 16; i15++) {
                    iArr4[i15] = k(iArr4[i15], iArr5[i15], iArr2);
                }
                for (int i16 = 0; i16 < 4; i16++) {
                    int i17 = mb3Var.g[iArr3[g[i16]]];
                    int i18 = i16 * 4;
                    iArr5[i18] = (i17 >>> 24) & 255;
                    iArr5[i18 + 1] = (i17 >>> 16) & 255;
                    iArr5[i18 + 2] = (i17 >>> 8) & 255;
                    iArr5[i18 + 3] = i17 & 255;
                }
                for (int i19 = 0; i19 < 16; i19++) {
                    iArr4[i19] = k(iArr4[i19], iArr5[i19], iArr2);
                }
                for (int i20 = 0; i20 < 4; i20++) {
                    int i21 = mb3Var.h[iArr3[h[i20]]];
                    int i22 = i20 * 4;
                    iArr5[i22] = (i21 >>> 24) & 255;
                    iArr5[i22 + 1] = (i21 >>> 16) & 255;
                    iArr5[i22 + 2] = (i21 >>> 8) & 255;
                    iArr5[i22 + 3] = i21 & 255;
                }
                for (int i23 = 0; i23 < 16; i23++) {
                    iArr4[i23] = k(iArr4[i23], iArr5[i23], iArr2);
                }
                int i24 = i8 * 16;
                int i25 = 0;
                for (int i26 = 16; i25 < i26; i26 = 16) {
                    iArr3[i25] = k(iArr4[i25], iArr[i24 + i25], iArr2);
                    i25++;
                }
                i8++;
                i4 = 16;
            }
            int i27 = i4;
            for (int i28 = 0; i28 < i27; i28++) {
                iArr4[i28] = mb3Var.c[iArr3[d[i28]]];
            }
            bArr4 = new byte[i27];
            int i29 = i2 * 16;
            int i30 = 0;
            while (i30 < i27) {
                bArr4[i30] = (byte) k(iArr4[i30], iArr[i29 + i30], mb3Var.d);
                i30++;
                i27 = 16;
            }
            System.arraycopy(bArr4, 0, bArr3, i5 * 16, 16);
            i5++;
            i4 = 16;
        }
        return bArr3;
    }

    public static byte[] g(String str) {
        int length = str.length();
        byte[] bArr = new byte[length / 2];
        for (int i2 = 0; i2 < length; i2 += 2) {
            bArr[i2 / 2] = (byte) (Character.digit(str.charAt(i2 + 1), 16) + (Character.digit(str.charAt(i2), 16) << 4));
        }
        return bArr;
    }

    public static byte[] h(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        for (int i2 = 0; i2 < bArr.length; i2++) {
            byte b2 = bArr[i2];
            int[] iArr = b;
            bArr2[i2] = (byte) (iArr[b2 & 15] | (iArr[(b2 & 255) >> 4] << 4));
        }
        return bArr2;
    }

    public static byte[] i(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        for (int i2 = 0; i2 < bArr.length; i2++) {
            byte b2 = bArr[i2];
            int[] iArr = f595a;
            bArr2[i2] = (byte) (iArr[b2 & 15] | (iArr[(b2 & 255) >> 4] << 4));
        }
        return bArr2;
    }

    public static WbskRoundKey j(String str) {
        int i2;
        int length = str.length();
        int i3 = length / 2;
        int[] iArr = new int[i3];
        int i4 = 0;
        while (true) {
            if (i4 >= length) {
                break;
            }
            iArr[i4 / 2] = Character.digit(str.charAt(i4 + 1), 16) + (Character.digit(str.charAt(i4), 16) << 4);
            i4 += 2;
        }
        if (i3 < 5) {
            throw new IllegalArgumentException("WBC key blob too short");
        }
        int i5 = iArr[0] ^ iArr[3];
        int[] iArr2 = new int[i3 - 4];
        for (i2 = 4; i2 < i3; i2++) {
            iArr2[i2 - 4] = iArr[i2] ^ iArr[i2 % 3];
        }
        int i6 = 64;
        switch (i5) {
            case 0:
            case 1:
            case 4:
            case 5:
            case 10 :
            case 11:
            case 12:
            case 13 :
            case 22:
            case 23:
                i6 = 128;
                break;
            case 2 :
            case 3:
            case 8 :
            case 9:
            case 14:
            case 15:
            case 20:
            case 21:
                i6 = 192;
                break;
            case 6:
            case 7 :
            case 18:
            case 19:
                break;
            case 16:
            case 17:
                i6 = 256;
                break;
            default:
                throw new IllegalArgumentException("Unknown WBC mode: 0x" + Integer.toHexString(i5));
        }
        int i7 = (i6 >> 5) + 6;
        if (i5 == 6 || i5 == 7 || i5 != 18) {
        }
        WbskRoundKey c31Var = new WbskRoundKey();
        c31Var.f132a = iArr2;
        c31Var.b = i7;
        return c31Var;
    }

    public static int k(int i2, int i3, int[] iArr) {
        return ((iArr[(((i2 & 15) << 4) ^ (i3 & 15)) & 255] >> 4) & 15) | (iArr[(((i2 >> 4) << 4) ^ (i3 >> 4)) & 255] & 240);
    }

    public static byte[] l(byte[] bArr) {
        int i2;
        if (bArr.length != 0 && (i2 = bArr[bArr.length - 1] & 255) >= 1 && i2 <= 16) {
            for (int length = bArr.length - i2; length < bArr.length; length++) {
                if ((bArr[length] & 255) == i2) {
                }
            }
            int length2 = bArr.length - i2;
            byte[] bArr2 = new byte[length2];
            System.arraycopy(bArr, 0, bArr2, 0, length2);
            return bArr2;
        }
        return bArr;
    }

    public static byte[] m(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        for (int i2 = 0; i2 < bArr.length; i2++) {
            byte b2 = bArr[i2];
            int[] iArr = c;
            bArr2[i2] = (byte) (iArr[b2 & 15] | (iArr[(b2 & 255) >> 4] << 4));
        }
        return bArr2;
    }

    public static byte[] n(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        for (int i2 = 0; i2 < bArr.length; i2++) {
            byte b2 = bArr[i2];
            int[] iArr = f595a;
            bArr2[i2] = (byte) (iArr[iArr[b2 & 15]] | (iArr[iArr[(b2 & 255) >> 4]] << 4));
        }
        return bArr2;
    }
}
