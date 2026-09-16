package com.thenetworkplan.networkplan.common.util;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal JSON writer for audit payloads.
 *
 * <p>Deliberately not Jackson: the leg-event payload is a flat map of scalars
 * written once and read by a human, so it does not justify coupling the audit
 * trail to a serialisation library's configuration (or to which major version of
 * it is on the classpath). HTTP responses are serialised by Spring as usual.
 */
public final class Json {

    private Json() {
    }

    /** Renders a flat map as a JSON object, or returns null for an empty map. */
    public static String object(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder(64).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escape(entry.getKey())).append("\":");
            appendValue(out, entry.getValue());
        }
        return out.append('}').toString();
    }

    private static void appendValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Temporal || value instanceof UUID || value instanceof Enum<?>) {
            out.append('"').append(escape(value.toString())).append('"');
        } else {
            out.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
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
        return out.toString();
    }
}
