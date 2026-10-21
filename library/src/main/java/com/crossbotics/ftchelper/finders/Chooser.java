package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list on the Driver Station to choose from with the D-pad (for example every servo in the
 * configuration). The chosen line has a "&gt;" in front. Long lists scroll.
 *
 * Docs: docs/modules/finders.md
 */
public final class Chooser {
    private static final int PAGE = 8;
    private final List<String> items;
    private int index = 0;

    /** @param items the names to choose from (copied, sorted A to Z) */
    public Chooser(List<String> items) {
        this.items = new ArrayList<>(items);
        Collections.sort(this.items);
    }

    /** Moves the choice down one (wraps from the last to the first). */
    public void next() {
        if (!items.isEmpty()) index = (index + 1) % items.size();
    }

    /** Moves the choice up one (wraps from the first to the last). */
    public void previous() {
        if (!items.isEmpty()) index = (index + items.size() - 1) % items.size();
    }

    /** The chosen name, or null if the list is empty. */
    public String current() {
        return items.isEmpty() ? null : items.get(index);
    }

    /** True if there is nothing to choose. */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Shows one page of the list around the choice. */
    public void addTelemetry(RobotTelemetry t, String emptyText) {
        if (items.isEmpty()) {
            t.text(emptyText);
            return;
        }
        int first = Math.max(0, Math.min(index - PAGE / 2, items.size() - PAGE));
        int last = Math.min(items.size(), first + PAGE);
        for (int i = first; i < last; i++) t.text((i == index ? "> " : "  ") + items.get(i));
        if (items.size() > PAGE) t.text("(" + (index + 1) + " of " + items.size() + ")");
    }
}
