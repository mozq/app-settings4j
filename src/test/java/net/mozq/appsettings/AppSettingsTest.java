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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.ArrayList;
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
			Path appData = tempDir.resolve("AppData").resolve("Roaming"); //$NON-NLS-1$ //$NON-NLS-2$

			AppSettings settings = settings("acme", "notes", "settings.properties", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Windows 11", Map.of("APPDATA", appData.toString())); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(appData.resolve("acme").resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void resolvesWindowsPathWithoutVendor() {
			Path appData = tempDir.resolve("AppData").resolve("Roaming"); //$NON-NLS-1$ //$NON-NLS-2$

			AppSettings settings = settings(null, "notes", "settings.properties", //$NON-NLS-1$ //$NON-NLS-2$
					"Windows 11", Map.of("APPDATA", appData.toString())); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(appData.resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Test
		void resolvesWindowsFallbackPathWhenAppDataIsMissing() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Windows 11", Map.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			assertEquals(tempDir.resolve("AppData").resolve("Roaming").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		@Test
		void resolvesMacosPath() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Mac OS X", Map.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			assertEquals(tempDir.resolve("Library").resolve("Application Support").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		@Test
		void resolvesLinuxPathWithXdgConfigHome() {
			Path xdgConfigHome = tempDir.resolve("xdg-config"); //$NON-NLS-1$

			AppSettings settings = settings("acme", "notes", "settings.properties", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Linux", Map.of("XDG_CONFIG_HOME", xdgConfigHome.toString())); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(xdgConfigHome.resolve("acme").resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void resolvesLinuxFallbackPath() {
			AppSettings settings = settings("acme", "notes", "settings.properties", "Linux", Map.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			assertEquals(tempDir.resolve(".config").resolve("acme").resolve("notes").resolve("settings.properties"), settings.path()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		@Test
		void rejectsPathSegmentsAndEmptyNames() {
			AppEnvironment environment = environment("Linux", Map.of()); //$NON-NLS-1$

			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme/notes", "notes", "settings.properties", environment)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "", "settings.properties", environment)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "notes", "../settings.properties", environment)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThrows(IllegalArgumentException.class, () -> AppSettings.of("acme", "notes", ".", environment)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void rejectsMissingUserHomeWhenResolvingPath() {
			AppSettings settings = AppSettings.of("acme", "notes", "settings.properties", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					new AppEnvironment("Linux", "", Map.of())); //$NON-NLS-1$ //$NON-NLS-2$

			assertThrows(AppSettingsException.class, settings::path);
		}
	}

	@Nested
	class BasicOperations {
		@Test
		void loadsMissingFileAsEmptySettings() throws IOException {
			AppSettings settings = settings("acme", "notes", "settings.properties").set("theme", "dark"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			settings.loadFrom(tempDir.resolve("missing.properties")); //$NON-NLS-1$

			assertTrue(settings.asStringMap().isEmpty());
		}

		@Test
		void storesToNestedDirectoryAndKeepsKeyOrder() throws IOException {
			Path file = tempDir.resolve("config").resolve("settings.properties"); //$NON-NLS-1$ //$NON-NLS-2$

			settings("acme", "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.set("theme", "light") //$NON-NLS-1$ //$NON-NLS-2$
					.set("window.width", 1024) //$NON-NLS-1$
					.set("theme", "dark") //$NON-NLS-1$ //$NON-NLS-2$
					.storeTo(file, "Application settings"); //$NON-NLS-1$

			AppSettings loaded = settings("acme", "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertEquals(List.of("theme", "window.width"), List.copyOf(loaded.asStringMap().keySet())); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("dark", loaded.getString("theme", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(1024, loaded.getInt("window.width", 0)); //$NON-NLS-1$
			assertEquals("# Application settings", Files.readAllLines(file).getFirst()); //$NON-NLS-1$
		}

		@Test
		void removesKeys() {
			AppSettings settings = settings("acme", "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.set("theme", "dark") //$NON-NLS-1$ //$NON-NLS-2$
					.remove("theme"); //$NON-NLS-1$

			assertFalse(settings.contains("theme")); //$NON-NLS-1$
			assertEquals("light", settings.getString("theme", "light")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void returnsUnmodifiableMapsAndDefaultLists() {
			AppSettings settings = settings("acme", "notes", "settings.properties"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			Map<String, String> strings = settings.set("theme", "dark").asStringMap(); //$NON-NLS-1$ //$NON-NLS-2$
			List<Object> defaultList = settings.getList("missing", Arrays.asList("one", null)); //$NON-NLS-1$ //$NON-NLS-2$

			assertThrows(UnsupportedOperationException.class, () -> strings.put("other", "value")); //$NON-NLS-1$ //$NON-NLS-2$
			assertThrows(UnsupportedOperationException.class, () -> defaultList.add("two")); //$NON-NLS-1$
		}
	}

	@Nested
	class FormatSelection {
		@Test
		void choosesBuiltInFormatFromFileName() throws IOException {
			Path properties = tempDir.resolve("settings.properties"); //$NON-NLS-1$
			Path ini = tempDir.resolve("settings.ini"); //$NON-NLS-1$
			Path yaml = tempDir.resolve("settings.yaml"); //$NON-NLS-1$
			Path yml = tempDir.resolve("settings.yml"); //$NON-NLS-1$
			Path json = tempDir.resolve("settings.json"); //$NON-NLS-1$

			settings("acme", "notes", "settings.properties").set("window.width", 1024).storeTo(properties); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			settings("acme", "notes", "settings.ini").set("window.width", 1024).storeTo(ini); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			settings("acme", "notes", "settings.yaml").set("window.width", 1024).storeTo(yaml); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			settings("acme", "notes", "settings.yml").set("window.width", 1024).storeTo(yml); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			settings("acme", "notes", "settings.json").set("window.width", 1024).storeTo(json); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			assertEquals("window.width=1024", Files.readString(properties).strip()); //$NON-NLS-1$
			assertTrue(Files.readString(ini).contains("[window]")); //$NON-NLS-1$
			assertTrue(Files.readString(yaml).contains("window:")); //$NON-NLS-1$
			assertTrue(Files.readString(yml).contains("window:")); //$NON-NLS-1$
			assertTrue(Files.readString(json).contains("\"window\"")); //$NON-NLS-1$
		}

		@Test
		void explicitFormatOverridesFileNameExtension() throws IOException {
			Path file = tempDir.resolve("settings.json"); //$NON-NLS-1$

			settings("acme", "notes", "settings.json") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.format(SettingsFormats.keyValue())
					.set("theme", "dark") //$NON-NLS-1$ //$NON-NLS-2$
					.storeTo(file);

			assertEquals("theme=dark", Files.readString(file).strip()); //$NON-NLS-1$
		}
	}

	@Nested
	class ValueAccess {
		@Test
		void storesAndReadsSupportedTypes() throws IOException {
			Instant instant = Instant.parse("2026-08-29T12:34:56.789Z"); //$NON-NLS-1$
			Date date = Date.from(instant);
			LocalDateTime localDateTime = LocalDateTime.parse("2026-08-29T12:34:56.789"); //$NON-NLS-1$
			LocalDate localDate = LocalDate.parse("2026-08-29"); //$NON-NLS-1$
			LocalTime localTime = LocalTime.parse("12:34:56.789"); //$NON-NLS-1$
			OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-08-29T12:34:56.789+09:00"); //$NON-NLS-1$
			ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-08-29T12:34:56.789+09:00[Asia/Tokyo]"); //$NON-NLS-1$
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$

			settings("acme", "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.set("integer", 10) //$NON-NLS-1$
					.set("long", 20L) //$NON-NLS-1$
					.set("double", 3.5) //$NON-NLS-1$
					.set("boolean", true) //$NON-NLS-1$
					.set("enum", SampleEnum.Second) //$NON-NLS-1$
					.set("locale", Locale.JAPAN) //$NON-NLS-1$
					.set("timeZone", TimeZone.getTimeZone("Asia/Tokyo")) //$NON-NLS-1$ //$NON-NLS-2$
					.set("instant", instant) //$NON-NLS-1$
					.set("date", date) //$NON-NLS-1$
					.set("localDateTime", localDateTime) //$NON-NLS-1$
					.set("localDate", localDate) //$NON-NLS-1$
					.set("localTime", localTime) //$NON-NLS-1$
					.set("offsetDateTime", offsetDateTime) //$NON-NLS-1$
					.set("zonedDateTime", zonedDateTime) //$NON-NLS-1$
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertEquals(10, loaded.getInt("integer", 0)); //$NON-NLS-1$
			assertEquals(20L, loaded.getLong("long", 0L)); //$NON-NLS-1$
			assertEquals(3.5, loaded.getDouble("double", 0.0)); //$NON-NLS-1$
			assertTrue(loaded.getBoolean("boolean", false)); //$NON-NLS-1$
			assertEquals(SampleEnum.Second, loaded.getEnum("enum", SampleEnum.class, SampleEnum.First)); //$NON-NLS-1$
			assertEquals(Locale.JAPAN, loaded.getLocale("locale", Locale.ROOT)); //$NON-NLS-1$
			assertEquals("Asia/Tokyo", loaded.getTimeZone("timeZone", TimeZone.getTimeZone("UTC")).getID()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(instant, loaded.getInstant("instant", Instant.EPOCH)); //$NON-NLS-1$
			assertEquals(date, loaded.getDate("date", new Date(0))); //$NON-NLS-1$
			assertEquals(localDateTime, loaded.getLocalDateTime("localDateTime", LocalDateTime.MIN)); //$NON-NLS-1$
			assertEquals(localDate, loaded.getLocalDate("localDate", LocalDate.MIN)); //$NON-NLS-1$
			assertEquals(localTime, loaded.getLocalTime("localTime", LocalTime.MIN)); //$NON-NLS-1$
			assertEquals(offsetDateTime, loaded.getOffsetDateTime("offsetDateTime", OffsetDateTime.MIN)); //$NON-NLS-1$
			assertEquals(zonedDateTime, loaded.getZonedDateTime("zonedDateTime", ZonedDateTime.parse("2000-01-01T00:00:00Z[UTC]"))); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Test
		void returnsTypedValuesAndConvertsWhenPossible() {
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("window.width", 1024) //$NON-NLS-1$
					.set("byte", 12) //$NON-NLS-1$
					.set("flag", true) //$NON-NLS-1$
					.set("letter", "A") //$NON-NLS-1$ //$NON-NLS-2$
					.set("recent.tags", List.of("work", "archive")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertEquals(new BigDecimal("1024"), settings.get("window.width")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(1024, settings.get("window.width", 0)); //$NON-NLS-1$
			assertEquals(1024, settings.get("window.width", Integer.class)); //$NON-NLS-1$
			assertEquals(1024, settings.get("window.width", Integer.class, 0)); //$NON-NLS-1$
			assertEquals(1024, settings.get("window.width", int.class, 0)); //$NON-NLS-1$
			assertEquals((byte) 12, settings.get("byte", byte.class, (byte) 0)); //$NON-NLS-1$
			assertEquals('A', settings.get("letter", char.class, '\0')); //$NON-NLS-1$
			assertTrue(settings.get("flag", boolean.class, false)); //$NON-NLS-1$
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags", String.class)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(List.of("work", "archive"), settings.getList("recent.tags", String.class, List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void convertsCompatibleNumberTypes() {
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("doubleInteger", 12.0d) //$NON-NLS-1$
					.set("shortText", "123") //$NON-NLS-1$ //$NON-NLS-2$
					.set("floatText", "1.25") //$NON-NLS-1$ //$NON-NLS-2$
					.set("decimal", new BigDecimal("123.50")) //$NON-NLS-1$ //$NON-NLS-2$
					.set("bigInteger", new BigInteger("12345678901234567890")) //$NON-NLS-1$ //$NON-NLS-2$
					.set("numericText", "42"); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(12, settings.getInt("doubleInteger", 0)); //$NON-NLS-1$
			assertEquals(12, settings.get("doubleInteger", Integer.class, 0)); //$NON-NLS-1$
			assertEquals((short) 123, settings.getShort("shortText", (short) 0)); //$NON-NLS-1$
			assertEquals((short) 123, settings.get("shortText", Short.class, (short) 0)); //$NON-NLS-1$
			assertEquals(1.25f, settings.getFloat("floatText", 0.0f)); //$NON-NLS-1$
			assertEquals(1.25f, settings.get("floatText", Float.class, 0.0f)); //$NON-NLS-1$
			assertEquals(123.5d, settings.getDouble("decimal", 0.0d)); //$NON-NLS-1$
			assertEquals(123.5d, settings.get("decimal", Double.class, 0.0d)); //$NON-NLS-1$
			assertEquals(new BigDecimal("12345678901234567890"), settings.get("bigInteger", BigDecimal.class)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(new BigInteger("42"), settings.get("numericText", BigInteger.class)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(new BigInteger("42"), settings.getBigInteger("numericText", BigInteger.ZERO)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(new BigDecimal("42"), settings.getBigDecimal("numericText", BigDecimal.ZERO)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(7, settings.get("decimal", Integer.class, 7)); //$NON-NLS-1$
			assertEquals((byte) 7, settings.get("bigInteger", Byte.class, (byte) 7)); //$NON-NLS-1$
		}

		@Test
		void convertsBasicTypesThroughSharedRules() {
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("enabled", "true") //$NON-NLS-1$ //$NON-NLS-2$
					.set("disabled", false) //$NON-NLS-1$
					.set("letter", "A") //$NON-NLS-1$ //$NON-NLS-2$
					.set("space", " ") //$NON-NLS-1$ //$NON-NLS-2$
					.set("enum", "Second") //$NON-NLS-1$ //$NON-NLS-2$
					.set("locale", "ja-JP") //$NON-NLS-1$ //$NON-NLS-2$
					.set("timeZone", "Asia/Tokyo") //$NON-NLS-1$ //$NON-NLS-2$
					.set("uppercaseBoolean", "TRUE"); //$NON-NLS-1$ //$NON-NLS-2$

			assertTrue(settings.getBoolean("enabled", false)); //$NON-NLS-1$
			assertTrue(settings.get("enabled", Boolean.class, false)); //$NON-NLS-1$
			assertFalse(settings.getBoolean("disabled", true)); //$NON-NLS-1$
			assertFalse(settings.get("disabled", Boolean.class, true)); //$NON-NLS-1$
			assertEquals('A', settings.getChar("letter", '\0')); //$NON-NLS-1$
			assertEquals('A', settings.get("letter", Character.class, '\0')); //$NON-NLS-1$
			assertEquals(' ', settings.getChar("space", '\0')); //$NON-NLS-1$
			assertEquals(' ', settings.get("space", Character.class, '\0')); //$NON-NLS-1$
			assertEquals(SampleEnum.Second, settings.getEnum("enum", SampleEnum.class, SampleEnum.First)); //$NON-NLS-1$
			assertEquals(SampleEnum.Second, settings.get("enum", SampleEnum.class, SampleEnum.First)); //$NON-NLS-1$
			assertEquals(Locale.JAPAN, settings.getLocale("locale", Locale.ROOT)); //$NON-NLS-1$
			assertEquals(Locale.JAPAN, settings.get("locale", Locale.class, Locale.ROOT)); //$NON-NLS-1$
			assertEquals("Asia/Tokyo", settings.getTimeZone("timeZone", TimeZone.getTimeZone("UTC")).getID()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("Asia/Tokyo", settings.get("timeZone", TimeZone.class, TimeZone.getTimeZone("UTC")).getID()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertFalse(settings.getBoolean("uppercaseBoolean", false)); //$NON-NLS-1$
			assertFalse(settings.get("uppercaseBoolean", Boolean.class, false)); //$NON-NLS-1$
		}

		@Test
		void convertsApplicationSettingTypesThroughSharedRules() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$
			Path outputDirectory = Path.of("data", "output"); //$NON-NLS-1$ //$NON-NLS-2$
			URI endpoint = URI.create("https://example.com/api"); //$NON-NLS-1$
			ZoneId zoneId = ZoneId.of("Asia/Tokyo"); //$NON-NLS-1$

			settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("output.directory", outputDirectory) //$NON-NLS-1$
					.set("endpoint", endpoint) //$NON-NLS-1$
					.set("zoneId", zoneId) //$NON-NLS-1$
					.set("timeZone", TimeZone.getTimeZone(zoneId)) //$NON-NLS-1$
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(outputDirectory, loaded.getPath("output.directory", Path.of("."))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(outputDirectory, loaded.get("output.directory", Path.class, Path.of("."))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(endpoint, loaded.getUri("endpoint", URI.create("https://example.org"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(endpoint, loaded.get("endpoint", URI.class, URI.create("https://example.org"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(zoneId, loaded.getZoneId("zoneId", ZoneId.of("UTC"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(zoneId, loaded.get("zoneId", ZoneId.class, ZoneId.of("UTC"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(zoneId, loaded.get("timeZone", ZoneId.class, ZoneId.of("UTC"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("Asia/Tokyo", loaded.get("zoneId", TimeZone.class, TimeZone.getTimeZone("UTC")).getID()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(Files.readString(file).contains("zoneId=Asia/Tokyo")); //$NON-NLS-1$
			assertTrue(Files.readString(file).contains("endpoint=https://example.com/api")); //$NON-NLS-1$
		}

		@Test
		void convertsCompatibleDateTimeTypesUsingConfiguredTimeZone() {
			TimeZone utc = TimeZone.getTimeZone("UTC"); //$NON-NLS-1$
			Instant instant = Instant.parse("2026-08-29T12:34:56Z"); //$NON-NLS-1$
			OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-08-29T21:34:56+09:00"); //$NON-NLS-1$
			ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-08-29T21:34:56+09:00[Asia/Tokyo]"); //$NON-NLS-1$
			LocalDateTime localDateTime = LocalDateTime.parse("2026-08-29T12:34:56"); //$NON-NLS-1$
			LocalDate localDate = LocalDate.parse("2026-08-29"); //$NON-NLS-1$
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.timeZone(utc)
					.set("instant", instant) //$NON-NLS-1$
					.set("offset", offsetDateTime) //$NON-NLS-1$
					.set("zoned", zonedDateTime) //$NON-NLS-1$
					.set("localDateTime", localDateTime) //$NON-NLS-1$
					.set("localDate", localDate) //$NON-NLS-1$
					.set("localTime", LocalTime.parse("12:34:56")) //$NON-NLS-1$ //$NON-NLS-2$
					.set("localDateText", "2026-08-29") //$NON-NLS-1$ //$NON-NLS-2$
					.set("instantText", "2026-08-29T12:34:56Z"); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals(Date.from(instant), settings.getDate("instant", new Date(0))); //$NON-NLS-1$
			assertEquals(LocalDateTime.parse("2026-08-29T12:34:56"), settings.getLocalDateTime("offset", LocalDateTime.MIN)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(LocalDate.parse("2026-08-29"), settings.getLocalDate("zoned", LocalDate.MIN)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(LocalTime.parse("12:34:56"), settings.getLocalTime("instantText", LocalTime.MIN)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(instant, settings.getInstant("localDateTime", Instant.EPOCH)); //$NON-NLS-1$
			assertEquals(Instant.parse("2026-08-29T00:00:00Z"), settings.getInstant("localDate", Instant.EPOCH)); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(Date.from(Instant.parse("2026-08-29T00:00:00Z")), settings.getDate("localDateText", new Date(0))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(Instant.EPOCH, settings.getInstant("localTime", Instant.EPOCH)); //$NON-NLS-1$
			assertEquals(instant.atZone(utc.toZoneId()), settings.getZonedDateTime("instant", ZonedDateTime.now())); //$NON-NLS-1$
			assertEquals(OffsetDateTime.parse("2026-08-29T12:34:56Z"), settings.getOffsetDateTime("zoned", OffsetDateTime.MIN)); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Test
		void returnsDefaultsForMissingOrInvalidValues() {
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("integer", "not-number") //$NON-NLS-1$ //$NON-NLS-2$
					.set("boolean", "yes") //$NON-NLS-1$ //$NON-NLS-2$
					.set("instant", "2026-08-29 12:34:56") //$NON-NLS-1$ //$NON-NLS-2$
					.set("timeZone", "not-a-zone") //$NON-NLS-1$ //$NON-NLS-2$
					.set("tags", List.of("work", "10")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertEquals(7, settings.getInt("integer", 7)); //$NON-NLS-1$
			assertFalse(settings.getBoolean("boolean", false)); //$NON-NLS-1$
			assertEquals(Instant.EPOCH, settings.getInstant("instant", Instant.EPOCH)); //$NON-NLS-1$
			assertEquals(TimeZone.getTimeZone("UTC"), settings.getTimeZone("timeZone", TimeZone.getTimeZone("UTC"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(TimeZone.getTimeZone("UTC"), settings.get("timeZone", TimeZone.class, TimeZone.getTimeZone("UTC"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("fallback", settings.getString("missing", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(List.of(1, 2), settings.getList("tags", Integer.class, List.of(1, 2))); //$NON-NLS-1$
		}

		@Test
		void storesNonFiniteFloatingPointValuesAsStrings() throws IOException {
			Path file = tempDir.resolve("settings.json"); //$NON-NLS-1$

			settings(null, "notes", "settings.json") //$NON-NLS-1$ //$NON-NLS-2$
					.set("nan", Double.NaN) //$NON-NLS-1$
					.set("infinity", Float.POSITIVE_INFINITY) //$NON-NLS-1$
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.json").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals("NaN", loaded.get("nan")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("Infinity", loaded.get("infinity")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Nested
	class NullValues {
		@Test
		void hidesNullValuesByDefault() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$

			settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.nullable(true)
					.set("last.opened", null) //$NON-NLS-1$
					.set("recent.tags", Arrays.asList("work", null, "archive")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.nullable(false)
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$

			assertFalse(loaded.contains("last.opened")); //$NON-NLS-1$
			assertEquals("fallback", loaded.getString("last.opened", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(List.of("work", "archive"), loaded.getList("recent.tags", String.class, List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertFalse(Files.readString(file).contains("null")); //$NON-NLS-1$
		}

		@Test
		void exposesNullValuesWhenNullableIsEnabled() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$

			settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.nullable(true)
					.set("last.opened", null) //$NON-NLS-1$
					.set("recent.tags", Arrays.asList("work", null, "archive")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.nullable(true)
					.loadFrom(file);

			assertTrue(loaded.contains("last.opened")); //$NON-NLS-1$
			assertEquals(null, loaded.getString("last.opened", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(Arrays.asList("work", null, "archive"), loaded.getList("recent.tags", String.class, List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(Files.readString(file).contains("last.opened=null")); //$NON-NLS-1$
		}

		@Test
		void keepsNullInternallyWhenNullableIsDisabled() {
			AppSettings settings = settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("last.opened", null); //$NON-NLS-1$

			assertFalse(settings.contains("last.opened")); //$NON-NLS-1$
			assertEquals("fallback", settings.getString("last.opened", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$

			settings.nullable(true);

			assertTrue(settings.contains("last.opened")); //$NON-NLS-1$
			assertEquals(null, settings.getString("last.opened", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Nested
	class KeyValueFiles {
		@Test
		void infersScalarsListsAndQuotedStrings() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$
			Files.writeString(file, String.join(System.lineSeparator(),
					"name=Notes", //$NON-NLS-1$
					"theme = dark", //$NON-NLS-1$
					"description=\" Personal notes \"", //$NON-NLS-1$
					"count=10", //$NON-NLS-1$
					"enabled=true", //$NON-NLS-1$
					"created=2026-08-29T12:34:56Z", //$NON-NLS-1$
					"tags=[work, \"hello, world\", \"[draft]\", \" leading \", 3]", //$NON-NLS-1$
					"last.opened", //$NON-NLS-1$
					"label=\"null\"", //$NON-NLS-1$
					"date.label=\"2026-08-29\"", //$NON-NLS-1$
					"list.label=\"[one, two]\"")); //$NON-NLS-1$

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals("Notes", loaded.getString("name", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("dark", loaded.getString("theme", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(" Personal notes ", loaded.getString("description", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(new BigDecimal("10"), loaded.get("count")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(true, loaded.get("enabled")); //$NON-NLS-1$
			assertInstanceOf(TemporalAccessor.class, loaded.get("created")); //$NON-NLS-1$
			assertEquals(List.of("work", "hello, world", "[draft]", " leading ", new BigDecimal("3")), loaded.getList("tags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			assertFalse(loaded.contains("last.opened")); //$NON-NLS-1$
			assertEquals("null", loaded.getString("label", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("2026-08-29", loaded.getString("date.label", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("[one, two]", loaded.getString("list.label", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void writesQuotedStringsWhenNeeded() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$

			settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.nullable(true)
					.set("empty", "") //$NON-NLS-1$ //$NON-NLS-2$
					.set("reserved", "true") //$NON-NLS-1$ //$NON-NLS-2$
					.set("number.text", "1.0") //$NON-NLS-1$ //$NON-NLS-2$
					.set("date.text", "2026-08-29") //$NON-NLS-1$ //$NON-NLS-2$
					.set("list.text", "[one, two]") //$NON-NLS-1$ //$NON-NLS-2$
					.set("quoted.text", "\"quoted\"") //$NON-NLS-1$ //$NON-NLS-2$
					.set("path", "C:\\Users\\me") //$NON-NLS-1$ //$NON-NLS-2$
					.set("tags", List.of("work", "hello, world", "[draft]", "item]", " leading ", "1.0")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
					.storeTo(file);

			List<String> lines = Files.readAllLines(file);

			assertTrue(lines.contains("empty=\"\"")); //$NON-NLS-1$
			assertTrue(lines.contains("reserved=\"true\"")); //$NON-NLS-1$
			assertTrue(lines.contains("number.text=\"1.0\"")); //$NON-NLS-1$
			assertTrue(lines.contains("date.text=\"2026-08-29\"")); //$NON-NLS-1$
			assertTrue(lines.contains("list.text=\"[one, two]\"")); //$NON-NLS-1$
			assertTrue(lines.contains("quoted.text=\"\\\"quoted\\\"\"")); //$NON-NLS-1$
			assertTrue(lines.contains("path=\"C:\\\\Users\\\\me\"")); //$NON-NLS-1$
			assertTrue(lines.contains("tags=[work, \"hello, world\", \"[draft]\", \"item]\", \" leading \", \"1.0\"]")); //$NON-NLS-1$
		}

		@Test
		void escapesKeysAndReadsQuotedEscapes() throws IOException {
			Path file = tempDir.resolve("settings.properties"); //$NON-NLS-1$

			settings(null, "notes", "settings.properties") //$NON-NLS-1$ //$NON-NLS-2$
					.set("path=key", "first line\nsecond line") //$NON-NLS-1$ //$NON-NLS-2$
					.storeTo(file);

			AppSettings loaded = settings(null, "notes", "settings.properties").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$

			assertTrue(Files.readString(file).contains("path\\=key=\"first line\\nsecond line\"")); //$NON-NLS-1$
			assertEquals("first line\nsecond line", loaded.getString("path=key", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	@Nested
	class IniFiles {
		@Test
		void writesDottedKeysAsSectionsAndUsesAtForSectionValue() throws IOException {
			Path file = tempDir.resolve("settings.ini"); //$NON-NLS-1$

			settings("acme", "notes", "settings.ini") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.set("theme", "dark") //$NON-NLS-1$ //$NON-NLS-2$
					.set("editor", "enabled") //$NON-NLS-1$ //$NON-NLS-2$
					.set("editor.wrap", true) //$NON-NLS-1$
					.set("editor.font.size", 14) //$NON-NLS-1$
					.storeTo(file, "INI settings"); //$NON-NLS-1$

			AppSettings loaded = settings("acme", "notes", "settings.ini").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String content = Files.readString(file);

			assertTrue(content.startsWith("; INI settings")); //$NON-NLS-1$
			assertTrue(content.contains("theme=dark")); //$NON-NLS-1$
			assertTrue(content.contains("[editor]")); //$NON-NLS-1$
			assertTrue(content.contains("@=enabled")); //$NON-NLS-1$
			assertTrue(content.contains("wrap=true")); //$NON-NLS-1$
			assertTrue(content.contains("[editor.font]")); //$NON-NLS-1$
			assertTrue(content.contains("size=14")); //$NON-NLS-1$
			assertEquals(List.of("theme", "editor", "editor.wrap", "editor.font.size"), List.copyOf(loaded.asStringMap().keySet())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertEquals("enabled", loaded.getString("editor", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(loaded.getBoolean("editor.wrap", false)); //$NON-NLS-1$
			assertEquals(14, loaded.getInt("editor.font.size", 0)); //$NON-NLS-1$
		}

		@Test
		void usesKeyValueSyntaxForValues() throws IOException {
			Path file = tempDir.resolve("settings.ini"); //$NON-NLS-1$
			Files.writeString(file, String.join(System.lineSeparator(),
					"theme = dark", //$NON-NLS-1$
					"count=10", //$NON-NLS-1$
					"enabled=true", //$NON-NLS-1$
					"tags=[work, \"hello, world\", 3]", //$NON-NLS-1$
					"literal=\"null\"")); //$NON-NLS-1$

			AppSettings loaded = settings("acme", "notes", "settings.ini").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertEquals("dark", loaded.get("theme")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(new BigDecimal("10"), loaded.get("count")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(true, loaded.get("enabled")); //$NON-NLS-1$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("tags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertEquals("null", loaded.get("literal")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Test
		void rejectsAtAtRoot() throws IOException {
			Path file = tempDir.resolve("settings.ini"); //$NON-NLS-1$
			Files.writeString(file, "@=enabled"); //$NON-NLS-1$

			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.ini").loadFrom(file)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	@Nested
	class SimpleYamlFiles {
		@Test
		void writesNestedObjectsListsAndTypedScalars() throws IOException {
			Path file = tempDir.resolve("settings.yaml"); //$NON-NLS-1$

			settings("acme", "notes", "settings.yaml") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.set("theme", "dark") //$NON-NLS-1$ //$NON-NLS-2$
					.set("window.width", 1024) //$NON-NLS-1$
					.set("autosave.enabled", true) //$NON-NLS-1$
					.set("recent.tags", List.of("work", "hello, world", 3)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					.set("empty.tags", List.of()) //$NON-NLS-1$
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.yaml").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String content = Files.readString(file);

			assertTrue(content.contains("theme: 'dark'")); //$NON-NLS-1$
			assertTrue(content.contains("window:")); //$NON-NLS-1$
			assertTrue(content.contains("  width: 1024")); //$NON-NLS-1$
			assertTrue(content.contains("autosave:")); //$NON-NLS-1$
			assertTrue(content.contains("  enabled: true")); //$NON-NLS-1$
			assertTrue(content.contains("recent:")); //$NON-NLS-1$
			assertTrue(content.contains("  tags: ['work', 'hello, world', 3]")); //$NON-NLS-1$
			assertTrue(content.contains("empty:")); //$NON-NLS-1$
			assertTrue(content.contains("  tags: []")); //$NON-NLS-1$
			assertEquals("dark", loaded.getString("theme", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(1024, loaded.getInt("window.width", 0)); //$NON-NLS-1$
			assertTrue(loaded.getBoolean("autosave.enabled", false)); //$NON-NLS-1$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("recent.tags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertEquals(List.of(), loaded.getList("empty.tags", List.of("fallback"))); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Test
		void readsQuotedStringsUnquotedScalarsAndLists() throws IOException {
			Path file = tempDir.resolve("settings.yaml"); //$NON-NLS-1$
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
					"""); //$NON-NLS-1$

			AppSettings loaded = settings("acme", "notes", "settings.yaml").loadFrom(file); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertInstanceOf(String.class, loaded.asMap().get("title")); //$NON-NLS-1$
			assertEquals("Lee's Notes", loaded.getString("owner", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("first line\nsecond line", loaded.getString("path", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertInstanceOf(BigDecimal.class, loaded.asMap().get("count")); //$NON-NLS-1$
			assertInstanceOf(Boolean.class, loaded.asMap().get("enabled")); //$NON-NLS-1$
			assertInstanceOf(TemporalAccessor.class, loaded.asMap().get("created")); //$NON-NLS-1$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("inlineTags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals(List.of(), loaded.getList("emptyTags", List.of("fallback"))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("blockTags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("value # not comment", loaded.getString("quoted.key:with:colon", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void rejectsInvalidYamlSyntax() throws IOException {
			Path oddIndent = tempDir.resolve("odd.yaml"); //$NON-NLS-1$
			Path itemWithoutKey = tempDir.resolve("item.yaml"); //$NON-NLS-1$
			Files.writeString(oddIndent, " theme: dark"); //$NON-NLS-1$
			Files.writeString(itemWithoutKey, "- item"); //$NON-NLS-1$

			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.yaml").loadFrom(oddIndent)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThrows(AppSettingsException.class, () -> settings("acme", "notes", "settings.yaml").loadFrom(itemWithoutKey)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
					"""; //$NON-NLS-1$

			Map<String, Object> values = SettingsFormats.json().read(new StringReader(json));

			assertEquals(List.of("theme", "escaped", "window.width", "autosave.enabled", "recent.tags"), List.copyOf(values.keySet())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			assertEquals("dark", values.get("theme")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("line1\nline2\t\u263a", values.get("escaped")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(new BigDecimal("1024"), values.get("window.width")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(true, values.get("autosave.enabled")); //$NON-NLS-1$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3"), false), values.get("recent.tags")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void writesNestedObjectsListsNullsAndAtValues() throws IOException {
			Path file = tempDir.resolve("settings.json"); //$NON-NLS-1$

			settings("acme", "notes", "settings.json") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.nullable(true)
					.set("editor", "enabled") //$NON-NLS-1$ //$NON-NLS-2$
					.set("editor.wrap", true) //$NON-NLS-1$
					.set("editor.font.size", 14) //$NON-NLS-1$
					.set("last.opened", null) //$NON-NLS-1$
					.set("recent.tags", List.of("work", "hello, world", 3)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.json") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.nullable(true)
					.loadFrom(file);
			String content = Files.readString(file);

			assertTrue(content.contains("\"editor\"")); //$NON-NLS-1$
			assertTrue(content.contains("\"@\": \"enabled\"")); //$NON-NLS-1$
			assertTrue(content.contains("\"wrap\": true")); //$NON-NLS-1$
			assertTrue(content.contains("\"size\": 14")); //$NON-NLS-1$
			assertTrue(content.contains("\"last\"")); //$NON-NLS-1$
			assertTrue(content.contains("\"opened\": null")); //$NON-NLS-1$
			assertEquals("enabled", loaded.getString("editor", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(loaded.getBoolean("editor.wrap", false)); //$NON-NLS-1$
			assertEquals(14, loaded.getInt("editor.font.size", 0)); //$NON-NLS-1$
			assertEquals(null, loaded.getString("last.opened", "fallback")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(List.of("work", "hello, world", new BigDecimal("3")), loaded.getList("recent.tags", List.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		@Test
		void rejectsNonObjectAndInvalidJson() {
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("[]"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": }"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": 01}"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": 1.}"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": \"\\u12xz\"}"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": \"line\nbreak\"}"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": true // comment\n}"))); //$NON-NLS-1$
			assertThrows(AppSettingsException.class, () -> SettingsFormats.json().read(new StringReader("{\"a\": /* comment */ true}"))); //$NON-NLS-1$
		}
	}

	@Nested
	class CustomFormats {
		@Test
		void usesCustomSettingsFormat() throws IOException {
			Path file = tempDir.resolve("settings.custom"); //$NON-NLS-1$
			SettingsFormat format = new SettingsFormat() {
				@Override
				public Map<String, Object> read(Reader reader) throws IOException {
					StringWriter content = new StringWriter();
					reader.transferTo(content);
					return Map.of("loaded", content.toString()); //$NON-NLS-1$
				}

				@Override
				public void write(Writer writer, Map<String, Object> values, String comments) throws IOException {
					writer.write(String.valueOf(values.get("message"))); //$NON-NLS-1$
				}
			};

			settings("acme", "notes", "settings.custom") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.format(format)
					.set("message", "hello") //$NON-NLS-1$ //$NON-NLS-2$
					.storeTo(file);

			AppSettings loaded = settings("acme", "notes", "settings.custom") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.format(format)
					.loadFrom(file);

			assertEquals("hello", loaded.getString("loaded", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Test
		void rejectsNullCustomFormat() {
			AppSettings settings = settings("acme", "notes", "settings.custom"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			assertThrows(NullPointerException.class, () -> settings.format(null));
		}
	}

	@Nested
	class ConcurrentAccess {
		@Test
		void handlesConcurrentReadsAndWrites() throws Exception {
			AppSettings settings = settings("acme", "notes", "settings.properties"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
							settings.set("key_" + threadId + "_" + j, j); //$NON-NLS-1$ //$NON-NLS-2$
						} else {
							settings.getString("key_" + (threadId - 1) + "_" + j, "default"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
		return settings(vendor, app, fileName, "Linux", Map.of()); //$NON-NLS-1$
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
