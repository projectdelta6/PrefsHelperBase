package com.duck.app

import com.duck.prefshelper.PrefCipher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Real AES/GCM with an in-memory key, standing in for `KeystorePrefCipher` (AndroidKeyStore does
 * not exist under Robolectric). Associated data is genuinely authenticated, so AAD binding is
 * exercised for real.
 */
class SoftwareGcmPrefCipher(private val key: SecretKey = newKey()) : PrefCipher {

	override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
		val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
		cipher.updateAAD(associatedData)
		return iv + cipher.doFinal(plaintext)
	}

	override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
		if (ciphertext.size < IV_SIZE + TAG_BITS / 8) throw GeneralSecurityException("Too short")
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_SIZE))
		cipher.updateAAD(associatedData)
		return cipher.doFinal(ciphertext, IV_SIZE, ciphertext.size - IV_SIZE)
	}

	private companion object {
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val IV_SIZE = 12
		const val TAG_BITS = 128

		fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
	}
}
