package com.duck.app

import android.content.SharedPreferences
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
		verify(mockEditor).putLong("date_key", -1L)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetDate() {
		`when`(mockSharedPreferences.getLong("date_key", -1L)).thenReturn(1234567890000L)
		val date = prefsHelper.getDate("date_key")
		assertNotNull(date)
		assertEquals(1234567890000L, date?.time)
	}

	@Test
	fun testGetDateReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.getLong("date_key", -1L)).thenReturn(-1L)
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
		verify(mockEditor).putLong("datetime_key", -1L)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalDateTime() {
		val dateTime = LocalDateTime.of(2023, 11, 25, 10, 30, 45)
		val epochSecond = dateTime.toEpochSecond(ZoneOffset.UTC)
		`when`(mockSharedPreferences.getLong("datetime_key", -1L)).thenReturn(epochSecond)
		val result = prefsHelper.getLocalDateTime("datetime_key")
		assertNotNull(result)
		assertEquals(dateTime, result)
	}

	@Test
	fun testGetLocalDateTimeReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.getLong("datetime_key", -1L)).thenReturn(-1L)
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
		verify(mockEditor).putLong("date_key", -1L)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalDate() {
		val date = LocalDate.of(2023, 11, 25)
		`when`(mockSharedPreferences.getLong("date_key", -1L)).thenReturn(date.toEpochDay())
		val result = prefsHelper.getLocalDate("date_key")
		assertNotNull(result)
		assertEquals(date, result)
	}

	@Test
	fun testGetLocalDateReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.getLong("date_key", -1L)).thenReturn(-1L)
		assertNull(prefsHelper.getLocalDate("date_key"))
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
		verify(mockEditor).putLong("time_key", -1L)
		verify(mockEditor).apply()
	}

	@Test
	fun testGetLocalTime() {
		val time = LocalTime.of(14, 30, 45)
		`when`(mockSharedPreferences.getLong("time_key", -1L)).thenReturn(time.toSecondOfDay().toLong())
		val result = prefsHelper.getLocalTime("time_key")
		assertNotNull(result)
		assertEquals(time, result)
	}

	@Test
	fun testGetLocalTimeReturnsNullWhenNotSet() {
		`when`(mockSharedPreferences.getLong("time_key", -1L)).thenReturn(-1L)
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

	enum class TestEnum {
		VALUE_A, VALUE_B, VALUE_C
	}

	private inner class TestPrefsHelper : BasePrefsHelper() {
		override val sharedPreferences: SharedPreferences
			get() = mockSharedPreferences
	}
}
