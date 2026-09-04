/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.util.Map;

record AppEnvironment(String osName, String userHome, Map<String, String> env) {
	static AppEnvironment current() {
		return new AppEnvironment(
				System.getProperty("os.name"),
				System.getProperty("user.home"),
				System.getenv());
	}

	String getenv(String name) {
		return env.get(name);
	}
}
