/*!
 * app-settings4j
 * Copyright 2026 Mozq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package net.mozq.appsettings;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

final class SettingsComments {
	private SettingsComments() {
	}

	static List<String> normalize(List<String> comments) {
		if (comments == null || comments.isEmpty()) {
			return List.of();
		}
		return comments.stream()
				.flatMap(comment -> lines(comment).stream())
				.toList();
	}

	static String parseLine(String line, char... markers) {
		String trimmed = line.stripLeading();
		if (trimmed.isEmpty()) {
			return null;
		}
		char first = trimmed.charAt(0);
		for (char marker : markers) {
			if (first == marker) {
				return trimmed.substring(1).trim();
			}
		}
		return null;
	}

	static void write(BufferedWriter writer, List<String> comments, String prefix) throws IOException {
		for (String line : normalize(comments)) {
			if (line.isEmpty()) {
				writer.write(prefix.stripTrailing());
			} else {
				writer.write(prefix);
				writer.write(line);
			}
			writer.newLine();
		}
	}

	static List<String> lines(String comment) {
		if (comment == null) {
			return List.of("");
		}
		return Arrays.stream(comment.split("\\R", -1))
				.map(String::trim)
				.toList();
	}
}
