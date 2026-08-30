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
import java.util.LinkedHashMap;
import java.util.Map;

final class IniSettingsFormat implements InternalSettingsFormat {
	private static final String NODE_VALUE_KEY = "@"; //$NON-NLS-1$

	@Override
	public LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		BufferedReader bufferedReader = new BufferedReader(reader);
		String section = null;
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) { //$NON-NLS-1$ //$NON-NLS-2$
				section = trimmed.substring(1, trimmed.length() - 1).trim();
				continue;
			}
			int separator = TextSettingsCodec.findSeparator(line);
			String rawKey = separator < 0 ? line.strip() : line.substring(0, separator).strip();
			String key = SettingsValues.unescape(rawKey);
			String rawValue = separator < 0 ? "" : line.substring(separator + 1); //$NON-NLS-1$
			SettingsValue value = separator < 0 ? SettingsValues.nullValue() : SettingsValues.inferEscaped(rawValue);
			if (NODE_VALUE_KEY.equals(key)) {
				if (section == null || section.isEmpty()) {
					throw new AppSettingsException("@ is reserved for section values"); //$NON-NLS-1$
				}
				values.put(section, value);
			} else if (section == null || section.isEmpty()) {
				values.put(key, value);
			} else {
				values.put(section + "." + key, value); //$NON-NLS-1$
			}
		}
		return values;
	}

	@Override
	public void writeValues(Writer writer, Map<String, SettingsValue> values, String comments, boolean nullable) throws IOException {
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		if (comments != null && !comments.isBlank()) {
			for (String line : comments.split("\\R")) { //$NON-NLS-1$
				bufferedWriter.write("; "); //$NON-NLS-1$
				bufferedWriter.write(line);
				bufferedWriter.newLine();
			}
		}
		SettingsNode root = SettingsNode.from(values);
		writeRootValues(bufferedWriter, root, nullable);
		writeSections(bufferedWriter, "", root, nullable); //$NON-NLS-1$
		bufferedWriter.flush();
	}

	private static void writeRootValues(BufferedWriter writer, SettingsNode root, boolean nullable) throws IOException {
		for (Map.Entry<String, SettingsNode> entry : root.children().entrySet()) {
			if (!entry.getValue().hasChildren() && entry.getValue().hasWritableValue(nullable)) {
				writeEntry(writer, entry.getKey(), entry.getValue().value(), nullable);
			}
		}
	}

	private static void writeSections(BufferedWriter writer, String path, SettingsNode node, boolean nullable) throws IOException {
		for (Map.Entry<String, SettingsNode> entry : node.children().entrySet()) {
			SettingsNode child = entry.getValue();
			if (!child.hasChildren()) {
				continue;
			}
			if (!child.hasWritableContent(nullable)) {
				continue;
			}
			String section = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(); //$NON-NLS-1$
			writer.newLine();
			writer.write('[' + section + ']');
			writer.newLine();
			if (child.hasWritableValue(nullable)) {
				writeEntry(writer, NODE_VALUE_KEY, child.value(), nullable);
			}
			for (Map.Entry<String, SettingsNode> childEntry : child.children().entrySet()) {
				SettingsNode grandchild = childEntry.getValue();
				if (!grandchild.hasChildren() && grandchild.hasWritableValue(nullable)) {
					writeEntry(writer, childEntry.getKey(), grandchild.value(), nullable);
				}
			}
			writeSections(writer, section, child, nullable);
		}
	}

	private static void writeEntry(BufferedWriter writer, String key, SettingsValue value, boolean nullable) throws IOException {
		if (value instanceof SettingsValue.NullValue && !nullable) {
			return;
		}
		writer.write(TextSettingsCodec.escapeIniKey(key));
		writer.write('=');
		writer.write(TextSettingsCodec.escapeIniValue(value, nullable));
		writer.newLine();
	}
}
