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
import java.util.List;
import java.util.Map;

/**
 * Storage format extension point used by {@link AppSettings}.
 */
public interface SettingsFormat {
	/**
	 * Reads settings as ordered key-value entries. Implementations should preserve
	 * iteration order when the source format has one.
	 */
	Map<String, Object> read(Reader reader) throws IOException;

	/**
	 * Writes settings. Formats that do not support comments may ignore
	 * or reject the comments argument.
	 */
	void write(Writer writer, Map<String, Object> values, List<String> comments) throws IOException;

	/**
	 * Returns whether this format can carry file-level comments. {@link AppSettings}
	 * rejects non-empty comments for formats that report {@code false}.
	 */
	default boolean supportsComments() {
		return true;
	}
}
