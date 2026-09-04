/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsTest {
	@TempDir
	Path tempDir;

	@Nested
	class Paths {
		@Test
		void resolvesWindowsPathWithVendor() {
			Path appData = tempDir.resolve("AppData").resolve("Roaming");

			AppSettings settings = settings("acme", "notes", "settings.properties",
					"Windows 11", Map.of("APPDATA", appData.toString()));

			assertEquals(appData.resolve("acme").resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void resolvesWindowsPathWithoutVendor() {
			Path appData = tempDir.resolve("AppData").resolve("Roaming");

			AppSettings settings = settings(null, "notes", "settings.properties",
					"Windows 11", Map.of("APPDATA", appData.toString()));

			assertEquals(appData.resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void resolvesWindowsFallbackPathWhenAppDataIsMissing() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Windows 11", Map.of());

			assertEquals(tempDir.resolve("AppData").resolve("Roaming").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void resolvesMacosPath() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Mac OS X", Map.of());

			assertEquals(tempDir.resolve("Library").resolve("Application Support").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void resolvesLinuxPathWithXdgConfigHome() {
			Path xdgConfigHome = tempDir.resolve("xdg-config");

			AppSettings settings = settings("acme", "notes", "settings.properties",
					"Linux", Map.of("XDG_CONFIG_HOME", xdgConfigHome.toString()));

			assertEquals(xdgConfigHome.resolve("acme").resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void resolvesLinuxFallbackPath() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Linux", Map.of());

			assertEquals(tempDir.resolve(".config").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path());
		}

		@Test
		void rejectsPathSegmentsAndEmptyNames() {
			AppEnvironment environment = environment("Linux", Map.of());

			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme/notes", "notes", "settings.properties", environment));
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "", "settings.properties", environment));
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "notes", "../settings.properties", environment));
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "notes", ".", environment));
		}

		@Test
		void rejectsMissingUserHomeWhenResolvingPath() {
			AppSettings settings = AppSettings.of("acme", "notes", "settings.properties",
					new AppEnvironment("Linux", "", Map.of()));

			assertThrows(AppSettingsException.class, settings::path);
		}
	}

	@Nested
	class BasicOperations {
		@Test
		void loadsMissingFileAsEmptySettings() throws IOException {
			AppSettings settings = settings("acme", "notes", "settings.properties").set("theme", "dark");

			settings.loadFrom(tempDir.resolve("missing.properties"));

			assertTrue(settings.asStringMap().isEmpty());
		}

		@Test
		void storesToNestedDirectoryAndKeepsKeyOrder() throws IOException {
			Path file = tempDir.resolve("config").resolve("settings.properties");

			settings("acme", "notes", "settings.properties")
					.set("theme", "light")
					.set("window.width", 1024)
					.set("theme", "dark")
					.storeTo(file, "Application settings");

			AppSettings loaded = settings("acme", "notes", "settings.properties").loadFrom(file);

			assertEquals(List.of("theme", "window.width"), List.copyOf(loaded.keySet()));
			assertEquals("dark", loaded.getString("theme", ""));
			assertEquals(1024, loaded.getInt("window.width", 0));
			assertEquals("# Application settings", Files.readAllLines(file).getFirst());
		}

		@Test
		void removesKeys() {
			AppSettings settings = settings("acme", "notes", "settings.properties")
					.set("theme", "dark")
					.remove("theme");

			assertFalse(settings.contains("theme"));
			assertEquals("light", settings.getString("theme", "light"));
		}

		@Test
		void rejectsInvalidKeysForLookupAndRemoval() {
			AppSettings settings = settings("acme", "notes", "settings.properties");

			assertThrows(IllegalArgumentException.class, () -> settings.contains(null));
			assertThrows(IllegalArgumentException.class, () -> settings.contains(""));
			assertThrows(IllegalArgumentException.class, () -> settings.remove(null));
			assertThrows(IllegalArgumentException.class, () -> settings.remove(""));
		}

		@Test
		void countsVisibleKeysAndClearsSettings() {
			AppSettings settings = settings("acme", "notes", "settings.properties")
					.set("theme", "dark")
					.set("window.width", 1024)
					.set("last.opened", null);

			assertEquals(2, settings.size());
			assertFalse(settings.isEmpty());
			assertEquals(List.of("theme", "window.width"), List.copyOf(settings.keySet()));

			settings.nullable(true);

			assertEquals(3, settings.size());
			assertEquals(List.of("theme", "window.width", "last.opened"), List.copyOf(settings.keySet()));

			settings.clear();

			assertEquals(0, settings.size());
			assertTrue(settings.isEmpty());
			assertTrue(settings.keySet().isEmpty());
		}

		@Test
		void returnsUnmodifiableMapsAndDefaultLists() {
			AppSettings settings = settings("acme", "notes", "settings.properties");

			Map<String, String> strings = settings.set("theme", "dark").asStringMap();
			Set<String> keys = settings.set("window.width", 1024).keySet();
			List<Object> defaultList = settings.getList("missing", Arrays.asList("one", null));

			assertEquals(List.of("theme", "window.width"), List.copyOf(keys));
			assertThrows(UnsupportedOperationException.class, () -> keys.add("other"));
			assertEquals(null, settings.getList("missing", (List<?>)null));
			assertThrows(UnsupportedOperationException.class, () -> strings.put("other", "value"));
			assertThrows(UnsupportedOperationException.class, () -> defaultList.add("two"));
		}
	}

	@Nested
	class FormatSelection {
		@Test
		void choosesBuiltInFormatFromFileName() throws IOException {
			Path properties = tempDir.resolve("settings.properties");
			Path ini = tempDir.resolve("settings.ini");
			Path yaml = tempDir.resolve("settings.yaml");
			Path yml = tempDir.resolve("settings.yml");
			Path json = tempDir.resolve("settings.json");

			settings("acme", "notes", "settings.properties").set("window.width", 1024).storeTo(properties);
			settings("acme", "notes", "settings.ini").set("window.width", 1024).storeTo(ini);
			settings("acme", "notes", "settings.yaml").set("window.width", 1024).storeTo(yaml);
			settings("acme", "notes", "settings.yml").set("window.width", 1024).storeTo(yml);
			settings("acme", "notes", "settings.json").set("window.width", 1024).storeTo(json);

			assertEquals("window.width=1024", Files.readString(properties).strip());
			assertTrue(Files.readString(ini).contains("[window]"));
			assertTrue(Files.readString(yaml).contains("window:"));
			assertTrue(Files.readString(yml).contains("window:"));
			assertTrue(Files.readString(json).contains("\"window\""));
		}

		@Test
		void explicitFormatOverridesFileNameExtension() throws IOException {
			Path file = tempDir.resolve("settings.json");

			settings("acme", "notes", "settings.json")
					.format(SettingsFormats.keyValue())
					.set("theme", "dark")
					.storeTo(file);

			assertEquals("theme=dark", Files.readString(file).strip());
		}
	}

	@Nested
	class ValueAccess {
		@Test
		void storesAndReadsSupportedTypes() throws IOException {
			Instant instant = Instant.parse("2026-08-29T12:34:56.789Z");
			Date date = Date.from(instant);
			LocalDateTime localDateTime = LocalDateTime.parse("2026-08-29T12:34:56.789");
			LocalDate localDate = LocalDate.parse("2026-08-29");
			LocalTime localTime = LocalTime.parse("12:34:56.789");
			OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-08-29T12:34:56.789+09:00");
			ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-08-29T12:34:56.789+09:00[Asia/Tokyo]");
			Path file = tempDir.resolve("settings.properties");

			settings("acme", "notes", "settings.properties")
					.set("integer", 10)
					.set("long", 20L)
					.set("double", 3.5)
					.set("boolean", true)
					.set("enum", SampleEnum.Second)
					.set("locale", Locale.JAPAN)
					.set("timeZone", TimeZone.getTimeZone("Asia/Tokyo"))
					.set("instant", instant)
					.set("date", date)
					.set("localDateTime", localDateTime)
					.set("localDate", localDate)
					.set("localTime", localTime)
					.set("offsetDateTime", offsetDateTime)
					.set("zonedDateTime", zonedDateTime)
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.properties").loadFrom(file);

			assertEquals(10, loaded.getInt("integer", 0));
			assertEquals(20L, loaded.getLong("long", 0L));
			assertEquals(3.5, loaded.getDouble("double", 0.0));
			assertTrue(loaded.getBoolean("boolean", false));
			assertEquals(SampleEnum.Second, loaded.getEnum("enum", SampleEnum.class, SampleEnum.First));
			assertEquals(Locale.JAPAN, loaded.getLocale("locale", Locale.ROOT));
			assertEquals("Asia/Tokyo", loaded.getTimeZone("timeZone", TimeZone.getTimeZone("UTC")).getID());
			assertEquals(instant, loaded.getInstant("instant", Instant.EPOCH));
			assertEquals(date, loaded.getDate("date", new Date(0)));
			assertEquals(localDateTime, loaded.getLocalDateTime("localDateTime", LocalDateTime.MIN));
			assertEquals(localDate, loaded.getLocalDate("localDate", LocalDate.MIN));
			assertEquals(localTime, loaded.getLocalTime("localTime", LocalTime.MIN));
			assertEquals(offsetDateTime, loaded.getOffsetDateTime("offsetDateTime", OffsetDateTime.MIN));
			assertEquals(zonedDateTime, loaded.getZonedDateTime("zonedDateTime", ZonedDateTime.parse("2000-01-01T00:00:00Z[UTC]")));
		}

		@Test
		void returnsTypedValuesAndConvertsWhenPossible() {
			AppSettings settings = settings(null, "notes", "settings.properties")
					.set("window.width", 1024)
					.set("byte", 12)
					.set("flag", true)
					.set("letter", "A")
					.set("recent.tags", List.of("work", "archive"));

			assertEquals(new BigDecimal("1024"), settings.get("window.width"));
			assertEquals("fallback", settings.get("missing", "fallback"));
			assertEquals(1024, settings.getInt("window.width", 0));
			assertEquals((byte) 12, settings.getByte("byte", (byte) 0));
			assertEquals('A', settings.getChar("letter", '\0'));
			assertTrue(settings.getBoolean("flag", false));
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags"));
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags", String.class));
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags", String.class, List.of()));
		}

		@Test
		void convertsCompatibleNumberTypes() {
			AppSettings settings = settings(null, "notes", "settings.properties")
					.set("doubleInteger", 12.0d)
					.set("shortText", "123")
					.set("floatText", "1.25")
					.set("decimal", new BigDecimal("123.50"))
					.set("bigInteger", new BigInteger("12345678901234567890"))
					.set("numericText", "42");

			assertEquals(12, settings.getInt("doubleInteger", 0));
			assertEquals((short) 123, settings.getShort("shortText", (short) 0));
			assertEquals(1.25f, settings.getFloat("floatText", 0.0f));
			assertEquals(123.5d, settings.getDouble("decimal", 0.0d));
			assertEquals(new BigDecimal("12345678901234567890"), settings.getBigDecimal("bigInteger"));
			assertEquals(new BigInteger("42"), settings.getBigInteger("numericText"));
			assertEquals(new BigInteger("42"), settings.getBigInteger("numericText", BigInteger.ZERO));
			assertEquals(new BigDecimal("42"), settings.getBigDecimal("numericText", BigDecimal.ZERO));
			assertEquals(new BigDecimal("42"), settings.getBigDecimal("numericText"));
			assertEquals(null, settings.getBigDecimal("missing"));
			assertEquals(7, settings.getInt("decimal", 7));
			assertEquals((byte) 7, settings.getByte("bigInteger", (byte) 7));
		}

		@Test
		void convertsBasicTypesThroughSharedRules() {
			AppSettings settings = settings(null, "notes", "settings.properties")
					.set("enabled", "true")
					.set("disabled", false)
					.set("letter", "A")
					.set("space", " ")
					.set("enum", "Second")
					.set("locale", "ja-JP")
					.set("timeZone", "Asia/Tokyo")
					.set("uppercaseBoolean", "TRUE");

			assertTrue(settings.getBoolean("enabled", false));
			assertFalse(settings.getBoolean("disabled", true));
			assertEquals('A', settings.getChar("letter", '\0'));
			assertEquals(' ', settings.getChar("space", '\0'));
			assertEquals(SampleEnum.Second, settings.getEnum("enum", SampleEnum.class, SampleEnum.First));
			assertEquals(SampleEnum.Second, settings.getEnum("enum", SampleEnum.class));
			assertEquals(Locale.JAPAN, settings.getLocale("locale", Locale.ROOT));
			assertEquals(Locale.JAPAN, settings.getLocale("locale"));
			assertEquals("Asia/Tokyo", settings.getTimeZone("timeZone", TimeZone.getTimeZone("UTC")).getID());
			assertEquals("Asia/Tokyo", settings.getTimeZone("timeZone").getID());
			assertFalse(settings.getBoolean("uppercaseBoolean", false));
		}

		@Test
		void convertsApplicationSettingTypesThroughSharedRules() throws IOException {
			Path file = tempDir.resolve("settings.properties");
			Path outputDirectory = Path.of("data", "output");
			URI endpoint = URI.create("https://example.com/api");
			ZoneId zoneId = ZoneId.of("Asia/Tokyo");

			settings(null, "notes", "settings.properties")
					.set("output.directory", outputDirectory)
					.set("endpoint", endpoint)
					.set("zoneId", zoneId)
					.set("timeZone", TimeZone.getTimeZone(zoneId))
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file);

			assertEquals(outputDirectory, loaded.getPath("output.directory", Path.of(".")));
			assertEquals(outputDirectory, loaded.getPath("output.directory"));
			assertEquals(endpoint, loaded.getUri("endpoint", URI.create("https://example.org")));
			assertEquals(endpoint, loaded.getUri("endpoint"));
			assertEquals(zoneId, loaded.getZoneId("zoneId", ZoneId.of("UTC")));
			assertEquals(zoneId, loaded.getZoneId("zoneId"));
			assertEquals(zoneId, loaded.getZoneId("timeZone", ZoneId.of("UTC")));
			assertEquals("Asia/Tokyo", loaded.getTimeZone("zoneId", TimeZone.getTimeZone("UTC")).getID());
			assertTrue(Files.readString(file).contains("zoneId=Asia/Tokyo"));
			assertTrue(Files.readString(file).contains("endpoint=https://example.com/api"));
		}

		@Test
		void convertsCompatibleDateTimeTypesUsingConfiguredTimeZone() {
			TimeZone utc = TimeZone.getTimeZone("UTC");
			Instant instant = Instant.parse("2026-08-29T12:34:56Z");
			OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-08-29T21:34:56+09:00");
			ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-08-29T21:34:56+09:00[Asia/Tokyo]");
			LocalDateTime localDateTime = LocalDateTime.parse("2026-08-29T12:34:56");
			LocalDate localDate = LocalDate.parse("2026-08-29");
			AppSettings settings = settings(null, "notes", "settings.properties")
					.timeZone(utc)
					.set("instant", instant)
					.set("offset", offsetDateTime)
					.set("zoned", zonedDateTime)
					.set("localDateTime", localDateTime)
					.set("localDate", localDate)
					.set("localTime", LocalTime.parse("12:34:56"))
					.set("localDateText", "2026-08-29")
					.set("instantText", "2026-08-29T12:34:56Z");

			assertEquals(Date.from(instant), settings.getDate("instant", new Date(0)));
			assertEquals(Date.from(instant), settings.getDate("instant"));
			assertEquals(LocalDateTime.parse("2026-08-29T12:34:56"), settings.getLocalDateTime("offset", LocalDateTime.MIN));
			assertEquals(LocalDateTime.parse("2026-08-29T12:34:56"), settings.getLocalDateTime("offset"));
			assertEquals(LocalDate.parse("2026-08-29"), settings.getLocalDate("zoned", LocalDate.MIN));
			assertEquals(LocalDate.parse("2026-08-29"), settings.getLocalDate("zoned"));
			assertEquals(LocalTime.parse("12:34:56"), settings.getLocalTime("instantText", LocalTime.MIN));
			assertEquals(LocalTime.parse("12:34:56"), settings.getLocalTime("instantText"));
			assertEquals(instant, settings.getInstant("localDateTime", Instant.EPOCH));
			assertEquals(instant, settings.getInstant("localDateTime"));
			assertEquals(Instant.parse("2026-08-29T00:00:00Z"), settings.getInstant("localDate", Instant.EPOCH));
			assertEquals(Date.from(Instant.parse("2026-08-29T00:00:00Z")), settings.getDate("localDateText", new Date(0)));
			assertEquals(Instant.EPOCH, settings.getInstant("localTime", Instant.EPOCH));
			assertEquals(instant.atZone(utc.toZoneId()), settings.getZonedDateTime("instant", ZonedDateTime.now()));
			assertEquals(instant.atZone(utc.toZoneId()), settings.getZonedDateTime("instant"));
			assertEquals(OffsetDateTime.parse("2026-08-29T12:34:56Z"), settings.getOffsetDateTime("zoned", OffsetDateTime.MIN));
			assertEquals(OffsetDateTime.parse("2026-08-29T12:34:56Z"), settings.getOffsetDateTime("zoned"));
		}

		@Test
		void returnsDefaultsForMissingOrInvalidValues() {
			AppSettings settings = settings(null, "notes", "settings.properties")
					.set("integer", "not-number")
					.set("boolean", "yes")
					.set("instant", "2026-08-29 12:34:56")
					.set("timeZone", "not-a-zone")
					.set("tags", List.of("work", "10"))
					.set("empty", "");

			assertEquals(7, settings.getInt("integer", 7));
			assertFalse(settings.getBoolean("boolean", false));
			assertEquals(Instant.EPOCH, settings.getInstant("instant", Instant.EPOCH));
			assertEquals(TimeZone.getTimeZone("UTC"), settings.getTimeZone("timeZone", TimeZone.getTimeZone("UTC")));
			assertEquals("fallback", settings.getString("missing", "fallback"));
			assertEquals(List.of(1, 2), settings.getList("tags", Integer.class, List.of(1, 2)));
			assertEquals("", settings.getString("empty", "fallback"));
			assertEquals(7, settings.getInt("empty", 7));
			assertEquals(BigDecimal.ONE, settings.getBigDecimal("empty", BigDecimal.ONE));
			assertTrue(settings.getBoolean("empty", true));
			assertEquals(Instant.EPOCH, settings.getInstant("empty", Instant.EPOCH));
			assertEquals('x', settings.getChar("empty", 'x'));
		}

		@Test
		void storesNonFiniteFloatingPointValuesAsStrings() throws IOException {
			Path file = tempDir.resolve("settings.json");

			settings(null, "notes", "settings.json")
					.set("nan", Double.NaN)
					.set("infinity", Float.POSITIVE_INFINITY)
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.json").loadFrom(file);

			assertEquals("NaN", loaded.get("nan"));
			assertEquals("Infinity", loaded.get("infinity"));
		}
	}

	@Nested
	class NullValues {
		@Test
		void hidesNullValuesByDefault() throws IOException {
			Path file = tempDir.resolve("settings.properties");

			settings(null, "notes", "settings.properties")
					.nullable(true)
					.set("last.opened", null)
					.set("recent.tags", Arrays.asList("work", null, "archive"))
					.nullable(false)
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file);

			assertFalse(loaded.contains("last.opened"));
			assertFalse(loaded.keySet().contains("last.opened"));
			assertEquals("fallback", loaded.getString("last.opened", "fallback"));
			assertEquals(List.of("work", "archive"), loaded.getList("recent.tags", String.class, List.of()));
			assertFalse(Files.readString(file).contains("null"));
		}

		@Test
		void exposesNullValuesWhenNullableIsEnabled() throws IOException {
			Path file = tempDir.resolve("settings.properties");

			settings(null, "notes", "settings.properties")
					.nullable(true)
					.set("last.opened", null)
					.set("recent.tags", Arrays.asList("work", null, "archive"))
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties")
					.nullable(true)
					.loadFrom(file);

			assertTrue(loaded.contains("last.opened"));
			assertTrue(loaded.keySet().contains("last.opened"));
			assertEquals(null, loaded.get("last.opened", "fallback"));
			assertEquals(null, loaded.getString("last.opened", "fallback"));
			assertEquals(Arrays.asList("work", null, "archive"), loaded.getList("recent.tags", String.class, List.of()));
			assertTrue(Files.readString(file).contains("last.opened=null"));
		}

		@Test
		void keepsNullInternallyWhenNullableIsDisabled() {
			AppSettings settings = settings(null, "notes", "settings.properties")
					.set("last.opened", null);

			assertFalse(settings.contains("last.opened"));
			assertEquals("fallback", settings.getString("last.opened", "fallback"));

			settings.nullable(true);

			assertTrue(settings.contains("last.opened"));
			assertEquals(null, settings.getString("last.opened", "fallback"));
		}
	}

	@Nested
	class KeyValueFiles {
		@Test
		void infersScalarsListsAndQuotedStrings() throws IOException {
			Path file = tempDir.resolve("settings.properties");
			Files.writeString(file, String.join(System.lineSeparator(),
					"name=Notes",
					"theme = dark",
					"description=\" Personal notes \"",
					"count=10",
					"enabled=true",
					"created=2026-08-29T12:34:56Z",
					"tags=[work, \"hello, world\", \"[draft]\", \" leading \", 3]",
					"empty.value=",
					"empty.flag",
					"last.opened=null",
					"label=\"null\"",
					"date.label=\"2026-08-29\"",
					"list.label=\"[one, two]\""));

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file);

			assertEquals("Notes", loaded.getString("name", ""));
			assertEquals("dark", loaded.getString("theme", ""));
			assertEquals(" Personal notes ", loaded.getString("description", ""));
			assertEquals(new BigDecimal("10"), loaded.get("count"));
			assertEquals(true, loaded.get("enabled"));
			assertInstanceOf(TemporalAccessor.class, loaded.get("created"));
			assertEquals(List.of("work", "hello, world", "[draft]", " leading ", new BigDecimal("3")), loaded.getList("tags", List.of()));
			assertEquals("", loaded.getString("empty.value", "fallback"));
			assertEquals("", loaded.getString("empty.flag", "fallback"));
			assertFalse(loaded.contains("last.opened"));
			assertEquals("null", loaded.getString("label", ""));
			assertEquals("2026-08-29", loaded.getString("date.label", ""));
			assertEquals("[one, two]", loaded.getString("list.label", ""));
		}

		@Test
		void writesQuotedStringsWhenNeeded() throws IOException {
			Path file = tempDir.resolve("settings.properties");

			settings(null, "notes", "settings.properties")
					.nullable(true)
					.set("empty", "")
					.set("reserved", "true")
					.set("number.text", "1.0")
					.set("date.text", "2026-08-29")
					.set("list.text", "[one, two]")
					.set("quoted.text", "\"quoted\"")
					.set("path", "C:\\Users\\me")
					.set("tags", List.of("work", "hello, world", "[draft]", "item]", " leading ", "1.0"))
					.storeTo(file);

			List<String> lines = Files.readAllLines(file);

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
		void escapesKeysAndReadsQuotedEscapes() throws IOException {
			Path file = tempDir.resolve("settings.properties");

			settings(null, "notes", "settings.properties")
					.set("path=key", "first line\nsecond line")
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file);

			assertTrue(Files.readString(file).contains("path\\=key=\"first line\\nsecond line\""));
			assertEquals("first line\nsecond line", loaded.getString("path=key", ""));
		}
	}

	@Nested
	class IniFiles {
		@Test
		void writesDottedKeysAsSectionsAndUsesAtForSectionValue() throws IOException {
			Path file = tempDir.resolve("settings.ini");

			settings("acme", "notes", "settings.ini")
					.set("theme", "dark")
					.set("empty", "")
					.set("editor", "enabled")
					.set("editor.wrap", true)
					.set("editor.font.size", 14)
					.storeTo(file, "INI settings");

			AppSettings loaded = settings("acme", "notes", "settings.ini").loadFrom(file);
			String content = Files.readString(file);

			assertTrue(content.startsWith("; INI settings"));
			assertTrue(content.contains("theme=dark"));
			assertTrue(content.contains("empty="));
			assertTrue(content.contains("[editor]"));
			assertTrue(content.contains("@=enabled"));
			assertTrue(content.contains("wrap=true"));
			assertTrue(content.contains("[editor.font]"));
			assertTrue(content.contains("size=14"));
			assertEquals(List.of("theme", "empty", "editor", "editor.wrap", "editor.font.size"), List.copyOf(loaded.keySet()));
			assertEquals("enabled", loaded.getString("editor", ""));
			assertEquals("", loaded.getString("empty", "fallback"));
			assertTrue(loaded.getBoolean("editor.wrap", false));
			assertEquals(14, loaded.getInt("editor.font.size", 0));
		}

		@Test
		void usesKeyValueSyntaxForValues() throws IOException {
			Path file = tempDir.resolve("settings.ini");
			Files.writeString(file, String.join(System.lineSeparator(),
					"theme = dark",
					"count=10",
					"enabled=true",
					"tags=[work, \"hello, world\", 3]",
					"empty.value=",
					"empty.flag",
					"last.opened=null",
					"literal=\"null\""));

			AppSettings loaded = settings("acme", "notes", "settings.ini").loadFrom(file);

			assertEquals("dark", loaded.get("theme"));
			assertEquals(new BigDecimal("10"), loaded.get("count"));
			assertEquals(true, loaded.get("enabled"));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("tags", List.of()));
			assertEquals("", loaded.getString("empty.value", "fallback"));
			assertEquals("", loaded.getString("empty.flag", "fallback"));
			assertFalse(loaded.contains("last.opened"));
			assertEquals("null", loaded.get("literal"));
		}

		@Test
		void rejectsAtAtRoot() throws IOException {
			Path file = tempDir.resolve("settings.ini");
			Files.writeString(file, "@=enabled");

			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.ini").loadFrom(file));
		}
	}

	@Nested
	class SimpleYamlFiles {
		@Test
		void writesNestedObjectsListsAndTypedScalars() throws IOException {
			Path file = tempDir.resolve("settings.yaml");

			settings("acme", "notes", "settings.yaml")
					.set("theme", "dark")
					.set("window.width", 1024)
					.set("autosave.enabled", true)
					.set("recent.tags", List.of("work", "hello, world", 3))
					.set("empty.tags", List.of())
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.yaml").loadFrom(file);
			String content = Files.readString(file);

			assertTrue(content.contains("theme: 'dark'"));
			assertTrue(content.contains("window:"));
			assertTrue(content.contains("  width: 1024"));
			assertTrue(content.contains("autosave:"));
			assertTrue(content.contains("  enabled: true"));
			assertTrue(content.contains("recent:"));
			assertTrue(content.contains("  tags: ['work', 'hello, world', 3]"));
			assertTrue(content.contains("empty:"));
			assertTrue(content.contains("  tags: []"));
			assertEquals("dark", loaded.getString("theme", ""));
			assertEquals(1024, loaded.getInt("window.width", 0));
			assertTrue(loaded.getBoolean("autosave.enabled", false));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("recent.tags", List.of()));
			assertEquals(List.of(), loaded.getList("empty.tags", List.of("fallback")));
		}

		@Test
		void readsQuotedStringsUnquotedScalarsAndLists() throws IOException {
			Path file = tempDir.resolve("settings.yaml");
			Files.writeString(file, """
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
					""");

			AppSettings loaded = settings("acme", "notes", "settings.yaml").loadFrom(file);

			assertInstanceOf(String.class, loaded.asMap().get("title"));
			assertEquals("Lee's Notes", loaded.getString("owner", ""));
			assertEquals("first line\nsecond line", loaded.getString("path", ""));
			assertInstanceOf(BigDecimal.class, loaded.asMap().get("count"));
			assertInstanceOf(Boolean.class, loaded.asMap().get("enabled"));
			assertInstanceOf(TemporalAccessor.class, loaded.asMap().get("created"));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("inlineTags", List.of()));
			assertEquals(List.of(), loaded.getList("emptyTags", List.of("fallback")));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("blockTags", List.of()));
			assertEquals("value # not comment", loaded.getString("quoted.key:with:colon", ""));
		}

		@Test
		void rejectsInvalidYamlSyntax() throws IOException {
			Path oddIndent = tempDir.resolve("odd.yaml");
			Path itemWithoutKey = tempDir.resolve("item.yaml");
			Files.writeString(oddIndent, " theme: dark");
			Files.writeString(itemWithoutKey, "- item");

			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.yaml").loadFrom(oddIndent));
			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.yaml").loadFrom(itemWithoutKey));
		}
	}

	@Nested
	class JsonFiles {
		@Test
		void readsNativeJsonTypesAndFlattensObjects() throws IOException {
			String json = """
					{
					  "theme": "dark",
					  "escaped": "line1\\nline2\\t\\u263a",
					  "window": {
					    "width": 1024
					  },
					  "autosave": {
					    "enabled": true
					  },
					  "recent": {
					    "tags": ["work", "hello, world", 3, false, null]
					  },
					  "ignored": null
					}
					""";

			Map<String, Object> values = SettingsFormats.json().read(new StringReader(json));

			assertEquals(List.of("theme", "escaped", "window.width", "autosave.enabled", "recent.tags"), List.copyOf(values.keySet()));
			assertEquals("dark", values.get("theme"));
			assertEquals("line1\nline2\t\u263a", values.get("escaped"));
			assertEquals(new BigDecimal("1024"), values.get("window.width"));
			assertEquals(true, values.get("autosave.enabled"));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3"), false), values.get("recent.tags"));
		}

		@Test
		void writesNestedObjectsListsNullsAndAtValues() throws IOException {
			Path file = tempDir.resolve("settings.json");

			settings("acme", "notes", "settings.json")
					.nullable(true)
					.set("editor", "enabled")
					.set("editor.wrap", true)
					.set("editor.font.size", 14)
					.set("last.opened", null)
					.set("recent.tags", List.of("work", "hello, world", 3))
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.json")
					.nullable(true)
					.loadFrom(file);
			String content = Files.readString(file);

			assertTrue(content.contains("\"editor\""));
			assertTrue(content.contains("\"@\": \"enabled\""));
			assertTrue(content.contains("\"wrap\": true"));
			assertTrue(content.contains("\"size\": 14"));
			assertTrue(content.contains("\"last\""));
			assertTrue(content.contains("\"opened\": null"));
			assertEquals("enabled", loaded.getString("editor", ""));
			assertTrue(loaded.getBoolean("editor.wrap", false));
			assertEquals(14, loaded.getInt("editor.font.size", 0));
			assertEquals(null, loaded.getString("last.opened", "fallback"));
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("recent.tags", List.of()));
		}

		@Test
		void rejectsNonObjectAndInvalidJson() {
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("[]")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": }")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": 01}")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": 1.}")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": \"\\u12xz\"}")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": \"line\nbreak\"}")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": true // comment\n}")));
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": /* comment */ true}")));
		}
	}

	@Nested
	class CustomFormats {
		@Test
		void usesCustomSettingsFormat() throws IOException {
			Path file = tempDir.resolve("settings.custom");
			SettingsFormat format = new SettingsFormat() {
				@Override
				public Map<String, Object> read(Reader reader) throws IOException {
					StringWriter content = new StringWriter();
					reader.transferTo(content);
					return Map.of("loaded", content.toString());
				}

				@Override
				public void write(Writer writer, Map<String, Object> values, String comments) throws IOException {
					writer.write(String.valueOf(values.get("message")));
				}
			};

			settings("acme", "notes", "settings.custom")
					.format(format)
					.set("message", "hello")
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.custom")
					.format(format)
					.loadFrom(file);

			assertEquals("hello", loaded.getString("loaded", ""));
		}

		@Test
		void rejectsNullCustomFormat() {
			AppSettings settings = settings("acme", "notes", "settings.custom");

			assertThrows(NullPointerException.class, () -> settings.format(null));
		}
	}

	@Nested
	class ConcurrentAccess {
		@Test
		void handlesConcurrentReadsAndWrites() throws Exception {
			AppSettings settings = settings("acme", "notes", "settings.properties");
			int threadCount = 10;
			int iterations = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch latch = new CountDownLatch(1);
			List<Future<?>> futures = new ArrayList<>();

			for (int i = 0; i < threadCount; i++) {
				final int threadId = i;
				futures.add(executor.submit(() -> {
					latch.await();
					for (int j = 0; j < iterations; j++) {
						if (threadId % 2 == 0) {
							settings.set("key_" + threadId + "_" + j, j);
						} else {
							settings.getString("key_" + (threadId - 1) + "_" + j, "default");
							settings.asMap();
						}
					}
					return null;
				}));
			}

			latch.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
			executor.shutdown();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private AppSettings settings(String vendor, String app, String fileName) {
		return settings(vendor, app, fileName, "Linux", Map.of());
	}

	private AppSettings settings(String vendor, String app, String fileName, String osName, Map<String, String> env) {
		return AppSettings.of(vendor, app, fileName, environment(osName, env));
	}

	private AppEnvironment environment(String osName, Map<String, String> env) {
		return new AppEnvironment(osName, tempDir.toString(), env);
	}

	private enum SampleEnum {
		First,
		Second
	}
}
