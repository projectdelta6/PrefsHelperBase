package com.duck.app

import com.duck.prefshelper.PrefCipher
import java.security.ProviderException

class ProviderExceptionPrefCipher(private val delegate: PrefCipher = SoftwareGcmPrefCipher()) : PrefCipher {

	override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
		delegate.encrypt(plaintext, associatedData)

	override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
		throw ProviderException("Keystore is unavailable")
}
