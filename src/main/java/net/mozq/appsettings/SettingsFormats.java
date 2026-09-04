/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.util.Locale;

/**
 * Built-in settings formats.
 */
public final class SettingsFormats {
	private static final SettingsFormat KEY_VALUE = new KeyValueSettingsFormat();
	private static final SettingsFormat INI = new IniSettingsFormat();
	private static final SettingsFormat SIMPLE_YAML = new SimpleYamlSettingsFormat();
	private static final SettingsFormat JSON = new JsonSettingsFormat();

	private SettingsFormats() {
	}

	/**
	 * Returns the Java properties-like key-value format.
	 */
	public static SettingsFormat keyValue() {
		return KEY_VALUE;
	}

	/**
	 * Returns the INI format.
	 */
	public static SettingsFormat ini() {
		return INI;
	}

	/**
	 * Returns the small built-in YAML format.
	 */
	public static SettingsFormat simpleYaml() {
		return SIMPLE_YAML;
	}

	/**
	 * Returns the built-in JSON format.
	 */
	public static SettingsFormat json() {
		return JSON;
	}

	/**
	 * Selects a built-in format from a file name extension.
	 */
	public static SettingsFormat byFileName(String fileName) {
		if (fileName == null) {
			return keyValue();
		}
		String lowerFileName = fileName.toLowerCase(Locale.ROOT);
		if (lowerFileName.endsWith(".ini")) {
			return ini();
		}
		if (lowerFileName.endsWith(".yaml") || lowerFileName.endsWith(".yml")) {
			return simpleYaml();
		}
		if (lowerFileName.endsWith(".json")) {
			return json();
		}
		return keyValue();
	}
}
