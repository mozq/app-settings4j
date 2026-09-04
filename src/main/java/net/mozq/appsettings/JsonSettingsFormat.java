/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonSettingsFormat implements InternalSettingsFormat {
	private static final String NODE_VALUE_KEY = "@"; //$NON-NLS-1$

	@Override
	public LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException {
		StringBuilder content = new StringBuilder(2048);
		char[] buffer = new char[4096];
		int numRead;
		while ((numRead = reader.read(buffer)) != -1) {
			content.append(buffer, 0, numRead);
		}
		Object root = new Parser(content).parse();
		if (!(root instanceof Map<?, ?> map)) {
			throw new AppSettingsException("JSON settings must be an object"); //$NON-NLS-1$
		}
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		flatten(values, "", map); //$NON-NLS-1$
		return values;
	}

	@Override
	public void writeValues(Writer writer, Map<String, SettingsValue> values, String comments, boolean nullable) throws IOException {
		writeNode(writer, SettingsNode.from(values), 0, nullable);
		writer.write(System.lineSeparator());
	}

	private static void flatten(Map<String, SettingsValue> values, String path, Map<?, ?> map) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = String.valueOf(entry.getKey());
			String fullKey = NODE_VALUE_KEY.equals(key) ? path : join(path, key);
			Object value = entry.getValue();
			if (value == null) {
				if (!fullKey.isEmpty()) {
					values.put(fullKey, SettingsValues.nullValue());
				}
				continue;
			}
			if (value instanceof Map<?, ?> childMap) {
				flatten(values, fullKey, childMap);
			} else if (!fullKey.isEmpty()) {
				values.put(fullKey, toSettingsValue(value));
			}
		}
	}

	private static SettingsValue toSettingsValue(Object value) {
		if (value instanceof SettingsValue settingsValue) {
			return settingsValue;
		}
		if (value == null) {
			return SettingsValues.nullValue();
		}
		if (value instanceof String string) {
			return SettingsValues.string(string);
		}
		if (value instanceof BigDecimal number) {
			return new SettingsValue.NumberValue(number.toPlainString(), number);
		}
		if (value instanceof Boolean bool) {
			return SettingsValues.bool(bool);
		}
		if (value instanceof List<?> list) {
			List<SettingsValue> values = new ArrayList<>(list.size());
			for (Object item : list) {
				values.add(toSettingsValue(item));
			}
			return SettingsValues.list(SettingsValues.formatList(values), values);
		}
		throw new AppSettingsException("Unsupported JSON value: " + value); //$NON-NLS-1$
	}

	private static void writeNode(Writer writer, SettingsNode node, int level, boolean nullable) throws IOException {
		writer.write('{');
		if (node.hasWritableContent(nullable)) {
			writer.write(System.lineSeparator());
		}
		boolean wrote = false;
		if (node.hasWritableValue(nullable)) {
			writeEntryPrefix(writer, NODE_VALUE_KEY, level + 1, false);
			writeValue(writer, node.value(), level + 1, nullable);
			wrote = true;
		}
		for (Map.Entry<String, SettingsNode> entry : node.children().entrySet()) {
			SettingsNode child = entry.getValue();
			if (!child.hasWritableContent(nullable)) {
				continue;
			}
			if (wrote) {
				writer.write(',');
				writer.write(System.lineSeparator());
			}
			writeEntryPrefix(writer, entry.getKey(), level + 1, false);
			if (child.hasValue() && !child.hasChildren()) {
				writeValue(writer, child.value(), level + 1, nullable);
			} else {
				writeNode(writer, child, level + 1, nullable);
			}
			wrote = true;
		}
		if (wrote) {
			writer.write(System.lineSeparator());
			writeIndent(writer, level);
		}
		writer.write('}');
	}

	private static void writeEntryPrefix(Writer writer, String key, int level, boolean arrayItem) throws IOException {
		writeIndent(writer, level);
		if (arrayItem) {
			return;
		}
		writer.write(quote(key));
		writer.write(": "); //$NON-NLS-1$
	}

	private static void writeValue(Writer writer, SettingsValue value, int level, boolean nullable) throws IOException {
		if (value instanceof SettingsValue.NullValue && nullable) {
			writer.write("null"); //$NON-NLS-1$
		} else if (value instanceof SettingsValue.NumberValue || value instanceof SettingsValue.BooleanValue) {
			writer.write(SettingsValues.raw(value));
		} else if (value instanceof SettingsValue.ListValue listValue) {
			writer.write('[');
			boolean wrote = false;
			for (SettingsValue item : listValue.values()) {
				if (item instanceof SettingsValue.NullValue && !nullable) {
					continue;
				}
				if (wrote) {
					writer.write(',');
					writer.write(System.lineSeparator());
				} else {
					writer.write(System.lineSeparator());
				}
				writeEntryPrefix(writer, "", level + 1, true); //$NON-NLS-1$
				writeValue(writer, item, level + 1, nullable);
				wrote = true;
			}
			if (wrote) {
				writer.write(System.lineSeparator());
				writeIndent(writer, level);
			}
			writer.write(']');
		} else {
			writer.write(quote(SettingsValues.raw(value)));
		}
	}

	private static String join(String path, String key) {
		return path.isEmpty() ? key : path + "." + key; //$NON-NLS-1$
	}

	private static String quote(String value) {
		StringBuilder quoted = new StringBuilder(value.length() + 8);
		quoted.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\': quoted.append("\\\\"); break; //$NON-NLS-1$
			case '"': quoted.append("\\\""); break; //$NON-NLS-1$
			case '\n': quoted.append("\\n"); break; //$NON-NLS-1$
			case '\r': quoted.append("\\r"); break; //$NON-NLS-1$
			case '\t': quoted.append("\\t"); break; //$NON-NLS-1$
			default:
				if (c < 0x20) {
					quoted.append(String.format("\\u%04x", (int) c)); //$NON-NLS-1$
				} else {
					quoted.append(c);
				}
			}
		}
		return quoted.append('"').toString();
	}

	private static void writeIndent(Writer writer, int level) throws IOException {
		for (int i = 0; i < level; i++) {
			writer.write("  "); //$NON-NLS-1$
		}
	}

	private static final class Parser {
		private final CharSequence source;
		private int index;

		Parser(CharSequence source) {
			this.source = source;
		}

		Object parse() {
			Object value = parseValue();
			skipWhitespace();
			if (index != source.length()) {
				throw error("Unexpected trailing JSON content"); //$NON-NLS-1$
			}
			return value;
		}

		private Object parseValue() {
			skipWhitespace();
			if (index >= source.length()) {
				throw error("Unexpected end of JSON"); //$NON-NLS-1$
			}
			char c = source.charAt(index);
			if (c == '{') {
				return parseObject();
			}
			if (c == '[') {
				return parseArray();
			}
			if (c == '"') {
				return parseString();
			}
			if (c == 't') {
				expect("true"); //$NON-NLS-1$
				return Boolean.TRUE;
			}
			if (c == 'f') {
				expect("false"); //$NON-NLS-1$
				return Boolean.FALSE;
			}
			if (c == 'n') {
				expect("null"); //$NON-NLS-1$
				return null;
			}
			return parseNumber();
		}

		private Map<String, Object> parseObject() {
			expect('{');
			LinkedHashMap<String, Object> map = new LinkedHashMap<>();
			skipWhitespace();
			if (peek('}')) {
				index++;
				return map;
			}
			while (true) {
				skipWhitespace();
				if (!peek('"')) {
					throw error("JSON object keys must be strings"); //$NON-NLS-1$
				}
				String key = parseString();
				skipWhitespace();
				expect(':');
				map.put(key, parseValue());
				skipWhitespace();
				if (peek('}')) {
					index++;
					return map;
				}
				expect(',');
			}
		}

		private List<Object> parseArray() {
			expect('[');
			List<Object> values = new ArrayList<>();
			skipWhitespace();
			if (peek(']')) {
				index++;
				return values;
			}
			while (true) {
				values.add(parseValue());
				skipWhitespace();
				if (peek(']')) {
					index++;
					return values;
				}
				expect(',');
			}
		}

		private String parseString() {
			expect('"');
			StringBuilder value = new StringBuilder();
			while (index < source.length()) {
				char c = source.charAt(index++);
				if (c == '"') {
					return value.toString();
				}
				if (c == '\\') {
					value.append(parseEscape());
				} else if (c < 0x20) {
					throw error("JSON strings must escape control characters"); //$NON-NLS-1$
				} else {
					value.append(c);
				}
			}
			throw error("Unterminated JSON string"); //$NON-NLS-1$
		}

		private char parseEscape() {
			if (index >= source.length()) {
				throw error("Unterminated JSON escape"); //$NON-NLS-1$
			}
			char c = source.charAt(index++);
			switch (c) {
			case '"': return '"';
			case '\\': return '\\';
			case '/': return '/';
			case 'b': return '\b';
			case 'f': return '\f';
			case 'n': return '\n';
			case 'r': return '\r';
			case 't': return '\t';
			case 'u':
				if (index + 4 > source.length()) {
					throw error("Invalid JSON unicode escape"); //$NON-NLS-1$
				}
				int unicodeValue = 0;
				for (int i = 0; i < 4; i++) {
					char hexChar = source.charAt(index + i);
					int digit = Character.digit(hexChar, 16);
					if (digit < 0) {
						throw error("Invalid JSON unicode escape"); //$NON-NLS-1$
					}
					unicodeValue = (unicodeValue << 4) | digit;
				}
				index += 4;
				return (char) unicodeValue;
			default:
				throw error("Invalid JSON escape"); //$NON-NLS-1$
			}
		}

		private BigDecimal parseNumber() {
			int start = index;
			if (peek('-')) {
				index++;
			}
			readInteger();
			if (peek('.')) {
				index++;
				readDigits();
			}
			if (peek('e') || peek('E')) {
				index++;
				if (peek('+') || peek('-')) {
					index++;
				}
				readDigits();
			}
			if (start == index) {
				throw error("Expected JSON value"); //$NON-NLS-1$
			}
			if (index < source.length() && !isJsonDelimiter(source.charAt(index))) {
				throw error("Invalid JSON number"); //$NON-NLS-1$
			}
			try {
				return new BigDecimal(source.subSequence(start, index).toString());
			} catch (NumberFormatException e) {
				throw error("Invalid JSON number"); //$NON-NLS-1$
			}
		}

		private void readInteger() {
			if (index >= source.length()) {
				throw error("Expected JSON digit"); //$NON-NLS-1$
			}
			if (peek('0')) {
				index++;
				if (index < source.length() && Character.isDigit(source.charAt(index))) {
					throw error("JSON numbers must not contain leading zeros"); //$NON-NLS-1$
				}
				return;
			}
			if (source.charAt(index) < '1' || source.charAt(index) > '9') {
				throw error("Expected JSON digit"); //$NON-NLS-1$
			}
			readDigits();
		}

		private void readDigits() {
			int start = index;
			while (index < source.length() && Character.isDigit(source.charAt(index))) {
				index++;
			}
			if (start == index) {
				throw error("Expected JSON digit"); //$NON-NLS-1$
			}
		}

		private void skipWhitespace() {
			while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
				index++;
			}
		}

		private boolean peek(char c) {
			return index < source.length() && source.charAt(index) == c;
		}

		private static boolean isHexDigit(int c) {
			return c >= '0' && c <= '9'
					|| c >= 'a' && c <= 'f'
					|| c >= 'A' && c <= 'F';
		}

		private static boolean isJsonDelimiter(char c) {
			return Character.isWhitespace(c) || c == ',' || c == ']' || c == '}';
		}

		private void expect(char c) {
			if (!peek(c)) {
				throw error("Expected '" + c + "'"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			index++;
		}

		private void expect(String value) {
			if (!startsWith(source, index, value)) {
				throw error("Expected " + value); //$NON-NLS-1$
			}
			index += value.length();
		}

		private static boolean startsWith(CharSequence cs, int offset, String prefix) {
			if (offset < 0 || offset + prefix.length() > cs.length()) {
				return false;
			}
			for (int i = 0; i < prefix.length(); i++) {
				if (cs.charAt(offset + i) != prefix.charAt(i)) {
					return false;
				}
			}
			return true;
		}

		private AppSettingsException error(String message) {
			return new AppSettingsException(message + " at character " + index); //$NON-NLS-1$
		}
	}
}
