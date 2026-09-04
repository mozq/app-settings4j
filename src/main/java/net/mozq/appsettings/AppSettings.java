/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reads and writes ordered application settings in the standard configuration
 * directory for the current operating system.
 */
public final class AppSettings {
	private final String vendor;
	private final String app;
	private final String fileName;
	private final AppEnvironment environment;
	private final LinkedHashMap<String, SettingsValue> values = new LinkedHashMap<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private SettingsFormat format;
	private TimeZone timeZone = TimeZone.getDefault();
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
		lock.writeLock().lock();
		try {
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
		} finally {
			lock.writeLock().unlock();
		}
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
		lock.readLock().lock();
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			storeAtomicallyOrDirect(file, comments);
			return this;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Returns the raw string value for a key, or the default when the key is
	 * missing, null-hidden, or has no string representation.
	 */
	public String getString(String key, String defaultValue) {
		lock.readLock().lock();
		try {
			if (!contains(key)) {
				return defaultValue;
			}
			SettingsValue settingsValue = values.get(key);
			if (settingsValue instanceof SettingsValue.NullValue) {
				return null;
			}
			String value = SettingsValues.raw(settingsValue, nullable);
			return value == null ? defaultValue : value;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Returns the raw string value for a key, or {@code null} when unavailable.
	 */
	public String getString(String key) {
		return getString(key, null);
	}

	/**
	 * Returns the typed Java value for a key, or {@code null} when unavailable.
	 */
	public Object get(String key) {
		lock.readLock().lock();
		try {
			if (!contains(key)) {
				return null;
			}
			return SettingsValues.object(values.get(key), nullable);
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Converts the value for a key to the default value type when possible.
	 */
	public <T> T get(String key, T defaultValue) {
		lock.readLock().lock();
		try {
			if (!contains(key)) {
				return defaultValue;
			}
			Object value = get(key);
			if (value == null) {
				return null;
			}
			if (defaultValue == null) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Class<T> type = (Class<T>) defaultValue.getClass();
			T converted = convertValue(value, type);
			return converted == null ? defaultValue : converted;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Converts the value for a key to the requested type, or {@code null} when
	 * unavailable.
	 */
	public <T> T get(String key, Class<T> type) {
		Objects.requireNonNull(type, "type"); //$NON-NLS-1$
		return get(key, type, null);
	}

	/**
	 * Converts the value for a key to the requested type when possible.
	 */
	public <T> T get(String key, Class<T> type, T defaultValue) {
		Objects.requireNonNull(type, "type"); //$NON-NLS-1$
		lock.readLock().lock();
		try {
			if (!contains(key)) {
				return defaultValue;
			}
			Object value = get(key);
			if (value == null) {
				return null;
			}
			T converted = convertValue(value, type);
			return converted == null ? defaultValue : converted;
		} finally {
			lock.readLock().unlock();
		}
	}

	public int getInt(String key, int defaultValue) {
		return get(key, Integer.class, defaultValue);
	}

	public byte getByte(String key, byte defaultValue) {
		return get(key, Byte.class, defaultValue);
	}

	public short getShort(String key, short defaultValue) {
		return get(key, Short.class, defaultValue);
	}

	public long getLong(String key, long defaultValue) {
		return get(key, Long.class, defaultValue);
	}

	public float getFloat(String key, float defaultValue) {
		return get(key, Float.class, defaultValue);
	}

	public double getDouble(String key, double defaultValue) {
		return get(key, Double.class, defaultValue);
	}

	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		return get(key, BigInteger.class, defaultValue);
	}

	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		return get(key, BigDecimal.class, defaultValue);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return get(key, Boolean.class, defaultValue);
	}

	public char getChar(String key, char defaultValue) {
		return get(key, Character.class, defaultValue);
	}

	public <T extends Enum<T>> T getEnum(String key, Class<T> enumType, T defaultValue) {
		return get(key, enumType, defaultValue);
	}

	public Locale getLocale(String key, Locale defaultValue) {
		return get(key, Locale.class, defaultValue);
	}

	public TimeZone getTimeZone(String key, TimeZone defaultValue) {
		return get(key, TimeZone.class, defaultValue);
	}

	public ZoneId getZoneId(String key, ZoneId defaultValue) {
		return get(key, ZoneId.class, defaultValue);
	}

	public Path getPath(String key, Path defaultValue) {
		return get(key, Path.class, defaultValue);
	}

	public URI getUri(String key, URI defaultValue) {
		return get(key, URI.class, defaultValue);
	}

	public List<Object> getList(String key) {
		return getList(key, List.of());
	}

	public List<Object> getList(String key, List<?> defaultValue) {
		lock.readLock().lock();
		try {
			SettingsValue value = visibleValue(key);
			if (value instanceof SettingsValue.ListValue listValue) {
				return listValue.values().stream()
						.filter(item -> nullable || !(item instanceof SettingsValue.NullValue))
						.map(item -> SettingsValues.object(item, nullable))
						.toList();
			}
			if (defaultValue.isEmpty()) {
				return List.of();
			}
			return Collections.unmodifiableList(new ArrayList<>(defaultValue));
		} finally {
			lock.readLock().unlock();
		}
	}

	public <T> List<T> getList(String key, Class<T> elementType) {
		return getList(key, elementType, List.of());
	}

	public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue) {
		lock.readLock().lock();
		try {
			SettingsValue value = visibleValue(key);
			if (!(value instanceof SettingsValue.ListValue listValue)) {
				return defaultValue;
			}
			List<T> converted = new ArrayList<>(listValue.values().size());
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
			if (converted.isEmpty()) {
				return List.of();
			}
			return Collections.unmodifiableList(converted);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Instant getInstant(String key, Instant defaultValue) {
		return get(key, Instant.class, defaultValue);
	}

	public Date getDate(String key, Date defaultValue) {
		return get(key, Date.class, defaultValue);
	}

	public LocalDateTime getLocalDateTime(String key, LocalDateTime defaultValue) {
		return get(key, LocalDateTime.class, defaultValue);
	}

	public LocalDate getLocalDate(String key, LocalDate defaultValue) {
		return get(key, LocalDate.class, defaultValue);
	}

	public LocalTime getLocalTime(String key, LocalTime defaultValue) {
		return get(key, LocalTime.class, defaultValue);
	}

	public OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue) {
		return get(key, OffsetDateTime.class, defaultValue);
	}

	public ZonedDateTime getZonedDateTime(String key, ZonedDateTime defaultValue) {
		return get(key, ZonedDateTime.class, defaultValue);
	}

	/**
	 * Stores a value. Supported values include strings, numbers, booleans,
	 * date/time types, collections, enums, locales, time zones, and null.
	 */
	public AppSettings set(String key, Object value) {
		requireKey(key);
		lock.writeLock().lock();
		try {
			values.put(key, SettingsValues.of(value));
			return this;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean contains(String key) {
		lock.readLock().lock();
		try {
			return visibleValue(key) != null;
		} finally {
			lock.readLock().unlock();
		}
	}

	public AppSettings remove(String key) {
		lock.writeLock().lock();
		try {
			values.remove(key);
			return this;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public AppSettings nullable(boolean nullable) {
		lock.writeLock().lock();
		try {
			this.nullable = nullable;
			return this;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public AppSettings timeZone(TimeZone timeZone) {
		Objects.requireNonNull(timeZone, "timeZone"); //$NON-NLS-1$
		lock.writeLock().lock();
		try {
			this.timeZone = timeZone;
			return this;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public AppSettings format(SettingsFormat format) {
		Objects.requireNonNull(format, "format"); //$NON-NLS-1$
		lock.writeLock().lock();
		try {
			this.format = format;
			return this;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public Map<String, String> asStringMap() {
		lock.readLock().lock();
		try {
			LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
			for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
				if (isVisible(entry.getValue())) {
					rawValues.put(entry.getKey(), SettingsValues.raw(entry.getValue(), nullable));
				}
			}
			return Collections.unmodifiableMap(rawValues);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<String, Object> asMap() {
		lock.readLock().lock();
		try {
			LinkedHashMap<String, Object> objectValues = new LinkedHashMap<>();
			for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
				if (isVisible(entry.getValue())) {
					objectValues.put(entry.getKey(), SettingsValues.object(entry.getValue(), nullable));
				}
			}
			return Collections.unmodifiableMap(objectValues);
		} finally {
			lock.readLock().unlock();
		}
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
				format.write(writer, asMap(), comments);
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

	private SettingsValue visibleValue(String key) {
		SettingsValue value = values.get(key);
		return isVisible(value) ? value : null;
	}

	private boolean isVisible(SettingsValue value) {
		return value != null && (nullable || !(value instanceof SettingsValue.NullValue));
	}

	private <T> T convertValue(Object value, Class<T> type) {
		return SettingsConverter.convert(value, type, timeZone);
	}
}
