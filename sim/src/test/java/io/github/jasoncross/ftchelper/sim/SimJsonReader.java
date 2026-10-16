package io.github.jasoncross.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for the simulator's data files (parts catalog, fields), so no extra library is
 * needed. Objects become Map<String, Object>, arrays List<Object>, numbers Double, plus String,
 * Boolean and null. Errors name the place in the text.
 */
final class SimJsonReader {
    private final String s;
    private int i = 0;

    private SimJsonReader(String text) {
        this.s = text;
    }

    static Object parse(String text) {
        SimJsonReader r = new SimJsonReader(text);
        Object v = r.value();
        r.space();
        if (r.i != r.s.length()) throw r.error("extra text after the end");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object o, String what) {
        if (!(o instanceof Map)) throw new IllegalArgumentException(what + ": expected an object");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object o, String what) {
        if (!(o instanceof List)) throw new IllegalArgumentException(what + ": expected a list");
        return (List<Object>) o;
    }

    static double number(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    static String string(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    private Object value() {
        space();
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': word("true"); return Boolean.TRUE;
            case 'f': word("false"); return Boolean.FALSE;
            case 'n': word("null"); return null;
            default: return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        space();
        if (peek('}')) { i++; return m; }
        while (true) {
            space();
            String k = str();
            space();
            expect(':');
            m.put(k, value());
            space();
            if (peek(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        space();
        if (peek(']')) { i++; return l; }
        while (true) {
            l.add(value());
            space();
            if (peek(',')) { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String str() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        throw error("a text value never ends");
    }

    private Double num() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("unexpected '" + s.charAt(i) + "'");
        return Double.parseDouble(s.substring(start, i));
    }

    private void word(String w) {
        if (!s.startsWith(w, i)) throw error("expected " + w);
        i += w.length();
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("expected '" + c + "'");
        i++;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
        return new IllegalArgumentException("JSON line " + line + ": " + what);
    }
}
