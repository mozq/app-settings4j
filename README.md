# app-settings4j

`app-settings4j` is a small Java library for reading and writing application settings in the standard configuration directory for each OS.

It keeps keys in insertion order, supports common scalar types, can hide or preserve null values, and can store the same settings as key-value, INI, simple YAML, or JSON files.

## Installation

Requires Java 21 or later.

With JitPack:

```gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation "com.github.mozq:app-settings4j:v1.0.0"
}
```

## Quick Start

```java
import java.util.List;

import net.mozq.appsettings.AppSettings;

AppSettings settings = AppSettings
        .of("acme", "notes", "settings.properties")
        .load();

String theme = settings.get("theme", "light");
int width = settings.getInt("window.width", 1024);

settings
        .setValue("theme", theme)
        .setValue("window.width", width)
        .setValue("recent.tags", List.of("work", "archive"))
        .store();
```

The settings file is written to an OS-specific location:

```text
<base>/<vendor>/<app>/<fileName>
```

For the example above, the path is:

| OS | Path |
| --- | --- |
| Windows | `%APPDATA%\acme\notes\settings.properties` |
| macOS | `~/Library/Application Support/acme/notes/settings.properties` |
| Linux and Unix-like OSes | `${XDG_CONFIG_HOME:-~/.config}/acme/notes/settings.properties` |

`vendor` is optional. If it is `null` or blank, it is omitted:

```java
AppSettings.of(null, "notes", "settings.properties");
```

## Formats

The format is selected from the file name by default.

| File name | Format |
| --- | --- |
| `.ini` | INI |
| `.yaml`, `.yml` | Simple YAML |
| `.json` | JSON |
| Other extensions | Key-value |

You can also choose a format explicitly:

```java
AppSettings settings = AppSettings
        .of("acme", "notes", "settings.conf")
        .format(SettingsFormats.json())
        .load();
```

Built-in factories:

```java
SettingsFormats.keyValue();
SettingsFormats.ini();
SettingsFormats.simpleYaml();
SettingsFormats.json();
SettingsFormats.byFileName("settings.json");
```

All built-in formats use UTF-8. Keys keep their insertion order: updating an existing key keeps its position, and new keys are appended.

Saving writes through a temporary file and replace operation when possible. If a temporary file cannot be created, it falls back to direct saving.

## Values

Use `setValue(String key, Object value)` to store values.

| Java input | Stored as |
| --- | --- |
| `String` | string |
| finite `Number` | number |
| `Double.NaN`, infinities, and the `Float` equivalents | string |
| `Boolean` | boolean |
| `Instant`, `Date` | UTC instant with `Z` |
| `LocalDateTime`, `LocalDate`, `LocalTime` | date/time without a time zone |
| `OffsetDateTime`, `ZonedDateTime` | date/time with offset or zone |
| `Collection` | list |
| `Enum`, `Locale`, `TimeZone` | string |
| `null` | null |

Read values as strings:

```java
String theme = settings.get("theme", "light");
Map<String, String> strings = settings.asStringMap();
```

Read values as typed Java objects:

```java
Object value = settings.getValue("window.width");
Map<String, Object> values = settings.asValueMap();
```

Read values as a specific type:

```java
int width = settings.getInt("window.width", 1024);
boolean enabled = settings.getBoolean("autosave.enabled", false);
List<String> tags = settings.getList("recent.tags", String.class, List.of());
```

Generic typed access is also available:

```java
Integer width = settings.getValue("window.width", Integer.class, 1024);
Locale locale = settings.getLocale("locale", Locale.US);
TimeZone timeZone = settings.getTimeZone("timeZone", TimeZone.getTimeZone("UTC"));
```

If a value is missing or cannot be converted, the provided default value is returned.

## Null Values

Null values are kept internally. By default, `nullable(false)` is used, so null values behave like missing keys.

```java
AppSettings settings = AppSettings
        .of("acme", "notes", "settings.properties")
        .setValue("last.opened", null);

settings.contains("last.opened");              // false
settings.getValue("last.opened", "fallback");  // "fallback"
```

When `nullable(true)` is enabled, null values are visible and are written to the file:

```java
AppSettings settings = AppSettings
        .of("acme", "notes", "settings.json")
        .nullable(true)
        .setValue("last.opened", null);

settings.contains("last.opened");              // true
settings.getValue("last.opened", "fallback");  // null
```

Use `remove(key)` to delete a key.

## Key-Value Files

Key-value files use one entry per line:

```properties
theme=dark
window.width=1024
autosave.enabled=true
opened.at=2026-08-29T12:34:56Z
recent.tags=[work, archive, "hello, world"]
description=" Personal notes "
```

Whitespace around the `=` separator is ignored, so `theme = dark` is read as the key `theme` and value `dark`.

When loading key-value files:

| Text | Value |
| --- | --- |
| `null` | null |
| `true`, `false` | boolean |
| `10`, `1.5` | `BigDecimal` |
| ISO date/time text | date/time value |
| `[a, b]` | list |
| `"10"` | string |

A line without `=` is read as a null value:

```properties
last.opened
```

Quoted strings force string values and preserve characters that would otherwise be ambiguous:

```properties
label="null"
enabled.label="true"
version.label="1.0"
date.label="2026-08-29"
list.label="[one, two]"
```

Strings are written as quoted strings when they are empty, look like another type, look like a quoted string, start or end with whitespace, or contain characters such as `"`, `\`, newlines, carriage returns, or tabs.

List elements are separated by commas. List strings are also quoted when they contain `,`, `[`, or `]`:

```properties
tags=[work, "hello, world", "[draft]", "item]", " leading "]
```

Backslash escapes are supported while reading and writing keys and values. Built-in writers prefer quoted strings for values that need escaping or type protection. In quoted strings, `\\`, `\"`, `\n`, `\r`, and `\t` are recognized.

## INI Files

INI files use the same value syntax as key-value files. Dotted keys become sections when writing:

```java
settings
        .setValue("theme", "dark")
        .setValue("editor", "enabled")
        .setValue("editor.font.size", "14");
```

```ini
theme=dark

[editor]
@=enabled

[editor.font]
size="14"
```

The `@` key is reserved for the value of the current section when a key has both a value and child keys.

## Simple YAML Files

The built-in YAML format is intentionally small. It supports objects created from dotted keys, scalar values, inline lists, empty lists, and block lists.

```yaml
theme: 'dark'
window:
  width: 1024
autosave:
  enabled: true
recent:
  tags: ['work', 'hello, world', 3]
empty:
  tags: []
```

Single-quoted and double-quoted YAML values are strings. Built-in writers use single quotes for normal strings and double quotes when escape sequences are needed. Unquoted scalars are inferred as null, boolean, number, or date/time values.

When a key has both a value and child keys, `@` is used in the same way as INI:

```yaml
editor:
  '@': 'enabled'
  font:
    size: '14'
```

This format is designed for app settings, not as a complete YAML parser. Flow maps, anchors, tags, and multiline string blocks are not supported. Comments and formatting from an existing file are not preserved when saving.

## JSON Files

JSON files use native JSON types:

```json
{
  "theme": "dark",
  "window": {
    "width": 1024
  },
  "autosave": {
    "enabled": true
  },
  "recent": {
    "tags": ["work", "hello, world", 3]
  }
}
```

Dotted keys are written as nested JSON objects. If a key has both a value and child keys, `@` stores the value of the current object:

```json
{
  "editor": {
    "@": "enabled",
    "font": {
      "size": 14
    }
  }
}
```

JSON comments are not supported.

The built-in JSON parser follows standard JSON syntax for strings, numbers, booleans, null, arrays, and objects. String control characters must be escaped, Unicode escapes must be valid `\uXXXX` sequences, and numbers must not use leading zeros.

## Custom Formats

Implement `SettingsFormat` to plug in another storage format:

```java
SettingsFormat format = new SettingsFormat() {
    @Override
    public Map<String, Object> read(Reader reader) throws IOException {
        return Map.of("theme", "dark");
    }

    @Override
    public void write(Writer writer, Map<String, Object> values, String comments)
            throws IOException {
        writer.write(values.toString());
    }
};

AppSettings settings = AppSettings
        .of("acme", "notes", "settings.custom")
        .format(format)
        .load();
```

Custom formats receive and return ordinary Java values. Built-in formats keep additional internal type information so they can preserve null handling and ordered typed values consistently.

## Notes

- `app`, `fileName`, and setting keys are required.
- `vendor` is optional.
- `app`, `vendor`, and `fileName` must be names, not paths.
- Dotted keys are used for nested formats.
- The `@` key is reserved by INI, simple YAML, and JSON nested output.
- File-level comments can be written, but existing comments are not preserved.

## License

Licensed under the [Apache License 2.0](LICENSE).
