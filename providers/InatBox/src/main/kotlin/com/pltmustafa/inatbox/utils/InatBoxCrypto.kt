package com.pltmustafa.inatbox.utils

import android.util.Base64
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.pltmustafa.inatbox.models.Domain
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object InatBoxCrypto {
    const val HMK_KEY = "x7kkk0qmqz63kj68tla5i7u26192v7zqnnddhjgm"
    const val BOOTSTRAP_AES_KEY = "a4osa8x1yl4w3vrk"
    const val CERTIFICATE_URL = "https://raw.githubusercontent.com/cencbit/ssl/main/certificate.pem"
    const val DEFAULT_CATEGORY_URL = "https://dizilabmedia.click/CDN/001/002/dizilab/v2/ct.php"
    const val DEFAULT_CONFIG_URL = "https://dizilabmedia.click/CDN/001/002/dizilab/v2/cf.php"

    private val secureRandom = SecureRandom()
    private val alphaNumericCharacters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val hexCharacters = "0123456789abcdef"

    fun getRandomAlphaNumeric(length: Int): String {
        val stringBuilder = java.lang.StringBuilder(length)
        for (i in 0 until length) {
            val randomIndex = secureRandom.nextInt(alphaNumericCharacters.length)
            stringBuilder.append(alphaNumericCharacters[randomIndex])
        }
        return stringBuilder.toString()
    }

    fun getRandomHex(byteLength: Int): String {
        val stringBuilder = java.lang.StringBuilder(byteLength * 2)
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        for (byte in bytes) {
            val intVal = byte.toInt() and 0xFF
            stringBuilder.append(hexCharacters[intVal ushr 4])
            stringBuilder.append(hexCharacters[intVal and 0x0F])
        }
        return stringBuilder.toString()
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        val stringBuilder = java.lang.StringBuilder(hash.size * 2)
        for (byte in hash) {
            val intVal = byte.toInt() and 0xFF
            stringBuilder.append(hexCharacters[intVal ushr 4])
            stringBuilder.append(hexCharacters[intVal and 0x0F])
        }
        return stringBuilder.toString()
    }

    fun sha256Hex(text: String): String {
        return sha256Hex(text.toByteArray(Charsets.UTF_8))
    }

    fun hmacSha256Hex(key: String, message: String): String {
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val rawHmac = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val stringBuilder = java.lang.StringBuilder(rawHmac.size * 2)
        for (byte in rawHmac) {
            val intVal = byte.toInt() and 0xFF
            stringBuilder.append(hexCharacters[intVal ushr 4])
            stringBuilder.append(hexCharacters[intVal and 0x0F])
        }
        return stringBuilder.toString()
    }

    fun decryptAesCbc(cipherTextBase64: String, ivBase64: String, keyText: String): String {
        val cipherBytes = Base64.decode(cipherTextBase64.trim(), Base64.DEFAULT)
        val ivBytes = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
        val keyBytes = keyText.toByteArray(Charsets.ISO_8859_1)

        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        val ivParameterSpec = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun decryptDoubleAesCbc(encryptedPayload: String, keyText: String): String {
        val cleanedPayload = encryptedPayload.trim()
        val firstSplit = cleanedPayload.split(":")
        val intermediateText = decryptAesCbc(firstSplit[0], firstSplit[1], keyText)
        if (!intermediateText.contains(":")) {
            return intermediateText
        }
        val secondSplit = intermediateText.split(":")
        val secondDecrypted = decryptAesCbc(secondSplit[0], secondSplit[1], keyText)
        if (secondDecrypted.length > 64) {
            val possibleHmac = secondDecrypted.takeLast(64)
            if (possibleHmac.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                return secondDecrypted.dropLast(64)
            }
        }
        return secondDecrypted
    }

    fun decryptChannelStream(encryptedPayload: String, regex1: String, regex2: String, regex2p: String? = null): String {
        val cleanedPayload = encryptedPayload.trim()
        val firstSplit = cleanedPayload.split(":")
        val intermediate = decryptAesCbc(firstSplit[0], firstSplit[1], regex1)
        val secondSplit = intermediate.split(":")
        val secondDecrypted = try {
            decryptAesCbc(secondSplit[0], secondSplit[1], regex2)
        } catch (e: Exception) {
            if (!regex2p.isNullOrEmpty()) {
                decryptAesCbc(secondSplit[0], secondSplit[1], regex2p)
            } else {
                throw e
            }
        }
        if (secondDecrypted.length > 64) {
            val possibleHmac = secondDecrypted.takeLast(64)
            if (possibleHmac.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                return secondDecrypted.dropLast(64)
            }
        }
        return secondDecrypted
    }

    fun getSignedHeaders(url: String, method: String = "POST", body: String = ""): Map<String, String> {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = getRandomHex(16)
        val urlPath = try {
            val uri = URI(url)
            uri.rawPath ?: "/"
        } catch (e: Exception) {
            url
        }
        val bodyHash = sha256Hex(body)
        val toSign = "$method\n$urlPath\n$timestamp\n$nonce\n$bodyHash"
        val signature = hmacSha256Hex(HMK_KEY, toSign)

        val headers = mutableMapOf(
            "User-Agent" to "speedrestapi",
            "X-Requested-With" to "com.bp.box",
            "Referer" to "https://speedrestapi.com/",
            "X-Ts" to timestamp,
            "X-Nc" to nonce,
            "X-Sg" to signature
        )
        if (method.equals("POST", ignoreCase = true)) {
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        }
        return headers
    }

    suspend fun fetchBootstrapDomain(): Domain? {
        return try {
            val response = app.get(CERTIFICATE_URL)
            if (!response.isSuccessful) {
                return null
            }
            val certificateText = response.text
            val cleanContent = certificateText
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim()
            val decryptedJson = decryptDoubleAesCbc(cleanContent, BOOTSTRAP_AES_KEY)
            jacksonObjectMapper().readValue(decryptedJson, Domain::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
