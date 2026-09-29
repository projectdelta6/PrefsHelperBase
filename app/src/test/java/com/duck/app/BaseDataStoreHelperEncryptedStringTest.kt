package com.duck.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duck.prefshelper.BaseDataStoreHelper
import com.duck.prefshelper.PrefCipher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BaseDataStoreHelperEncryptedStringTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private val storeScopes = mutableListOf<CoroutineScope>()
	private var fileCounter = 0

	@After
	fun tearDown() {
		storeScopes.forEach { it.cancel() }
	}

	private fun newDataStore(): DataStore<Preferences> {
		val file = File(tempFolder.root, "enc_${fileCounter++}.preferences_pb")
		val storeScope = CoroutineScope(Dispatchers.IO + Job())
		storeScopes += storeScope
		return PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file })
	}

	private fun newScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + Job()).also { storeScopes += it }

	private fun newHelper(store: DataStore<Preferences>, cipher: PrefCipher) =
		EncryptedDataStoreHelper(store, cipher, newScope())

	private suspend fun rawBytes(store: DataStore<Preferences>, key: String): ByteArray? =
		store.data.first()[byteArrayPreferencesKey(key)]

	private suspend fun putRaw(store: DataStore<Preferences>, key: String, bytes: ByteArray) {
		store.edit { it[byteArrayPreferencesKey(key)] = bytes }
	}

	private suspend fun <T> awaitValue(predicate: (T) -> Boolean, read: () -> T): T = withTimeout(5_000) {
		var value = read()
		while (!predicate(value)) {
			delay(20)
			value = read()
		}
		value
	}

	@Test
	fun testDelegateRoundTrip() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		helper.secret = "hunter2"
		assertEquals("hunter2", awaitValue({ it != null }) { helper.secret })
	}

	@Test
	fun testDelegateWithDefaultRoundTripAndFallback() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		assertEquals("fallback", helper.secretWithDefault)
		helper.secretWithDefault = "stored"
		assertEquals("stored", awaitValue({ it != "fallback" }) { helper.secretWithDefault })
	}

	@Test
	fun testDirectMethodsRoundTrip() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		helper.testWrite("direct", "héllo wörld ✓")
		assertEquals("héllo wörld ✓", helper.testReadValue("direct"))
		assertEquals("héllo wörld ✓", helper.testReadFlow("direct").first())
	}

	@Test
	fun testNullAssignmentRemovesKey() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		helper.testWrite("k", "value")
		helper.testWrite("k", null)
		assertNull(helper.testReadValue("k"))
		assertNull(rawBytes(store, "k"))
	}

	@Test
	fun testAbsentKeyReadsNullOrDefault() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		assertNull(helper.testReadValue("nothing"))
		assertNull(helper.testReadFlow("nothing").first())
		assertNull(helper.secret)
		assertEquals("fallback", helper.secretWithDefault)
	}

	@Test
	fun testStoredValueIsNotPlaintext() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		val plaintext = "super-secret-token"
		helper.testWrite("k", plaintext)
		val raw = rawBytes(store, "k")
		assertNotNull(raw)
		assertFalse(String(raw!!).contains(plaintext))
		assertFalse(raw.contentEquals(plaintext.toByteArray()))
	}

	@Test
	fun testCiphertextCopiedToAnotherKeyReadsNull() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		helper.testWrite("key_a", "value")
		putRaw(store, "key_b", rawBytes(store, "key_a")!!)
		assertEquals("value", helper.testReadValue("key_a"))
		assertNull(helper.testReadValue("key_b"))
		assertNull(helper.testReadFlow("key_b").first())
	}

	@Test
	fun testValueWrittenUnderDifferentCipherKeyReadsNull() = runBlocking {
		val store = newDataStore()
		newHelper(store, SoftwareGcmPrefCipher()).testWrite("k", "value")
		val other = newHelper(store, SoftwareGcmPrefCipher())
		assertNull(other.testReadValue("k"))
		assertNull(other.testReadFlow("k").first())
	}

	@Test
	fun testGarbageBytesReadNull() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		putRaw(store, "k", ByteArray(40) { it.toByte() })
		assertNull(helper.testReadValue("k"))
		putRaw(store, "k", byteArrayOf(1, 2, 3))
		assertNull(helper.testReadFlow("k").first())
	}

	@Test
	fun testFlowEmitsDecryptedValueThenNullAfterRemoval() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		helper.testWrite("k", "first")
		assertEquals("first", helper.testPrefFlow("k").first())
		helper.testWrite("k", null)
		assertNull(helper.testPrefFlow("k").first())
	}

	@Test
	fun testFlowObservesLaterWrite() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		val subscribed = CompletableDeferred<Unit>()
		val observed = async(Dispatchers.IO) {
			helper.testReadFlow("k")
				.onEach { if (it == null) subscribed.complete(Unit) }
				.first { it == "later" }
		}
		withTimeout(5_000) { subscribed.await() }
		helper.testWrite("k", "later")
		assertEquals("later", withTimeout(5_000) { observed.await() })
	}

	@Test
	fun testAsyncWriteJobCanBeJoined() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		val job = helper.testWriteAsync("k", "async")
		job.join()
		assertTrue(job.isCompleted)
		assertEquals("async", helper.testReadValue("k"))
		helper.testWriteAsync("k", null).join()
		assertNull(helper.testReadValue("k"))
	}

	@Test
	fun testProviderExceptionOnDecryptReadsNull() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		helper.testWrite("secret", "hunter2")
		helper.testWrite("secret_with_default", "stored")
		val failing = newHelper(store, ProviderExceptionPrefCipher())
		assertNull(failing.testReadValue("secret"))
		assertNull(failing.testReadFlow("secret").first())
		assertNull(failing.secret)
		assertEquals("fallback", failing.secretWithDefault)
	}

	@Test
	fun testSingleBitTamperReadsNull() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		helper.testWrite("k", "hunter2")
		val original = rawBytes(store, "k")!!
		listOf(0, original.size / 2, original.lastIndex).forEach { position ->
			val tampered = original.copyOf()
			tampered[position] = (tampered[position].toInt() xor 1).toByte()
			putRaw(store, "k", tampered)
			assertNull("bit flip at byte $position", helper.testReadValue("k"))
			assertNull("bit flip at byte $position", helper.testReadFlow("k").first())
		}
	}

	@Test
	fun testDelegateNullAssignmentRemovesKey() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		helper.secret = "hunter2"
		awaitValue({ it != null }) { helper.secret }
		helper.secret = null
		awaitValue({ it == null }) { helper.secret }
		assertNull(rawBytes(store, "secret"))
	}

	@Test
	fun testWithoutCipherOverrideThrows() {
		val store = newDataStore()
		val helper = NoCipherDataStoreHelper(store, newScope())
		assertThrows(UnsupportedOperationException::class.java) { runBlocking { helper.testWrite("k", "v") } }
		runBlocking { putRaw(store, "k", ByteArray(40)); putRaw(store, "secret", ByteArray(40)) }
		assertThrows(UnsupportedOperationException::class.java) { helper.testReadValue("k") }
		assertThrows(UnsupportedOperationException::class.java) { helper.secret }
	}

	@Test
	fun testDelegatesDeclaredBeforeCipherOverrideRoundTrip() = runBlocking {
		val helper = DelegatesFirstDataStoreHelper(newDataStore(), newScope())
		helper.secret = "hunter2"
		helper.secretWithDefault = "stored"
		assertEquals("hunter2", awaitValue({ it != null }) { helper.secret })
		assertEquals("stored", awaitValue({ it != "fallback" }) { helper.secretWithDefault })
	}

	@Test
	fun testDefaultReturnedWhenDecryptedWithDifferentCipher() = runBlocking {
		val store = newDataStore()
		newHelper(store, SoftwareGcmPrefCipher()).testWrite("secret_with_default", "stored")
		val other = newHelper(store, SoftwareGcmPrefCipher())
		assertEquals("fallback", other.secretWithDefault)
	}

	@Test
	fun testDefaultReturnedWhenStoredBytesAreGarbage() = runBlocking {
		val store = newDataStore()
		val helper = newHelper(store, SoftwareGcmPrefCipher())
		putRaw(store, "secret_with_default", ByteArray(40) { it.toByte() })
		assertEquals("fallback", helper.secretWithDefault)
		putRaw(store, "secret_with_default", byteArrayOf(1, 2, 3))
		assertEquals("fallback", helper.secretWithDefault)
	}

	@Test
	fun testPrefFlowWithDefaultFollowsWrites() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		val emissions = CopyOnWriteArrayList<String>()
		val collector = launch(Dispatchers.IO) { helper.testPrefFlow("k", "fallback").collect { emissions += it } }
		awaitValue({ it == listOf("fallback") }) { emissions.toList() }
		helper.testWrite("k", "stored")
		awaitValue({ it == listOf("fallback", "stored") }) { emissions.toList() }
		helper.testWrite("k", null)
		awaitValue({ it == listOf("fallback", "stored", "fallback") }) { emissions.toList() }
		collector.cancel()
	}

	@Test
	fun testUnrelatedKeyWritesDoNotTriggerDecrypt() = runBlocking {
		val cipher = CountingPrefCipher(SoftwareGcmPrefCipher())
		val helper = newHelper(newDataStore(), cipher)
		val emissions = CopyOnWriteArrayList<String?>()
		val collector = launch(Dispatchers.IO) { helper.testReadFlow("k").collect { emissions += it } }
		awaitValue({ it == listOf(null) }) { emissions.toList() }
		helper.testWrite("k", "value")
		awaitValue({ it == listOf(null, "value") }) { emissions.toList() }
		val decryptsBefore = cipher.decryptCount.get()
		helper.testWriteString("other", "one")
		helper.testWriteString("other", "two")
		helper.testWriteString("other", "three")
		delay(200)
		assertEquals(decryptsBefore, cipher.decryptCount.get())
		assertEquals(listOf(null, "value"), emissions.toList())
		collector.cancel()
	}

	@Test
	fun testEdgeCaseStringsRoundTrip() = runBlocking {
		val helper = newHelper(newDataStore(), SoftwareGcmPrefCipher())
		listOf("", "a".repeat(100_000), "😀 \u0000 mixed").forEach { value ->
			helper.testWrite("k", value)
			assertEquals(value, helper.testReadValue("k"))
			assertEquals(value, helper.testReadFlow("k").first())
		}
	}

	private class EncryptedDataStoreHelper(
		dataStore: DataStore<Preferences>,
		override val prefCipher: PrefCipher,
		scope: CoroutineScope,
	) : BaseDataStoreHelper(dataStore, scope = scope) {
		var secret by encryptedStringPref("secret")
		var secretWithDefault by encryptedStringPref("secret_with_default", "fallback")

		suspend fun testWrite(key: String, value: String?) = writeEncryptedString(key, value)
		fun testWriteAsync(key: String, value: String?) = writeEncryptedStringAsync(key, value)
		fun testReadFlow(key: String) = readEncryptedString(key)
		fun testPrefFlow(key: String) = encryptedStringPrefFlow(key)
		fun testPrefFlow(key: String, defaultValue: String) = encryptedStringPrefFlow(key, defaultValue)
		suspend fun testWriteString(key: String, value: String?) = writeString(key, value)
		fun testReadValue(key: String) = readEncryptedStringValue(key)
	}

	private class CountingPrefCipher(private val delegate: PrefCipher) : PrefCipher {
		val decryptCount = AtomicInteger()

		override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
			delegate.encrypt(plaintext, associatedData)

		override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
			decryptCount.incrementAndGet()
			return delegate.decrypt(ciphertext, associatedData)
		}
	}

	private class DelegatesFirstDataStoreHelper(
		dataStore: DataStore<Preferences>,
		scope: CoroutineScope,
	) : BaseDataStoreHelper(dataStore, scope = scope) {
		var secret by encryptedStringPref("secret")
		var secretWithDefault by encryptedStringPref("secret_with_default", "fallback")

		override val prefCipher: PrefCipher = SoftwareGcmPrefCipher()
	}

	private class NoCipherDataStoreHelper(
		dataStore: DataStore<Preferences>,
		scope: CoroutineScope,
	) : BaseDataStoreHelper(dataStore, scope = scope) {
		var secret by encryptedStringPref("secret")

		suspend fun testWrite(key: String, value: String?) = writeEncryptedString(key, value)
		fun testReadValue(key: String) = readEncryptedStringValue(key)
	}
}
