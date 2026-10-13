/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A Router ACL (the routing handoff 3): a named, ordered list of rules, each matching resources (an item, #tag, *ITEM
// *FLUID *PRES *ENERGY, *ANY, or *CONTROL for Directory control requests) going one way or both (*IN: into the Router's
// own network, *OUT: out of it), and permitting or denying them, optionally only so many a minute (rate) or a game day
// (quota) - past those it denies until the window turns. The first rule that matches decides; with none, the default
// (aclDefault). Each rule counts what it matched (hits) and when it last did.
//
// An ACL is applied to a route (a policy) or to a network the Router reaches directly (a neighbour, an exchange peer):
// a Move moves only what it permits; a Share shows only what it permits.
public final class RouterAcl {
    public static final int MAX_RULES = 16, MAX_ACLS = 8, SEQ_STEP = 10, MAX_NAME = 10;
    public static final long MINUTE = 1_200, DAY = 24_000;

    public enum Dir {
        IN, OUT, BOTH;

        public String special() {
            return "*" + name();
        }

        public boolean covers(Dir way) {
            return this == BOTH || way == BOTH || this == way;
        }
    }

    public static final class Rule {
        private int seq;
        private String match;
        private Dir dir;
        private boolean permit;
        private int rate, quota;
        private long hits, lastMatched = -1;
        // The current rate and quota windows and what they've let through.
        private long minute = -1, minuteCount, day = -1, dayCount;
        private @Nullable Object resolved;
        private boolean resolvedOnce;

        public Rule(int seq, String match, Dir dir, boolean permit, int rate, int quota) {
            this.seq = seq;
            this.match = match;
            this.dir = dir;
            this.permit = permit;
            this.rate = Math.max(0, rate);
            this.quota = Math.max(0, quota);
        }

        public int seq() {
            return seq;
        }

        public String match() {
            return match;
        }

        public Dir dir() {
            return dir;
        }

        public boolean permit() {
            return permit;
        }

        public int rate() {
            return rate;
        }

        public int quota() {
            return quota;
        }

        public long hits() {
            return hits;
        }

        public long lastMatched() {
            return lastMatched;
        }

        public String action() {
            return permit ? "*PERMIT" : "*DENY";
        }

        // "64/min", "2000/day", "64/min 2000/day" or "".
        public String limit() {
            String text = rate > 0 ? rate + "/min" : "";
            return quota > 0 ? (text.isEmpty() ? "" : text + " ") + quota + "/day" : text;
        }

        public void set(String match, Dir dir, boolean permit, int rate, int quota) {
            this.match = match;
            this.dir = dir;
            this.permit = permit;
            this.rate = Math.max(0, rate);
            this.quota = Math.max(0, quota);
            resolvedOnce = false;
        }

        boolean matches(StorageKey key, Dir way) {
            if (!dir.covers(way)) {
                return false;
            }
            return switch (match) {
                case "*ANY" -> true;
                case "*CONTROL" -> false;
                case "*ITEM" -> key.is(ResourceType.ITEM);
                case "*FLUID" -> key.is(ResourceType.FLUID);
                case "*PRES" -> key.is(ResourceType.PRESSURIZED);
                case "*ENERGY" -> key.is(ResourceType.ENERGY);
                default -> {
                    Object target = target();
                    if (target instanceof TagKey<?> tag) {
                        @SuppressWarnings("unchecked")
                        TagKey<Item> items = (TagKey<Item>) tag;
                        yield key.isItem() && key.stack().is(items);
                    }
                    if (target instanceof Item item) {
                        yield key.isItem() && key.stack().is(item);
                    }
                    yield target instanceof StorageKey resource && resource.equals(key);
                }
            };
        }

        private @Nullable Object target() {
            if (!resolvedOnce) {
                resolvedOnce = true;
                resolved = resolve(match);
            }
            return resolved;
        }

        // How much of amount the rate and quota still let through now.
        long room(long amount, long now) {
            roll(now);
            long room = amount;
            if (rate > 0) {
                room = Math.min(room, Math.max(0, rate - minuteCount));
            }
            if (quota > 0) {
                room = Math.min(room, Math.max(0, quota - dayCount));
            }
            return room;
        }

        void count(long amount, long now) {
            roll(now);
            hits += amount;
            lastMatched = now;
            minuteCount += amount;
            dayCount += amount;
        }

        private void roll(long now) {
            if (now / MINUTE != minute) {
                minute = now / MINUTE;
                minuteCount = 0;
            }
            if (now / DAY != day) {
                day = now / DAY;
                dayCount = 0;
            }
        }
    }

    // What matched a resource: the rule (null: the default) and whether it lets it through.
    public record Verdict(@Nullable Rule rule, boolean permit) {}

    private String name;
    private final List<Rule> rules = new ArrayList<>();
    private long defaultHits, defaultLast = -1;

    public RouterAcl(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<Rule> rules() {
        return rules;
    }

    public long defaultHits() {
        return defaultHits;
    }

    public long defaultLast() {
        return defaultLast;
    }

    public @Nullable Rule rule(int seq) {
        for (Rule rule : rules) {
            if (rule.seq == seq) {
                return rule;
            }
        }
        return null;
    }

    // The rule that decides for a resource going one way (null: the default), and its call ignoring rate and quota.
    public Verdict verdict(StorageKey key, Dir way, boolean permitByDefault) {
        for (Rule rule : rules) {
            if (rule.matches(key, way)) {
                return new Verdict(rule, rule.permit);
            }
        }
        return new Verdict(null, permitByDefault);
    }

    // How much of amount may go now (0: denied, or its rate or quota is used up).
    public long allow(Verdict verdict, long amount, long now) {
        if (!verdict.permit()) {
            return 0;
        }
        return verdict.rule() != null ? verdict.rule().room(amount, now) : amount;
    }

    // Whether a resource may be seen (a Share): permitted, with room left in its windows.
    public boolean permits(StorageKey key, Dir way, boolean permitByDefault, long now) {
        Verdict verdict = verdict(key, way, permitByDefault);
        return allow(verdict, 1, now) > 0;
    }

    // Counts what went (or was turned away) against the rule that decided.
    public void count(Verdict verdict, long amount, long now) {
        if (verdict.rule() != null) {
            verdict.rule().count(amount, now);
        } else {
            defaultHits += amount;
            defaultLast = now;
        }
    }

    // --- Changing it (ADDACLE, CHGACLE, RMVACLE, the panel) ---

    // Adds a rule at seq (-1: after the last); false when it's full or seq is taken.
    public boolean add(int seq, String match, Dir dir, boolean permit, int rate, int quota) {
        if (rules.size() >= MAX_RULES || seq >= 0 && rule(seq) != null) {
            return false;
        }
        int at = seq >= 0 ? seq : rules.isEmpty() ? SEQ_STEP : rules.getLast().seq + SEQ_STEP;
        rules.add(new Rule(at, match, dir, permit, rate, quota));
        sort();
        return true;
    }

    public boolean remove(int seq) {
        return rules.removeIf(rule -> rule.seq == seq);
    }

    // Swaps a rule with the one before (up) or after it; false when there's none.
    public boolean move(int seq, boolean up) {
        Rule rule = rule(seq);
        if (rule == null) {
            return false;
        }
        int index = rules.indexOf(rule), other = up ? index - 1 : index + 1;
        if (other < 0 || other >= rules.size()) {
            return false;
        }
        Rule swap = rules.get(other);
        int seqs = rule.seq;
        rule.seq = swap.seq;
        swap.seq = seqs;
        sort();
        return true;
    }

    private void sort() {
        rules.sort(Comparator.comparingInt(Rule::seq));
    }

    // --- Text ---

    // A match as rules keep it: *ANY and the other specials, #namespace:tag, or an item (COAL, mod:item) or other
    // resource (FLUID minecraft:water); null when it names nothing.
    public static @Nullable String normalise(String text) {
        String trimmed = text.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "*ANY", "*ITEM", "*FLUID", "*PRES", "*ENERGY", "*CONTROL" -> {
                return upper;
            }
            default -> {
            }
        }
        if (trimmed.startsWith("#")) {
            Identifier id = Identifier.tryParse(trimmed.substring(1).toLowerCase(Locale.ROOT));
            return id != null ? "#" + id : null;
        }
        StorageKey key = ElclItems.parse(trimmed);
        if (key != null && !key.isItem()) {
            return ElclItems.text(key);
        }
        try {
            return ElclItems.id(ElclItems.resolve(trimmed));
        } catch (ElclException e) {
            return null;
        }
    }

    private static @Nullable Object resolve(String match) {
        if (match.startsWith("#")) {
            Identifier id = Identifier.tryParse(match.substring(1));
            return id != null ? TagKey.create(Registries.ITEM, id) : null;
        }
        StorageKey key = ElclItems.parse(match);
        if (key == null) {
            return null;
        }
        return key.isItem() ? key.stack().getItem() : key;
    }

    // A rule as the panel's editor writes it: "MATCH [permit|deny] [in|out|both] [N/m] [N/d]" (the match may be a
    // resource with a space: FLUID minecraft:water). Null when it can't be read.
    public record Spec(String match, Dir dir, boolean permit, int rate, int quota) {}

    public static @Nullable Spec parse(String text) {
        List<String> words = new ArrayList<>(List.of(text.trim().split("\\s+")));
        Dir dir = Dir.BOTH;
        boolean permit = true;
        int rate = 0, quota = 0;
        while (words.size() > 1) {
            String last = words.getLast().toLowerCase(Locale.ROOT).replace("*", "");
            if (last.equals("permit") || last.equals("deny")) {
                permit = last.equals("permit");
            } else if (last.equals("in") || last.equals("out") || last.equals("both")) {
                dir = Dir.valueOf(last.toUpperCase(Locale.ROOT));
            } else if (last.matches("\\d+/(m|min)")) {
                rate = Integer.parseInt(last.substring(0, last.indexOf('/')));
            } else if (last.matches("\\d+/(d|day)")) {
                quota = Integer.parseInt(last.substring(0, last.indexOf('/')));
            } else {
                break;
            }
            words.removeLast();
        }
        String match = normalise(String.join(" ", words));
        return match != null ? new Spec(match, dir, permit, rate, quota) : null;
    }

    // The rule as parse() reads it back.
    public static String spec(Rule rule) {
        StringBuilder text = new StringBuilder(rule.match).append(rule.permit ? " permit" : " deny");
        if (rule.dir != Dir.BOTH) {
            text.append(' ').append(rule.dir.name().toLowerCase(Locale.ROOT));
        }
        if (rule.rate > 0) {
            text.append(' ').append(rule.rate).append("/m");
        }
        if (rule.quota > 0) {
            text.append(' ').append(rule.quota).append("/d");
        }
        return text.toString();
    }

    public static boolean validName(String name) {
        return name.matches("[A-Z][A-Z0-9]{0," + (MAX_NAME - 1) + "}");
    }

    // --- Saving ---

    public void save(ValueOutput output) {
        output.putString("name", name);
        output.putLong("default_hits", defaultHits);
        ValueOutput.ValueOutputList list = output.childrenList("rules");
        for (Rule rule : rules) {
            ValueOutput child = list.addChild();
            child.putInt("seq", rule.seq);
            child.putString("match", rule.match);
            child.putString("dir", rule.dir.name());
            child.putBoolean("permit", rule.permit);
            child.putInt("rate", rule.rate);
            child.putInt("quota", rule.quota);
            child.putLong("hits", rule.hits);
            child.putLong("last", rule.lastMatched);
        }
    }

    public static RouterAcl load(ValueInput input) {
        RouterAcl acl = new RouterAcl(input.getStringOr("name", "ACL"));
        acl.defaultHits = input.getLongOr("default_hits", 0);
        for (ValueInput child : input.childrenListOrEmpty("rules")) {
            if (acl.rules.size() >= MAX_RULES) {
                break;
            }
            Dir dir;
            try {
                dir = Dir.valueOf(child.getStringOr("dir", "BOTH"));
            } catch (IllegalArgumentException e) {
                dir = Dir.BOTH;
            }
            Rule rule = new Rule(child.getIntOr("seq", (acl.rules.size() + 1) * SEQ_STEP), child.getStringOr("match", "*ANY"), dir,
                    child.getBooleanOr("permit", true), child.getIntOr("rate", 0), child.getIntOr("quota", 0));
            rule.hits = child.getLongOr("hits", 0);
            rule.lastMatched = child.getLongOr("last", -1);
            acl.rules.add(rule);
        }
        acl.sort();
        return acl;
    }

    public void rename(String name) {
        this.name = name;
    }
}
