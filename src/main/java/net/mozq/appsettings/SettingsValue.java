/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.List;

sealed interface SettingsValue
		permits SettingsValue.StringValue, SettingsValue.NumberValue, SettingsValue.BooleanValue,
				SettingsValue.DateTimeValue, SettingsValue.ListValue, SettingsValue.NullValue {
	String raw();

	record NullValue() implements SettingsValue {
		@Override
		public String raw() {
			return null;
		}
	}

	record StringValue(String raw, String value) implements SettingsValue {
	}

	record NumberValue(String raw, BigDecimal value) implements SettingsValue {
	}

	record BooleanValue(String raw, boolean value) implements SettingsValue {
	}

	record DateTimeValue(String raw, TemporalAccessor value) implements SettingsValue {
	}

	record ListValue(String raw, List<SettingsValue> values) implements SettingsValue {
	}
}
