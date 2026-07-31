package com.duck.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BaseDataStoreHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseDataStoreHelperTest {

	private lateinit var context: Context
	private lateinit var dataStoreHelper: TestDataStoreHelper

	@Before
	fun setUp() {
		context = InstrumentationRegistry.getInstrumentation().targetContext
		dataStoreHelper = TestDataStoreHelper(context)
	}

	@After
	fun tearDown() {
		runBlocking {
			dataStoreHelper.clearPrefs()
		}
	}

	/**
	 * DataStore async/delegate writes are fire-and-forget (`scope.launch`), so reading after a
	 * fixed [delay] is racy under device load — the write may not have hit disk yet. Poll [read]
	 * until it satisfies [predicate], or a generous timeout elapses, then return the latest value
	 * for assertion. Deterministic regardless of how busy the device is.
	 */
	private suspend fun <T> awaitValue(
		timeoutMs: Long = 5_000L,
		predicate: (T) -> Boolean,
		read: () -> T,
	): T {
		val deadline = System.currentTimeMillis() + timeoutMs
		var value = read()
		while (!predicate(value) && System.currentTimeMillis() < deadline) {
			delay(20.milliseconds)
			value = read()
		}
		return value
	}

	@Test
	fun testWriteAndReadString() = runBlocking {
		dataStoreHelper.testWriteString("test_key", "test_value")
		val value = dataStoreHelper.testReadStringValue("test_key")
		assertEquals("test_value", value)
	}

	@Test
	fun testWriteAndReadInt() = runBlocking {
		dataStoreHelper.testWriteInt("int_key", 42)
		val value = dataStoreHelper.testReadIntValue("int_key")
		assertEquals(42, value)
	}

	@Test
	fun testWriteAndReadLong() = runBlocking {
		dataStoreHelper.testWriteLong("long_key", 123456789L)
		val value = dataStoreHelper.testReadLongValue("long_key")
		assertEquals(123456789L, value)
	}

	@Test
	fun testWriteAndReadDouble() = runBlocking {
		dataStoreHelper.testWriteDouble("double_key", 3.14)
		val value = dataStoreHelper.testReadDoubleValue("double_key")
		assertEquals(3.14, value ?: 0.0, 0.01)
	}

	@Test
	fun testWriteAndReadDate() = runBlocking {
		val date = java.util.Date(1_700_000_000_000L)
		dataStoreHelper.testWriteDate("date_key", date)
		assertEquals(date, dataStoreHelper.testReadDateValue("date_key"))
	}

	/**
	 * Date is stored as epoch millis, so it must survive the epoch itself and pre-epoch (negative)
	 * values — the case a naive -1L sentinel would corrupt.
	 */
	@Test
	fun testDateBoundaryValues() = runBlocking {
		val epoch = java.util.Date(0L)
		dataStoreHelper.testWriteDate("epoch", epoch)
		assertEquals(epoch, dataStoreHelper.testReadDateValue("epoch"))

		val preEpoch = java.util.Date(-1L)
		dataStoreHelper.testWriteDate("pre_epoch", preEpoch)
		assertEquals(preEpoch, dataStoreHelper.testReadDateValue("pre_epoch"))
	}

	@Test
	fun testWriteNullDateRemovesKey() = runBlocking {
		dataStoreHelper.testWriteDate("null_date_key", java.util.Date(1_700_000_000_000L))
		assertNotNull(dataStoreHelper.testReadDateValue("null_date_key"))

		dataStoreHelper.testWriteDate("null_date_key", null)
		assertNull(dataStoreHelper.testReadDateValue("null_date_key"))
	}

	@Test
	fun testReadDateFlow() = runBlocking {
		val date = java.util.Date(1_600_000_000_000L)
		dataStoreHelper.testWriteDate("date_flow_key", date)
		assertEquals(date, dataStoreHelper.testReadDateFlow("date_flow_key").first())
	}

	@Test
	fun testReadDateValueWithDefault() = runBlocking {
		val fallback = java.util.Date(42L)
		assertEquals(fallback, dataStoreHelper.testReadDateValueWithDefault("no_such_date", fallback))
		assertEquals(fallback, dataStoreHelper.testReadDateFlowWithDefault("no_such_date", fallback).first())
	}

	@Test
	fun testWriteDateAsync() = runBlocking {
		val date = java.util.Date(1_500_000_000_000L)
		dataStoreHelper.testWriteDateAsync("date_async_key", date).join()
		assertEquals(date, dataStoreHelper.testReadDateValue("date_async_key"))
	}

	@Test
	fun testWriteAndReadBoolean() = runBlocking {
		dataStoreHelper.testWriteBoolean("bool_key", true)
		val value = dataStoreHelper.testReadBooleanValue("bool_key")
		assertTrue(value ?: false)
	}

	// Edge Case Tests - Boundary Values
	@Test
	fun testIntegerBoundaryValues() = runBlocking {
		dataStoreHelper.testWriteInt("max_int", Int.MAX_VALUE)
		assertEquals(Int.MAX_VALUE, dataStoreHelper.testReadIntValue("max_int"))

		dataStoreHelper.testWriteInt("min_int", Int.MIN_VALUE)
		assertEquals(Int.MIN_VALUE, dataStoreHelper.testReadIntValue("min_int"))

		dataStoreHelper.testWriteInt("zero", 0)
		assertEquals(0, dataStoreHelper.testReadIntValue("zero"))
	}

	@Test
	fun testLongBoundaryValues() = runBlocking {
		dataStoreHelper.testWriteLong("max_long", Long.MAX_VALUE)
		assertEquals(Long.MAX_VALUE, dataStoreHelper.testReadLongValue("max_long"))

		dataStoreHelper.testWriteLong("min_long", Long.MIN_VALUE)
		assertEquals(Long.MIN_VALUE, dataStoreHelper.testReadLongValue("min_long"))
	}

	@Test
	fun testDoubleBoundaryValues() = runBlocking {
		dataStoreHelper.testWriteDouble("positive_infinity", Double.POSITIVE_INFINITY)
		assertEquals(Double.POSITIVE_INFINITY, dataStoreHelper.testReadDoubleValue("positive_infinity"))

		dataStoreHelper.testWriteDouble("negative_infinity", Double.NEGATIVE_INFINITY)
		assertEquals(Double.NEGATIVE_INFINITY, dataStoreHelper.testReadDoubleValue("negative_infinity"))

		dataStoreHelper.testWriteDouble("max", Double.MAX_VALUE)
		assertEquals(Double.MAX_VALUE, dataStoreHelper.testReadDoubleValue("max"))

		dataStoreHelper.testWriteDouble("min", Double.MIN_VALUE)
		assertEquals(Double.MIN_VALUE, dataStoreHelper.testReadDoubleValue("min"))
	}

	@Test
	fun testDoubleNaN() = runBlocking {
		dataStoreHelper.testWriteDouble("nan", Double.NaN)
		val value = dataStoreHelper.testReadDoubleValue("nan")
		assertNotNull(value)
		assertTrue("Value should be NaN", value!!.isNaN())
	}

	// Edge Case Tests - Empty and Special Strings
	@Test
	fun testEmptyStringStorage() = runBlocking {
		dataStoreHelper.testWriteString("empty_key", "")
		assertEquals("", dataStoreHelper.testReadStringValue("empty_key"))
	}

	@Test
	fun testWhitespaceStringStorage() = runBlocking {
		dataStoreHelper.testWriteString("whitespace_key", "   ")
		assertEquals("   ", dataStoreHelper.testReadStringValue("whitespace_key"))
	}

	@Test
	fun testSpecialCharactersInValues() = runBlocking {
		val specialChars = "!@#$%^&*()_+-=[]{}|;:',.<>?/~`"
		dataStoreHelper.testWriteString("special_key", specialChars)
		assertEquals(specialChars, dataStoreHelper.testReadStringValue("special_key"))
	}

	@Test
	fun testUnicodeCharacters() = runBlocking {
		val unicode = "你好世界 🎉🚀💻 مرحبا בעברית"
		dataStoreHelper.testWriteString("unicode_key", unicode)
		assertEquals(unicode, dataStoreHelper.testReadStringValue("unicode_key"))
	}

	@Test
	fun testVeryLongString() = runBlocking {
		val longString = "a".repeat(10000)
		dataStoreHelper.testWriteString("long_key", longString)
		val result = dataStoreHelper.testReadStringValue("long_key")
		assertEquals(10000, result?.length)
		assertEquals(longString, result)
	}

	@Test
	fun testKeysWithSpecialCharacters() = runBlocking {
		dataStoreHelper.testWriteString("key.with.dots", "value1")
		assertEquals("value1", dataStoreHelper.testReadStringValue("key.with.dots"))

		dataStoreHelper.testWriteString("key_with_underscores", "value2")
		assertEquals("value2", dataStoreHelper.testReadStringValue("key_with_underscores"))

		dataStoreHelper.testWriteString("key-with-dashes", "value3")
		assertEquals("value3", dataStoreHelper.testReadStringValue("key-with-dashes"))
	}

	@Test
	fun testWriteNullRemovesKey() = runBlocking {
		dataStoreHelper.testWriteString("test_key", "value")
		assertNotNull(dataStoreHelper.testReadStringValue("test_key"))

		dataStoreHelper.testWriteString("test_key", null)
		assertNull(dataStoreHelper.testReadStringValue("test_key"))
	}

	@Test
	fun testClearPrefs() = runBlocking {
		dataStoreHelper.testWriteString("test_key", "value")
		assertNotNull(dataStoreHelper.testReadStringValue("test_key"))

		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.testReadStringValue("test_key"))
	}

	@Test
	fun testReadStringFlow() = runBlocking {
		dataStoreHelper.testWriteString("flow_key", "initial")
		val firstValue = dataStoreHelper.testReadStringFlow("flow_key").first()
		assertEquals("initial", firstValue)

		dataStoreHelper.testWriteString("flow_key", "updated")
		val secondValue = dataStoreHelper.testReadStringFlow("flow_key").first()
		assertEquals("updated", secondValue)
	}

	@Test
	fun testReadStringFlowWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadStringFlowWithDefault("nonexistent_key", "default").first()
		assertEquals("default", value)
	}

	@Test
	fun testReadIntFlow() = runBlocking {
		dataStoreHelper.testWriteInt("flow_key", 100)
		val value = dataStoreHelper.testReadIntFlow("flow_key").first()
		assertEquals(100, value)
	}

	@Test
	fun testReadIntFlowWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadIntFlowWithDefault("nonexistent_key", 42).first()
		assertEquals(42, value)
	}

	@Test
	fun testReadLongFlow() = runBlocking {
		dataStoreHelper.testWriteLong("flow_key", 999888777L)
		val value = dataStoreHelper.testReadLongFlow("flow_key").first()
		assertEquals(999888777L, value)
	}

	@Test
	fun testReadDoubleFlow() = runBlocking {
		dataStoreHelper.testWriteDouble("flow_key", 2.71828)
		val value = dataStoreHelper.testReadDoubleFlow("flow_key").first()
		assertEquals(2.71828, value ?: 0.0, 0.00001)
	}

	@Test
	fun testReadBooleanFlow() = runBlocking {
		dataStoreHelper.testWriteBoolean("flow_key", false)
		val value = dataStoreHelper.testReadBooleanFlow("flow_key").first()
		assertEquals(false, value)
	}

	@Test
	fun testFlowEmitsMultipleUpdates() = runBlocking {
		val emissions = mutableListOf<String?>()
		val job = launch {
			dataStoreHelper.testReadStringFlow("multi_flow_key")
				.take(4)
				.collect { emissions.add(it) }
		}

		delay(50.milliseconds) // Initial null emission
		dataStoreHelper.testWriteString("multi_flow_key", "first")
		delay(50.milliseconds)
		dataStoreHelper.testWriteString("multi_flow_key", "second")
		delay(50.milliseconds)
		dataStoreHelper.testWriteString("multi_flow_key", "third")
		delay(50.milliseconds)

		job.join()
		assertEquals(4, emissions.size)
		assertEquals(null, emissions[0]) // Initial state
		assertEquals("first", emissions[1])
		assertEquals("second", emissions[2])
		assertEquals("third", emissions[3])
	}

	@Test
	fun testConcurrentWrites() = runBlocking {
		val jobs = (1..20).map { index ->
			launch {
				dataStoreHelper.testWriteInt("concurrent_key", index)
				delay(10.milliseconds)
			}
		}
		jobs.joinAll()

		// Verify last write succeeded (any value 1-20 is acceptable)
		val result = dataStoreHelper.testReadIntValue("concurrent_key")
		assertNotNull("Concurrent writes should result in a value", result)
		assertTrue("Value should be between 1 and 20", result in 1..20)
	}

	@Test
	fun testWriteStringAsync() = runBlocking {
		dataStoreHelper.testWriteStringAsync("async_key", "async_value").join()
		val value = dataStoreHelper.testReadStringValue("async_key")
		assertEquals("async_value", value)
	}

	@Test
	fun testWriteIntAsync() = runBlocking {
		dataStoreHelper.testWriteIntAsync("async_key", 999).join()
		val value = dataStoreHelper.testReadIntValue("async_key")
		assertEquals(999, value)
	}

	@Test
	fun testWriteLongAsync() = runBlocking {
		dataStoreHelper.testWriteLongAsync("async_key", 123123123L).join()
		val value = dataStoreHelper.testReadLongValue("async_key")
		assertEquals(123123123L, value)
	}

	@Test
	fun testWriteDoubleAsync() = runBlocking {
		dataStoreHelper.testWriteDoubleAsync("async_key", 9.99).join()
		val value = dataStoreHelper.testReadDoubleValue("async_key")
		assertEquals(9.99, value ?: 0.0, 0.01)
	}

	@Test
	fun testWriteBooleanAsync() = runBlocking {
		dataStoreHelper.testWriteBooleanAsync("async_key", true).join()
		val value = dataStoreHelper.testReadBooleanValue("async_key")
		assertTrue(value ?: false)
	}

	@Test
	fun testReadIntValueWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadIntValueWithDefault("nonexistent_key", 777)
		assertEquals(777, value)
	}

	@Test
	fun testReadLongValueWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadLongValueWithDefault("nonexistent_key", 888L)
		assertEquals(888L, value)
	}

	@Test
	fun testReadDoubleValueWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadDoubleValueWithDefault("nonexistent_key", 3.14)
		assertEquals(3.14, value, 0.01)
	}

	@Test
	fun testReadBooleanValueWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadBooleanValueWithDefault("nonexistent_key", true)
		assertTrue(value)
	}

	@Test
	fun testReadStringValueWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadStringValueWithDefault("nonexistent_key", "fallback")
		assertEquals("fallback", value)
	}

	@Test
	fun testReadLongFlowWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadLongFlowWithDefault("nonexistent_key", 888L).first()
		assertEquals(888L, value)
	}

	@Test
	fun testReadDoubleFlowWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadDoubleFlowWithDefault("nonexistent_key", 3.14).first()
		assertEquals(3.14, value, 0.01)
	}

	@Test
	fun testReadBooleanFlowWithDefault() = runBlocking {
		val value = dataStoreHelper.testReadBooleanFlowWithDefault("nonexistent_key", true).first()
		assertTrue(value)
	}

	@Test
	fun testReadLocalDateTimeValueWithDefault() = runBlocking {
		val default = java.time.LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		val value = dataStoreHelper.testReadLocalDateTimeValueWithDefault("nonexistent_key", default)
		assertEquals(default, value)
	}

	@Test
	fun testReadLocalDateTimeFlowWithDefault() = runBlocking {
		val default = java.time.LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		val value = dataStoreHelper.testReadLocalDateTimeFlowWithDefault("nonexistent_key", default).first()
		assertEquals(default, value)
	}

	@Test
	fun testReadLocalDateValueWithDefault() = runBlocking {
		val default = java.time.LocalDate.of(2023, 11, 25)
		val value = dataStoreHelper.testReadLocalDateValueWithDefault("nonexistent_key", default)
		assertEquals(default, value)
	}

	@Test
	fun testReadLocalDateFlowWithDefault() = runBlocking {
		val default = java.time.LocalDate.of(2023, 11, 25)
		val value = dataStoreHelper.testReadLocalDateFlowWithDefault("nonexistent_key", default).first()
		assertEquals(default, value)
	}

	@Test
	fun testReadLocalTimeValueWithDefault() = runBlocking {
		val default = java.time.LocalTime.of(14, 30, 45)
		val value = dataStoreHelper.testReadLocalTimeValueWithDefault("nonexistent_key", default)
		assertEquals(default, value)
	}

	@Test
	fun testReadLocalTimeFlowWithDefault() = runBlocking {
		val default = java.time.LocalTime.of(14, 30, 45)
		val value = dataStoreHelper.testReadLocalTimeFlowWithDefault("nonexistent_key", default).first()
		assertEquals(default, value)
	}

	@Test
	fun testWriteAndReadLocalDateTime() = runBlocking {
		val dateTime = java.time.LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		dataStoreHelper.testWriteLocalDateTime("datetime_key", dateTime)
		val value = dataStoreHelper.testReadLocalDateTimeValue("datetime_key")
		assertEquals(dateTime, value)
	}

	@Test
	fun testWriteNullLocalDateTime() = runBlocking {
		dataStoreHelper.testWriteLocalDateTime("datetime_key", java.time.LocalDateTime.now())
		assertNotNull(dataStoreHelper.testReadLocalDateTimeValue("datetime_key"))

		dataStoreHelper.testWriteLocalDateTime("datetime_key", null)
		assertNull(dataStoreHelper.testReadLocalDateTimeValue("datetime_key"))
	}

	@Test
	fun testReadLocalDateTimeFlow() = runBlocking {
		val dateTime = java.time.LocalDateTime.of(2023, 11, 25, 15, 45, 30)
		dataStoreHelper.testWriteLocalDateTime("datetime_key", dateTime)
		val value = dataStoreHelper.testReadLocalDateTimeFlow("datetime_key").first()
		assertEquals(dateTime, value)
	}

	@Test
	fun testWriteLocalDateTimeAsync() = runBlocking {
		val dateTime = java.time.LocalDateTime.of(2023, 12, 1, 8, 0, 0)
		dataStoreHelper.testWriteLocalDateTimeAsync("datetime_key", dateTime).join()
		val value = dataStoreHelper.testReadLocalDateTimeValue("datetime_key")
		assertEquals(dateTime, value)
	}

	@Test
	fun testWriteAndReadLocalDate() = runBlocking {
		val date = java.time.LocalDate.of(2023, 11, 25)
		dataStoreHelper.testWriteLocalDate("date_key", date)
		val value = dataStoreHelper.testReadLocalDateValue("date_key")
		assertEquals(date, value)
	}

	@Test
	fun testWriteNullLocalDate() = runBlocking {
		dataStoreHelper.testWriteLocalDate("date_key", java.time.LocalDate.now())
		assertNotNull(dataStoreHelper.testReadLocalDateValue("date_key"))

		dataStoreHelper.testWriteLocalDate("date_key", null)
		assertNull(dataStoreHelper.testReadLocalDateValue("date_key"))
	}

	@Test
	fun testReadLocalDateFlow() = runBlocking {
		val date = java.time.LocalDate.of(2023, 11, 25)
		dataStoreHelper.testWriteLocalDate("date_key", date)
		val value = dataStoreHelper.testReadLocalDateFlow("date_key").first()
		assertEquals(date, value)
	}

	@Test
	fun testWriteLocalDateAsync() = runBlocking {
		val date = java.time.LocalDate.of(2024, 1, 1)
		dataStoreHelper.testWriteLocalDateAsync("date_key", date).join()
		val value = dataStoreHelper.testReadLocalDateValue("date_key")
		assertEquals(date, value)
	}

	@Test
	fun testWriteAndReadLocalTime() = runBlocking {
		val time = java.time.LocalTime.of(14, 30, 45)
		dataStoreHelper.testWriteLocalTime("time_key", time)
		val value = dataStoreHelper.testReadLocalTimeValue("time_key")
		assertEquals(time, value)
	}

	@Test
	fun testWriteNullLocalTime() = runBlocking {
		dataStoreHelper.testWriteLocalTime("time_key", java.time.LocalTime.now())
		assertNotNull(dataStoreHelper.testReadLocalTimeValue("time_key"))

		dataStoreHelper.testWriteLocalTime("time_key", null)
		assertNull(dataStoreHelper.testReadLocalTimeValue("time_key"))
	}

	@Test
	fun testReadLocalTimeFlow() = runBlocking {
		val time = java.time.LocalTime.of(16, 45, 30)
		dataStoreHelper.testWriteLocalTime("time_key", time)
		val value = dataStoreHelper.testReadLocalTimeFlow("time_key").first()
		assertEquals(time, value)
	}

	@Test
	fun testWriteLocalTimeAsync() = runBlocking {
		val time = java.time.LocalTime.of(9, 15, 0)
		dataStoreHelper.testWriteLocalTimeAsync("time_key", time).join()
		val value = dataStoreHelper.testReadLocalTimeValue("time_key")
		assertEquals(time, value)
	}

	@Test
	fun testWriteAndReadEnum() = runBlocking {
		// Test using property-based access (like NormalDataStore pattern)
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_B
		val value = awaitValue(predicate = { it == TestEnum.VALUE_B }) { dataStoreHelper.testEnumProperty }
		assertEquals(TestEnum.VALUE_B, value)
	}

	@Test
	fun testWriteNullEnum() = runBlocking {
		// Test null handling via direct method calls
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_B
		assertEquals(
			TestEnum.VALUE_B,
			awaitValue(predicate = { it == TestEnum.VALUE_B }) { dataStoreHelper.testEnumProperty },
		)

		dataStoreHelper.testEnumProperty = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.testEnumProperty })
	}

	@Test
	fun testReadEnumFlow() = runBlocking {
		// Test Flow-based enum reading via property
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_C
		awaitValue(predicate = { it == TestEnum.VALUE_C }) { dataStoreHelper.testEnumProperty }
		assertEquals(TestEnum.VALUE_C, dataStoreHelper.testEnumPropertyFlow.first())
	}

	@Test
	fun testReadEnumFlowWithDefault() = runBlocking {
		// Test that non-existent enum returns null from Flow
		dataStoreHelper.testClearPrefs()
		val value = dataStoreHelper.testEnumPropertyFlow.first()
		assertNull(value)
	}

	@Test
	fun testReadEnumValueWithDefault() = runBlocking {
		// Test using the default value getter property
		dataStoreHelper.testClearPrefs()
		val value = dataStoreHelper.testEnumPropertyWithDefault
		assertEquals(TestEnum.VALUE_A, value)
	}

	// Property delegate tests
	@Test
	fun testIntPrefDelegateReturnsDefaultWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals(-1, dataStoreHelper.delegateInt)
	}

	@Test
	fun testIntPrefDelegateRoundTripsValue() = runBlocking {
		dataStoreHelper.delegateInt = 42
		val value = awaitValue(predicate = { it == 42 }) { dataStoreHelper.delegateInt }
		assertEquals(42, value)
	}

	@Test
	fun testNullableIntPrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableInt)
	}

	@Test
	fun testNullableIntPrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableInt = 7
		assertEquals(7, awaitValue(predicate = { it == 7 }) { dataStoreHelper.delegateNullableInt })

		dataStoreHelper.delegateNullableInt = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableInt })
	}

	@Test
	fun testIntPrefFlowEmitsDelegateWrites() = runBlocking {
		dataStoreHelper.delegateInt = 5
		awaitValue(predicate = { it == 5 }) { dataStoreHelper.delegateInt }
		assertEquals(5, dataStoreHelper.delegateIntFlow.first())
	}

	@Test
	fun testEnumPrefDelegateRoundTrip() = runBlocking {
		dataStoreHelper.delegateEnum = TestEnum.VALUE_C
		val value = awaitValue(predicate = { it == TestEnum.VALUE_C }) { dataStoreHelper.delegateEnum }
		assertEquals(TestEnum.VALUE_C, value)
	}

	@Test
	fun testEnumPrefDelegateFallsBackToDefault() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals(TestEnum.VALUE_A, dataStoreHelper.delegateEnum)
	}

	// String delegate
	@Test
	fun testStringPrefDelegateReturnsDefaultWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals("fallback", dataStoreHelper.delegateString)
	}

	@Test
	fun testStringPrefDelegateRoundTripsValue() = runBlocking {
		dataStoreHelper.delegateString = "hello"
		val value = awaitValue(predicate = { it == "hello" }) { dataStoreHelper.delegateString }
		assertEquals("hello", value)
	}

	@Test
	fun testNullableStringPrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableString)
	}

	@Test
	fun testNullableStringPrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableString = "temp"
		assertEquals("temp", awaitValue(predicate = { it == "temp" }) { dataStoreHelper.delegateNullableString })

		dataStoreHelper.delegateNullableString = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableString })
	}

	@Test
	fun testStringPrefFlowEmitsDelegateWrites() = runBlocking {
		dataStoreHelper.delegateString = "flowed"
		awaitValue(predicate = { it == "flowed" }) { dataStoreHelper.delegateString }
		assertEquals("flowed", dataStoreHelper.delegateStringFlow.first())
	}

	// Long delegate
	@Test
	fun testLongPrefDelegateReturnsDefaultWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals(-1L, dataStoreHelper.delegateLong)
	}

	@Test
	fun testLongPrefDelegateRoundTripsValue() = runBlocking {
		dataStoreHelper.delegateLong = 123456789L
		val value = awaitValue(predicate = { it == 123456789L }) { dataStoreHelper.delegateLong }
		assertEquals(123456789L, value)
	}

	@Test
	fun testLongPrefFlowEmitsDelegateWrites() = runBlocking {
		dataStoreHelper.delegateLong = 42L
		awaitValue(predicate = { it == 42L }) { dataStoreHelper.delegateLong }
		assertEquals(42L, dataStoreHelper.delegateLongFlow.first())
	}

	// Double delegate
	@Test
	fun testDoublePrefDelegateReturnsDefaultWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals(-1.0, dataStoreHelper.delegateDouble, 0.00001)
	}

	@Test
	fun testDoublePrefDelegateRoundTripsValue() = runBlocking {
		dataStoreHelper.delegateDouble = 3.14159
		val value = awaitValue(predicate = { it == 3.14159 }) { dataStoreHelper.delegateDouble }
		assertEquals(3.14159, value, 0.00001)
	}

	@Test
	fun testDoublePrefFlowEmitsDelegateWrites() = runBlocking {
		dataStoreHelper.delegateDouble = 2.71828
		awaitValue(predicate = { it == 2.71828 }) { dataStoreHelper.delegateDouble }
		assertEquals(2.71828, dataStoreHelper.delegateDoubleFlow.first(), 0.00001)
	}

	// Boolean delegate
	@Test
	fun testBooleanPrefDelegateReturnsDefaultWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertEquals(false, dataStoreHelper.delegateBoolean)
	}

	@Test
	fun testBooleanPrefDelegateRoundTripsValue() = runBlocking {
		dataStoreHelper.delegateBoolean = true
		val value = awaitValue(predicate = { it }) { dataStoreHelper.delegateBoolean }
		assertTrue(value)
	}

	@Test
	fun testBooleanPrefFlowEmitsDelegateWrites() = runBlocking {
		dataStoreHelper.delegateBoolean = true
		awaitValue(predicate = { it }) { dataStoreHelper.delegateBoolean }
		assertEquals(true, dataStoreHelper.delegateBooleanFlow.first())
	}

	// Nullable Long delegate
	@Test
	fun testNullableLongPrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableLong)
	}

	@Test
	fun testNullableLongPrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableLong = 99L
		assertEquals(99L, awaitValue(predicate = { it == 99L }) { dataStoreHelper.delegateNullableLong })

		dataStoreHelper.delegateNullableLong = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableLong })
	}

	// Nullable Double delegate
	@Test
	fun testNullableDoublePrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableDouble)
	}

	@Test
	fun testNullableDoublePrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableDouble = 1.5
		assertEquals(1.5, awaitValue(predicate = { it == 1.5 }) { dataStoreHelper.delegateNullableDouble } ?: 0.0, 0.00001)

		dataStoreHelper.delegateNullableDouble = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableDouble })
	}

	// Nullable Boolean delegate
	@Test
	fun testNullableBooleanPrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableBoolean)
	}

	@Test
	fun testNullableBooleanPrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableBoolean = true
		assertEquals(true, awaitValue(predicate = { it == true }) { dataStoreHelper.delegateNullableBoolean })

		dataStoreHelper.delegateNullableBoolean = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableBoolean })
	}

	// Nullable Enum delegate
	@Test
	fun testNullableEnumPrefDelegateReturnsNullWhenAbsent() = runBlocking {
		dataStoreHelper.testClearPrefs()
		assertNull(dataStoreHelper.delegateNullableEnum)
	}

	@Test
	fun testNullableEnumPrefDelegateRemovesKeyOnNull() = runBlocking {
		dataStoreHelper.delegateNullableEnum = TestEnum.VALUE_B
		assertEquals(
			TestEnum.VALUE_B,
			awaitValue(predicate = { it == TestEnum.VALUE_B }) { dataStoreHelper.delegateNullableEnum },
		)

		dataStoreHelper.delegateNullableEnum = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateNullableEnum })
	}

	// LocalDateTime delegate
	@Test
	fun testLocalDateTimePrefDelegateRoundTripsValue() = runBlocking {
		val dateTime = java.time.LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		dataStoreHelper.delegateLocalDateTime = dateTime
		val value = awaitValue(predicate = { it == dateTime }) { dataStoreHelper.delegateLocalDateTime }
		assertEquals(dateTime, value)
	}

	@Test
	fun testLocalDateTimePrefDelegateRemovesKeyOnNull() = runBlocking {
		val dateTime = java.time.LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		dataStoreHelper.delegateLocalDateTime = dateTime
		assertNotNull(awaitValue(predicate = { it == dateTime }) { dataStoreHelper.delegateLocalDateTime })

		dataStoreHelper.delegateLocalDateTime = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateLocalDateTime })
	}

	// LocalDate delegate
	@Test
	fun testLocalDatePrefDelegateRoundTripsValue() = runBlocking {
		val date = java.time.LocalDate.of(2023, 11, 25)
		dataStoreHelper.delegateLocalDate = date
		val value = awaitValue(predicate = { it == date }) { dataStoreHelper.delegateLocalDate }
		assertEquals(date, value)
	}

	@Test
	fun testLocalDatePrefDelegateRemovesKeyOnNull() = runBlocking {
		val date = java.time.LocalDate.of(2023, 11, 25)
		dataStoreHelper.delegateLocalDate = date
		assertNotNull(awaitValue(predicate = { it == date }) { dataStoreHelper.delegateLocalDate })

		dataStoreHelper.delegateLocalDate = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateLocalDate })
	}

	// LocalTime delegate
	@Test
	fun testLocalTimePrefDelegateRoundTripsValue() = runBlocking {
		val time = java.time.LocalTime.of(14, 30, 45)
		dataStoreHelper.delegateLocalTime = time
		val value = awaitValue(predicate = { it == time }) { dataStoreHelper.delegateLocalTime }
		assertEquals(time, value)
	}

	@Test
	fun testLocalTimePrefDelegateRemovesKeyOnNull() = runBlocking {
		val time = java.time.LocalTime.of(14, 30, 45)
		dataStoreHelper.delegateLocalTime = time
		assertNotNull(awaitValue(predicate = { it == time }) { dataStoreHelper.delegateLocalTime })

		dataStoreHelper.delegateLocalTime = null
		assertNull(awaitValue(predicate = { it == null }) { dataStoreHelper.delegateLocalTime })
	}

	enum class TestEnum {
		VALUE_A, VALUE_B, VALUE_C
	}

	// Integration Test - Simulates Real-World Usage
	@Test
	fun testCompleteUserSessionFlow() = runBlocking {
		// Simulate storing user session data
		dataStoreHelper.testWriteString("user_name", "John Doe")
		dataStoreHelper.testWriteString("user_email", "john@example.com")
		dataStoreHelper.testWriteInt("user_age", 30)
		dataStoreHelper.testWriteLong("user_id", 123456789L)
		dataStoreHelper.testWriteBoolean("is_premium", true)
		dataStoreHelper.testWriteDouble("account_balance", 1234.56)

		// Verify all data persisted correctly
		assertEquals("John Doe", dataStoreHelper.testReadStringValue("user_name"))
		assertEquals("john@example.com", dataStoreHelper.testReadStringValue("user_email"))
		assertEquals(30, dataStoreHelper.testReadIntValue("user_age"))
		assertEquals(123456789L, dataStoreHelper.testReadLongValue("user_id"))
		assertEquals(true, dataStoreHelper.testReadBooleanValue("is_premium"))
		assertEquals(1234.56, dataStoreHelper.testReadDoubleValue("account_balance") ?: 0.0, 0.01)

		// Simulate logout - clear all data
		dataStoreHelper.testClearPrefs()

		// Verify all data cleared
		assertNull(dataStoreHelper.testReadStringValue("user_name"))
		assertNull(dataStoreHelper.testReadStringValue("user_email"))
		assertNull(dataStoreHelper.testReadIntValue("user_age"))
		assertNull(dataStoreHelper.testReadLongValue("user_id"))
		assertNull(dataStoreHelper.testReadBooleanValue("is_premium"))
		assertNull(dataStoreHelper.testReadDoubleValue("account_balance"))
	}

	private class TestDataStoreHelper(context: Context) : BaseDataStoreHelper(context, "test_datastore_${System.nanoTime()}") {

		// Basic write/read methods
		suspend fun testWriteString(key: String, value: String?) = writeString(key, value)
		fun testReadStringValue(key: String) = readStringValue(key)
		fun testReadStringValueWithDefault(key: String, default: String) = readStringValue(key, default)
		fun testReadStringFlow(key: String) = readString(key)
		fun testReadStringFlowWithDefault(key: String, default: String) = readString(key, default)
		fun testWriteStringAsync(key: String, value: String?) = writeStringAsync(key, value)

		suspend fun testWriteInt(key: String, value: Int?) = writeInt(key, value)
		fun testReadIntValue(key: String) = readIntValue(key)
		fun testReadIntValueWithDefault(key: String, default: Int) = readIntValue(key, default)
		fun testReadIntFlow(key: String) = readInt(key)
		fun testReadIntFlowWithDefault(key: String, default: Int) = readInt(key, default)
		fun testWriteIntAsync(key: String, value: Int?) = writeIntAsync(key, value)

		suspend fun testWriteLong(key: String, value: Long?) = writeLong(key, value)
		fun testReadLongValue(key: String) = readLongValue(key)
		fun testReadLongValueWithDefault(key: String, default: Long) = readLongValue(key, default)
		fun testReadLongFlow(key: String) = readLong(key)
		fun testReadLongFlowWithDefault(key: String, default: Long) = readLong(key, default)
		fun testWriteLongAsync(key: String, value: Long?) = writeLongAsync(key, value)

		suspend fun testWriteDouble(key: String, value: Double?) = writeDouble(key, value)
		fun testReadDoubleValue(key: String) = readDoubleValue(key)
		fun testReadDoubleValueWithDefault(key: String, default: Double) = readDoubleValue(key, default)
		fun testReadDoubleFlow(key: String) = readDouble(key)
		fun testReadDoubleFlowWithDefault(key: String, default: Double) = readDouble(key, default)
		fun testWriteDoubleAsync(key: String, value: Double?) = writeDoubleAsync(key, value)

		suspend fun testWriteDate(key: String, value: java.util.Date?) = writeDate(key, value)
		fun testReadDateValue(key: String) = readDateValue(key)
		fun testReadDateValueWithDefault(key: String, default: java.util.Date) = readDateValue(key, default)
		fun testReadDateFlow(key: String) = readDate(key)
		fun testReadDateFlowWithDefault(key: String, default: java.util.Date) = readDate(key, default)
		fun testWriteDateAsync(key: String, value: java.util.Date?) = writeDateAsync(key, value)

		suspend fun testWriteBoolean(key: String, value: Boolean?) = writeBoolean(key, value)
		fun testReadBooleanValue(key: String) = readBooleanValue(key)
		fun testReadBooleanValueWithDefault(key: String, default: Boolean) = readBooleanValue(key, default)
		fun testReadBooleanFlow(key: String) = readBoolean(key)
		fun testReadBooleanFlowWithDefault(key: String, default: Boolean) = readBoolean(key, default)
		fun testWriteBooleanAsync(key: String, value: Boolean?) = writeBooleanAsync(key, value)

		// LocalDateTime methods
		suspend fun testWriteLocalDateTime(key: String, value: java.time.LocalDateTime?) = writeLocalDateTime(key, value)
		fun testReadLocalDateTimeValue(key: String) = readLocalDateTimeValue(key)
		fun testReadLocalDateTimeValueWithDefault(key: String, default: java.time.LocalDateTime) = readLocalDateTimeValue(key, default)
		fun testReadLocalDateTimeFlow(key: String) = readLocalDateTime(key)
		fun testReadLocalDateTimeFlowWithDefault(key: String, default: java.time.LocalDateTime) = readLocalDateTime(key, default)
		fun testWriteLocalDateTimeAsync(key: String, value: java.time.LocalDateTime?) = writeLocalDateTimeAsync(key, value)

		// LocalDate methods
		suspend fun testWriteLocalDate(key: String, value: java.time.LocalDate?) = writeLocalDate(key, value)
		fun testReadLocalDateValue(key: String) = readLocalDateValue(key)
		fun testReadLocalDateValueWithDefault(key: String, default: java.time.LocalDate) = readLocalDateValue(key, default)
		fun testReadLocalDateFlow(key: String) = readLocalDate(key)
		fun testReadLocalDateFlowWithDefault(key: String, default: java.time.LocalDate) = readLocalDate(key, default)
		fun testWriteLocalDateAsync(key: String, value: java.time.LocalDate?) = writeLocalDateAsync(key, value)

		// LocalTime methods
		suspend fun testWriteLocalTime(key: String, value: java.time.LocalTime?) = writeLocalTime(key, value)
		fun testReadLocalTimeValue(key: String) = readLocalTimeValue(key)
		fun testReadLocalTimeValueWithDefault(key: String, default: java.time.LocalTime) = readLocalTimeValue(key, default)
		fun testReadLocalTimeFlow(key: String) = readLocalTime(key)
		fun testReadLocalTimeFlowWithDefault(key: String, default: java.time.LocalTime) = readLocalTime(key, default)
		fun testWriteLocalTimeAsync(key: String, value: java.time.LocalTime?) = writeLocalTimeAsync(key, value)

		// Enum property-based access - Uses actual BaseDataStoreHelper enum methods
		// This pattern matches NormalDataStore and actually tests the base class methods!
		var testEnumProperty: TestEnum?
			get() = readEnumValue<TestEnum>(KEY_TEST_ENUM)
			set(value) {
				writeEnumAsync(KEY_TEST_ENUM, value)
			}

		// Flow-based enum property - directly uses base class readEnum method
		val testEnumPropertyFlow: Flow<TestEnum?> = readEnum<TestEnum>(KEY_TEST_ENUM)

		// Property with default value - uses base class readEnumValue with default
		var testEnumPropertyWithDefault: TestEnum
			get() = readEnumValue(KEY_TEST_ENUM, TestEnum.VALUE_A)
			set(value) {
				writeEnumAsync(KEY_TEST_ENUM, value)
			}

		// Delegate-backed properties under test
		var delegateInt by intPref(KEY_DELEGATE_INT, defaultValue = -1)
		var delegateNullableInt by intPref(KEY_DELEGATE_NULLABLE_INT)
		val delegateIntFlow = intPrefFlow(KEY_DELEGATE_INT, defaultValue = -1)
		var delegateEnum by enumPref(KEY_DELEGATE_ENUM, default = TestEnum.VALUE_A)

		var delegateString by stringPref(KEY_DELEGATE_STRING, defaultValue = "fallback")
		var delegateNullableString by stringPref(KEY_DELEGATE_NULLABLE_STRING)
		val delegateStringFlow = stringPrefFlow(KEY_DELEGATE_STRING, defaultValue = "fallback")

		var delegateLong by longPref(KEY_DELEGATE_LONG, defaultValue = -1L)
		var delegateNullableLong by longPref(KEY_DELEGATE_NULLABLE_LONG)
		val delegateLongFlow = longPrefFlow(KEY_DELEGATE_LONG, defaultValue = -1L)

		var delegateDouble by doublePref(KEY_DELEGATE_DOUBLE, defaultValue = -1.0)
		var delegateNullableDouble by doublePref(KEY_DELEGATE_NULLABLE_DOUBLE)
		val delegateDoubleFlow = doublePrefFlow(KEY_DELEGATE_DOUBLE, defaultValue = -1.0)

		var delegateBoolean by booleanPref(KEY_DELEGATE_BOOLEAN, defaultValue = false)
		var delegateNullableBoolean by booleanPref(KEY_DELEGATE_NULLABLE_BOOLEAN)
		val delegateBooleanFlow = booleanPrefFlow(KEY_DELEGATE_BOOLEAN, defaultValue = false)

		var delegateNullableEnum by enumPref<TestEnum>(KEY_DELEGATE_NULLABLE_ENUM)

		var delegateLocalDateTime by localDateTimePref(KEY_DELEGATE_LDT)
		var delegateLocalDate by localDatePref(KEY_DELEGATE_LD)
		var delegateLocalTime by localTimePref(KEY_DELEGATE_LT)

		companion object {
			private const val KEY_TEST_ENUM = "test_enum_key"
			private const val KEY_DELEGATE_INT = "delegate_int_key"
			private const val KEY_DELEGATE_NULLABLE_INT = "delegate_nullable_int_key"
			private const val KEY_DELEGATE_ENUM = "delegate_enum_key"
			private const val KEY_DELEGATE_STRING = "delegate_string_key"
			private const val KEY_DELEGATE_NULLABLE_STRING = "delegate_nullable_string_key"
			private const val KEY_DELEGATE_LONG = "delegate_long_key"
			private const val KEY_DELEGATE_NULLABLE_LONG = "delegate_nullable_long_key"
			private const val KEY_DELEGATE_DOUBLE = "delegate_double_key"
			private const val KEY_DELEGATE_NULLABLE_DOUBLE = "delegate_nullable_double_key"
			private const val KEY_DELEGATE_BOOLEAN = "delegate_boolean_key"
			private const val KEY_DELEGATE_NULLABLE_BOOLEAN = "delegate_nullable_boolean_key"
			private const val KEY_DELEGATE_NULLABLE_ENUM = "delegate_nullable_enum_key"
			private const val KEY_DELEGATE_LDT = "delegate_ldt_key"
			private const val KEY_DELEGATE_LD = "delegate_ld_key"
			private const val KEY_DELEGATE_LT = "delegate_lt_key"
		}

		suspend fun testClearPrefs() = clearPrefs()
	}
}
