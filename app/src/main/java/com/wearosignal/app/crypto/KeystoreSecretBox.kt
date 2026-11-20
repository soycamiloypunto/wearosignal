package com.wearosignal.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive account preferences at rest using an Android Keystore AES-256-GCM key.
 * Plaintext values stored before encryption was enabled are transparently read as-is.
 */
object KeystoreSecretBox {

  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "wearosignal_account_aes_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val PREFIX = "enc:v1:"
  private const val IV_LENGTH_BYTES = 12
  private const val GCM_TAG_BITS = 128

  @Synchronized
  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    keyGen.init(spec)
    return keyGen.generateKey()
  }

  fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val combined = ByteArray(iv.size + ciphertext.size)
    System.arraycopy(iv, 0, combined, 0, iv.size)
    System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
    return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  fun decrypt(stored: String): String {
    if (!stored.startsWith(PREFIX)) return stored
    val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
    require(combined.size > IV_LENGTH_BYTES) { "Corrupted encrypted preference payload" }
    val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
    val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
  }
}
