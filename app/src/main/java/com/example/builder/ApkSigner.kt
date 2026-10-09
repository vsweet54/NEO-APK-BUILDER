package com.example.builder

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkSigner {

    private const val KEYSTORE_ASSET = "neo_runner.keystore"
    private const val KEYSTORE_PASS = "neobuilder123"
    private const val KEY_ALIAS = "neokey"

    fun signApk(
        context: Context,
        inputEntries: Map<String, ByteArray>,
        outputFile: File
    ) {
        val (privateKey, cert) = loadOrGenerateCredentials(context)

        val md = MessageDigest.getInstance("SHA-256")

        // 1. Generate MANIFEST.MF
        val manifestSb = StringBuilder()
        manifestSb.append("Manifest-Version: 1.0\r\n")
        manifestSb.append("Created-By: 1.0 (NEO APK BUILDER)\r\n\r\n")

        val entryManifestDigests = LinkedHashMap<String, String>()

        for ((name, data) in inputEntries) {
            if (name.startsWith("META-INF/")) continue
            val digest = Base64.getEncoder().encodeToString(md.digest(data))
            val section = "Name: $name\r\nSHA-256-Digest: $digest\r\n\r\n"
            manifestSb.append(section)
            val sectionDigest = Base64.getEncoder().encodeToString(md.digest(section.toByteArray(Charsets.UTF_8)))
            entryManifestDigests[name] = sectionDigest
        }

        val manifestBytes = manifestSb.toString().toByteArray(Charsets.UTF_8)
        val manifestMainDigest = Base64.getEncoder().encodeToString(md.digest(manifestBytes))

        // 2. Generate CERT.SF
        val certSfSb = StringBuilder()
        certSfSb.append("Signature-Version: 1.0\r\n")
        certSfSb.append("Created-By: 1.0 (NEO APK BUILDER)\r\n")
        certSfSb.append("SHA-256-Digest-Manifest: $manifestMainDigest\r\n\r\n")

        for ((name, sectionDigest) in entryManifestDigests) {
            certSfSb.append("Name: $name\r\n")
            certSfSb.append("SHA-256-Digest: $sectionDigest\r\n\r\n")
        }

        val certSfBytes = certSfSb.toString().toByteArray(Charsets.UTF_8)

        // 3. Generate CERT.RSA using Bouncy Castle
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val digestCalcProvider = JcaDigestCalculatorProviderBuilder().build()
        val signerInfoGen = JcaSignerInfoGeneratorBuilder(digestCalcProvider).build(contentSigner, cert)

        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(signerInfoGen)
        gen.addCertificate(JcaX509CertificateHolder(cert))

        val cmsData = CMSProcessableByteArray(certSfBytes)
        val signedData = gen.generate(cmsData, false) // detached signature
        val certRsaBytes = signedData.encoded

        // 4. Write final output zip
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // Write standard entries first
                for ((name, data) in inputEntries) {
                    if (name.startsWith("META-INF/")) continue
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(data)
                    zos.closeEntry()
                }

                // Write META-INF entries
                val mfEntry = ZipEntry("META-INF/MANIFEST.MF")
                zos.putNextEntry(mfEntry)
                zos.write(manifestBytes)
                zos.closeEntry()

                val sfEntry = ZipEntry("META-INF/CERT.SF")
                zos.putNextEntry(sfEntry)
                zos.write(certSfBytes)
                zos.closeEntry()

                val rsaEntry = ZipEntry("META-INF/CERT.RSA")
                zos.putNextEntry(rsaEntry)
                zos.write(certRsaBytes)
                zos.closeEntry()
            }
        }
    }

    private fun loadOrGenerateCredentials(context: Context): Pair<PrivateKey, X509Certificate> {
        val typesToTry = listOf("PKCS12", "BKS", "JKS", KeyStore.getDefaultType())
        for (type in typesToTry) {
            try {
                val ks = KeyStore.getInstance(type)
                context.assets.open(KEYSTORE_ASSET).use { isStream ->
                    ks.load(isStream, KEYSTORE_PASS.toCharArray())
                }
                val key = ks.getKey(KEY_ALIAS, KEYSTORE_PASS.toCharArray()) as? PrivateKey
                val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate
                if (key != null && cert != null) {
                    return Pair(key, cert)
                }
            } catch (ignored: Exception) {}
        }

        // Reliable fallback: Generate self-signed RSA keypair dynamically
        return generateDynamicCredentials()
    }

    private fun generateDynamicCredentials(): Pair<PrivateKey, X509Certificate> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()

        val subject = X500Name("CN=NEO APK BUILDER, O=Neo Technologies, C=ID")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 24 * 3600 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 30L * 365 * 24 * 3600 * 1000L) // 30 years

        val certBuilder = JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(certHolder)

        return Pair(keyPair.private, cert)
    }
}
