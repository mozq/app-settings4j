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

final class KeyValueSettingsFormat implements InternalSettingsFormat {
	@Override
	public LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException {
		return readValuesWithComments(reader).values();
	}

	@Override
	public SettingsReadResult readValuesWithComments(Reader reader) throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		BufferedReader bufferedReader = new BufferedReader(reader);
		List<String> comments = new ArrayList<>();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			String comment = SettingsComments.parseLine(line, '#', '!');
			if (comment != null) {
				comments.add(comment);
				continue;
			}
			parseLine(values, line);
		}
		return new SettingsReadResult(values, comments);
	}

	@Override
	public void writeValues(Writer writer, Map<String, SettingsValue> values, List<String> comments, boolean nullable) throws IOException {
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		SettingsComments.write(bufferedWriter, comments, "# ");
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
		if (line.isBlank()) {
			return;
		}
		int separator = TextSettingsCodec.findSeparator(line);
		if (separator < 0) {
			values.put(SettingsValues.unescape(line.strip()), SettingsValues.string(""));
		} else {
			String rawValue = line.substring(separator + 1);
			SettingsValue value = SettingsValues.inferEscaped(rawValue);
			values.put(SettingsValues.unescape(line.substring(0, separator).strip()), value);
		}
	}

}
