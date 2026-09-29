package com.duck.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BasePrefsHelper
import com.duck.prefshelper.KeystorePrefCipher
import com.duck.prefshelper.PrefCipher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.UUID

/**
 * Exercises [KeystorePrefCipher] against the real AndroidKeyStore, which Robolectric can't provide.
 *
 * **Manual only — needs a device or emulator, not in CI:**
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.duck.app.KeystorePrefCipherTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class KeystorePrefCipherTest {

	private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val aliases = mutableListOf<String>()
	private val aad = "pref_key".toByteArray()

	@Before
	fun setUp() {
		aliases.clear()
	}

	@After
	fun tearDown() {
		aliases.forEach { if (keyStore.containsAlias(it)) keyStore.deleteEntry(it) }
	}

	private fun newAlias(): String = "keystore_pref_cipher_test_${UUID.randomUUID()}".also { aliases += it }

	private fun newCipher(alias: String = newAlias()) = KeystorePrefCipher(alias)

	private fun ByteArray.containsRun(bytes: ByteArray): Boolean =
		(0..size - bytes.size).any { start -> bytes.indices.all { this[start + it] == bytes[it] } }

	@Test
	fun testRoundTrip() {
		val cipher = newCipher()
		val plaintext = "hunter2 ✓".toByteArray()
		val sealed = cipher.encrypt(plaintext, aad)
		assertFalse(sealed.containsRun(plaintext))
		assertArrayEquals(plaintext, cipher.decrypt(sealed, aad))
	}

	@Test
	fun testEncryptingTwiceGivesDifferentBlobs() {
		val cipher = newCipher()
		assertFalse(cipher.encrypt("v".toByteArray(), aad).contentEquals(cipher.encrypt("v".toByteArray(), aad)))
	}

	@Test
	fun testDifferentAssociatedDataThrows() {
		val cipher = newCipher()
		val sealed = cipher.encrypt("secret".toByteArray(), aad)
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed, "other_key".toByteArray()) }
	}

	@Test
	fun testFlippedCiphertextByteThrows() {
		val cipher = newCipher()
		val sealed = cipher.encrypt("secret".toByteArray(), aad)
		sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed, aad) }
	}

	@Test
	fun testTooShortBlobThrows() {
		val cipher = newCipher()
		cipher.encrypt("secret".toByteArray(), aad)
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(ByteArray(0), aad) }
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(byteArrayOf(1, 2, 3), aad) }
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(ByteArray(28).also { it[0] = 1 }, aad) }
	}

	@Test
	fun testWrongVersionBlobThrows() {
		val cipher = newCipher()
		val sealed = cipher.encrypt("secret".toByteArray(), aad)
		sealed[0] = 2
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed, aad) }
	}

	@Test
	fun testDecryptWithNoKeyThrowsAndDoesNotCreateEntry() {
		val alias = newAlias()
		val cipher = KeystorePrefCipher(alias)
		val blob = ByteArray(64).also { it[0] = 1 }
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob, aad) }
		assertFalse(keyStore.containsAlias(alias))
	}

	@Test
	fun testDecryptAfterDeleteKeyDoesNotRecreateEntry() {
		val alias = newAlias()
		val cipher = KeystorePrefCipher(alias)
		val sealed = cipher.encrypt("secret".toByteArray(), aad)
		cipher.deleteKey()
		assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed, aad) }
		assertFalse(keyStore.containsAlias(alias))
	}

	@Test
	fun testInstanceRecoversFromKeyDeletedByAnotherInstance() {
		val alias = newAlias()
		val a = KeystorePrefCipher(alias)
		val b = KeystorePrefCipher(alias)
		val plaintext = "secret".toByteArray()

		val before = a.encrypt(plaintext, aad)
		assertArrayEquals(plaintext, b.decrypt(before, aad))
		assertArrayEquals(plaintext, a.decrypt(before, aad))

		a.deleteKey()

		assertThrows(GeneralSecurityException::class.java) { b.decrypt(before, aad) }
		assertFalse(keyStore.containsAlias(alias))

		val after = b.encrypt(plaintext, aad)
		assertArrayEquals(plaintext, a.decrypt(after, aad))
		assertArrayEquals(plaintext, b.decrypt(after, aad))
		assertThrows(GeneralSecurityException::class.java) { a.decrypt(before, aad) }
		assertThrows(GeneralSecurityException::class.java) { b.decrypt(before, aad) }
	}

	@Test
	fun testInstanceWithStaleCachedKeyRecoversWhenAliasIsRecreated() {
		val alias = newAlias()
		val a = KeystorePrefCipher(alias)
		val b = KeystorePrefCipher(alias)
		val plaintext = "secret".toByteArray()

		assertArrayEquals(plaintext, b.decrypt(a.encrypt(plaintext, aad), aad))

		a.deleteKey()
		val recreated = a.encrypt(plaintext, aad)

		assertArrayEquals(plaintext, b.decrypt(recreated, aad))
	}

	@Test
	fun testNonSecretKeyEntryFailsWithKeyStoreException() {
		val alias = newAlias()
		KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
			.apply {
				initialize(
					KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
						.setDigests(KeyProperties.DIGEST_SHA256)
						.build()
				)
			}
			.generateKeyPair()
		val cipher = KeystorePrefCipher(alias)

		assertThrows(KeyStoreException::class.java) { cipher.encrypt("secret".toByteArray(), aad) }
		assertThrows(KeyStoreException::class.java) { cipher.decrypt(ByteArray(64).also { it[0] = 1 }, aad) }
	}

	@Test
	fun testBasePrefsHelperRoundTrip() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val prefs = context.getSharedPreferences("keystore_pref_cipher_test", Context.MODE_PRIVATE)
		prefs.edit().clear().commit()
		try {
			val helper = KeystorePrefsHelper(prefs, newCipher())
			assertEquals("fallback", helper.secretWithDefault)
			helper.secret = "hunter2"
			helper.secretWithDefault = "stored"
			assertEquals("hunter2", helper.secret)
			assertEquals("stored", helper.secretWithDefault)
			val stored = Base64.decode(prefs.getString("secret", null), Base64.NO_WRAP)
			assertFalse(stored.containsRun("hunter2".toByteArray()))
			helper.secret = null
			assertNull(helper.secret)
			assertFalse(prefs.contains("secret"))
		} finally {
			prefs.edit().clear().commit()
		}
	}

	private class KeystorePrefsHelper(
		override val sharedPreferences: SharedPreferences,
		override val prefCipher: PrefCipher,
	) : BasePrefsHelper() {
		var secret by encryptedStringPref("secret")
		var secretWithDefault by encryptedStringPref("secret_with_default", "fallback")
	}
}
