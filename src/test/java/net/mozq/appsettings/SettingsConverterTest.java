/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SettingsConverterTest {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	private static <T> T convert(Object value, Class<T> type) {
		return SettingsConverter.convert(value, type, UTC);
	}

	@Nested
	class NumericConversions {
		@Test
		void convertsExactValuesAcrossNumericTypes() {
			assertEquals(Integer.valueOf(42), convert(new BigDecimal("42"), Integer.class));
			assertEquals(Long.valueOf(42L), convert("42", Long.class));
			assertEquals(new BigInteger("42"), convert("42", BigInteger.class));
			assertEquals(new BigDecimal("42.5"), convert("42.5", BigDecimal.class));
			assertEquals(Double.valueOf(1.25d), convert(new BigDecimal("1.25"), Double.class));
			assertEquals(Float.valueOf(1.25f), convert("1.25", Float.class));
		}

		@Test
		void rejectsFractionalOrOutOfRangeValuesForIntegerTypes() {
			assertNull(convert(new BigDecimal("1.5"), Integer.class));
			assertNull(convert(new BigDecimal("99999999999999999999"), Integer.class));
			assertNull(convert(new BigDecimal("1.5"), BigInteger.class));
		}

		@Test
		void rejectsNonFiniteValuesForFloatingPointTypes() {
			// Non-finite numbers are stored as the strings "NaN"/"Infinity" (see
			// SettingsValues.number), so this is the realistic failure path.
			assertNull(convert("NaN", Double.class));
			assertNull(convert("Infinity", Float.class));
			// A raw non-finite Number converting to a different numeric type also
			// fails, since BigDecimal cannot represent it.
			assertNull(convert(Double.NaN, Integer.class));
			assertNull(convert(Float.POSITIVE_INFINITY, Long.class));
		}

		@Test
		void rejectsUnparsableOrEmptyText() {
			assertNull(convert("not-a-number", Integer.class));
			assertNull(convert("", Integer.class));
		}

		@Test
		void returnsSameInstanceWhenAlreadyRequestedType() {
			BigDecimal decimal = new BigDecimal("10");
			assertSame(decimal, convert(decimal, BigDecimal.class));
		}
	}

	@Nested
	class BooleanAndCharacterConversions {
		@Test
		void acceptsOnlyStrictBooleanLiterals() {
			assertEquals(Boolean.TRUE, convert("true", Boolean.class));
			assertEquals(Boolean.FALSE, convert("false", Boolean.class));
			assertNull(convert("TRUE", Boolean.class));
			assertNull(convert("yes", Boolean.class));
			assertNull(convert("", Boolean.class));
		}

		@Test
		void acceptsOnlySingleCharacterText() {
			assertEquals(Character.valueOf('A'), convert("A", Character.class));
			assertEquals(Character.valueOf(' '), convert(" ", Character.class));
			assertNull(convert("AB", Character.class));
			assertNull(convert("", Character.class));
		}
	}

	@Nested
	class LocaleTimeZoneAndZoneIdConversions {
		@Test
		void parsesWellFormedLanguageTags() {
			assertEquals(Locale.JAPAN, convert("ja-JP", Locale.class));
			assertEquals(Locale.ROOT, convert("und", Locale.class));
		}

		@Test
		void rejectsIllFormedOrEmptyLanguageTags() {
			assertNull(convert("not-a-locale-!!!", Locale.class));
			assertNull(convert("", Locale.class));
			assertNull(convert("en_US", Locale.class));
		}

		@Test
		void convertsTimeZonesAndZoneIds() {
			assertEquals(TimeZone.getTimeZone("Asia/Tokyo"), convert("Asia/Tokyo", TimeZone.class));
			assertEquals(ZoneId.of("Asia/Tokyo"), convert("Asia/Tokyo", ZoneId.class));
			assertEquals(ZoneId.of("Asia/Tokyo"), convert(TimeZone.getTimeZone("Asia/Tokyo"), ZoneId.class));
			assertEquals(TimeZone.getTimeZone("Asia/Tokyo"), convert(ZoneId.of("Asia/Tokyo"), TimeZone.class));
		}

		@Test
		void rejectsUnknownTimeZonesAndZoneIds() {
			assertNull(convert("not-a-zone", TimeZone.class));
			assertNull(convert("not-a-zone", ZoneId.class));
		}
	}

	@Nested
	class PathUriAndEnumConversions {
		private enum SampleEnum {
			First, Second
		}

		@Test
		void convertsPathAndUriText() {
			Path expectedPath = Path.of("data", "output");
			assertEquals(expectedPath, convert(expectedPath.toString(), Path.class));
			assertEquals(URI.create("https://example.com/api"), convert("https://example.com/api", URI.class));
		}

		@Test
		void rejectsMalformedUriText() {
			assertNull(convert("http://[invalid", URI.class));
		}

		@Test
		void convertsMatchingEnumConstantNamesOnly() {
			assertEquals(SampleEnum.Second, convert("Second", SampleEnum.class));
			assertNull(convert("Third", SampleEnum.class));
			assertNull(convert("second", SampleEnum.class));
		}
	}

	@Nested
	class DateTimeConversions {
		private final Instant instant = Instant.parse("2026-08-29T12:34:56Z");

		@Test
		void convertsBetweenTimelineTypes() {
			assertEquals(instant, convert(Date.from(instant), Instant.class));
			assertEquals(Date.from(instant), convert(instant, Date.class));
			assertEquals(instant, convert(OffsetDateTime.parse("2026-08-29T21:34:56+09:00"), Instant.class));
			assertEquals(instant, convert(ZonedDateTime.parse("2026-08-29T21:34:56+09:00[Asia/Tokyo]"), Instant.class));
		}

		@Test
		void convertsLocalTypesUsingConfiguredZone() {
			assertEquals(instant, convert(LocalDateTime.parse("2026-08-29T12:34:56"), Instant.class));
			assertEquals(Instant.parse("2026-08-29T00:00:00Z"), convert(LocalDate.parse("2026-08-29"), Instant.class));
			assertEquals(LocalDateTime.parse("2026-08-29T12:34:56"), convert(instant, LocalDateTime.class));
			assertEquals(LocalDate.parse("2026-08-29"), convert(instant, LocalDate.class));
			assertEquals(LocalTime.parse("12:34:56"), convert(instant, LocalTime.class));
		}

		@Test
		void localTimeCannotConvertToTimelineTypesBecauseItHasNoDate() {
			assertNull(convert(LocalTime.parse("12:34:56"), Instant.class));
		}

		@Test
		void parsesIsoTextThroughTypeInferenceFirst() {
			assertEquals(instant, convert("2026-08-29T12:34:56Z", Instant.class));
			assertEquals(LocalDate.parse("2026-08-29"), convert("2026-08-29", LocalDate.class));
		}
	}

	@Nested
	class PrimitiveTypeTokens {
		// AppSettings itself never passes a primitive Class token (getInt calls
		// getAs(key, Integer.class, ...), not int.class), so this path only runs
		// when SettingsConverter is called directly. It is still real, deliberate
		// logic (see SettingsConverter.wrapperType/PRIMITIVE_WRAPPERS) worth
		// covering on its own.

		@Test
		void convertsPrimitiveTypeTokensTheSameAsTheirWrapperTypes() {
			assertEquals(Integer.valueOf(42), convert(new BigDecimal("42"), int.class));
			assertEquals(Long.valueOf(42L), convert("42", long.class));
			assertEquals(Byte.valueOf((byte) 7), convert("7", byte.class));
			assertEquals(Short.valueOf((short) 7), convert("7", short.class));
			assertEquals(Double.valueOf(1.25d), convert(new BigDecimal("1.25"), double.class));
			assertEquals(Float.valueOf(1.25f), convert("1.25", float.class));
			assertEquals(Boolean.TRUE, convert("true", boolean.class));
			assertEquals(Character.valueOf('A'), convert("A", char.class));
		}

		@Test
		void returnsNullRatherThanThrowingWhenConversionFails() {
			assertNull(convert("not-a-number", int.class));
			assertNull(convert("not-a-boolean", boolean.class));
		}

		@Test
		void unboxingAFailedConversionToAPrimitiveLocalThrowsNullPointerException() {
			assertThrows(NullPointerException.class, () -> {
				int ignored = convert("not-a-number", int.class);
			});
			assertThrows(NullPointerException.class, () -> {
				boolean ignored = convert("not-a-boolean", boolean.class);
			});
		}
	}
}
