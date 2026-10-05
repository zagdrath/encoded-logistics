/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A Printout (HANDOFF 9): the Line Printer's output, continuous-form green-bar paper - what it printed, as the item's
// encodedlogistics:printout component. title, report (inventory, joblog, devices, splf:<name>), the system, when it was
// printed (the system's clock), the printer, and its pages: up to BODY lines each of up to COLUMNS characters, each with
// an ink (normal, light, red). A page is drawn 384 x 512 (client/printout/PrintoutPages; tools/printout.py the reference):
// rows 0-2 the header (system, title, date; ENCODED LOGISTICS and PAGE n OF m; a rule), the body from row 4, row 47 the
// footer.
public record Printout(String title, String report, String system, String printed, String printer, List<Page> pages) {
    public static final int COLUMNS = 56, ROWS = 48, BODY = ROWS - 6, MAX_PAGES = 100;
    public static final byte NORMAL = 0, LIGHT = 1, RED = 2;
    // A report line's ink, as its first character (Reports): none for normal.
    public static final char MARK_LIGHT = '\u0001', MARK_RED = '\u0002';

    public record Line(String text, byte ink) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Line::text),
                Codec.BYTE.optionalFieldOf("ink", NORMAL).forGetter(Line::ink))
                .apply(i, Line::new));
        public static final StreamCodec<ByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(COLUMNS * 4), Line::text,
                ByteBufCodecs.BYTE, Line::ink,
                Line::new);
    }

    public record Page(List<Line> lines) {
        public static final Codec<Page> CODEC = Line.CODEC.listOf().xmap(Page::new, Page::lines);
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = Line.STREAM_CODEC.apply(ByteBufCodecs.list(BODY)).map(Page::new, Page::lines);
    }

    public static final Codec<Printout> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(Printout::title),
            Codec.STRING.fieldOf("report").forGetter(Printout::report),
            Codec.STRING.fieldOf("system").forGetter(Printout::system),
            Codec.STRING.fieldOf("printed").forGetter(Printout::printed),
            Codec.STRING.fieldOf("printer").forGetter(Printout::printer),
            Page.CODEC.listOf().fieldOf("pages").forGetter(Printout::pages))
            .apply(i, Printout::new));
    public static final StreamCodec<ByteBuf, Printout> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Printout::title,
            ByteBufCodecs.STRING_UTF8, Printout::report,
            ByteBufCodecs.STRING_UTF8, Printout::system,
            ByteBufCodecs.STRING_UTF8, Printout::printed,
            ByteBufCodecs.STRING_UTF8, Printout::printer,
            Page.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PAGES)), Printout::pages,
            Printout::new);

    // A report's lines as pages: each line's ink from its mark; a line longer than a row is cut there and goes on on the
    // next row after a light ">"; BODY rows a page (at least one page).
    public static List<Page> paginate(List<String> lines) {
        List<Line> rows = new ArrayList<>();
        for (String raw : lines) {
            byte ink = NORMAL;
            String text = raw.stripTrailing();
            if (!text.isEmpty() && (text.charAt(0) == MARK_LIGHT || text.charAt(0) == MARK_RED)) {
                ink = text.charAt(0) == MARK_LIGHT ? LIGHT : RED;
                text = text.substring(1);
            }
            text = text.replace('\t', ' ');
            rows.add(new Line(text.length() > COLUMNS ? text.substring(0, COLUMNS) : text, ink));
            for (int at = COLUMNS; at < text.length(); at += COLUMNS - 1) {
                String rest = text.substring(at, Math.min(text.length(), at + COLUMNS - 1));
                rows.add(new Line(">" + rest, LIGHT));
            }
        }
        List<Page> pages = new ArrayList<>();
        for (int at = 0; at < rows.size(); at += BODY) {
            pages.add(new Page(List.copyOf(rows.subList(at, Math.min(rows.size(), at + BODY)))));
        }
        if (pages.isEmpty()) {
            pages.add(new Page(List.of()));
        }
        return pages;
    }

    // The header's lines on a page (rows 0 and 1; row 2 is a rule): the system, the title centred, the date; ENCODED
    // LOGISTICS and PAGE n OF m.
    public String header(int page) {
        String date = printed.length() > 20 ? printed.substring(0, 20) : printed;
        String system = this.system.length() > 10 ? this.system.substring(0, 10) : this.system;
        int middle = COLUMNS - 30;
        String centred = title.length() > middle ? title.substring(0, middle) : title;
        int left = (middle - centred.length()) / 2;
        return String.format("%-10s%s%20s", system, " ".repeat(left) + centred + " ".repeat(middle - left - centred.length()), date);
    }

    public String subheader(int page) {
        String right = String.format("PAGE %3d OF %-3d", page + 1, pages.size());
        return "ENCODED LOGISTICS" + " ".repeat(Math.max(1, COLUMNS - 17 - right.length())) + right;
    }

    // The footer: the last page ends the report.
    public String footer(int page) {
        String text = page >= pages.size() - 1 ? "*** END OF REPORT ***" : "*** CONTINUED ***";
        int left = (COLUMNS - text.length()) / 2;
        return " ".repeat(left) + text;
    }
}
