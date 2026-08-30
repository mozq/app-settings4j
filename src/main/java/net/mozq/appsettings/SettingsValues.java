/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
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
		if (value instanceof ZoneId zoneId) {
			return string(zoneId.getId());
		}
		if (value instanceof Path path) {
			return string(path.toString());
		}
		if (value instanceof URI uri) {
			return string(uri.toString());
		}
		if (value instanceof Collection<?> collection) {
			List<SettingsValue> values = collection.stream().map(SettingsValues::of).toList();
			return list(formatList(values), values);
		}
		return string(String.valueOf(value));
	}

	static SettingsValue infer(String raw) {
		return SettingsValueParser.infer(raw);
	}

	static SettingsValue inferEscaped(String raw) {
		return SettingsValueParser.inferEscaped(raw);
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
}
