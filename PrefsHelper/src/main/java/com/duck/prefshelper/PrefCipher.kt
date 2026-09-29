package com.duck.prefshelper

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals preference values before they reach storage, for the `encryptedStringPref` delegates on
 * [BasePrefsHelper] and [BaseDataStoreHelper].
 *
 * [associatedData] is authenticated but not encrypted. The helpers pass the preference key, which
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

/**
 * A [PrefCipher] backed by an AES-256-GCM key held in the Android Keystore.
 *
 * The key is generated on the first [encrypt] under [keyAlias] and never leaves the Keystore; on
 * most devices it lives in secure hardware, so copying the preference file off the device yields
 * nothing usable. It is not bound to user authentication, so reads work without a biometric prompt.
 * [decrypt] never generates a key: with nothing under the alias it fails, and the helper reads the
 * value as absent.
 *
 * **Pick a fixed [keyAlias], one per helper.** Don't derive it from a class name: R8 renames
 * classes, and a new alias means a new key and every existing value unreadable. Each ciphertext is
 * bound to its preference key but not to its file, so two helpers sharing an alias could have
 * same-named values swapped between their files undetected.
 *
 * If the Keystore entry disappears underneath an instance — [deleteKey] on another instance, or an
 * OEM keystore reset — its cached key is dropped and the operation retried once against whatever
 * the alias now holds.
 *
 * The key doesn't survive an app uninstall or a device-to-device restore. With backup enabled,
 * restored values will read as absent, which is the correct outcome for credentials. Keystore
 * calls run on the calling thread and take a few milliseconds.
 *
 * Requires API 23. Apps with a lower minSdk must guard construction with a `Build.VERSION.SDK_INT`
 * check and supply another [PrefCipher] below it.
 *
 * Blob layout: `[format version: 1 byte][IV: 12 bytes][ciphertext + 16-byte GCM tag]`, with the
 * version byte authenticated alongside the associated data.
 *
 * @param keyAlias Keystore alias for the key
 */
@RequiresApi(Build.VERSION_CODES.M)
class KeystorePrefCipher(private val keyAlias: String) : PrefCipher {

	init {
		require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
	}

	@Volatile
	private var cachedKey: SecretKey? = null

	override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
		val cipher = initCipher(Cipher.ENCRYPT_MODE, spec = null, generateIfMissing = true)
		cipher.updateAAD(byteArrayOf(FORMAT_VERSION) + associatedData)
		val iv = cipher.iv
		check(iv.size == IV_SIZE) { "Unexpected GCM IV size ${iv.size}" }
		return byteArrayOf(FORMAT_VERSION) + iv + cipher.doFinal(plaintext)
	}

	override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
		if (ciphertext.size < HEADER_SIZE + TAG_SIZE || ciphertext[0] != FORMAT_VERSION) {
			throw GeneralSecurityException("Not a KeystorePrefCipher blob")
		}
		val spec = GCMParameterSpec(TAG_SIZE * 8, ciphertext, 1, IV_SIZE)
		val cipher = initCipher(Cipher.DECRYPT_MODE, spec, generateIfMissing = false)
		cipher.updateAAD(byteArrayOf(FORMAT_VERSION) + associatedData)
		return cipher.doFinal(ciphertext, HEADER_SIZE, ciphertext.size - HEADER_SIZE)
	}

	/**
	 * Delete the Keystore key. Everything encrypted under [keyAlias] becomes permanently unreadable;
	 * the next [encrypt] on any instance generates a fresh key.
	 *
	 * Don't run it concurrently with writes: an [encrypt] already under way completes with the old
	 * key and stores a value nothing can read. Call it after the helper's writes have finished, for
	 * example once logout has cancelled or joined them.
	 */
	fun deleteKey() {
		synchronized(keystoreLock) {
			keyStore().deleteEntry(keyAlias)
			cachedKey = null
		}
	}

	private fun initCipher(mode: Int, spec: GCMParameterSpec?, generateIfMissing: Boolean): Cipher {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		val cached = cachedKey
		if (cached != null) {
			try {
				cipher.init(mode, cached, spec)
				return cipher
			} catch (e: GeneralSecurityException) {
				dropCachedKey(cached)
			} catch (e: ProviderException) {
				dropCachedKey(cached)
			}
		}
		cipher.init(mode, loadKey(generateIfMissing), spec)
		return cipher
	}

	private fun dropCachedKey(stale: SecretKey) {
		synchronized(keystoreLock) {
			if (cachedKey === stale) cachedKey = null
		}
	}

	private fun loadKey(generateIfMissing: Boolean): SecretKey = synchronized(keystoreLock) {
		cachedKey ?: when (val entry = keyStore().getKey(keyAlias, null)) {
			is SecretKey -> entry
			null ->
				if (generateIfMissing) generateKey()
				else throw KeyStoreException("No key under alias \"$keyAlias\"")
			else -> throw KeyStoreException("Alias \"$keyAlias\" holds a ${entry.algorithm} key, not an AES secret key")
		}.also { cachedKey = it }
	}

	private fun generateKey(): SecretKey {
		val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.build()
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
			.apply { init(spec) }
			.generateKey()
	}

	// load declares IOException, which would slip past the helpers' GeneralSecurityException catch
	private fun keyStore(): KeyStore =
		try {
			KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
		} catch (e: IOException) {
			throw KeyStoreException("Could not load $ANDROID_KEYSTORE", e)
		}

	private companion object {
		const val ANDROID_KEYSTORE = "AndroidKeyStore"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val FORMAT_VERSION: Byte = 1
		const val IV_SIZE = 12
		const val TAG_SIZE = 16
		const val HEADER_SIZE = 1 + IV_SIZE

		// Process-wide, so two instances on one alias can't both see it empty and each generate a key
		val keystoreLock = Any()
	}
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
