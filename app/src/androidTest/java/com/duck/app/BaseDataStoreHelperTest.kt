package com.duck.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BaseDataStoreHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

		delay(50) // Initial null emission
		dataStoreHelper.testWriteString("multi_flow_key", "first")
		delay(50)
		dataStoreHelper.testWriteString("multi_flow_key", "second")
		delay(50)
		dataStoreHelper.testWriteString("multi_flow_key", "third")
		delay(50)

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
				delay(10)
			}
		}
		jobs.forEach { it.join() }

		// Verify last write succeeded (any value 1-20 is acceptable)
		val result = dataStoreHelper.testReadIntValue("concurrent_key")
		assertNotNull("Concurrent writes should result in a value", result)
		assertTrue("Value should be between 1 and 20", result in 1..20)
	}

	@Test
	fun testWriteStringAsync() = runBlocking {
		dataStoreHelper.testWriteStringAsync("async_key", "async_value")
		// Give async operation time to complete
		kotlinx.coroutines.delay(100)
		val value = dataStoreHelper.testReadStringValue("async_key")
		assertEquals("async_value", value)
	}

	@Test
	fun testWriteIntAsync() = runBlocking {
		dataStoreHelper.testWriteIntAsync("async_key", 999)
		kotlinx.coroutines.delay(100)
		val value = dataStoreHelper.testReadIntValue("async_key")
		assertEquals(999, value)
	}

	@Test
	fun testWriteLongAsync() = runBlocking {
		dataStoreHelper.testWriteLongAsync("async_key", 123123123L)
		kotlinx.coroutines.delay(100)
		val value = dataStoreHelper.testReadLongValue("async_key")
		assertEquals(123123123L, value)
	}

	@Test
	fun testWriteDoubleAsync() = runBlocking {
		dataStoreHelper.testWriteDoubleAsync("async_key", 9.99)
		kotlinx.coroutines.delay(100)
		val value = dataStoreHelper.testReadDoubleValue("async_key")
		assertEquals(9.99, value ?: 0.0, 0.01)
	}

	@Test
	fun testWriteBooleanAsync() = runBlocking {
		dataStoreHelper.testWriteBooleanAsync("async_key", true)
		kotlinx.coroutines.delay(100)
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
		dataStoreHelper.testWriteLocalDateTimeAsync("datetime_key", dateTime)
		kotlinx.coroutines.delay(100)
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
		dataStoreHelper.testWriteLocalDateAsync("date_key", date)
		kotlinx.coroutines.delay(100)
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
		dataStoreHelper.testWriteLocalTimeAsync("time_key", time)
		kotlinx.coroutines.delay(100)
		val value = dataStoreHelper.testReadLocalTimeValue("time_key")
		assertEquals(time, value)
	}

	@Test
	fun testWriteAndReadEnum() = runBlocking {
		// Test using property-based access (like NormalDataStore pattern)
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_B
		delay(100)
		assertEquals(TestEnum.VALUE_B, dataStoreHelper.testEnumProperty)
	}

	@Test
	fun testWriteNullEnum() = runBlocking {
		// Test null handling via direct method calls
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_B
		delay(100)
		assertEquals(TestEnum.VALUE_B, dataStoreHelper.testEnumProperty)

		dataStoreHelper.testEnumProperty = null
		delay(100)
		assertNull(dataStoreHelper.testEnumProperty)
	}

	@Test
	fun testReadEnumFlow() = runBlocking {
		// Test Flow-based enum reading via property
		dataStoreHelper.testEnumProperty = TestEnum.VALUE_C
		delay(100)
		val value = dataStoreHelper.testEnumPropertyFlow.first()
		assertEquals(TestEnum.VALUE_C, value)
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

		companion object {
			private const val KEY_TEST_ENUM = "test_enum_key"
		}

		suspend fun testClearPrefs() = clearPrefs()
	}
}
