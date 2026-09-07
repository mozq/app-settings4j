/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the JSONC (JSON-with-comments) mode of {@link JsonSettingsFormat},
 * as returned by {@link SettingsFormats#jsonc()}.
 */
class JsoncSettingsFormatTest {
	private final JsonSettingsFormat format = new JsonSettingsFormat(true);

	@Test
	void readsLineAndBlockComments() throws IOException {
		String jsonc = """
				// leading comment
				{
				  /* block comment
				     spanning lines */
				  "theme": "dark", // trailing comment
				  "window": {
				    "width": 1024
				  }
				}
				""";

		SettingsReadResult result = format.readValuesWithComments(new StringReader(jsonc));

		assertEquals(List.of("leading comment", "block comment", "spanning lines", "trailing comment"), result.comments());
		assertEquals("dark", SettingsValues.object(result.values().get("theme"), false));
		assertEquals(new BigDecimal("1024"), SettingsValues.object(result.values().get("window.width"), false));
	}

	@Test
	void ignoresCommentMarkersInsideStrings() throws IOException {
		String jsonc = """
				{
				  "url": "https://example.com/*not a comment*/",
				  "note": "double slash // is not a comment either"
				}
				""";

		Map<String, Object> values = format.read(new StringReader(jsonc));

		assertEquals("https://example.com/*not a comment*/", values.get("url"));
		assertEquals("double slash // is not a comment either", values.get("note"));
	}

	@Test
	void writesCommentsWithSlashSlashPrefixThenJsonBody() throws IOException {
		StringWriter writer = new StringWriter();

		format.write(writer, Map.of("theme", "dark"), List.of("JSONC settings"));

		assertEquals("""
				// JSONC settings
				{
				  "theme": "dark"
				}
				""", writer.toString());
	}

	@Test
	void roundTripsValuesAndComments() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("theme", SettingsValues.string("dark"));
		values.put("window.width", SettingsValues.of(1024));

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, List.of("Notes settings"), false);

		SettingsReadResult reloaded = format.readValuesWithComments(new StringReader(writer.toString()));

		assertEquals(List.of("Notes settings"), reloaded.comments());
		assertEquals("dark", SettingsValues.object(reloaded.values().get("theme"), false));
		assertEquals(new BigDecimal("1024"), SettingsValues.object(reloaded.values().get("window.width"), false));
	}

	@Test
	void rejectsUnterminatedBlockComment() {
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{/* never closed}")));
	}

	@Test
	void stillRejectsInvalidJsonAfterStrippingComments() {
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("[] // comment")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": 01} /* trailing */")));
	}
}
