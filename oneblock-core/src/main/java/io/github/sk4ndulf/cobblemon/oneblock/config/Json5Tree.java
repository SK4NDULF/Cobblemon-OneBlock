package io.github.sk4ndulf.cobblemon.oneblock.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the JSON5 subset we support (comments, unquoted keys, single quotes) with Gson's lenient reader and keeps
 * line/column positions. Duplicate keys are an error instead of silently keeping the last value.
 */
final class Json5Tree {
    /** A parsed value with its position. {@code value} is a Map, List, String, {@link NumberLiteral}, Boolean or null. */
    record Node(Object value, int line, int column) {
    }

    /** Numbers are kept as their literal text so integer fields can reject fractions and overflow exactly. */
    record NumberLiteral(String text) {
    }

    static final class ParseException extends Exception {
        private final int line;
        private final int column;

        ParseException(String message, int line, int column) {
            super(message);
            this.line = line;
            this.column = column;
        }

        int line() {
            return line;
        }

        int column() {
            return column;
        }
    }

    private static final Pattern POSITION = Pattern.compile("line (\\d+) column (\\d+)");

    private Json5Tree() {
    }

    static Node parse(Reader in) throws ParseException {
        JsonReader reader = new JsonReader(in);
        reader.setLenient(true);
        try {
            Node root = read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                int[] pos = position(reader);
                throw new ParseException("unexpected content after the top-level object", pos[0], pos[1]);
            }
            return root;
        } catch (MalformedJsonException | EOFException e) {
            int[] pos = position(reader);
            throw new ParseException("syntax error: " + cleanMessage(e.getMessage()), pos[0], pos[1]);
        } catch (IOException e) {
            int[] pos = position(reader);
            throw new ParseException("could not read: " + e.getMessage(), pos[0], pos[1]);
        } catch (IllegalStateException | NumberFormatException e) {
            int[] pos = position(reader);
            throw new ParseException("syntax error: " + cleanMessage(e.getMessage()), pos[0], pos[1]);
        }
    }

    private static Node read(JsonReader reader) throws IOException, ParseException {
        int[] pos = position(reader);
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Map<String, Node> map = new LinkedHashMap<>();
                while (reader.hasNext()) {
                    int[] keyPos = position(reader);
                    String key = reader.nextName();
                    if (map.containsKey(key)) {
                        throw new ParseException("duplicate key '" + key + "'", keyPos[0], keyPos[1]);
                    }
                    map.put(key, read(reader));
                }
                reader.endObject();
                return new Node(map, pos[0], pos[1]);
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                List<Node> list = new ArrayList<>();
                while (reader.hasNext()) {
                    list.add(read(reader));
                }
                reader.endArray();
                return new Node(list, pos[0], pos[1]);
            }
            case STRING -> {
                return new Node(reader.nextString(), pos[0], pos[1]);
            }
            case NUMBER -> {
                return new Node(new NumberLiteral(reader.nextString()), pos[0], pos[1]);
            }
            case BOOLEAN -> {
                return new Node(reader.nextBoolean(), pos[0], pos[1]);
            }
            case NULL -> {
                reader.nextNull();
                return new Node(null, pos[0], pos[1]);
            }
            default -> throw new ParseException("unexpected " + token, pos[0], pos[1]);
        }
    }

    /** Gson only exposes the position through toString(), e.g. "JsonReader at line 3 column 5 path $.a". */
    private static int[] position(JsonReader reader) {
        Matcher m = POSITION.matcher(reader.toString());
        if (m.find()) {
            return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return new int[] {0, 0};
    }

    private static String cleanMessage(String message) {
        if (message == null) {
            return "unexpected end of file";
        }
        int at = message.indexOf(" at line ");
        String head = at >= 0 ? message.substring(0, at) : message;
        int see = head.indexOf("\nSee ");
        return see >= 0 ? head.substring(0, see) : head;
    }
}
