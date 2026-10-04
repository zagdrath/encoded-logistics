/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A line of green-screen text from the server: cells laid side by side, each padded (or cut) to its width - 0 for as
// wide as it is - and right-aligned or not, so columns line up whatever language names come out in on the client;
// the whole line in one attribute (NORMAL, BRIGHT, DIM).
public record TerminalLine(List<Cell> cells, int attr) {
    public static final int NORMAL = 0, BRIGHT = 1, DIM = 2;

    public record Cell(Component text, int width, boolean right) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Cell::text,
                ByteBufCodecs.VAR_INT, Cell::width,
                ByteBufCodecs.BOOL, Cell::right,
                Cell::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLine> STREAM_CODEC = StreamCodec.composite(
            Cell.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalLine::cells,
            ByteBufCodecs.VAR_INT, TerminalLine::attr,
            TerminalLine::new);

    public static TerminalLine of(String text) {
        return of(Component.literal(text), NORMAL);
    }

    public static TerminalLine of(Component text, int attr) {
        return new TerminalLine(List.of(new Cell(text, 0, false)), attr);
    }

    public static TerminalLine blank() {
        return of("");
    }

    // The line as plain text (the client's language for translated parts).
    public String text() {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            String text = cell.text().getString();
            if (cell.width() <= 0) {
                out.append(text);
            } else if (text.length() >= cell.width()) {
                out.append(text, 0, cell.width());
            } else if (cell.right()) {
                out.append(" ".repeat(cell.width() - text.length())).append(text);
            } else {
                out.append(text).append(" ".repeat(cell.width() - text.length()));
            }
        }
        return out.toString();
    }

    // Builds a line cell by cell.
    public static final class Builder {
        private final List<Cell> cells = new ArrayList<>();
        private int attr = NORMAL;

        public Builder text(String text) {
            return cell(Component.literal(text), 0, false);
        }

        public Builder text(Component text) {
            return cell(text, 0, false);
        }

        public Builder left(Object text, int width) {
            return cell(text instanceof Component component ? component : Component.literal(String.valueOf(text)), width, false);
        }

        public Builder right(Object text, int width) {
            return cell(text instanceof Component component ? component : Component.literal(String.valueOf(text)), width, true);
        }

        public Builder cell(Component text, int width, boolean right) {
            cells.add(new Cell(text, width, right));
            return this;
        }

        public Builder attr(int attr) {
            this.attr = attr;
            return this;
        }

        public TerminalLine build() {
            return new TerminalLine(List.copyOf(cells), attr);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
