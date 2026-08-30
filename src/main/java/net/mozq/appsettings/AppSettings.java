/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Reads and writes ordered application settings in the standard configuration
 * directory for the current operating system.
 */
public final class AppSettings {
	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = primitiveWrappers();

	private final String vendor;
	private final String app;
	private final String fileName;
	private final AppEnvironment environment;
	private final LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
	private SettingsFormat format;
	private boolean nullable;

	/**
	 * Creates settings stored under {@code <base>/<vendor>/<app>/<fileName>}.
	 * A null or blank vendor is omitted from the path.
	 */
	public static AppSettings of(String vendor, String app, String fileName) {
		return new AppSettings(vendor, app, fileName, AppEnvironment.current());
	}

	static AppSettings of(String vendor, String app, String fileName, AppEnvironment environment) {
		return new AppSettings(vendor, app, fileName, environment);
	}

	private AppSettings(String vendor, String app, String fileName, AppEnvironment environment) {
		this.vendor = normalizeVendor(vendor);
		this.app = requirePathName(app, "app"); //$NON-NLS-1$
		this.fileName = requireFileName(fileName);
		this.environment = Objects.requireNonNull(environment, "environment"); //$NON-NLS-1$
		this.format = SettingsFormats.byFileName(this.fileName);
	}

	/**
	 * Returns the OS-specific settings file path.
	 */
	public Path path() {
		Path baseDirectory = baseConfigDirectory();
		if (vendor == null) {
			return baseDirectory.resolve(app).resolve(fileName);
		}
		return baseDirectory.resolve(vendor).resolve(app).resolve(fileName);
	}

	/**
	 * Loads settings from {@link #path()}. Missing files are treated as empty
	 * settings.
	 */
	public AppSettings load() throws IOException {
		return loadFrom(path());
	}

	/**
	 * Loads settings from the given file. Existing in-memory settings are cleared
	 * first.
	 */
	public AppSettings loadFrom(Path file) throws IOException {
		values.clear();
		if (!Files.exists(file)) {
			return this;
		}
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			if (format instanceof InternalSettingsFormat internalFormat) {
				values.putAll(internalFormat.readValues(reader));
			} else {
				for (Map.Entry<String, Object> entry : format.read(reader).entrySet()) {
					values.put(entry.getKey(), SettingsValues.of(entry.getValue()));
				}
			}
		}
		return this;
	}

	/**
	 * Stores settings to {@link #path()}.
	 */
	public AppSettings store() throws IOException {
		return store(null);
	}

	/**
	 * Stores settings to {@link #path()} with optional file-level comments.
	 */
	public AppSettings store(String comments) throws IOException {
		return storeTo(path(), comments);
	}

	/**
	 * Stores settings to the given file.
	 */
	public AppSettings storeTo(Path file) throws IOException {
		return storeTo(file, null);
	}

	/**
	 * Stores settings to the given file with optional file-level comments.
	 */
	public AppSettings storeTo(Path file, String comments) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		storeAtomicallyOrDirect(file, comments);
		return this;
	}

	/**
	 * Returns the raw string value for a key, or the default when the key is
	 * missing, null-hidden, or has no string representation.
	 */
	public String get(String key, String defaultValue) {
		String value = SettingsValues.raw(visibleValue(key), nullable);
		return value == null ? defaultValue : value;
	}

	/**
	 * Returns the typed Java value for a key, or {@code null} when unavailable.
	 */
	public Object getValue(String key) {
		return getValue(key, null);
	}

	/**
	 * Returns the typed Java value for a key, or the default when unavailable.
	 */
	public Object getValue(String key, Object defaultValue) {
		if (!contains(key)) {
			return defaultValue;
		}
		return SettingsValues.object(values.get(key), nullable);
	}

	/**
	 * Converts the value for a key to the requested type when possible.
	 */
	public <T> T getValue(String key, Class<T> type, T defaultValue) {
		if (!contains(key)) {
			return defaultValue;
		}
		Object value = getValue(key);
		if (value == null) {
			return null;
		}
		T converted = convertValue(value, type);
		return converted == null ? defaultValue : converted;
	}

	public int getInt(String key, int defaultValue) {
		return parse(key, defaultValue, Integer::parseInt);
	}

	public long getLong(String key, long defaultValue) {
		return parse(key, defaultValue, Long::parseLong);
	}

	public double getDouble(String key, double defaultValue) {
		return parse(key, defaultValue, Double::parseDouble);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String value = SettingsValues.raw(visibleValue(key), nullable);
		if ("true".equalsIgnoreCase(value)) { //$NON-NLS-1$
			return true;
		}
		if ("false".equalsIgnoreCase(value)) { //$NON-NLS-1$
			return false;
		}
		return defaultValue;
	}

	public <T extends Enum<T>> T getEnum(String key, Class<T> enumType, T defaultValue) {
		return parse(key, defaultValue, value -> Enum.valueOf(enumType, value));
	}

	public Locale getLocale(String key, Locale defaultValue) {
		return parse(key, defaultValue, Locale::forLanguageTag);
	}

	public TimeZone getTimeZone(String key, TimeZone defaultValue) {
		String value = SettingsValues.raw(visibleValue(key), nullable);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		TimeZone timeZone = parseTimeZone(value);
		return timeZone == null ? defaultValue : timeZone;
	}

	public List<Object> getList(String key, List<?> defaultValue) {
		SettingsValue value = visibleValue(key);
		if (value instanceof SettingsValue.ListValue listValue) {
			return listValue.values().stream()
					.filter(item -> nullable || !(item instanceof SettingsValue.NullValue))
					.map(item -> SettingsValues.object(item, nullable))
					.toList();
		}
		return Collections.unmodifiableList(new ArrayList<>(defaultValue));
	}

	public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue) {
		SettingsValue value = visibleValue(key);
		if (!(value instanceof SettingsValue.ListValue listValue)) {
			return defaultValue;
		}
		List<T> converted = new ArrayList<>();
		for (SettingsValue item : listValue.values()) {
			if (item instanceof SettingsValue.NullValue && !nullable) {
				continue;
			}
			Object object = SettingsValues.object(item, nullable);
			if (object == null) {
				converted.add(null);
				continue;
			}
			T convertedItem = convertValue(object, elementType);
			if (convertedItem == null) {
				return defaultValue;
			}
			converted.add(convertedItem);
		}
		return Collections.unmodifiableList(converted);
	}

	public Instant getInstant(String key, Instant defaultValue) {
		return parse(key, defaultValue, Instant::parse);
	}

	public Date getDate(String key, Date defaultValue) {
		return parse(key, defaultValue, value -> Date.from(Instant.parse(value)));
	}

	public LocalDateTime getLocalDateTime(String key, LocalDateTime defaultValue) {
		return parse(key, defaultValue, LocalDateTime::parse);
	}

	public LocalDate getLocalDate(String key, LocalDate defaultValue) {
		return parse(key, defaultValue, LocalDate::parse);
	}

	public LocalTime getLocalTime(String key, LocalTime defaultValue) {
		return parse(key, defaultValue, LocalTime::parse);
	}

	public OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue) {
		return parse(key, defaultValue, OffsetDateTime::parse);
	}

	public ZonedDateTime getZonedDateTime(String key, ZonedDateTime defaultValue) {
		return parse(key, defaultValue, ZonedDateTime::parse);
	}

	/**
	 * Stores a value. Supported values include strings, numbers, booleans,
	 * date/time types, collections, enums, locales, time zones, and null.
	 */
	public AppSettings setValue(String key, Object value) {
		requireKey(key);
		values.put(key, SettingsValues.of(value));
		return this;
	}

	/**
	 * Returns whether a key is visible. Null values are hidden unless nullable mode
	 * is enabled.
	 */
	public boolean contains(String key) {
		return visibleValue(key) != null;
	}

	/**
	 * Removes a key from the in-memory settings.
	 */
	public AppSettings remove(String key) {
		values.remove(key);
		return this;
	}

	/**
	 * Controls whether null values behave as visible values.
	 */
	public AppSettings nullable(boolean nullable) {
		this.nullable = nullable;
		return this;
	}

	/**
	 * Overrides the storage format selected from the file name.
	 */
	public AppSettings format(SettingsFormat format) {
		this.format = Objects.requireNonNull(format, "format"); //$NON-NLS-1$
		return this;
	}

	/**
	 * Returns visible values as raw strings in insertion order.
	 */
	public Map<String, String> asStringMap() {
		LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
		for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
			if (isVisible(entry.getValue())) {
				rawValues.put(entry.getKey(), SettingsValues.raw(entry.getValue(), nullable));
			}
		}
		return Collections.unmodifiableMap(rawValues);
	}

	/**
	 * Returns visible values as typed Java objects in insertion order.
	 */
	public Map<String, Object> asValueMap() {
		LinkedHashMap<String, Object> objectValues = new LinkedHashMap<>();
		for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
			if (isVisible(entry.getValue())) {
				objectValues.put(entry.getKey(), SettingsValues.object(entry.getValue(), nullable));
			}
		}
		return Collections.unmodifiableMap(objectValues);
	}

	private void storeAtomicallyOrDirect(Path file, String comments) throws IOException {
		Path parent = file.getParent();
		if (parent == null) {
			writeTo(file, comments);
			return;
		}
		Path temporaryFile;
		try {
			temporaryFile = Files.createTempFile(parent, file.getFileName().toString(), ".tmp"); //$NON-NLS-1$
		} catch (IOException e) {
			writeTo(file, comments);
			return;
		}
		try {
			writeTo(temporaryFile, comments);
			moveReplacing(temporaryFile, file);
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Path baseConfigDirectory() {
		String osName = environment.osName() == null ? "" : environment.osName().toLowerCase(Locale.ROOT); //$NON-NLS-1$
		String userHome = requireUserHome(environment.userHome());
		if (osName.startsWith("windows")) { //$NON-NLS-1$
			String appData = environment.getenv("APPDATA"); //$NON-NLS-1$
			if (appData != null && !appData.isBlank()) {
				return Path.of(appData);
			}
			return Path.of(userHome, "AppData", "Roaming"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (osName.startsWith("mac")) { //$NON-NLS-1$
			return Path.of(userHome, "Library", "Application Support"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		String xdgConfigHome = environment.getenv("XDG_CONFIG_HOME"); //$NON-NLS-1$
		if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
			return Path.of(xdgConfigHome);
		}
		return Path.of(userHome, ".config"); //$NON-NLS-1$
	}

	private void writeTo(Path file, String comments) throws IOException {
		try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			if (format instanceof InternalSettingsFormat internalFormat) {
				internalFormat.writeValues(writer, values, comments, nullable);
			} else {
				format.write(writer, asValueMap(), comments);
			}
		}
	}

	private static String normalizeVendor(String vendor) {
		if (vendor == null || vendor.isBlank()) {
			return null;
		}
		return requirePathName(vendor, "vendor"); //$NON-NLS-1$
	}

	private static String requirePathName(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required"); //$NON-NLS-1$
		}
		if (value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			throw new IllegalArgumentException(name + " must be a path name, not a path"); //$NON-NLS-1$
		}
		return value;
	}

	private static String requireFileName(String value) {
		return requirePathName(value, "fileName"); //$NON-NLS-1$
	}

	private static String requireKey(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("key is required"); //$NON-NLS-1$
		}
		return key;
	}

	private static String requireUserHome(String userHome) {
		if (userHome == null || userHome.isBlank()) {
			throw new AppSettingsException("user.home is required to resolve app settings path"); //$NON-NLS-1$
		}
		return userHome;
	}

	private <T> T parse(String key, T defaultValue, ValueParser<T> parser) {
		String value = SettingsValues.raw(visibleValue(key), nullable);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return parser.parse(value);
		} catch (RuntimeException e) {
			return defaultValue;
		}
	}

	private SettingsValue visibleValue(String key) {
		SettingsValue value = values.get(key);
		return isVisible(value) ? value : null;
	}

	private boolean isVisible(SettingsValue value) {
		return value != null && (nullable || !(value instanceof SettingsValue.NullValue));
	}

	@FunctionalInterface
	private interface ValueParser<T> {
		T parse(String value);
	}

	private static <T> T convertValue(Object value, Class<T> type) {
		Objects.requireNonNull(type, "type"); //$NON-NLS-1$
		Class<?> wrapperType = wrapperType(type);
		if (wrapperType.isInstance(value)) {
			return castValue(value, type);
		}
		String raw = value.toString();
		try {
			Object converted = switch (wrapperType.getName()) {
			case "java.lang.String" -> raw; //$NON-NLS-1$
			case "java.lang.Byte" -> Byte.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Short" -> Short.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Integer" -> Integer.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Long" -> Long.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Float" -> Float.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Double" -> Double.valueOf(raw); //$NON-NLS-1$
			case "java.lang.Character" -> parseCharacter(raw); //$NON-NLS-1$
			case "java.math.BigDecimal" -> new BigDecimal(raw); //$NON-NLS-1$
			case "java.lang.Boolean" -> parseBoolean(raw); //$NON-NLS-1$
			case "java.time.Instant" -> Instant.parse(raw); //$NON-NLS-1$
			case "java.util.Date" -> Date.from(Instant.parse(raw)); //$NON-NLS-1$
			case "java.time.LocalDateTime" -> LocalDateTime.parse(raw); //$NON-NLS-1$
			case "java.time.LocalDate" -> LocalDate.parse(raw); //$NON-NLS-1$
			case "java.time.LocalTime" -> LocalTime.parse(raw); //$NON-NLS-1$
			case "java.time.OffsetDateTime" -> OffsetDateTime.parse(raw); //$NON-NLS-1$
			case "java.time.ZonedDateTime" -> ZonedDateTime.parse(raw); //$NON-NLS-1$
			case "java.util.Locale" -> Locale.forLanguageTag(raw); //$NON-NLS-1$
			case "java.util.TimeZone" -> parseTimeZone(raw); //$NON-NLS-1$
			default -> convertEnum(raw, wrapperType);
			};
			return castValue(converted, type);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Class<?> wrapperType(Class<?> type) {
		return type.isPrimitive() ? PRIMITIVE_WRAPPERS.get(type) : type;
	}

	@SuppressWarnings("unchecked")
	private static <T> T castValue(Object value, Class<T> type) {
		return (T) wrapperType(type).cast(value);
	}

	private static Map<Class<?>, Class<?>> primitiveWrappers() {
		Map<Class<?>, Class<?>> wrappers = new HashMap<>();
		wrappers.put(boolean.class, Boolean.class);
		wrappers.put(byte.class, Byte.class);
		wrappers.put(short.class, Short.class);
		wrappers.put(int.class, Integer.class);
		wrappers.put(long.class, Long.class);
		wrappers.put(float.class, Float.class);
		wrappers.put(double.class, Double.class);
		wrappers.put(char.class, Character.class);
		return Map.copyOf(wrappers);
	}

	private static Boolean parseBoolean(String value) {
		if ("true".equalsIgnoreCase(value)) { //$NON-NLS-1$
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(value)) { //$NON-NLS-1$
			return Boolean.FALSE;
		}
		throw new IllegalArgumentException("not a boolean: " + value); //$NON-NLS-1$
	}

	private static Character parseCharacter(String value) {
		if (value.length() != 1) {
			throw new IllegalArgumentException("not a character: " + value); //$NON-NLS-1$
		}
		return value.charAt(0);
	}

	private static TimeZone parseTimeZone(String value) {
		try {
			return TimeZone.getTimeZone(ZoneId.of(value));
		} catch (DateTimeException e) {
			return null;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object convertEnum(String value, Class<?> type) {
		if (type.isEnum()) {
			return Enum.valueOf((Class<? extends Enum>) type, value);
		}
		return null;
	}
}
