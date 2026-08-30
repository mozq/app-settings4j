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

final class KeyValueSettingsFormat implements InternalSettingsFormat {
	@Override
	public LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		BufferedReader bufferedReader = new BufferedReader(reader);
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			parseLine(values, line);
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
		for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
			if (entry.getValue() instanceof SettingsValue.NullValue && !nullable) {
				continue;
			}
			bufferedWriter.write(TextSettingsCodec.escapeKeyValueKey(entry.getKey()));
			bufferedWriter.write('=');
			bufferedWriter.write(TextSettingsCodec.escapeKeyValueValue(entry.getValue(), nullable));
			bufferedWriter.newLine();
		}
		bufferedWriter.flush();
	}

	private static void parseLine(Map<String, SettingsValue> values, String line) {
		String trimmed = line.stripLeading();
		if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) { //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		int separator = TextSettingsCodec.findSeparator(line);
		if (separator < 0) {
			values.put(SettingsValues.unescape(line.strip()), SettingsValues.nullValue());
		} else {
			String rawValue = line.substring(separator + 1);
			SettingsValue value = SettingsValues.inferEscaped(rawValue);
			values.put(SettingsValues.unescape(line.substring(0, separator).strip()), value);
		}
	}
}
