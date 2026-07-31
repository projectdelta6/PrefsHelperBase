package com.duck.app

import android.content.SharedPreferences
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date

class BasePrefsHelperTest {

	@Mock
	lateinit var mockSharedPreferences: SharedPreferences

	@Mock
	lateinit var mockEditor: SharedPreferences.Editor

	private lateinit var prefsHelper: TestPrefsHelper

	@Before
	fun setUp() {
		MockitoAnnotations.openMocks(this)
		`when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
		`when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
		`when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
		`when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
		`when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
		`when`(mockEditor.remove(anyString())).thenReturn(mockEditor)
		`when`(mockEditor.clear()).thenReturn(mockEditor)
		prefsHelper = TestPrefsHelper()
	}

	@Test
	fun testGetString() {
		`when`(mockSharedPreferences.getString("key", "default")).thenReturn("value")
		assertEquals("value", prefsHelper.getString("key", "default"))
	}

	@Test
	fun testSetString() {
		prefsHelper.setString("key", "value")
		verify(mockEditor).putString("key", "value")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetInt() {
		`when`(mockSharedPreferences.getInt("key", 0)).thenReturn(42)
		assertEquals(42, prefsHelper.getInt("key", 0))
	}

	@Test
	fun testSetInt() {
		prefsHelper.setInt("key", 42)
		verify(mockEditor).putInt("key", 42)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLong() {
		`when`(mockSharedPreferences.getLong("key", 0L)).thenReturn(123456789L)
		assertEquals(123456789L, prefsHelper.getLong("key", 0L))
	}

	@Test
	fun testSetLong() {
		prefsHelper.setLong("key", 123456789L)
		verify(mockEditor).putLong("key", 123456789L)
		verify(mockEditor).apply()
	}

	@Test
	fun testSetDouble() {
		prefsHelper.setDouble("key", 3.14)
		// SharedPreferences has no double primitive — stored as raw IEEE-754 bits in a Long.
		verify(mockEditor).putLong("key", 3.14.toRawBits())
		verify(mockEditor).apply()
	}

	@Test
	fun testGetDouble() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("key", 0L)).thenReturn(3.14.toRawBits())
		assertEquals(3.14, prefsHelper.getDouble("key"), 0.0)
	}

	@Test
	fun testGetDoubleReturnsDefaultWhenKeyAbsent() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(false)
		assertEquals(2.5, prefsHelper.getDouble("key", 2.5), 0.0)
	}

	/**
	 * The raw-bits encoding must round-trip every double exactly, including the values a
	 * lossy Float-based encoding would mangle. Mirrors the Double coverage
	 * `BaseDataStoreHelperTest` has, so both helpers are held to the same standard.
	 */
	@Test
	fun testDoubleBitEncodingRoundTripsExactly() {
		val values = listOf(
			0.0,
			-0.0,
			3.14159265358979,
			Double.MAX_VALUE,
			Double.MIN_VALUE,
			Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY,
		)
		for (value in values) {
			assertEquals(value, Double.fromBits(value.toRawBits()), 0.0)
		}
		assertTrue(Double.fromBits(Double.NaN.toRawBits()).isNaN())
	}

	@Test
	fun testSetFloat() {
		prefsHelper.setFloat("key", 1.5f)
		verify(mockEditor).putFloat("key", 1.5f)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetFloat() {
		`when`(mockSharedPreferences.getFloat("key", 0f)).thenReturn(2.5f)
		assertEquals(2.5f, prefsHelper.getFloat("key"), 0f)
	}

	@Test
	fun testSetStringSet() {
		prefsHelper.setStringSet("key", setOf("a", "b"))
		verify(mockEditor).putStringSet("key", linkedSetOf("a", "b"))
		verify(mockEditor).apply()
	}

	@Test
	fun testGetStringSet() {
		`when`(mockSharedPreferences.getStringSet("key", null)).thenReturn(linkedSetOf("a", "b"))
		assertEquals(setOf("a", "b"), prefsHelper.getStringSet("key"))
	}

	@Test
	fun testGetStringSetReturnsDefaultWhenAbsent() {
		`when`(mockSharedPreferences.getStringSet("key", null)).thenReturn(null)
		assertEquals(setOf("fallback"), prefsHelper.getStringSet("key", setOf("fallback")))
	}

	/**
	 * [android.content.SharedPreferences.getStringSet] documents its result as one callers must not
	 * modify, with undefined behaviour if they do. The helper hands back a copy so callers can't
	 * fall into that; mutating what we return must not touch what the platform gave us.
	 */
	@Test
	fun testGetStringSetReturnsDefensiveCopy() {
		val backing = linkedSetOf("a", "b")
		`when`(mockSharedPreferences.getStringSet("key", null)).thenReturn(backing)

		val returned = prefsHelper.getStringSet("key") as MutableSet<String>
		returned.add("c")

		assertEquals(setOf("a", "b"), backing)
	}

	/**
	 * Counterpart to the read side: the platform keeps a reference to the set it is handed, so the
	 * helper must store a copy rather than the caller's instance.
	 */
	@Test
	fun testSetStringSetStoresDefensiveCopy() {
		val caller = mutableSetOf("a")
		prefsHelper.setStringSet("key", caller)
		caller.add("mutated-after-write")

		verify(mockEditor).putStringSet("key", linkedSetOf("a"))
	}

	@Test
	fun testSetInstant() {
		prefsHelper.setInstant("key", java.time.Instant.ofEpochMilli(1_700_000_000_000L))
		verify(mockEditor).putLong("key", 1_700_000_000_000L)
		verify(mockEditor).apply()
	}

	@Test
	fun testSetInstantNullRemovesKey() {
		prefsHelper.setInstant("key", null)
		verify(mockEditor).remove("key")
	}

	@Test
	fun testGetInstant() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("key", 0L)).thenReturn(1_700_000_000_000L)
		assertEquals(java.time.Instant.ofEpochMilli(1_700_000_000_000L), prefsHelper.getInstant("key"))
	}

	@Test
	fun testGetInstantReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(false)
		assertNull(prefsHelper.getInstant("key"))
	}

	@Test
	fun testSetEnumSet() {
		prefsHelper.setEnumSet("key", setOf(TestEnum.VALUE_A, TestEnum.VALUE_B))
		verify(mockEditor).putStringSet("key", linkedSetOf("VALUE_A", "VALUE_B"))
	}

	@Test
	fun testGetEnumSet() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(true)
		`when`(mockSharedPreferences.getStringSet("key", null)).thenReturn(linkedSetOf("VALUE_A", "VALUE_C"))
		assertEquals(setOf(TestEnum.VALUE_A, TestEnum.VALUE_C), prefsHelper.getEnumSet<TestEnum>("key"))
	}

	/**
	 * Removing an enum constant must not break reads of data stored before the removal — unknown
	 * names are dropped rather than throwing.
	 */
	@Test
	fun testGetEnumSetDropsUnknownNames() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(true)
		`when`(mockSharedPreferences.getStringSet("key", null))
			.thenReturn(linkedSetOf("VALUE_A", "REMOVED_IN_A_LATER_VERSION"))
		assertEquals(setOf(TestEnum.VALUE_A), prefsHelper.getEnumSet<TestEnum>("key"))
	}

	@Test
	fun testGetEnumSetReturnsDefaultWhenAbsent() {
		`when`(mockSharedPreferences.getStringSet("key", null)).thenReturn(null)
		assertEquals(setOf(TestEnum.VALUE_B), prefsHelper.getEnumSet("key", setOf(TestEnum.VALUE_B)))
	}

	@Test
	fun testGetBoolean() {
		`when`(mockSharedPreferences.getBoolean("key", false)).thenReturn(true)
		assertTrue(prefsHelper.getBoolean("key", false))
	}

	@Test
	fun testSetBoolean() {
		prefsHelper.setBoolean("key", true)
		verify(mockEditor).putBoolean("key", true)
		verify(mockEditor).apply()
	}

	@Test
	fun testClearPrefs() {
		runBlocking {
			prefsHelper.clearPrefs()
		}
		verify(mockEditor).clear()
		verify(mockEditor).commit()
	}

	@Test
	fun testContains() {
		`when`(mockSharedPreferences.contains("key")).thenReturn(true)
		assertTrue(prefsHelper.contains("key"))
	}

	@Test
	fun testGetStringWithDefault() {
		`when`(mockSharedPreferences.getString("key", "default")).thenReturn("default")
		assertEquals("default", prefsHelper.getString("key", "default"))
	}

	@Test
	fun testGetStringWithNullReturnsDefault() {
		`when`(mockSharedPreferences.getString("key", "default")).thenReturn(null)
		assertEquals("default", prefsHelper.getString("key", "default"))
	}

	@Test
	fun testSetDate() {
		val date = Date(1234567890000L)
		prefsHelper.setDate("date_key", date)
		verify(mockEditor).putLong("date_key", 1234567890000L)
		verify(mockEditor).apply()
	}

	@Test
	fun testSetNullDate() {
		prefsHelper.setDate("date_key", null)
		verify(mockEditor).remove("date_key")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetDate() {
		`when`(mockSharedPreferences.contains("date_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("date_key", 0L)).thenReturn(1234567890000L)
		val date = prefsHelper.getDate("date_key")
		assertNotNull(date)
		assertEquals(1234567890000L, date?.time)
	}

	@Test
	fun testGetDateReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.contains("date_key")).thenReturn(false)
		assertNull(prefsHelper.getDate("date_key"))
	}

	@Test
	fun testSetLocalDateTime() {
		val dateTime = LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		prefsHelper.setLocalDateTime("datetime_key", dateTime)
		verify(mockEditor).putLong("datetime_key", dateTime.toEpochSecond(ZoneOffset.UTC))
		verify(mockEditor).apply()
	}

	@Test
	fun testSetNullLocalDateTime() {
		prefsHelper.setLocalDateTime("datetime_key", null)
		verify(mockEditor).remove("datetime_key")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalDateTime() {
		val dateTime = LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		val epochSecond = dateTime.toEpochSecond(ZoneOffset.UTC)
		`when`(mockSharedPreferences.contains("datetime_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("datetime_key", 0L)).thenReturn(epochSecond)
		val result = prefsHelper.getLocalDateTime("datetime_key")
		assertNotNull(result)
		assertEquals(dateTime, result)
	}

	@Test
	fun testGetLocalDateTimeReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.contains("datetime_key")).thenReturn(false)
		assertNull(prefsHelper.getLocalDateTime("datetime_key"))
	}

	@Test
	fun testSetLocalDate() {
		val date = LocalDate.of(2023, 11, 25)
		prefsHelper.setLocalDate("date_key", date)
		verify(mockEditor).putLong("date_key", date.toEpochDay())
		verify(mockEditor).apply()
	}

	@Test
	fun testSetNullLocalDate() {
		prefsHelper.setLocalDate("date_key", null)
		verify(mockEditor).remove("date_key")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalDate() {
		val date = LocalDate.of(2023, 11, 25)
		`when`(mockSharedPreferences.contains("date_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("date_key", 0L)).thenReturn(date.toEpochDay())
		val result = prefsHelper.getLocalDate("date_key")
		assertNotNull(result)
		assertEquals(date, result)
	}

	@Test
	fun testGetLocalDateReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.contains("date_key")).thenReturn(false)
		assertNull(prefsHelper.getLocalDate("date_key"))
	}

	/**
	 * Epoch day -1 is 1969-12-31, which the pre-2.0 sentinel made unstorable. It is an ordinary
	 * value now, so it must NOT be mistaken for "not set".
	 */
	@Test
	fun testGetLocalDateReadsEpochDayMinusOneAsARealDate() {
		`when`(mockSharedPreferences.contains("date_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("date_key", 0L)).thenReturn(-1L)
		assertEquals(LocalDate.of(1969, 12, 31), prefsHelper.getLocalDate("date_key"))
	}

	@Test
	fun testSetLocalTime() {
		val time = LocalTime.of(14, 30, 45)
		prefsHelper.setLocalTime("time_key", time)
		verify(mockEditor).putLong("time_key", time.toSecondOfDay().toLong())
		verify(mockEditor).apply()
	}

	@Test
	fun testSetNullLocalTime() {
		prefsHelper.setLocalTime("time_key", null)
		verify(mockEditor).remove("time_key")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalTime() {
		val time = LocalTime.of(14, 30, 45)
		`when`(mockSharedPreferences.contains("time_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("time_key", 0L)).thenReturn(time.toSecondOfDay().toLong())
		val result = prefsHelper.getLocalTime("time_key")
		assertNotNull(result)
		assertEquals(time, result)
	}

	@Test
	fun testGetLocalTimeReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.contains("time_key")).thenReturn(false)
		assertNull(prefsHelper.getLocalTime("time_key"))
	}

	@Test
	fun testSetEnum() {
		prefsHelper.setEnum("enum_key", TestEnum.VALUE_B)
		verify(mockEditor).putString("enum_key", "VALUE_B")
		verify(mockEditor).apply()
	}

	@Test
	fun testSetNullEnum() {
		prefsHelper.setEnum("enum_key", null)
		verify(mockEditor).putString("enum_key", "")
		verify(mockEditor).apply()
	}

	@Test
	fun testGetEnum() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("VALUE_B")
		val result = prefsHelper.getEnum<TestEnum>("enum_key")
		assertEquals(TestEnum.VALUE_B, result)
	}

	@Test
	fun testGetEnumWithDefault() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("")
		val result = prefsHelper.getEnum("enum_key", TestEnum.VALUE_A)
		assertEquals(TestEnum.VALUE_A, result)
	}

	@Test
	fun testGetEnumReturnsNullForInvalidValue() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("INVALID_VALUE")
		val result = prefsHelper.getEnum<TestEnum>("enum_key")
		assertNull(result)
	}

	@Test
	fun testGetEnumReturnsDefaultForInvalidValue() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("INVALID_VALUE")
		val result = prefsHelper.getEnum("enum_key", TestEnum.VALUE_C)
		assertEquals(TestEnum.VALUE_C, result)
	}

	// Edge Case Tests - Boundary Values
	@Test
	fun testIntegerBoundaryValues() {
		`when`(mockSharedPreferences.getInt("max_int", 0)).thenReturn(Int.MAX_VALUE)
		assertEquals(Int.MAX_VALUE, prefsHelper.getInt("max_int", 0))

		`when`(mockSharedPreferences.getInt("min_int", 0)).thenReturn(Int.MIN_VALUE)
		assertEquals(Int.MIN_VALUE, prefsHelper.getInt("min_int", 0))
	}

	@Test
	fun testLongBoundaryValues() {
		`when`(mockSharedPreferences.getLong("max_long", 0L)).thenReturn(Long.MAX_VALUE)
		assertEquals(Long.MAX_VALUE, prefsHelper.getLong("max_long", 0L))

		`when`(mockSharedPreferences.getLong("min_long", 0L)).thenReturn(Long.MIN_VALUE)
		assertEquals(Long.MIN_VALUE, prefsHelper.getLong("min_long", 0L))
	}

	@Test
	fun testEmptyStringHandling() {
		`when`(mockSharedPreferences.getString("empty_key", "")).thenReturn("")
		assertEquals("", prefsHelper.getString("empty_key", ""))
	}

	@Test
	fun testWhitespaceStringHandling() {
		`when`(mockSharedPreferences.getString("whitespace_key", "")).thenReturn("   ")
		assertEquals("   ", prefsHelper.getString("whitespace_key", ""))
	}

	@Test
	fun testSpecialCharactersInValues() {
		val specialChars = "!@#$%^&*()_+-=[]{}|;:',.<>?/~`"
		`when`(mockSharedPreferences.getString("special_key", "")).thenReturn(specialChars)
		assertEquals(specialChars, prefsHelper.getString("special_key", ""))
	}

	@Test
	fun testMultipleOperationsInSequence() {
		prefsHelper.setString("key1", "value1")
		prefsHelper.setInt("key2", 42)
		prefsHelper.setBoolean("key3", true)

		verify(mockEditor, times(3)).apply()
		verify(mockEditor).putString("key1", "value1")
		verify(mockEditor).putInt("key2", 42)
		verify(mockEditor).putBoolean("key3", true)
	}

	// Property delegate tests
	@Test
	fun testStringPrefDelegateGet() {
		`when`(mockSharedPreferences.getString("string_key", "fallback")).thenReturn("stored")
		assertEquals("stored", prefsHelper.stringValue)
	}

	@Test
	fun testStringPrefDelegateSet() {
		prefsHelper.stringValue = "new-value"
		verify(mockEditor).putString("string_key", "new-value")
		verify(mockEditor).apply()
	}

	@Test
	fun testIntPrefDelegateGet() {
		`when`(mockSharedPreferences.getInt("int_key", 10)).thenReturn(77)
		assertEquals(77, prefsHelper.intValue)
	}

	@Test
	fun testIntPrefDelegateSet() {
		prefsHelper.intValue = 99
		verify(mockEditor).putInt("int_key", 99)
		verify(mockEditor).apply()
	}

	@Test
	fun testNullableIntPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("maybe_int")).thenReturn(false)
		assertNull(prefsHelper.maybeInt)
	}

	@Test
	fun testNullableIntPrefReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.contains("maybe_int")).thenReturn(true)
		`when`(mockSharedPreferences.getInt("maybe_int", 0)).thenReturn(123)
		assertEquals(123, prefsHelper.maybeInt)
	}

	@Test
	fun testNullableIntPrefRemovesKeyOnNullAssignment() {
		prefsHelper.maybeInt = null
		verify(mockEditor).remove("maybe_int")
		verify(mockEditor).apply()
	}

	@Test
	fun testEnumPrefDelegateGet() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("VALUE_B")
		assertEquals(TestEnum.VALUE_B, prefsHelper.enumValue)
	}

	@Test
	fun testEnumPrefDelegateGetFallsBackToDefault() {
		`when`(mockSharedPreferences.getString("enum_key", "")).thenReturn("")
		assertEquals(TestEnum.VALUE_A, prefsHelper.enumValue)
	}

	@Test
	fun testEnumPrefDelegateSet() {
		prefsHelper.enumValue = TestEnum.VALUE_C
		verify(mockEditor).putString("enum_key", "VALUE_C")
		verify(mockEditor).apply()
	}

	// Nullable String delegate
	@Test
	fun testNullableStringPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_string_key")).thenReturn(false)
		assertNull(prefsHelper.nullableString)
	}

	@Test
	fun testNullableStringPrefReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.contains("nullable_string_key")).thenReturn(true)
		`when`(mockSharedPreferences.getString("nullable_string_key", null)).thenReturn("stored")
		assertEquals("stored", prefsHelper.nullableString)
	}

	@Test
	fun testNullableStringPrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableString = null
		verify(mockEditor).remove("nullable_string_key")
		verify(mockEditor).apply()
	}

	// Long delegate
	@Test
	fun testLongPrefDelegateGet() {
		`when`(mockSharedPreferences.getLong("long_key", 5L)).thenReturn(999L)
		assertEquals(999L, prefsHelper.longValue)
	}

	@Test
	fun testLongPrefDelegateSet() {
		prefsHelper.longValue = 4242L
		verify(mockEditor).putLong("long_key", 4242L)
		verify(mockEditor).apply()
	}

	@Test
	fun testNullableLongPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_long_key")).thenReturn(false)
		assertNull(prefsHelper.nullableLong)
	}

	@Test
	fun testNullableLongPrefReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.contains("nullable_long_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("nullable_long_key", 0L)).thenReturn(55L)
		assertEquals(55L, prefsHelper.nullableLong)
	}

	@Test
	fun testNullableLongPrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableLong = null
		verify(mockEditor).remove("nullable_long_key")
		verify(mockEditor).apply()
	}

	// Double delegate
	@Test
	fun testDoublePrefDelegateGet() {
		`when`(mockSharedPreferences.contains("double_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("double_key", 0L)).thenReturn(9.75.toRawBits())
		assertEquals(9.75, prefsHelper.doubleValue, 0.0)
	}

	@Test
	fun testDoublePrefDelegateFallsBackToDefault() {
		`when`(mockSharedPreferences.contains("double_key")).thenReturn(false)
		assertEquals(1.5, prefsHelper.doubleValue, 0.0)
	}

	@Test
	fun testDoublePrefDelegateSet() {
		prefsHelper.doubleValue = 2.25
		verify(mockEditor).putLong("double_key", 2.25.toRawBits())
		verify(mockEditor).apply()
	}

	@Test
	fun testNullableDoublePrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_double_key")).thenReturn(false)
		assertNull(prefsHelper.nullableDouble)
	}

	@Test
	fun testNullableDoublePrefReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.contains("nullable_double_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("nullable_double_key", 0L)).thenReturn((-0.5).toRawBits())
		assertEquals(-0.5, prefsHelper.nullableDouble!!, 0.0)
	}

	@Test
	fun testNullableDoublePrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableDouble = null
		verify(mockEditor).remove("nullable_double_key")
		verify(mockEditor).apply()
	}

	// Float delegate
	@Test
	fun testFloatPrefDelegateGet() {
		`when`(mockSharedPreferences.getFloat("float_key", 0.25f)).thenReturn(7.5f)
		assertEquals(7.5f, prefsHelper.floatValue, 0f)
	}

	@Test
	fun testFloatPrefDelegateSet() {
		prefsHelper.floatValue = 3.5f
		verify(mockEditor).putFloat("float_key", 3.5f)
	}

	@Test
	fun testNullableFloatPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_float_key")).thenReturn(false)
		assertNull(prefsHelper.nullableFloat)
	}

	@Test
	fun testNullableFloatPrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableFloat = null
		verify(mockEditor).remove("nullable_float_key")
	}

	// String set delegate
	@Test
	fun testStringSetPrefDelegateGet() {
		`when`(mockSharedPreferences.getStringSet("string_set_key", null)).thenReturn(linkedSetOf("x", "y"))
		assertEquals(setOf("x", "y"), prefsHelper.stringSetValue)
	}

	@Test
	fun testStringSetPrefDelegateFallsBackToDefault() {
		`when`(mockSharedPreferences.getStringSet("string_set_key", null)).thenReturn(null)
		assertEquals(setOf("default"), prefsHelper.stringSetValue)
	}

	@Test
	fun testStringSetPrefDelegateSet() {
		prefsHelper.stringSetValue = setOf("p", "q")
		verify(mockEditor).putStringSet("string_set_key", linkedSetOf("p", "q"))
	}

	@Test
	fun testNullableStringSetPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_string_set_key")).thenReturn(false)
		assertNull(prefsHelper.nullableStringSet)
	}

	@Test
	fun testNullableStringSetPrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableStringSet = null
		verify(mockEditor).remove("nullable_string_set_key")
	}

	// Instant delegate
	@Test
	fun testInstantPrefDelegateGet() {
		`when`(mockSharedPreferences.contains("instant_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("instant_key", 0L)).thenReturn(1_234_567L)
		assertEquals(java.time.Instant.ofEpochMilli(1_234_567L), prefsHelper.instantValue)
	}

	@Test
	fun testInstantPrefDelegateSet() {
		prefsHelper.instantValue = java.time.Instant.ofEpochMilli(99L)
		verify(mockEditor).putLong("instant_key", 99L)
	}

	// Enum set delegate
	@Test
	fun testEnumSetPrefDelegateGet() {
		`when`(mockSharedPreferences.contains("enum_set_key")).thenReturn(true)
		`when`(mockSharedPreferences.getStringSet("enum_set_key", null))
			.thenReturn(linkedSetOf("VALUE_B", "VALUE_C"))
		assertEquals(setOf(TestEnum.VALUE_B, TestEnum.VALUE_C), prefsHelper.enumSetValue)
	}

	@Test
	fun testEnumSetPrefDelegateSet() {
		prefsHelper.enumSetValue = setOf(TestEnum.VALUE_A)
		verify(mockEditor).putStringSet("enum_set_key", linkedSetOf("VALUE_A"))
	}

	// ByteArray delegate (encoding itself is covered against a real SharedPreferences in
	// BasePrefsHelperRealPrefsTest — android.util.Base64 is not available to these plain-JVM mocks)
	@Test
	fun testByteArrayPrefRemovesKeyOnNullAssignment() {
		prefsHelper.byteArrayValue = null
		verify(mockEditor).remove("byte_array_key")
	}

	// Boolean delegate
	@Test
	fun testBooleanPrefDelegateGet() {
		`when`(mockSharedPreferences.getBoolean("bool_key", true)).thenReturn(false)
		assertFalse(prefsHelper.boolValue)
	}

	@Test
	fun testBooleanPrefDelegateSet() {
		prefsHelper.boolValue = false
		verify(mockEditor).putBoolean("bool_key", false)
		verify(mockEditor).apply()
	}

	@Test
	fun testNullableBooleanPrefReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.contains("nullable_bool_key")).thenReturn(false)
		assertNull(prefsHelper.nullableBool)
	}

	@Test
	fun testNullableBooleanPrefReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.contains("nullable_bool_key")).thenReturn(true)
		`when`(mockSharedPreferences.getBoolean("nullable_bool_key", false)).thenReturn(true)
		assertEquals(true, prefsHelper.nullableBool)
	}

	@Test
	fun testNullableBooleanPrefRemovesKeyOnNullAssignment() {
		prefsHelper.nullableBool = null
		verify(mockEditor).remove("nullable_bool_key")
		verify(mockEditor).apply()
	}

	// Date delegate
	@Test
	fun testDatePrefDelegateGet() {
		`when`(mockSharedPreferences.contains("date_delegate_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("date_delegate_key", 0L)).thenReturn(1234567890000L)
		assertEquals(Date(1234567890000L), prefsHelper.dateValue)
	}

	@Test
	fun testDatePrefDelegateSet() {
		prefsHelper.dateValue = Date(1234567890000L)
		verify(mockEditor).putLong("date_delegate_key", 1234567890000L)
		verify(mockEditor).apply()
	}

	@Test
	fun testDatePrefDelegateReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.contains("date_delegate_key")).thenReturn(false)
		assertNull(prefsHelper.dateValue)
	}

	// LocalDateTime delegate
	@Test
	fun testLocalDateTimePrefDelegateGet() {
		val dateTime = LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		`when`(mockSharedPreferences.contains("ldt_delegate_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("ldt_delegate_key", 0L))
			.thenReturn(dateTime.toEpochSecond(ZoneOffset.UTC))
		assertEquals(dateTime, prefsHelper.localDateTimeValue)
	}

	@Test
	fun testLocalDateTimePrefDelegateSet() {
		val dateTime = LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		prefsHelper.localDateTimeValue = dateTime
		verify(mockEditor).putLong("ldt_delegate_key", dateTime.toEpochSecond(ZoneOffset.UTC))
		verify(mockEditor).apply()
	}

	// LocalDate delegate
	@Test
	fun testLocalDatePrefDelegateGet() {
		val date = LocalDate.of(2023, 11, 25)
		`when`(mockSharedPreferences.contains("ld_delegate_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("ld_delegate_key", 0L)).thenReturn(date.toEpochDay())
		assertEquals(date, prefsHelper.localDateValue)
	}

	@Test
	fun testLocalDatePrefDelegateSet() {
		val date = LocalDate.of(2023, 11, 25)
		prefsHelper.localDateValue = date
		verify(mockEditor).putLong("ld_delegate_key", date.toEpochDay())
		verify(mockEditor).apply()
	}

	// LocalTime delegate
	@Test
	fun testLocalTimePrefDelegateGet() {
		val time = LocalTime.of(14, 30, 45)
		`when`(mockSharedPreferences.contains("lt_delegate_key")).thenReturn(true)
		`when`(mockSharedPreferences.getLong("lt_delegate_key", 0L)).thenReturn(time.toSecondOfDay().toLong())
		assertEquals(time, prefsHelper.localTimeValue)
	}

	@Test
	fun testLocalTimePrefDelegateSet() {
		val time = LocalTime.of(14, 30, 45)
		prefsHelper.localTimeValue = time
		verify(mockEditor).putLong("lt_delegate_key", time.toSecondOfDay().toLong())
		verify(mockEditor).apply()
	}

	// Nullable Enum delegate
	@Test
	fun testNullableEnumPrefDelegateReturnsNullWhenAbsent() {
		`when`(mockSharedPreferences.getString("nullable_enum_key", "")).thenReturn("")
		assertNull(prefsHelper.nullableEnumValue)
	}

	@Test
	fun testNullableEnumPrefDelegateReturnsValueWhenPresent() {
		`when`(mockSharedPreferences.getString("nullable_enum_key", "")).thenReturn("VALUE_B")
		assertEquals(TestEnum.VALUE_B, prefsHelper.nullableEnumValue)
	}

	@Test
	fun testNullableEnumPrefDelegateReturnsNullForInvalidValue() {
		`when`(mockSharedPreferences.getString("nullable_enum_key", "")).thenReturn("NOT_A_VALUE")
		assertNull(prefsHelper.nullableEnumValue)
	}

	@Test
	fun testNullableEnumPrefDelegateSetNull() {
		prefsHelper.nullableEnumValue = null
		verify(mockEditor).putString("nullable_enum_key", "")
		verify(mockEditor).apply()
	}

	enum class TestEnum {
		VALUE_A, VALUE_B, VALUE_C
	}

	private inner class TestPrefsHelper : BasePrefsHelper() {
		override val sharedPreferences: SharedPreferences
			get() = mockSharedPreferences

		var stringValue by stringPref("string_key", defaultValue = "fallback")
		var nullableString by stringPref("nullable_string_key")
		var intValue by intPref("int_key", defaultValue = 10)
		var maybeInt by intPref("maybe_int")
		var longValue by longPref("long_key", defaultValue = 5L)
		var nullableLong by longPref("nullable_long_key")
		var doubleValue by doublePref("double_key", defaultValue = 1.5)
		var nullableDouble by doublePref("nullable_double_key")
		var floatValue by floatPref("float_key", defaultValue = 0.25f)
		var nullableFloat by floatPref("nullable_float_key")
		var stringSetValue by stringSetPref("string_set_key", defaultValue = setOf("default"))
		var nullableStringSet by stringSetPref("nullable_string_set_key")
		var byteArrayValue by byteArrayPref("byte_array_key")
		var instantValue by instantPref("instant_key")
		var enumSetValue by enumSetPref<TestEnum>("enum_set_key")
		var boolValue by booleanPref("bool_key", defaultValue = true)
		var nullableBool by booleanPref("nullable_bool_key")
		var dateValue by datePref("date_delegate_key")
		var localDateTimeValue by localDateTimePref("ldt_delegate_key")
		var localDateValue by localDatePref("ld_delegate_key")
		var localTimeValue by localTimePref("lt_delegate_key")
		var enumValue by enumPref("enum_key", TestEnum.VALUE_A)
		var nullableEnumValue by enumPref<TestEnum>("nullable_enum_key")
	}
}
