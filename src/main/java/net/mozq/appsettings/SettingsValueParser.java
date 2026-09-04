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
import java.util.List;

final class SettingsValueParser {
	private SettingsValueParser() {
	}

	static SettingsValue infer(String raw) {
		return inferUnquoted(raw, raw.strip(), false);
	}

	static SettingsValue inferEscaped(String raw) {
		String stripped = raw.strip();
		if (SettingsValues.isQuoted(stripped)) {
			return SettingsValues.string(SettingsValues.unquote(stripped));
		}
		if (isListLiteral(stripped)) {
			return parseList(stripped, true);
		}
		String value = trimEdgeSpaces(SettingsValues.unescape(raw));
		return inferUnquoted(value, value, true);
	}

	private static SettingsValue inferUnquoted(String raw, String normalized, boolean escapedInput) {
		if (isListLiteral(normalized)) {
			return parseList(normalized, escapedInput);
		}
		return inferScalar(raw, normalized);
	}

	private static SettingsValue inferScalar(String raw, String normalized) {
		if (isNullLiteral(normalized)) {
			return SettingsValues.nullValue();
		}
		if (isTrueLiteral(normalized)) {
			return new SettingsValue.BooleanValue(raw, true);
		}
		if (isFalseLiteral(normalized)) {
			return new SettingsValue.BooleanValue(raw, false);
		}
		if (isNumberCandidate(normalized)) {
			try {
				return new SettingsValue.NumberValue(raw, new BigDecimal(normalized));
			} catch (NumberFormatException e) {
				// Try date/time formats next.
			}
		}
		if (isDateTimeCandidate(normalized)) {
			TemporalAccessor dateTime = parseDateTime(normalized);
			if (dateTime != null) {
				return new SettingsValue.DateTimeValue(raw, dateTime);
			}
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
		if (SettingsValues.isQuoted(trimmed)) {
			return SettingsValues.string(SettingsValues.unquote(trimmed));
		}
		if (escaped) {
			String value = trimEdgeSpaces(SettingsValues.unescape(element));
			return inferUnquoted(value, value, true);
		}
		return infer(trimmed);
	}

	private static String listRaw(String raw, boolean escaped) {
		return escaped ? SettingsValues.unescape(raw) : raw;
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

	private static boolean isListLiteral(String value) {
		return value.startsWith("[") && value.endsWith("]");
	}

	private static boolean isNullLiteral(String normalized) {
		return "null".equals(normalized);
	}

	private static boolean isTrueLiteral(String normalized) {
		return "true".equals(normalized);
	}

	private static boolean isFalseLiteral(String normalized) {
		return "false".equals(normalized);
	}

	private static boolean isNumberCandidate(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		char first = s.charAt(0);
		return (first >= '0' && first <= '9') || first == '+' || first == '-' || first == '.';
	}

	private static boolean isDateTimeCandidate(String s) {
		if (s == null || s.length() < 4) {
			return false;
		}
		char first = s.charAt(0);
		if (first < '0' || first > '9') {
			return false;
		}
		return s.indexOf('-') >= 0 || s.indexOf(':') >= 0;
	}

	private static TemporalAccessor parseDateTime(String value) {
		TemporalAccessor parsed = null;
		if (value.indexOf('[') >= 0) {
			parsed = tryParse(value, ZonedDateTime::parse);
		} else if (value.endsWith("Z")) {
			parsed = tryParse(value, Instant::parse);
		} else {
			parsed = tryParse(value, OffsetDateTime::parse);
		}
		if (parsed != null) {
			return parsed;
		}
		parsed = tryParse(value, Instant::parse);
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
