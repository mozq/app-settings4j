/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.util.LinkedHashMap;
import java.util.List;

record SettingsReadResult(LinkedHashMap<String, SettingsValue> values, List<String> comments) {
	SettingsReadResult(LinkedHashMap<String, SettingsValue> values, List<String> comments) {
		this.values = values;
		this.comments = SettingsComments.normalize(comments);
	}
}
