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
	 * Writes settings. The comments argument may be ignored by formats that do not
	 * support file-level comments.
	 */
	void write(Writer writer, Map<String, Object> values, String comments) throws IOException;
}
