// Ported from user-supplied 26.0928 DEX; transforms retained for protocol compatibility.
package li.gkd.app.feature.vehicle.auto;

import java.nio.charset.StandardCharsets;



public final class WbskCodec {

    
    public volatile WbskTables f653a;

    public final String a(String str) {
        WbskTables mb3Var = this.f653a;
        if (mb3Var == null) {
            throw new IllegalStateException("WBSK tables not loaded");
        }
        int[] iArr = WbskTransform.f595a;
        byte[] bArrB = WbskTransform.b(str.trim());
        byte[] bArrD = WbskTransform.d(WbskTransform.i(bArrB), new byte[256]);
        WbskRoundKey c31VarJ = WbskTransform.j("72ca0163b22e2973656a67ac1ae1490a61133824a0cd235bcfcd6032dba79d3ca51b1c4d1b03068566ff084645dcc9e6e8a28b39c71e72dec3fe4074109a84f5564d3f43f4854fb634bf633dabe218a5b73470dff70b07161b76c74d92bffa15bcb7fa4fc448a0fe83b62c9dd97f36d1d1d7613028041bb1dd328397bbf8c8bf6f81321f5e2d4982761a375bded52a1de198169839fabad771bc677b57f806c1ca385f43627e9a5081c43d7c9d9fa86e7e78cc0a8050a2420a76c842abbe93eb38f2487bdf93087cd24097a16539da2f86feb693432daf8f0618cbf97ffea3a762b7b91050f8634a8d3ceb25ea7d2b3264a77337");
        byte[] bArrE = WbskTransform.e(mb3Var, bArrD, c31VarJ.f132a, c31VarJ.b, WbskTransform.g("54cc5558c551c155c4c05cc4c158ca58"));
        byte[] bArr = new byte[bArrB.length];
        System.arraycopy(bArrE, 0, bArr, 0, bArrB.length);
        byte[] bArrL = WbskTransform.l(WbskTransform.h(bArr));
        int length = bArrL.length - 16;
        byte[] bArr2 = new byte[length];
        System.arraycopy(bArrL, 0, bArr2, 0, length);
        String str2 = new String(bArr2, StandardCharsets.ISO_8859_1);
        byte[] bArr3 = new byte[16];
        System.arraycopy(bArrE, length, bArr3, 0, 16);
        byte[] bArrB2 = WbskTransform.b(str2);
        byte[] bArrD2 = WbskTransform.d(WbskTransform.i(bArrB2), new byte[256]);
        WbskRoundKey c31VarJ2 = WbskTransform.j("71ca0160b689febe1c11e07bc8f8cec81decf71b6c3e0be299a35211888c2fb177958e57a6e971d0a874ecb50991786faf3a34b178f13a668bd14b81a82d3f799f6f0c8bd002406c8b6fdd54b3bb30c0c7d27c906dba87decde28717a0874abacf41755646b4a2c06854615ab00ae53136cbea3302b047659e7a42f792a7369fc130d8ffdc114a7a2cf2fa669b9b337905ff58fe3cc40b9b1edf37ebe50d36b3416abbd32837895b8ea1f22b9eab35efd791d9153208630297b8b953a9ca33265854c33959979b9eb1d049326986851170f4b51d151f43a30c6298a8c03503477336b2000c49746181ca30eabded6d3088b7f615");
        byte[] bArrE2 = WbskTransform.e(mb3Var, bArrD2, c31VarJ2.f132a, c31VarJ2.b, bArr3);
        byte[] bArr4 = new byte[bArrB2.length];
        System.arraycopy(bArrE2, 0, bArr4, 0, bArrB2.length);
        return new String(WbskTransform.l(WbskTransform.h(bArr4)), StandardCharsets.UTF_8);
    }

    public final String b(String str) {
        WbskTables mb3Var = this.f653a;
        if (mb3Var == null) {
            throw new IllegalStateException("WBSK tables not loaded");
        }
        int[] iArr = WbskTransform.f595a;
        byte[] bArrA = WbskTransform.a(WbskTransform.m(str.getBytes(StandardCharsets.UTF_8)));
        WbskRoundKey c31VarJ = WbskTransform.j("9fca018f72712b15e23c275ea5e06a92d8b98404cf0bf960955596ff47dd2adf8f9e0c3ca1363e8be88cb6fced211933e5c3484c20c7bc3ae1eb8541027fef4a20b2f302d93582f7f4349fef05c16d389956ae9f2a7aea278fd5232229e38caca017ffbaf5138d2cadbca917b4694fb2882a64809c095387b7353608ca3a17913e5863770465986995c684ea7db01e0c35c69fd169a8e14ec5123beb5b8dad6e1c5198f34ed1c44d5f9b15035673df5953e5e42351f58052c1483fa4cf93c646a396081355a46d5a7e0ce30d54049802829cd78c1a77c7db8f74acb244b73c5147f161ee25bd702112cec97c339a6b3314527d12");
        byte[] bArrG = WbskTransform.g("a8bb9ab895ba95363a81b1949da68184");
        byte[] bArrA2 = WbskTransform.a(WbskTransform.m(WbskTransform.d(WbskTransform.c(WbskTransform.n(WbskTransform.f(mb3Var, bArrA, c31VarJ.f132a, c31VarJ.b, bArrG))).getBytes(StandardCharsets.ISO_8859_1), WbskTransform.n(bArrG))));
        WbskRoundKey c31VarJ2 = WbskTransform.j("4dca015d9f0488cdea45e890de3b9c4d16c9f82e1082e295c8312d34da7214b805bdec33d8473ab04c84a51eebee4fd5efee21ed403a159a083dbb2854c92719d8f24dd3002ce675c4b930fd5f410ebe56d9594532f9c109b7f2dc58eebd83a83cc948fd3dc0b696add8b06d19efa7c8c04d17f60d144d943e21ef4add5af566ef14241de9c3bb03cf9b9d3c5d042caa1fcdf222e02ba7cf577cc70375d0b4e7e3340278e56ddee1a180451b3a04f25fe34f0d1f05ec426b0de801e7d7382ecf2c3ab7be923c2d5ff0c33eaa4c45c71b258045f68bd7ad0f594ff86785611f67f30da78dfa9b427f04d625a2c61e2db62e1fe7d4");
        return WbskTransform.c(WbskTransform.n(WbskTransform.f(mb3Var, bArrA2, c31VarJ2.f132a, c31VarJ2.b, WbskTransform.g("91339992399838993130933138923692"))));
    }
}
