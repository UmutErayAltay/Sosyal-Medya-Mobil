package com.umuterayaltay.sosyal.nativeapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Projede İLK `MessageDigest` kullanımı — akışlı (dosyayı belleğe tam
 * yüklemeden) SHA-256, güncelleme kimliği doğrulaması için. */
suspend fun File.sha256Hex(): String = withContext(Dispatchers.IO) {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}

fun ByteArray.sha256Hex(): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(this).joinToString("") { "%02x".format(it) }
}
