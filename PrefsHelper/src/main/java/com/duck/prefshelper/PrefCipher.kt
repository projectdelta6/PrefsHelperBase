package com.duck.prefshelper

import android.util.Log
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Seals preference values before they reach storage, for the `encryptedStringPref` delegates on
 * [BasePrefsHelper] and [BaseDataStoreHelper].
 *
 * `associatedData` is authenticated but not encrypted. The helpers pass the preference key, which
 * binds each ciphertext to the key it was written under — copying one encrypted value over another
 * in the same file makes it fail to decrypt rather than silently read as the wrong secret.
 *
 * Implementations signal any failure to open a value (wrong key, tampered bytes, a key the
 * Keystore has invalidated) by throwing [GeneralSecurityException]. The helpers catch that, log
 * it, and read the preference as absent.
 *
 * [KeystorePrefCipher] is the production implementation. Tests can supply their own, since
 * Robolectric has no AndroidKeyStore provider.
 */
interface PrefCipher {
	/**
	 * Encrypt [plaintext], authenticating [associatedData] alongside it.
	 *
	 * @return An opaque blob that only [decrypt] needs to understand
	 */
	fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

	/**
	 * Reverse [encrypt].
	 *
	 * @throws GeneralSecurityException If [ciphertext] can't be opened with [associatedData]
	 */
	fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

internal fun PrefCipher.sealString(key: String, value: String): ByteArray =
	encrypt(value.encodeToByteArray(), key.encodeToByteArray())

// ProviderException is unchecked, and some Keystore implementations throw it for a missing or broken key
internal fun PrefCipher.openStringOrNull(key: String, sealed: ByteArray, logTag: String): String? =
	try {
		decrypt(sealed, key.encodeToByteArray()).decodeToString()
	} catch (e: GeneralSecurityException) {
		Log.w(logTag, "Could not decrypt \"$key\"; reading it as absent", e)
		null
	} catch (e: ProviderException) {
		Log.w(logTag, "Could not decrypt \"$key\"; reading it as absent", e)
		null
	}
