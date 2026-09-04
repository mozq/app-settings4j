/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

final class SettingsConverter {
	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = primitiveWrappers();

	static <T> T convert(Object value, Class<T> type, TimeZone timeZone) {
		Objects.requireNonNull(type, "type");
		ZoneId zoneId = Objects.requireNonNull(timeZone, "timeZone").toZoneId();
		Class<?> wrapperType = wrapperType(type);
		if (wrapperType.isInstance(value)) {
			return castValue(value, type);
		}
		try {
			Object converted = convertObject(value, wrapperType, zoneId);
			return castValue(converted, type);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private SettingsConverter() {
	}

	private static Object convertObject(Object value, Class<?> type, ZoneId zoneId) {
		if (type == String.class) {
			return value.toString();
		}
		if (value instanceof String string) {
			return convertString(string, type, zoneId);
		}
		if (isNumericType(type) && value instanceof Number number) {
			return convertNumber(number, type);
		}
		Object dateTime = convertDateTime(value, type, zoneId);
		if (dateTime != null) {
			return dateTime;
		}
		if (type == Boolean.class && value instanceof Boolean bool) {
			return bool;
		}
		if (type == Character.class) {
			return parseCharacter(value.toString());
		}
		if (type == Locale.class) {
			return Locale.forLanguageTag(value.toString());
		}
		if (type == TimeZone.class) {
			return toTimeZone(value);
		}
		if (type == ZoneId.class) {
			return toZoneId(value);
		}
		if (type == Path.class) {
			return Path.of(value.toString());
		}
		if (type == URI.class) {
			return URI.create(value.toString());
		}
		return convertEnum(value.toString(), type);
	}

	private static Object convertString(String value, Class<?> type, ZoneId zoneId) {
		SettingsValue inferred = SettingsValues.infer(value);
		Object inferredObject = SettingsValues.object(inferred);
		if (inferredObject == null) {
			return null;
		}
		if (type.isInstance(inferredObject)) {
			return inferredObject;
		}
		if (isNumericType(type) && inferredObject instanceof Number number) {
			return convertNumber(number, type);
		}
		Object dateTime = convertDateTime(inferredObject, type, zoneId);
		if (dateTime != null) {
			return dateTime;
		}
		if (type == Boolean.class && inferredObject instanceof Boolean bool) {
			return bool;
		}
		if (type == Character.class) {
			return parseCharacter(value);
		}
		if (type == Locale.class) {
			return Locale.forLanguageTag(value);
		}
		if (type == TimeZone.class) {
			return toTimeZone(value);
		}
		if (type == ZoneId.class) {
			return toZoneId(value);
		}
		if (type == Path.class) {
			return Path.of(value);
		}
		if (type == URI.class) {
			return URI.create(value);
		}
		return convertEnum(value, type);
	}

	private static boolean isNumericType(Class<?> type) {
		return type == Byte.class
				|| type == Short.class
				|| type == Integer.class
				|| type == Long.class
				|| type == Float.class
				|| type == Double.class
				|| type == BigInteger.class
				|| type == BigDecimal.class;
	}

	private static Object convertNumber(Number number, Class<?> type) {
		BigDecimal decimal = toBigDecimal(number);
		if (type == BigDecimal.class) {
			return decimal;
		}
		if (type == BigInteger.class) {
			return decimal.toBigIntegerExact();
		}
		if (type == Byte.class) {
			return decimal.byteValueExact();
		}
		if (type == Short.class) {
			return decimal.shortValueExact();
		}
		if (type == Integer.class) {
			return decimal.intValueExact();
		}
		if (type == Long.class) {
			return decimal.longValueExact();
		}
		if (type == Float.class) {
			float converted = decimal.floatValue();
			if (!Float.isFinite(converted)) {
				throw new ArithmeticException("not a finite float");
			}
			return converted;
		}
		if (type == Double.class) {
			double converted = decimal.doubleValue();
			if (!Double.isFinite(converted)) {
				throw new ArithmeticException("not a finite double");
			}
			return converted;
		}
		return null;
	}

	private static BigDecimal toBigDecimal(Number number) {
		if (number instanceof BigDecimal decimal) {
			return decimal;
		}
		if (number instanceof BigInteger integer) {
			return new BigDecimal(integer);
		}
		if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
			return BigDecimal.valueOf(number.longValue());
		}
		if (number instanceof Float floatValue) {
			if (!Float.isFinite(floatValue)) {
				throw new NumberFormatException("not a finite float");
			}
			return BigDecimal.valueOf(floatValue.doubleValue());
		}
		if (number instanceof Double doubleValue) {
			if (!Double.isFinite(doubleValue)) {
				throw new NumberFormatException("not a finite double");
			}
			return BigDecimal.valueOf(doubleValue);
		}
		return new BigDecimal(number.toString());
	}

	private static Object convertDateTime(Object value, Class<?> type, ZoneId zoneId) {
		if (type == Instant.class) {
			return toInstant(value, zoneId);
		}
		if (type == Date.class) {
			Instant instant = toInstant(value, zoneId);
			return instant == null ? null : Date.from(instant);
		}
		if (type == LocalDateTime.class) {
			return toLocalDateTime(value, zoneId);
		}
		if (type == LocalDate.class) {
			return toLocalDate(value, zoneId);
		}
		if (type == LocalTime.class) {
			return toLocalTime(value, zoneId);
		}
		if (type == OffsetDateTime.class) {
			return toOffsetDateTime(value, zoneId);
		}
		if (type == ZonedDateTime.class) {
			return toZonedDateTime(value, zoneId);
		}
		return null;
	}

	private static Instant toInstant(Object value, ZoneId zoneId) {
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof Date date) {
			return date.toInstant();
		}
		if (value instanceof ZonedDateTime zonedDateTime) {
			return zonedDateTime.toInstant();
		}
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant();
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.atZone(zoneId).toInstant();
		}
		if (value instanceof LocalDate localDate) {
			return localDate.atStartOfDay(zoneId).toInstant();
		}
		return null;
	}

	private static LocalDateTime toLocalDateTime(Object value, ZoneId zoneId) {
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof LocalDate localDate) {
			return localDate.atStartOfDay();
		}
		Instant instant = toInstant(value, zoneId);
		return instant == null ? null : LocalDateTime.ofInstant(instant, zoneId);
	}

	private static LocalDate toLocalDate(Object value, ZoneId zoneId) {
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.toLocalDate();
		}
		Instant instant = toInstant(value, zoneId);
		return instant == null ? null : instant.atZone(zoneId).toLocalDate();
	}

	private static LocalTime toLocalTime(Object value, ZoneId zoneId) {
		if (value instanceof LocalTime localTime) {
			return localTime;
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.toLocalTime();
		}
		Instant instant = toInstant(value, zoneId);
		return instant == null ? null : instant.atZone(zoneId).toLocalTime();
	}

	private static OffsetDateTime toOffsetDateTime(Object value, ZoneId zoneId) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime;
		}
		if (value instanceof ZonedDateTime zonedDateTime) {
			return zonedDateTime.withZoneSameInstant(zoneId).toOffsetDateTime();
		}
		ZonedDateTime zonedDateTime = toZonedDateTime(value, zoneId);
		return zonedDateTime == null ? null : zonedDateTime.toOffsetDateTime();
	}

	private static ZonedDateTime toZonedDateTime(Object value, ZoneId zoneId) {
		if (value instanceof ZonedDateTime zonedDateTime) {
			return zonedDateTime;
		}
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.atZoneSameInstant(zoneId);
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.atZone(zoneId);
		}
		if (value instanceof LocalDate localDate) {
			return localDate.atStartOfDay(zoneId);
		}
		Instant instant = toInstant(value, zoneId);
		return instant == null ? null : instant.atZone(zoneId);
	}

	private static Class<?> wrapperType(Class<?> type) {
		return type.isPrimitive() ? PRIMITIVE_WRAPPERS.get(type) : type;
	}

	@SuppressWarnings("unchecked")
	private static <T> T castValue(Object value, Class<T> type) {
		return (T) wrapperType(type).cast(value);
	}

	private static Map<Class<?>, Class<?>> primitiveWrappers() {
		Map<Class<?>, Class<?>> wrappers = new HashMap<>();
		wrappers.put(boolean.class, Boolean.class);
		wrappers.put(byte.class, Byte.class);
		wrappers.put(short.class, Short.class);
		wrappers.put(int.class, Integer.class);
		wrappers.put(long.class, Long.class);
		wrappers.put(float.class, Float.class);
		wrappers.put(double.class, Double.class);
		wrappers.put(char.class, Character.class);
		return Map.copyOf(wrappers);
	}

	private static Character parseCharacter(String value) {
		if (value.length() != 1) {
			throw new IllegalArgumentException("not a character: " + value);
		}
		return value.charAt(0);
	}

	private static TimeZone parseTimeZone(String value) {
		try {
			return TimeZone.getTimeZone(ZoneId.of(value));
		} catch (DateTimeException e) {
			return null;
		}
	}

	private static TimeZone toTimeZone(Object value) {
		if (value instanceof TimeZone timeZone) {
			return timeZone;
		}
		if (value instanceof ZoneId zoneId) {
			return TimeZone.getTimeZone(zoneId);
		}
		return parseTimeZone(value.toString());
	}

	private static ZoneId toZoneId(Object value) {
		if (value instanceof ZoneId zoneId) {
			return zoneId;
		}
		if (value instanceof TimeZone timeZone) {
			return timeZone.toZoneId();
		}
		return ZoneId.of(value.toString());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object convertEnum(String value, Class<?> type) {
		if (type.isEnum()) {
			return Enum.valueOf((Class<? extends Enum>) type, value);
		}
		return null;
	}
}
