/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link KeyValueSettingsFormat} directly, including the exact
 * examples documented in the README's "Key-Value Files" section.
 */
class KeyValueSettingsFormatTest {
	private final KeyValueSettingsFormat format = new KeyValueSettingsFormat();

	@Test
	void matchesReadmeBasicExample() throws IOException {
		String text = String.join("\n",
				"theme=dark",
				"window.width=1024",
				"autosave.enabled=true",
				"opened.at=2026-08-29T12:34:56Z",
				"recent.tags=[work, archive, \"hello, world\"]",
				"empty=",
				"flag",
				"last.opened=null",
				"description=\" Personal notes \"");

		Map<String, Object> values = format.read(new StringReader(text));

		assertEquals("dark", values.get("theme"));
		assertEquals(new BigDecimal("1024"), values.get("window.width"));
		assertEquals(true, values.get("autosave.enabled"));
		assertEquals(Instant.parse("2026-08-29T12:34:56Z"), values.get("opened.at"));
		assertEquals(List.of("work", "archive", "hello, world"), values.get("recent.tags"));
		assertEquals("", values.get("empty"));
		assertEquals("", values.get("flag"));
		assertFalse(values.containsKey("last.opened"));
		assertEquals(" Personal notes ", values.get("description"));
	}

	@Test
	void matchesReadmeQuotedStringExamples() throws IOException {
		String text = String.join("\n",
				"label=\"null\"",
				"enabled.label=\"true\"",
				"version.label=\"1.0\"",
				"date.label=\"2026-08-29\"",
				"list.label=\"[one, two]\"");

		Map<String, Object> values = format.read(new StringReader(text));

		assertEquals("null", values.get("label"));
		assertEquals("true", values.get("enabled.label"));
		assertEquals("1.0", values.get("version.label"));
		assertEquals("2026-08-29", values.get("date.label"));
		assertEquals("[one, two]", values.get("list.label"));
	}

	@Test
	void matchesReadmeListQuotingExample() throws IOException {
		String text = "tags=[work, \"hello, world\", \"[draft]\", \"item]\", \" leading \"]";

		Map<String, Object> values = format.read(new StringReader(text));

		assertEquals(List.of("work", "hello, world", "[draft]", "item]", " leading "), values.get("tags"));
	}

	@Test
	void writesQuotedStringsWhenValuesNeedProtection() throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		values.put("empty", "");
		values.put("reserved", "true");
		values.put("number.text", "1.0");
		values.put("date.text", "2026-08-29");
		values.put("list.text", "[one, two]");
		values.put("quoted.text", "\"quoted\"");
		values.put("path", "C:\\Users\\me");
		values.put("tags", List.of("work", "hello, world", "[draft]", "item]", " leading ", "1.0"));

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		List<String> lines = writer.toString().lines().toList();

		assertTrue(lines.contains("empty="));
		assertTrue(lines.contains("reserved=\"true\""));
		assertTrue(lines.contains("number.text=\"1.0\""));
		assertTrue(lines.contains("date.text=\"2026-08-29\""));
		assertTrue(lines.contains("list.text=\"[one, two]\""));
		assertTrue(lines.contains("quoted.text=\"\\\"quoted\\\"\""));
		assertTrue(lines.contains("path=\"C:\\\\Users\\\\me\""));
		assertTrue(lines.contains("tags=[work, \"hello, world\", \"[draft]\", \"item]\", \" leading \", \"1.0\"]"));
	}

	@Test
	void escapesKeysAndPreservesEscapedValuesOnRoundTrip() throws IOException {
		Map<String, Object> values = Map.of("path=key", "first line\nsecond line");

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		String content = writer.toString();

		assertTrue(content.contains("path\\=key=\"first line\\nsecond line\""));

		Map<String, Object> reloaded = format.read(new StringReader(content));
		assertEquals("first line\nsecond line", reloaded.get("path=key"));
	}

	@Test
	void writesAndReadsNullValuesWhenNullableIsEnabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("last.opened", SettingsValues.nullValue());
		values.put("theme", SettingsValues.string("dark"));

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, true);
		String content = writer.toString();

		assertTrue(content.contains("last.opened=null"));

		LinkedHashMap<String, SettingsValue> reloaded = format.readValues(new StringReader(content));
		assertEquals(new SettingsValue.NullValue(), reloaded.get("last.opened"));
		assertEquals("dark", SettingsValues.object(reloaded.get("theme"), true));
	}

	@Test
	void ignoresCommentAndBlankLinesOnRead() throws IOException {
		String text = String.join("\n",
				"# a comment",
				"! another comment",
				"",
				"theme=dark");

		Map<String, Object> values = format.read(new StringReader(text));

		assertEquals(Map.of("theme", "dark"), values);
	}
}
