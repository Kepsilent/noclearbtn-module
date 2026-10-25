import java.io.File;
import java.io.RandomAccessFile;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.SignatureAlgorithm;
import com.android.apksig.internal.apk.v2.V2SchemeSigner;
import com.android.apksig.util.DataSources;

public class GenFinal {
    public static void main(String[] a) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(a[0], "r");
        var zs = ApkUtils.findZipSections(DataSources.asDataSource(raf));
        long cd = zs.getZipCentralDirectoryOffset();
        long eocd = zs.getZipEndOfCentralDirectoryOffset();
        PrivateKey pk = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(java.nio.file.Files.readAllBytes(new File(a[1]).toPath())));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new java.io.FileInputStream(a[2]));
        ApkSigningBlockUtils.SignerConfig cfg = new ApkSigningBlockUtils.SignerConfig();
        cfg.privateKey = pk;
        cfg.certificates = Collections.singletonList(cert);
        cfg.signatureAlgorithms = Collections.singletonList(SignatureAlgorithm.ECDSA_WITH_SHA256);
        cfg.minSdkVersion = 24;
        cfg.maxSdkVersion = Integer.MAX_VALUE;
        byte[] all = java.nio.file.Files.readAllBytes(new File(a[0]).toPath());
        java.nio.ByteBuffer ec = java.nio.ByteBuffer.allocate((int)(all.length - eocd)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        ec.put(all, (int) eocd, (int)(all.length - eocd));
        ec.flip();
        com.android.apksig.internal.zip.ZipUtils.setZipEocdCentralDirectoryOffset(ec, cd);
        var res = V2SchemeSigner.generateApkSignatureSchemeV2Block(
            com.android.apksig.util.RunnablesExecutor.SINGLE_THREADED,
            DataSources.asDataSource(raf, 0, cd),
            DataSources.asDataSource(raf, cd, eocd - cd),
            new com.android.apksig.internal.util.ByteBufferDataSource(ec),
            Collections.singletonList(cfg), false, Collections.emptyList());
        byte[] content = res.signingSchemeBlock.getFirst();
        java.nio.file.Files.write(new File(a[3]).toPath(), content);
        System.out.println("content=" + content.length + " cd=" + cd + " eocd=" + eocd);
    }
}
