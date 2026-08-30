/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SettingsValues {
	private SettingsValues() {
	}

	static SettingsValue string(String value) {
		return new SettingsValue.StringValue(value, value);
	}

	static SettingsValue number(Number value) {
		String raw = value.toString();
		if (isNonFinite(value)) {
			return string(raw);
		}
		return new SettingsValue.NumberValue(raw, new BigDecimal(raw));
	}

	static SettingsValue number(String raw) {
		return new SettingsValue.NumberValue(raw, new BigDecimal(raw));
	}

	static SettingsValue bool(boolean value) {
		return new SettingsValue.BooleanValue(Boolean.toString(value), value);
	}

	static SettingsValue dateTime(String raw, TemporalAccessor value) {
		return new SettingsValue.DateTimeValue(raw, value);
	}

	static SettingsValue list(String raw, List<SettingsValue> values) {
		return new SettingsValue.ListValue(raw, List.copyOf(values));
	}

	static SettingsValue nullValue() {
		return new SettingsValue.NullValue();
	}

	static SettingsValue of(Object value) {
		if (value instanceof SettingsValue settingsValue) {
			return settingsValue;
		}
		if (value == null) {
			return nullValue();
		}
		if (value instanceof String string) {
			return string(string);
		}
		if (value instanceof Number number) {
			return number(number);
		}
		if (value instanceof Boolean bool) {
			return bool(bool);
		}
		if (value instanceof Date date) {
			return dateTime(date.toInstant().toString(), date.toInstant());
		}
		if (value instanceof TemporalAccessor temporalAccessor) {
			return dateTime(temporalAccessor.toString(), temporalAccessor);
		}
		if (value instanceof Enum<?> enumValue) {
			return string(enumValue.name());
		}
		if (value instanceof Locale locale) {
			return string(locale.toLanguageTag());
		}
		if (value instanceof TimeZone timeZone) {
			return string(timeZone.getID());
		}
		if (value instanceof Collection<?> collection) {
			List<SettingsValue> values = collection.stream().map(SettingsValues::of).toList();
			return list(formatList(values), values);
		}
		return string(String.valueOf(value));
	}

	static SettingsValue infer(String raw) {
		return inferUnquoted(raw, raw.strip(), false);
	}

	static SettingsValue inferEscaped(String raw) {
		String stripped = raw.strip();
		if (isQuoted(stripped)) {
			return string(unquote(stripped));
		}
		if (isListLiteral(stripped)) {
			return parseList(stripped, true);
		}
		String value = trimEdgeSpaces(unescape(raw));
		return inferUnquoted(value, value, true);
	}

	static String raw(SettingsValue value) {
		return value == null ? null : value.raw();
	}

	static String raw(SettingsValue value, boolean nullable) {
		if (value instanceof SettingsValue.ListValue listValue) {
			return formatList(listValue.values(), nullable);
		}
		return raw(value);
	}

	static Object object(SettingsValue value) {
		return object(value, true);
	}

	static Object object(SettingsValue value, boolean nullable) {
		return switch (value) {
		case null -> null;
		case SettingsValue.NullValue ignored -> null;
		case SettingsValue.StringValue stringValue -> stringValue.value();
		case SettingsValue.NumberValue numberValue -> numberValue.value();
		case SettingsValue.BooleanValue booleanValue -> booleanValue.value();
		case SettingsValue.DateTimeValue dateTimeValue -> dateTimeValue.value();
		case SettingsValue.ListValue listValue -> listValue.values().stream()
				.filter(item -> nullable || !(item instanceof SettingsValue.NullValue))
				.map(item -> object(item, nullable))
				.toList();
		};
	}

	static boolean isList(SettingsValue value) {
		return value instanceof SettingsValue.ListValue;
	}

	static String formatList(List<SettingsValue> values) {
		return formatList(values, true);
	}

	static String formatList(List<SettingsValue> values, boolean nullable) {
		StringBuilder builder = new StringBuilder("["); //$NON-NLS-1$
		boolean wrote = false;
		for (SettingsValue value : values) {
			if (value instanceof SettingsValue.NullValue && !nullable) {
				continue;
			}
			if (wrote) {
				builder.append(", "); //$NON-NLS-1$
			}
			builder.append(escapeListElement(value));
			wrote = true;
		}
		return builder.append(']').toString();
	}

	private static SettingsValue inferUnquoted(String raw, String normalized, boolean escapedInput) {
		if (isListLiteral(normalized)) {
			return parseList(normalized, escapedInput);
		}
		return inferScalar(raw, normalized);
	}

	private static SettingsValue inferScalar(String raw, String normalized) {
		if (isNullLiteral(normalized)) {
			return nullValue();
		}
		if (isTrueLiteral(normalized)) {
			return new SettingsValue.BooleanValue(raw, true);
		}
		if (isFalseLiteral(normalized)) {
			return new SettingsValue.BooleanValue(raw, false);
		}
		try {
			return new SettingsValue.NumberValue(raw, new BigDecimal(normalized));
		} catch (NumberFormatException e) {
			// Try date/time formats next.
		}
		TemporalAccessor dateTime = parseDateTime(normalized);
		if (dateTime != null) {
			return new SettingsValue.DateTimeValue(raw, dateTime);
		}
		return new SettingsValue.StringValue(raw, raw);
	}

	private static SettingsValue parseList(String raw, boolean escapedInput) {
		String body = raw.substring(1, raw.length() - 1);
		if (body.strip().isEmpty()) {
			return new SettingsValue.ListValue(listRaw(raw, escapedInput), List.of());
		}
		List<SettingsValue> values = new ArrayList<>();
		StringBuilder element = new StringBuilder();
		boolean quoted = false;
		boolean escapedCharacter = false;
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (escapedCharacter) {
				element.append('\\');
				element.append(c);
				escapedCharacter = false;
			} else if (c == '\\') {
				escapedCharacter = true;
			} else if (c == '"') {
				quoted = !quoted;
				element.append(c);
			} else if (c == ',' && !quoted) {
				values.add(parseListElement(element.toString(), escapedInput));
				element.setLength(0);
			} else {
				element.append(c);
			}
		}
		if (escapedCharacter) {
			element.append('\\');
		}
		values.add(parseListElement(element.toString(), escapedInput));
		return new SettingsValue.ListValue(listRaw(raw, escapedInput), List.copyOf(values));
	}

	private static SettingsValue parseListElement(String element, boolean escaped) {
		String trimmed = trimEdgeSpaces(element);
		if (isQuoted(trimmed)) {
			return string(unquote(trimmed));
		}
		if (escaped) {
			String value = trimEdgeSpaces(unescape(element));
			return inferUnquoted(value, value, true);
		}
		return infer(trimmed);
	}

	private static String listRaw(String raw, boolean escaped) {
		return escaped ? unescape(raw) : raw;
	}

	private static String trimEdgeSpaces(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && Character.isWhitespace(value.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	private static String escapeListElement(SettingsValue value) {
		String raw = value.raw();
		if (raw == null) {
			return "null"; //$NON-NLS-1$
		}
		if (shouldQuoteListString(value)) {
			return quote(raw);
		}
		return raw;
	}

	static String unescape(String value) {
		StringBuilder unescaped = new StringBuilder();
		boolean escaped = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (escaped) {
				switch (c) {
				case 'n': unescaped.append('\n'); break;
				case 'r': unescaped.append('\r'); break;
				case 't': unescaped.append('\t'); break;
				default: unescaped.append(c);
				}
				escaped = false;
			} else if (c == '\\') {
				escaped = true;
			} else {
				unescaped.append(c);
			}
		}
		if (escaped) {
			unescaped.append('\\');
		}
		return unescaped.toString();
	}

	static boolean shouldQuoteString(SettingsValue value) {
		if (!(value instanceof SettingsValue.StringValue stringValue)) {
			return false;
		}
		return shouldQuoteText(stringValue.raw(), false);
	}

	private static boolean shouldQuoteListString(SettingsValue value) {
		if (!(value instanceof SettingsValue.StringValue stringValue)) {
			return false;
		}
		return shouldQuoteText(stringValue.raw(), true);
	}

	static String quote(String value) {
		StringBuilder quoted = new StringBuilder("\""); //$NON-NLS-1$
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\': quoted.append("\\\\"); break; //$NON-NLS-1$
			case '"': quoted.append("\\\""); break; //$NON-NLS-1$
			case '\n': quoted.append("\\n"); break; //$NON-NLS-1$
			case '\r': quoted.append("\\r"); break; //$NON-NLS-1$
			case '\t': quoted.append("\\t"); break; //$NON-NLS-1$
			default: quoted.append(c);
			}
		}
		return quoted.append('"').toString();
	}

	static String unquote(String value) {
		if (!isQuoted(value)) {
			return value;
		}
		StringBuilder result = new StringBuilder();
		boolean escaped = false;
		for (int i = 1; i < value.length() - 1; i++) {
			char c = value.charAt(i);
			if (escaped) {
				switch (c) {
				case 'n': result.append('\n'); break;
				case 'r': result.append('\r'); break;
				case 't': result.append('\t'); break;
				default: result.append(c);
				}
				escaped = false;
			} else if (c == '\\') {
				escaped = true;
			} else {
				result.append(c);
			}
		}
		if (escaped) {
			result.append('\\');
		}
		return result.toString();
	}

	static boolean isQuoted(String value) {
		return value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"';
	}

	private static boolean isListLiteral(String value) {
		return value.startsWith("[") && value.endsWith("]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isNullLiteral(String normalized) {
		return "null".equals(normalized); //$NON-NLS-1$
	}

	private static boolean isTrueLiteral(String normalized) {
		return "true".equals(normalized); //$NON-NLS-1$
	}

	private static boolean isFalseLiteral(String normalized) {
		return "false".equals(normalized); //$NON-NLS-1$
	}

	private static boolean startsOrEndsWithWhitespace(String value) {
		return Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1));
	}

	private static boolean shouldQuoteText(String value, boolean listElement) {
		return value.isEmpty()
				|| startsOrEndsWithWhitespace(value)
				|| value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0
				|| value.indexOf('\t') >= 0
				|| value.indexOf('"') >= 0
				|| value.indexOf('\\') >= 0
				|| isQuoted(value)
				|| listElement && (value.indexOf(',') >= 0 || value.indexOf('[') >= 0 || value.indexOf(']') >= 0)
				|| wouldInferAsNonString(value);
	}

	private static boolean wouldInferAsNonString(String value) {
		SettingsValue inferred = infer(value);
		return !(inferred instanceof SettingsValue.StringValue);
	}

	private static boolean isNonFinite(Number value) {
		return value instanceof Double doubleValue && !Double.isFinite(doubleValue)
				|| value instanceof Float floatValue && !Float.isFinite(floatValue);
	}

	private static TemporalAccessor parseDateTime(String value) {
		TemporalAccessor parsed = tryParse(value, Instant::parse);
		if (parsed != null) {
			return parsed;
		}
		parsed = tryParse(value, ZonedDateTime::parse);
		if (parsed != null) {
			return parsed;
		}
		parsed = tryParse(value, OffsetDateTime::parse);
		if (parsed != null) {
			return parsed;
		}
		parsed = tryParse(value, LocalDateTime::parse);
		if (parsed != null) {
			return parsed;
		}
		parsed = tryParse(value, LocalDate::parse);
		if (parsed != null) {
			return parsed;
		}
		return tryParse(value, LocalTime::parse);
	}

	private static TemporalAccessor tryParse(String value, DateTimeParser parser) {
		try {
			return parser.parse(value);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	@FunctionalInterface
	private interface DateTimeParser {
		TemporalAccessor parse(String value);
	}
}
