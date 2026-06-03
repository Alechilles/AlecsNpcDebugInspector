package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Minimal JSON reader/writer for the file contract shared with external tools.
 */
public final class NpcRuntimeJson {
    private NpcRuntimeJson() {
    }

    @Nonnull
    public static Object parse(@Nonnull String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON at offset " + parser.position());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public static Map<String, Object> parseObject(@Nonnull String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON root must be an object");
        }
        return (Map<String, Object>) map;
    }

    @Nonnull
    public static String stringify(@Nullable Object value) {
        StringBuilder out = new StringBuilder();
        writeJson(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(@Nonnull StringBuilder out, @Nullable Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String string) {
            writeString(out, string);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeJson(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeJson(out, item);
            }
            out.append(']');
            return;
        }
        if (value.getClass().isArray()) {
            out.append('[');
            Object[] array = (Object[]) value;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeJson(out, array[i]);
            }
            out.append(']');
            return;
        }
        writeString(out, String.valueOf(value));
    }

    private static void writeString(@Nonnull StringBuilder out, @Nonnull String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private int position() {
            return index;
        }

        private boolean isAtEnd() {
            return index >= text.length();
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = text.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                return parseNull();
            }
            if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected JSON character '" + c + "' at offset " + index);
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!isAtEnd()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated escape sequence");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        out.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported escape sequence \\" + escaped);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean at offset " + index);
        }

        private Object parseNull() {
            if (!text.startsWith("null", index)) {
                throw new IllegalArgumentException("Invalid null at offset " + index);
            }
            index += 4;
            return null;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (!isAtEnd() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean floating = false;
            if (!isAtEnd() && text.charAt(index) == '.') {
                floating = true;
                index++;
                while (!isAtEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!isAtEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                floating = true;
                index++;
                if (!isAtEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (!isAtEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String raw = text.substring(start, index);
            return floating ? Double.parseDouble(raw) : Long.parseLong(raw);
        }

        private boolean peek(char expected) {
            return !isAtEnd() && text.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (isAtEnd() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at offset " + index);
            }
            index++;
        }
    }
}
