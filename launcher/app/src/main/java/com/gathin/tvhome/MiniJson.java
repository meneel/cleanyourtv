package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader (objects, arrays, strings, numbers, booleans, null) with no Android types, so replies can be
 * parsed in host tests; org.json is not available there and a test-only dependency was not worth adding.
 */
final class MiniJson {
    private static final int MAX_DEPTH = 32;
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** The parsed value (Map, List, String, Double, Boolean or null for a JSON null), or null when malformed. */
    static Object parse(String text) {
        if (text == null) return null;
        try {
            MiniJson p = new MiniJson(text);
            p.ws();
            Object v = p.value(0);
            p.ws();
            return p.i == p.s.length() ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    static String string(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String ? (String) v : null;
    }

    static double number(Map<String, Object> m, String key, double fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Double ? (Double) v : fallback;
    }

    static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw new IllegalStateException("too deep");
        char c = s.charAt(i);
        if (c == '{') return obj(depth);
        if (c == '[') return arr(depth);
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalStateException("unexpected " + c);
        return Double.valueOf(s.substring(start, i));
    }

    private Map<String, Object> obj(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            if (s.charAt(i++) != ':') throw new IllegalStateException("colon");
            ws();
            m.put(k, value(depth + 1));
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw new IllegalStateException("comma");
        }
    }

    private List<Object> arr(int depth) {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw new IllegalStateException("comma");
        }
    }

    private String str() {
        if (s.charAt(i) != '"') throw new IllegalStateException("quote");
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: sb.append(e);   // \" \\ \/
            }
        }
    }
}
