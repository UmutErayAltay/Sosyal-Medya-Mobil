// release.ps1'in KALICI yayın-öncesi doğrulama adımı — bkz. delta güncelleme
// planı. Cihazın kullanacağı BİREBİR native yol (ZSTD_DCtx_refPrefix, JNA
// ile) ile bir yamayı açıp beklenen SHA-256 ile karşılaştırır. Bu, `zstd.exe
// -d --patch-from=...` ile CLI'nin KENDİ açması YETERLİ değil (o zstd-jni'nin
// APK'ya gömdüğü .so'yu hiç kullanmıyor) — asıl amaç cihazın gerçekten
// açabileceğini kanıtlamak. BAŞARISIZ olursa release.ps1 HİÇBİR ŞEY
// yayınlamamalı.
//
// Kullanım: java --class-path zstd-jni-<ver>.jar;jna-<ver>.jar PatchVerify.java
//           <zstdNativeLibPath> <baseApk> <patchFile> <expectedNewSha256Hex> <windowLog>
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.file.Files;
import java.security.MessageDigest;

public class PatchVerify {

    public interface Zstd extends Library {
        Pointer ZSTD_createDCtx();
        long ZSTD_freeDCtx(Pointer d);
        long ZSTD_DCtx_setParameter(Pointer dctx, int param, int value);
        long ZSTD_DCtx_refPrefix(Pointer dctx, Pointer prefix, long prefixSize);
        long ZSTD_decompressDCtx(Pointer dctx, byte[] dst, long dstCapacity, byte[] src, long srcSize);
        int ZSTD_isError(long code);
        String ZSTD_getErrorName(long code);
    }

    static final int ZSTD_d_windowLogMax = 100;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchVerify <nativeLibPath> <baseApk> <patchFile> <expectedNewSha256Hex> <windowLog> [expectedNewSize]");
            System.exit(2);
        }
        String nativeLibPath = args[0];
        String baseApkPath = args[1];
        String patchPath = args[2];
        String expectedSha = args[3].toLowerCase();
        int windowLog = Integer.parseInt(args[4]);
        int expectedSize = args.length > 5 ? Integer.parseInt(args[5]) : -1;

        Zstd z = Native.load(nativeLibPath, Zstd.class);

        byte[] baseBytes = Files.readAllBytes(java.nio.file.Path.of(baseApkPath));
        byte[] patchBytes = Files.readAllBytes(java.nio.file.Path.of(patchPath));
        int outSize = expectedSize > 0 ? expectedSize : baseBytes.length * 4; // kaba üst sınır, gerekirse büyütülür

        Memory prefixMem = new Memory(baseBytes.length);
        prefixMem.write(0, baseBytes, 0, baseBytes.length);

        Pointer dctx = z.ZSTD_createDCtx();
        check(z, z.ZSTD_DCtx_setParameter(dctx, ZSTD_d_windowLogMax, windowLog), "setParameter");
        check(z, z.ZSTD_DCtx_refPrefix(dctx, prefixMem, baseBytes.length), "refPrefix");

        byte[] out = new byte[outSize];
        long written = z.ZSTD_decompressDCtx(dctx, out, out.length, patchBytes, patchBytes.length);
        check(z, written, "decompressDCtx");
        z.ZSTD_freeDCtx(dctx);
        prefixMem.close();

        byte[] actual = java.util.Arrays.copyOf(out, (int) written);
        String actualSha = sha256Hex(actual);

        boolean ok = actualSha.equals(expectedSha);
        System.out.println("written=" + written + " expectedSha=" + expectedSha + " actualSha=" + actualSha + " OK=" + ok);
        if (!ok) {
            System.exit(1);
        }
    }

    static void check(Zstd z, long code, String where) {
        if (z.ZSTD_isError(code) != 0) {
            throw new RuntimeException(where + " failed: " + z.ZSTD_getErrorName(code));
        }
    }

    static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
