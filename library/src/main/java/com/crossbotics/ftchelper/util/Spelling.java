package com.crossbotics.ftchelper.util;

import java.util.Collection;
import java.util.Locale;

/**
 * "Did you mean ...?" for names typed by hand: a position name, a Tunables field. Used by the library's
 * messages ("no position called "closd" (did you mean closed?)"); handy in your own too.
 *
 * <pre>{@code
 * String like = Spelling.closest("closd", Arrays.asList("open", "closed"));   // "closed"
 * }</pre>
 *
 * Docs: docs/modules/util.md
 */
public final class Spelling {
    private Spelling() { }

    /**
     * The known name that {@code name} is most likely a typo of, or null if none is close. Capitals and
     * "_" never matter; then a name of 5 or more letters may be 2 letters off (added, missing or changed),
     * a shorter one (3 or 4 letters) 1 letter.
     *
     * @param name  what was typed, e.g. "closd"
     * @param known the real names
     * @return the closest real name, or null
     */
    public static String closest(String name, Collection<String> known) {
        if (name == null || known == null) return null;
        String n = squash(name);
        for (String k : known) if (squash(k).equals(n)) return k;
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String k : known) {
            String q = squash(k);
            int shortest = Math.min(q.length(), n.length());
            if (shortest < 3) continue;
            int allowed = shortest >= 5 ? 2 : 1;
            int d = distance(q, n);
            if (d <= allowed && d < bestD) {
                best = k;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * How many letters must be added, removed or changed to turn {@code a} into {@code b} (the "edit
     * distance"). "closd" to "closed" is 1.
     */
    public static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int change = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(change, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    // Lower case without "_": the forms a typo hides in.
    private static String squash(String s) {
        return s.replace("_", "").toLowerCase(Locale.US);
    }
}
