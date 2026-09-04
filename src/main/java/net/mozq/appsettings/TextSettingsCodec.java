/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

final class TextSettingsCodec {
	private TextSettingsCodec() {
	}

	static int findSeparator(String line) {
		boolean escaped = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (escaped) {
				escaped = false;
			} else if (c == '\\') {
				escaped = true;
			} else if (c == '=') {
				return i;
			}
		}
		return -1;
	}

	static String escapeKeyValueKey(String value) {
		return escape(value, true, true);
	}

	static String escapeKeyValueValue(SettingsValue value, boolean nullable) {
		return escapeSettingValue(value, nullable, true);
	}

	static String escapeIniKey(String value) {
		return escape(value, false, false);
	}

	static String escapeIniValue(SettingsValue value, boolean nullable) {
		return escapeSettingValue(value, nullable, false);
	}

	private static String escapeSettingValue(SettingsValue value, boolean nullable, boolean keyValueStyle) {
		if (value instanceof SettingsValue.NullValue && nullable) {
			return "null";
		}
		if (value instanceof SettingsValue.ListValue listValue) {
			return SettingsValues.formatList(listValue.values(), nullable);
		}
		if (SettingsValues.shouldQuoteString(value)) {
			return SettingsValues.quote(SettingsValues.raw(value));
		}
		String raw = SettingsValues.raw(value);
		return escape(raw, false, keyValueStyle);
	}

	private static String escape(String value, boolean key, boolean keyValueStyle) {
		StringBuilder escaped = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (shouldEscape(value, c, i, key, keyValueStyle)) {
				escaped.append('\\');
				switch (c) {
				case '\n': escaped.append('n'); break;
				case '\r': escaped.append('r'); break;
				case '\t': escaped.append('t'); break;
				default: escaped.append(c);
				}
			} else {
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private static boolean shouldEscape(String value, char c, int index, boolean key, boolean keyValueStyle) {
		return c == '\\'
				|| c == '='
				|| c == '\n'
				|| c == '\r'
				|| c == '\t'
				|| (keyValueStyle && index == 0 && (c == '#' || c == '!'))
				|| (key && keyValueStyle && Character.isWhitespace(c))
				|| (!key && index == 0 && c == ' ')
				|| (!key && index == value.length() - 1 && c == ' ');
	}
}
