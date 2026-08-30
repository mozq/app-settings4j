/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleYamlSettingsFormat implements InternalSettingsFormat {
	private static final String NODE_VALUE_KEY = "@"; //$NON-NLS-1$
	private static final char SINGLE_QUOTE = '\'';
	private static final char DOUBLE_QUOTE = '"';

	@Override
	public LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		LinkedHashMap<String, List<SettingsValue>> lists = new LinkedHashMap<>();
		BufferedReader bufferedReader = new BufferedReader(reader);
		List<String> path = new ArrayList<>();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			String withoutComment = stripComment(line);
			if (withoutComment.isBlank()) {
				continue;
			}
			int indent = countIndent(withoutComment);
			if (indent % 2 != 0) {
				throw new AppSettingsException("YAML indentation must use multiples of two spaces"); //$NON-NLS-1$
			}
			int level = indent / 2;
			while (path.size() > level) {
				path.removeLast();
			}
			String item = withoutComment.substring(indent);
			if (item.startsWith("- ")) { //$NON-NLS-1$
				String fullKey = String.join(".", path); //$NON-NLS-1$
				if (fullKey.isEmpty()) {
					throw new AppSettingsException("YAML list entries must belong to a key"); //$NON-NLS-1$
				}
				List<SettingsValue> list = lists.computeIfAbsent(fullKey, key -> new ArrayList<>());
				SettingsValue itemValue = parseValue(item.substring(2).strip());
				list.add(itemValue);
				values.put(fullKey, SettingsValues.list(SettingsValues.formatList(list), list));
				continue;
			}
			int separator = findEntrySeparator(item);
			if (separator < 0) {
				throw new AppSettingsException("YAML entries must use key: value syntax"); //$NON-NLS-1$
			}
			String key = parseKey(item.substring(0, separator).strip());
			String value = item.substring(separator + 1).strip();
			if (value.isEmpty()) {
				path.add(key);
			} else {
				String fullKey = NODE_VALUE_KEY.equals(key) ? String.join(".", path) : join(path, key); //$NON-NLS-1$
				if (fullKey.isEmpty()) {
					throw new AppSettingsException("@ is reserved for nested values"); //$NON-NLS-1$
				}
				values.put(fullKey, parseValue(value));
			}
		}
		return values;
	}

	@Override
	public void writeValues(Writer writer, Map<String, SettingsValue> values, String comments, boolean nullable) throws IOException {
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		if (comments != null && !comments.isBlank()) {
			for (String line : comments.split("\\R")) { //$NON-NLS-1$
				bufferedWriter.write("# "); //$NON-NLS-1$
				bufferedWriter.write(line);
				bufferedWriter.newLine();
			}
		}
		writeNode(bufferedWriter, SettingsNode.from(values), 0, nullable);
		bufferedWriter.flush();
	}

	private static void writeNode(BufferedWriter writer, SettingsNode node, int level, boolean nullable) throws IOException {
		for (Map.Entry<String, SettingsNode> entry : node.children().entrySet()) {
			SettingsNode child = entry.getValue();
			if (!child.hasWritableContent(nullable)) {
				continue;
			}
			writeIndent(writer, level);
			writer.write(quoteKey(entry.getKey()));
			if (child.hasChildren()) {
				writer.write(':');
				writer.newLine();
				if (child.hasWritableValue(nullable)) {
					writeIndent(writer, level + 1);
					writer.write("'@': "); //$NON-NLS-1$
					writeValue(writer, child.value(), level + 1, nullable);
				}
				writeNode(writer, child, level + 1, nullable);
			} else {
				writer.write(": "); //$NON-NLS-1$
				writeValue(writer, child.value(), level, nullable);
			}
		}
	}

	private static void writeValue(BufferedWriter writer, SettingsValue value, int level, boolean nullable) throws IOException {
		if (value instanceof SettingsValue.NullValue && nullable) {
			writer.write("null"); //$NON-NLS-1$
			writer.newLine();
			return;
		}
		if (value instanceof SettingsValue.ListValue listValue) {
			writer.write(formatInlineList(listValue, nullable));
			writer.newLine();
			return;
		}
		writer.write(formatScalar(value));
		writer.newLine();
	}

	private static String stripComment(String line) {
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (escaped) {
				escaped = false;
			} else if (quote == DOUBLE_QUOTE && c == '\\') {
				escaped = true;
			} else if (quote == SINGLE_QUOTE && c == SINGLE_QUOTE && i + 1 < line.length() && line.charAt(i + 1) == SINGLE_QUOTE) {
				i++;
			} else if (quote == 0 && (c == SINGLE_QUOTE || c == DOUBLE_QUOTE)) {
				quote = c;
			} else if (quote == c) {
				quote = 0;
			} else if (quote == 0 && c == '#') {
				return line.substring(0, i);
			}
		}
		return line;
	}

	private static int countIndent(String line) {
		int indent = 0;
		while (indent < line.length() && line.charAt(indent) == ' ') {
			indent++;
		}
		return indent;
	}

	private static int findEntrySeparator(String item) {
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < item.length(); i++) {
			char c = item.charAt(i);
			if (escaped) {
				escaped = false;
			} else if (quote == DOUBLE_QUOTE && c == '\\') {
				escaped = true;
			} else if (quote == SINGLE_QUOTE && c == SINGLE_QUOTE && i + 1 < item.length() && item.charAt(i + 1) == SINGLE_QUOTE) {
				i++;
			} else if (quote == 0 && (c == SINGLE_QUOTE || c == DOUBLE_QUOTE)) {
				quote = c;
			} else if (quote == c) {
				quote = 0;
			} else if (quote == 0 && c == ':') {
				return i;
			}
		}
		return -1;
	}

	private static String join(List<String> path, String key) {
		if (path.isEmpty()) {
			return key;
		}
		return String.join(".", path) + "." + key; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String quoteKey(String key) {
		if (NODE_VALUE_KEY.equals(key) || key.contains(":") || key.contains("#") || key.isBlank()) { //$NON-NLS-1$ //$NON-NLS-2$
			return quoteSingle(key);
		}
		return key;
	}

	private static String parseKey(String key) {
		if (isSingleQuoted(key)) {
			return unquoteSingle(key);
		}
		if (isDoubleQuoted(key)) {
			return SettingsValues.unquote(key);
		}
		return key;
	}

	private static SettingsValue parseValue(String value) {
		if (isSingleQuoted(value)) {
			return SettingsValues.string(unquoteSingle(value));
		}
		if (isDoubleQuoted(value)) {
			return SettingsValues.string(SettingsValues.unquote(value));
		}
		if (isInlineList(value)) {
			return parseInlineList(value);
		}
		return SettingsValues.infer(value);
	}

	private static String formatScalar(SettingsValue value) {
		if (value instanceof SettingsValue.NullValue) {
			return "null"; //$NON-NLS-1$
		}
		if (value instanceof SettingsValue.NumberValue || value instanceof SettingsValue.BooleanValue) {
			return SettingsValues.raw(value);
		}
		return quoteYamlString(SettingsValues.raw(value));
	}

	private static String formatInlineList(SettingsValue.ListValue listValue, boolean nullable) {
		StringBuilder builder = new StringBuilder("["); //$NON-NLS-1$
		boolean wrote = false;
		for (SettingsValue item : listValue.values()) {
			if (item instanceof SettingsValue.NullValue && !nullable) {
				continue;
			}
			if (wrote) {
				builder.append(", "); //$NON-NLS-1$
			}
			builder.append(formatScalar(item));
			wrote = true;
		}
		return builder.append(']').toString();
	}

	private static SettingsValue parseInlineList(String value) {
		String body = value.substring(1, value.length() - 1);
		if (body.strip().isEmpty()) {
			return SettingsValues.list(value, List.of());
		}
		List<SettingsValue> values = new ArrayList<>();
		StringBuilder element = new StringBuilder();
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (escaped) {
				element.append('\\');
				element.append(c);
				escaped = false;
			} else if (quote == DOUBLE_QUOTE && c == '\\') {
				escaped = true;
			} else if (quote == SINGLE_QUOTE && c == SINGLE_QUOTE && i + 1 < body.length() && body.charAt(i + 1) == SINGLE_QUOTE) {
				element.append(c);
				element.append(body.charAt(++i));
			} else if (quote == 0 && (c == SINGLE_QUOTE || c == DOUBLE_QUOTE)) {
				quote = c;
				element.append(c);
			} else if (quote == c) {
				quote = 0;
				element.append(c);
			} else if (quote == 0 && c == ',') {
				values.add(parseValue(element.toString().strip()));
				element.setLength(0);
			} else {
				element.append(c);
			}
		}
		values.add(parseValue(element.toString().strip()));
		return SettingsValues.list(SettingsValues.formatList(values), values);
	}

	private static boolean isInlineList(String value) {
		return value.startsWith("[") && value.endsWith("]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isSingleQuoted(String value) {
		return value.length() >= 2 && value.charAt(0) == SINGLE_QUOTE && value.charAt(value.length() - 1) == SINGLE_QUOTE;
	}

	private static boolean isDoubleQuoted(String value) {
		return SettingsValues.isQuoted(value);
	}

	private static String unquoteSingle(String value) {
		return value.substring(1, value.length() - 1).replace("''", "'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String quoteYamlString(String value) {
		if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0) {
			return SettingsValues.quote(value);
		}
		return quoteSingle(value);
	}

	private static String quoteSingle(String value) {
		return SINGLE_QUOTE + value.replace("'", "''") + SINGLE_QUOTE; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void writeIndent(BufferedWriter writer, int level) throws IOException {
		for (int i = 0; i < level; i++) {
			writer.write("  "); //$NON-NLS-1$
		}
	}
}
