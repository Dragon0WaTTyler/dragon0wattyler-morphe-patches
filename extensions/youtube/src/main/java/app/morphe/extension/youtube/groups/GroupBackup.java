/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Independently implemented, bounded parser for the two public backup shapes. No network access. */
public final class GroupBackup {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_GROUPS = 2000;
    private static final int MAX_MEMBERSHIPS = 100000;

    public static final class InvalidBackup extends IOException {
        private static final long serialVersionUID = 1L;
        public InvalidBackup(String message) { super(message); }
    }

    public static final class Group {
        public final String name;
        public final List<String> channelIds;

        private Group(String name, LinkedHashSet<String> ids) {
            this.name = name;
            channelIds = Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }

    public static final class Snapshot {
        public final List<Group> groups;
        public final int memberships;
        public final int duplicateMemberships;

        private Snapshot(List<Group> groups, int memberships) {
            this(groups, memberships, 0);
        }

        private Snapshot(List<Group> groups, int memberships, int duplicates) {
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.memberships = memberships;
            this.duplicateMemberships = duplicates;
        }
    }

    public static Snapshot empty() { return new Snapshot(Collections.emptyList(), 0); }

    public static boolean isChannelId(String value) {
        return value != null && value.matches("UC[A-Za-z0-9_-]{22}");
    }

    /** Reads at most MAX_BYTES; the caller owns and closes the input stream. */
    static String read(InputStream input) throws IOException {
        if (input == null) throw new InvalidBackup("File cannot be read");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new InvalidBackup("File exceeds 4 MiB");
            bytes.write(buffer, 0, count);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (java.nio.charset.CharacterCodingException ex) {
            throw new InvalidBackup("File is not UTF-8");
        }
    }

    public static Snapshot parse(InputStream input) throws IOException { return parse(read(input)); }

    static void validateJson(String text) throws InvalidBackup { new Json(text).document(); }

    public static Snapshot parse(String text) throws InvalidBackup {
        Map<String, Object> root = object(new Json(text).document());
        Object collection = root.get("ysc_collection");
        if (collection instanceof Map) {
            Map<String, Object> index = object(collection);
            Map<String, Object> groups = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : index.entrySet()) {
                if (!(entry.getValue() instanceof String)) {
                    throw new InvalidBackup("Unsupported group index");
                }
                if (!root.containsKey(entry.getKey())) throw new InvalidBackup("Missing group members");
                groups.put(entry.getKey(), root.get(entry.getKey()));
            }
            return groups(groups);
        }
        return groups(root);
    }

    private static Snapshot groups(Map<String, Object> source) throws InvalidBackup {
        if (source.size() > MAX_GROUPS) throw new InvalidBackup("Too many groups");
        List<Group> groups = new ArrayList<>();
        int membershipCount = 0;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String name = entry.getKey();
            if (name.trim().isEmpty() || name.length() > 512) throw new InvalidBackup("Invalid group name");
            if (!(entry.getValue() instanceof List)) throw new InvalidBackup("Expected channel ID arrays");
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Object value : (List<?>) entry.getValue()) {
                if (!(value instanceof String) || !isChannelId((String) value)) {
                    throw new InvalidBackup("Invalid YouTube channel ID");
                }
                ids.add((String) value);
                // Count input entries too, so repeated entries cannot evade the bound.
                if (++membershipCount > MAX_MEMBERSHIPS) throw new InvalidBackup("Too many memberships");
            }
            groups.add(new Group(name, ids));
        }
        int uniqueMemberships = 0;
        for (Group group : groups) uniqueMemberships += group.channelIds.size();
        return new Snapshot(groups, uniqueMemberships, membershipCount - uniqueMemberships);
    }

    /** V2 mutations reuse the same name, channel and size validation as V1 imports. */
    static Snapshot fromGroups(Map<String, Object> source) throws InvalidBackup { return groups(source); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) throws InvalidBackup {
        if (!(value instanceof Map)) throw new InvalidBackup("Expected a JSON object");
        return (Map<String, Object>) value;
    }

    static Snapshot readStored(InputStream input) throws IOException {
        Map<String, Object> root = object(new Json(read(input)).document());
        if (!(root.get("schema") instanceof JsonNumber)
                || !"1".equals(((JsonNumber) root.get("schema")).value)) {
            throw new InvalidBackup("Unsupported local schema");
        }
        return groups(object(root.get("groups")));
    }

    static String encode(Snapshot snapshot) {
        StringBuilder out = new StringBuilder("{\"schema\":1,\"groups\":{");
        boolean firstGroup = true;
        for (Group group : snapshot.groups) {
            if (!firstGroup) out.append(',');
            firstGroup = false;
            quote(out, group.name);
            out.append(":[");
            boolean firstId = true;
            for (String id : group.channelIds) {
                if (!firstId) out.append(',');
                firstId = false;
                quote(out, id);
            }
            out.append(']');
        }
        return out.append("}}").toString();
    }

    static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 0x20) {
                out.append("\\u");
                String hex = Integer.toHexString(c);
                for (int j = hex.length(); j < 4; j++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        out.append('"');
    }

    private static final class JsonNumber {
        final String value;
        JsonNumber(String value) { this.value = value; }
    }

    /** Strict JSON grammar avoids Android/JVM JSONObject differences and lenient corruption recovery. */
    private static final class Json {
        final String text;
        int position;
        int nodes;

        Json(String text) throws InvalidBackup {
            if (text == null || text.length() > MAX_BYTES
                    || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new InvalidBackup("Missing or oversized file");
            }
            this.text = text;
            if (text.startsWith("\uFEFF")) position++;
        }

        Object document() throws InvalidBackup {
            Object value = value(0);
            space();
            if (position != text.length()) fail();
            return value;
        }

        Object value(int depth) throws InvalidBackup {
            if (depth > 32 || ++nodes > 250000) throw new InvalidBackup("JSON is too complex");
            space();
            if (position == text.length()) return fail();
            char c = text.charAt(position);
            if (c == '"') return string();
            if (c == '{') {
                position++;
                Map<String, Object> result = new LinkedHashMap<>();
                space();
                if (take('}')) return result;
                do {
                    space();
                    if (!take('"')) return fail();
                    position--;
                    String key = string();
                    space();
                    if (!take(':') || result.containsKey(key)) return fail();
                    result.put(key, value(depth + 1));
                    space();
                    if (take('}')) return result;
                } while (take(','));
                return fail();
            }
            if (c == '[') {
                position++;
                List<Object> result = new ArrayList<>();
                space();
                if (take(']')) return result;
                do {
                    result.add(value(depth + 1));
                    space();
                    if (take(']')) return result;
                } while (take(','));
                return fail();
            }
            if (c == 't' || c == 'f' || c == 'n') {
                String literal = c == 't' ? "true" : c == 'f' ? "false" : "null";
                if (!text.startsWith(literal, position)) return fail();
                position += literal.length();
                return c == 'n' ? null : Boolean.valueOf(literal);
            }
            int start = position;
            while (position < text.length() && "-+0123456789.eE".indexOf(text.charAt(position)) >= 0) position++;
            String number = text.substring(start, position);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) return fail();
            // Keep numeric tokens as text; only the local integer schema is interpreted.
            return new JsonNumber(number);
        }

        String string() throws InvalidBackup {
            if (!take('"')) return fail();
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    String result = out.toString();
                    for (int i = 0; i < result.length(); i++) {
                        char part = result.charAt(i);
                        if (Character.isHighSurrogate(part)) {
                            if (++i == result.length() || !Character.isLowSurrogate(result.charAt(i))) return fail();
                        } else if (Character.isLowSurrogate(part)) return fail();
                    }
                    return result;
                }
                if (c < 0x20) return fail();
                if (c == '\\') {
                    if (position == text.length()) return fail();
                    char escape = text.charAt(position++);
                    switch (escape) {
                        case '"': case '\\': case '/': c = escape; break;
                        case 'b': c = '\b'; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'u':
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                if (position == text.length()) return fail();
                                char digit = text.charAt(position++);
                                int hex = "0123456789abcdef".indexOf(Character.toLowerCase(digit));
                                if (hex < 0) return fail();
                                code = (code << 4) | hex;
                            }
                            c = (char) code;
                            break;
                        default: return fail();
                    }
                }
                out.append(c);
            }
            return fail();
        }

        void space() {
            while (position < text.length() && " \r\n\t".indexOf(text.charAt(position)) >= 0) position++;
        }

        boolean take(char c) {
            if (position < text.length() && text.charAt(position) == c) { position++; return true; }
            return false;
        }

        <T> T fail() throws InvalidBackup { throw new InvalidBackup("Invalid JSON near character " + position); }
    }

    private GroupBackup() {}
}
