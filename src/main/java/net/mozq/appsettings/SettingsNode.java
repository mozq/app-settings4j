/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class SettingsNode {
	private SettingsValue value;
	private final LinkedHashMap<String, SettingsNode> children = new LinkedHashMap<>();

	SettingsValue value() {
		return value;
	}

	Map<String, SettingsNode> children() {
		return children;
	}

	boolean hasValue() {
		return value != null;
	}

	boolean hasWritableValue(boolean nullable) {
		return hasValue() && (nullable || !(value instanceof SettingsValue.NullValue));
	}

	boolean hasChildren() {
		return !children.isEmpty();
	}

	boolean hasWritableContent(boolean nullable) {
		if (hasWritableValue(nullable)) {
			return true;
		}
		return children.values().stream().anyMatch(child -> child.hasWritableContent(nullable));
	}

	static SettingsNode from(Map<String, SettingsValue> values) {
		SettingsNode root = new SettingsNode();
		for (Map.Entry<String, SettingsValue> entry : values.entrySet()) {
			root.put(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

	private void put(String key, SettingsValue value) {
		String[] parts = DOT_PATTERN.split(key, -1);
		SettingsNode node = this;
		for (String part : parts) {
			if (part.isEmpty()) {
				throw new AppSettingsException("settings keys must not contain empty path segments");
			}
			node = node.children.computeIfAbsent(part, name -> new SettingsNode());
		}
		node.value = value;
	}
}
