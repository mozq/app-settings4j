/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntFunction;

/**
 * A resolved settings directory, returned by
 * {@link AppSettings#directory(String, String, String)}. Carries the
 * directory path together with the {@code vendor}/{@code app} that produced
 * it, so {@link AppSettings#of(AppSettingsDirectory, String)} can build
 * settings for a file inside it without losing that context.
 */
public record AppSettingsDirectory(Path path, String vendor, String app) {
	private static final int MAX_UNIQUE_FILE_NAME_ATTEMPTS = 10_000;

	/**
	 * Creates this directory, including any missing parent directories, if
	 * it does not already exist. Does nothing if it already exists.
	 */
	public AppSettingsDirectory ensureExists() throws IOException {
		Files.createDirectories(path);
		return this;
	}

	/**
	 * Calls {@code fileNameGenerator} with an increasing attempt number,
	 * starting at 1, until it returns a name that does not exist in this
	 * directory yet, then returns that name.
	 */
	public String uniqueFileName(IntFunction<String> fileNameGenerator) {
		for (int attempt = 1; attempt <= MAX_UNIQUE_FILE_NAME_ATTEMPTS; attempt++) {
			String candidate = fileNameGenerator.apply(attempt);
			if (!Files.exists(path.resolve(candidate))) {
				return candidate;
			}
		}
		throw new AppSettingsException(
				"Could not find a unique file name in " + path + " after " + MAX_UNIQUE_FILE_NAME_ATTEMPTS + " attempts");
	}
}
