/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Verifies {@link IniSettingsFormat} directly, including the exact example
 * documented in the README's "INI Files" section.
 */
class IniSettingsFormatTest {
	private final IniSettingsFormat format = new IniSettingsFormat();

	@Test
	void matchesReadmeExampleOnWrite() throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		values.put("theme", "dark");
		values.put("editor", "enabled");
		values.put("editor.wrap", true);
		values.put("editor.font.size", 14);

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		List<String> lines = writer.toString().lines().toList();

		assertEquals(List.of(
				"theme=dark",
				"",
				"[editor]",
				"@=enabled",
				"wrap=true",
				"",
				"[editor.font]",
				"size=14"), lines);
	}

	@Test
	void matchesReadmeExampleOnRead() throws IOException {
		String content = String.join("\n",
				"theme=dark",
				"",
				"[editor]",
				"@=enabled",
				"wrap=true",
				"",
				"[editor.font]",
				"size=14");

		Map<String, Object> values = format.read(new StringReader(content));

		assertEquals("dark", values.get("theme"));
		assertEquals("enabled", values.get("editor"));
		assertEquals(true, values.get("editor.wrap"));
		assertEquals(new BigDecimal("14"), values.get("editor.font.size"));
	}

	@Test
	void usesKeyValueSyntaxForValues() throws IOException {
		String content = String.join("\n",
				"theme = dark",
				"count=10",
				"enabled=true",
				"tags=[work, \"hello, world\", 3]",
				"empty.value=",
				"empty.flag",
				"last.opened=null",
				"literal=\"null\"");

		Map<String, Object> values = format.read(new StringReader(content));

		assertEquals("dark", values.get("theme"));
		assertEquals(new BigDecimal("10"), values.get("count"));
		assertEquals(true, values.get("enabled"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3")), values.get("tags"));
		assertEquals("", values.get("empty.value"));
		assertEquals("", values.get("empty.flag"));
		assertFalse(values.containsKey("last.opened"));
		assertEquals("null", values.get("literal"));
	}

	@Test
	void rejectsAtAtRoot() {
		assertThrows(AppSettingsException.class, () -> format.readValues(new StringReader("@=enabled")));
	}

	@Test
	void writesFileLevelCommentsWithSemicolonPrefix() throws IOException {
		StringWriter writer = new StringWriter();
		format.write(writer, Map.of("theme", "dark"), "INI settings");

		assertTrue(writer.toString().startsWith("; INI settings" + System.lineSeparator()));
	}

	@Test
	void writesAndReadsNullValuesWhenNullableIsEnabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("lastOpened", SettingsValues.nullValue());

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, true);

		assertTrue(writer.toString().contains("lastOpened=null"));

		LinkedHashMap<String, SettingsValue> reloaded = format.readValues(new StringReader(writer.toString()));
		assertEquals(new SettingsValue.NullValue(), reloaded.get("lastOpened"));
	}
}
