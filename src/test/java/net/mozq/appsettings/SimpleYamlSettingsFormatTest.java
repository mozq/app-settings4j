/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link SimpleYamlSettingsFormat} directly, including the exact
 * examples documented in the README's "Simple YAML Files" section.
 */
class SimpleYamlSettingsFormatTest {
	private final SimpleYamlSettingsFormat format = new SimpleYamlSettingsFormat();

	@Test
	void matchesReadmeExampleOnWrite() throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		values.put("theme", "dark");
		values.put("window.width", 1024);
		values.put("autosave.enabled", true);
		values.put("recent.tags", List.of("work", "hello, world", 3));
		values.put("empty.tags", List.of());

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		String content = writer.toString();

		assertTrue(content.contains("theme: 'dark'"));
		assertTrue(content.contains("window:"));
		assertTrue(content.contains("  width: 1024"));
		assertTrue(content.contains("autosave:"));
		assertTrue(content.contains("  enabled: true"));
		assertTrue(content.contains("recent:"));
		assertTrue(content.contains("  tags: ['work', 'hello, world', 3]"));
		assertTrue(content.contains("empty:"));
		assertTrue(content.contains("  tags: []"));
	}

	@Test
	void matchesReadmeExampleOnRead() throws IOException {
		String content = String.join("\n",
				"theme: 'dark'",
				"window:",
				"  width: 1024",
				"autosave:",
				"  enabled: true",
				"recent:",
				"  tags: ['work', 'hello, world', 3]",
				"empty:",
				"  tags: []");

		Map<String, Object> values = format.read(new StringReader(content));

		assertEquals("dark", values.get("theme"));
		assertEquals(new BigDecimal("1024"), values.get("window.width"));
		assertEquals(true, values.get("autosave.enabled"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3")), values.get("recent.tags"));
		assertEquals(List.of(), values.get("empty.tags"));
	}

	@Test
	void matchesReadmeAtValueExample() throws IOException {
		String content = String.join("\n",
				"editor:",
				"  '@': 'enabled'",
				"  wrap: true",
				"  font:",
				"    size: 14");

		Map<String, Object> values = format.read(new StringReader(content));

		assertEquals("enabled", values.get("editor"));
		assertEquals(true, values.get("editor.wrap"));
		assertEquals(new BigDecimal("14"), values.get("editor.font.size"));
	}

	@Test
	void writesAtKeyForValuesWithChildren() throws IOException {
		LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		values.put("editor", "enabled");
		values.put("editor.wrap", true);
		values.put("editor.font.size", 14);

		StringWriter writer = new StringWriter();
		format.write(writer, values, null);
		String content = writer.toString();

		assertTrue(content.contains("editor:"));
		assertTrue(content.contains("  '@': 'enabled'"));
		assertTrue(content.contains("  wrap: true"));
		assertTrue(content.contains("  font:"));
		assertTrue(content.contains("    size: 14"));
	}

	@Test
	void readsQuotedStringsUnquotedScalarsAndBlockLists() throws IOException {
		String content = """
				title: '10'
				owner: 'Lee''s Notes'
				path: "first line\\nsecond line"
				count: 10
				enabled: true
				created: 2026-08-29
				inlineTags: ['work', 'hello, world', 3]
				emptyTags: []
				blockTags:
				  - "work"
				  - 'hello, world'
				  - 3
				quoted:
				  'key:with:colon': 'value # not comment'
				""";

		Map<String, Object> values = format.read(new StringReader(content));

		assertInstanceOf(String.class, values.get("title"));
		assertEquals("Lee's Notes", values.get("owner"));
		assertEquals("first line\nsecond line", values.get("path"));
		assertInstanceOf(BigDecimal.class, values.get("count"));
		assertInstanceOf(Boolean.class, values.get("enabled"));
		assertInstanceOf(TemporalAccessor.class, values.get("created"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3")), values.get("inlineTags"));
		assertEquals(List.of(), values.get("emptyTags"));
		assertEquals(List.of("work", "hello, world", new BigDecimal("3")), values.get("blockTags"));
		assertEquals("value # not comment", values.get("quoted.key:with:colon"));
	}

	@Test
	void rejectsOddIndentation() {
		assertThrows(AppSettingsException.class, () -> format.readValues(new StringReader(" theme: dark")));
	}

	@Test
	void rejectsListEntriesWithoutAKey() {
		assertThrows(AppSettingsException.class, () -> format.readValues(new StringReader("- item")));
	}

	@Test
	void writesAndReadsNullValuesWhenNullableIsEnabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("lastOpened", SettingsValues.nullValue());

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, true);

		assertTrue(writer.toString().contains("lastOpened: null"));

		LinkedHashMap<String, SettingsValue> reloaded = format.readValues(new StringReader(writer.toString()));
		assertEquals(new SettingsValue.NullValue(), reloaded.get("lastOpened"));
	}

	@Test
	void writesAndReadsListsContainingNullElementsWhenNullableIsEnabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("tags", SettingsValues.of(Arrays.asList("work", null, "archive")));

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, true);
		String content = writer.toString();

		LinkedHashMap<String, SettingsValue> reloaded = format.readValues(new StringReader(content));
		assertEquals(Arrays.asList("work", null, "archive"), SettingsValues.object(reloaded.get("tags"), true));
	}

	@Test
	void rejectsDottedKeysWithEmptyPathSegments() {
		assertThrows(AppSettingsException.class,
				() -> format.write(new StringWriter(), Map.of("a..b", "value"), null));
	}

	@Test
	void rejectsAtAtRoot() {
		assertThrows(AppSettingsException.class, () -> format.readValues(new StringReader("'@': enabled")));
	}

	@Test
	void omitsObjectsThatContainOnlyNullValuesWhenNullableIsDisabled() throws IOException {
		LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
		values.put("theme", SettingsValues.string("dark"));
		values.put("editor.wrap", SettingsValues.nullValue());
		values.put("editor.font.size", SettingsValues.nullValue());

		StringWriter writer = new StringWriter();
		format.writeValues(writer, values, null, false);
		String content = writer.toString();

		assertEquals("theme: 'dark'" + System.lineSeparator(), content);
	}
}
