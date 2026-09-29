package com.duck.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BasePrefsHelper
import com.duck.prefshelper.PrefCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasePrefsHelperEncryptedStringTest {

	private lateinit var prefs: SharedPreferences
	private lateinit var helper: EncryptedPrefsHelper

	@Before
	fun setUp() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		prefs = context.getSharedPreferences("encrypted_string_test", Context.MODE_PRIVATE)
		prefs.edit().clear().commit()
		helper = EncryptedPrefsHelper(prefs, SoftwareGcmPrefCipher())
	}

	@Test
	fun testDelegateRoundTrip() {
		helper.secret = "hunter2"
		assertEquals("hunter2", helper.secret)
	}

	@Test
	fun testDelegateWithDefaultRoundTripAndFallback() {
		assertEquals("fallback", helper.secretWithDefault)
		helper.secretWithDefault = "stored"
		assertEquals("stored", helper.secretWithDefault)
	}

	@Test
	fun testDirectMethodsRoundTrip() {
		helper.setEncryptedString("direct", "héllo wörld ✓")
		assertEquals("héllo wörld ✓", helper.getEncryptedString("direct"))
	}

	@Test
	fun testNullAssignmentRemovesKey() {
		helper.secret = "hunter2"
		helper.secret = null
		assertNull(helper.secret)
		assertFalse(prefs.contains("secret"))
	}

	@Test
	fun testSetEncryptedStringNullRemovesKey() {
		helper.setEncryptedString("direct", "value")
		helper.setEncryptedString("direct", null)
		assertNull(helper.getEncryptedString("direct"))
		assertFalse(prefs.contains("direct"))
	}

	@Test
	fun testAbsentKeyReadsNullOrDefault() {
		assertNull(helper.secret)
		assertNull(helper.getEncryptedString("nothing_here"))
		assertEquals("fallback", helper.secretWithDefault)
	}

	@Test
	fun testStoredValueIsNotPlaintext() {
		val plaintext = "super-secret-token"
		helper.secret = plaintext
		val raw = prefs.getString("secret", null)!!
		assertNotEquals(plaintext, raw)
		assertFalse(raw.contains(plaintext))
		assertFalse(String(Base64.decode(raw, Base64.NO_WRAP)).contains(plaintext))
	}

	@Test
	fun testCiphertextCopiedToAnotherKeyReadsNull() {
		helper.setEncryptedString("key_a", "value")
		prefs.edit().putString("key_b", prefs.getString("key_a", null)).commit()
		assertEquals("value", helper.getEncryptedString("key_a"))
		assertNull(helper.getEncryptedString("key_b"))
	}

	@Test
	fun testValueWrittenUnderDifferentCipherKeyReadsNull() {
		helper.secret = "hunter2"
		helper.secretWithDefault = "stored"
		val other = EncryptedPrefsHelper(prefs, SoftwareGcmPrefCipher())
		assertNull(other.secret)
		assertEquals("fallback", other.secretWithDefault)
	}

	@Test
	fun testGarbageBytesReadNull() {
		helper.setByteArray("secret", ByteArray(40) { it.toByte() })
		assertNull(helper.secret)
		helper.setByteArray("secret", byteArrayOf(1, 2, 3))
		assertNull(helper.secret)
	}

	@Test
	fun testNonBase64GarbageReadsNull() {
		prefs.edit().putString("secret", "!!! not base64 !!!").commit()
		assertNull(helper.secret)
	}

	@Test
	fun testWriteReplacesUndecryptableValue() {
		prefs.edit().putString("secret", "!!! not base64 !!!").commit()
		helper.secret = "fresh"
		assertEquals("fresh", helper.secret)
	}

	@Test
	fun testProviderExceptionOnDecryptReadsNull() {
		helper.secret = "hunter2"
		helper.secretWithDefault = "stored"
		val failing = EncryptedPrefsHelper(prefs, ProviderExceptionPrefCipher())
		assertNull(failing.secret)
		assertNull(failing.getEncryptedString("secret"))
		assertEquals("fallback", failing.secretWithDefault)
	}

	@Test
	fun testSingleBitTamperReadsNull() {
		helper.secret = "hunter2"
		val original = Base64.decode(prefs.getString("secret", null)!!, Base64.NO_WRAP)
		listOf(0, original.size / 2, original.lastIndex).forEach { position ->
			val tampered = original.copyOf()
			tampered[position] = (tampered[position].toInt() xor 1).toByte()
			prefs.edit().putString("secret", Base64.encodeToString(tampered, Base64.NO_WRAP)).commit()
			assertNull("bit flip at byte $position", helper.secret)
		}
	}

	@Test
	fun testWithoutCipherOverrideThrows() {
		val noCipher = NoCipherPrefsHelper(prefs)
		assertThrows(UnsupportedOperationException::class.java) { noCipher.setEncryptedString("k", "v") }
		assertThrows(UnsupportedOperationException::class.java) { noCipher.secret = "v" }
		prefs.edit().putString("stored", "AAAA").commit()
		assertThrows(UnsupportedOperationException::class.java) { noCipher.getEncryptedString("stored") }
	}

	@Test
	fun testDelegatesDeclaredBeforeCipherOverrideRoundTrip() {
		val delegatesFirst = DelegatesFirstPrefsHelper(prefs)
		delegatesFirst.secret = "hunter2"
		delegatesFirst.secretWithDefault = "stored"
		assertEquals("hunter2", delegatesFirst.secret)
		assertEquals("stored", delegatesFirst.secretWithDefault)
	}

	@Test
	fun testEdgeCaseStringsRoundTrip() {
		listOf("", "a".repeat(100_000), "😀 \u0000 mixed").forEach { value ->
			helper.secret = value
			assertEquals(value, helper.secret)
			helper.setEncryptedString("direct", value)
			assertEquals(value, helper.getEncryptedString("direct"))
		}
	}

	private class EncryptedPrefsHelper(
		override val sharedPreferences: SharedPreferences,
		override val prefCipher: PrefCipher,
	) : BasePrefsHelper() {
		var secret by encryptedStringPref("secret")
		var secretWithDefault by encryptedStringPref("secret_with_default", "fallback")
	}

	private class DelegatesFirstPrefsHelper(
		override val sharedPreferences: SharedPreferences,
	) : BasePrefsHelper() {
		var secret by encryptedStringPref("secret")
		var secretWithDefault by encryptedStringPref("secret_with_default", "fallback")

		override val prefCipher: PrefCipher = SoftwareGcmPrefCipher()
	}

	private class NoCipherPrefsHelper(
		override val sharedPreferences: SharedPreferences,
	) : BasePrefsHelper() {
		var secret by encryptedStringPref("secret")
	}
}
