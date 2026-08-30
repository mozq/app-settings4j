/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

/**
 * Runtime exception thrown when settings cannot be parsed or resolved.
 */
public final class AppSettingsException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AppSettingsException(String message) {
		super(message);
	}

	public AppSettingsException(String message, Throwable cause) {
		super(message, cause);
	}
}
