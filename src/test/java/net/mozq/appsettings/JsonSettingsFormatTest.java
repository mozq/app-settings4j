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
 * Verifies {@link JsonSettingsFormat} directly, including the exact examples
 * documented in the README's "JSON Files" section.
 */
class JsonSettingsFormatTest {
	private final JsonSettingsFormat format = new JsonSettingsFormat();

	@Test
	void matchesReadmeExampleOnRead() throws IOException {
		String json = """
				{
				  "theme": "dark",
				  "window": {
				    "width": 1024
				  },
				  "autosave": {
				    "enabled": true
				  },
				  "recent": {
				    "tags": ["work", "hello, world", 3]
				  }
				}
				""";

		Map<String, Object> values = format.read(new StringReader(json));

		assertEquals("dark", values.get("theme"));
		assertEquals(new BigDecimal("1024"), values.get("window.width"));
		assertEquals(true, values.get("autosave.enabled"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3")), values.get("recent.tags"));
	}

	@Test
	void matchesReadmeExampleOnWrite() throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		values.put("theme", "dark");
		values.put("window.width", 1024);
		values.put("autosave.enabled", true);
		values.put("recent.tags", List.of("work", "hello, world", 3));

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		String content = writer.toString();

		assertTrue(content.contains("\"theme\": \"dark\""));
		assertTrue(content.contains("\"window\""));
		assertTrue(content.contains("\"width\": 1024"));
		assertTrue(content.contains("\"autosave\""));
		assertTrue(content.contains("\"enabled\": true"));
		assertTrue(content.contains("\"recent\""));
		assertTrue(content.contains("\"tags\""));
	}

	@Test
	void matchesReadmeAtValueExample() throws IOException {
		String json = """
				{
				  "editor": {
				    "@": "enabled",
				    "wrap": true,
				    "font": {
				      "size": 14
				    }
				  }
				}
				""";

		Map<String, Object> values = format.read(new StringReader(json));

		assertEquals("enabled", values.get("editor"));
		assertEquals(true, values.get("editor.wrap"));
		assertEquals(new BigDecimal("14"), values.get("editor.font.size"));
	}

	@Test
	void readsEscapeSequencesAndMixedListTypesAndIgnoresExplicitNulls() throws IOException {
		String json = """
				{
				  "escaped": "line1\\nline2\\t\\u263a",
				  "mixed": ["work", "hello, world", 3, false, null],
				  "ignored": null
				}
				""";

		Map<String, Object> values = format.read(new StringReader(json));

		assertEquals(List.of("escaped", "mixed"), List.copyOf(values.keySet()));
		assertEquals("line1\nline2\t\u263a", values.get("escaped"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3"), false), values.get("mixed"));
	}

	@Test
	void writesNestedObjectsAndNullsWhenNullableIsEnabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("editor", SettingsValues.string("enabled"));
		values.put("editor.wrap", SettingsValues.bool(true));
		values.put("last.opened", SettingsValues.nullValue());

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, true);
		String content = writer.toString();

		assertTrue(content.contains("\"editor\""));
		assertTrue(content.contains("\"@\": \"enabled\""));
		assertTrue(content.contains("\"wrap\": true"));
		assertTrue(content.contains("\"last\""));
		assertTrue(content.contains("\"opened\": null"));

		LinkedHashMap<String, SettingsValue> reloaded = format.readValues(new StringReader(content));
		assertEquals("enabled", SettingsValues.object(reloaded.get("editor"), true));
		assertEquals(true, SettingsValues.object(reloaded.get("editor.wrap"), true));
		assertEquals(new SettingsValue.NullValue(), reloaded.get("last.opened"));
	}

	@Test
	void rejectsNonObjectAndInvalidJson() {
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("[]")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": }")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": 01}")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": 1.}")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": \"\\u12xz\"}")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": \"line\nbreak\"}")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": true // comment\n}")));
		assertThrows(AppSettingsException.class, () -> format.read(new StringReader("{\"a\": /* comment */ true}")));
	}
}
