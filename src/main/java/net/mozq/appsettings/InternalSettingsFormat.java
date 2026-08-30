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
import java.util.LinkedHashMap;
import java.util.Map;

interface InternalSettingsFormat extends SettingsFormat {
	LinkedHashMap<String, SettingsValue> readValues(Reader reader) throws IOException;

	default void writeValues(Writer writer, Map<String, SettingsValue> values, String comments) throws IOException {
		writeValues(writer, values, comments, false);
	}

	void writeValues(Writer writer, Map<String, SettingsValue> values, String comments, boolean nullable) throws IOException;

	@Override
	default Map<String, Object> read(Reader reader) throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<String, SettingsValue> entry : readValues(reader).entrySet()) {
			if (!(entry.getValue() instanceof SettingsValue.NullValue)) {
				values.put(entry.getKey(), SettingsValues.object(entry.getValue(), false));
			}
		}
		return values;
	}

	@Override
	default void write(Writer writer, Map<String, Object> values, String comments) throws IOException {
		LinkedHashMap<String, SettingsValue> settingsValues = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			settingsValues.put(entry.getKey(), SettingsValues.of(entry.getValue()));
		}
		writeValues(writer, settingsValues, comments);
	}
}
