/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import it.unimi.dsi.fastutil.floats.FloatConsumer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.zagdrath.encodedlogistics.signal.AudioFiles;

// A Speaker's audio decoded for the sound engine: OGG Vorbis (Minecraft's own decoder) or MP3 (JLayer), mixed down to
// mono 16-bit so it's positioned like any block sound, started part way in (where the play should be by now), looped
// or not, and cut at the longest a server allows.
final class AudioDecoding {
    private AudioDecoding() {}

    // Interleaved samples (-1..1) a chunk at a time; false at the end.
    interface PcmSource extends AutoCloseable {
        int rate();

        int channels();

        boolean read(FloatConsumer output) throws IOException;

        @Override
        void close() throws IOException;
    }

    static PcmSource open(byte[] bytes) throws IOException {
        AudioFiles.Format format = AudioFiles.format(bytes);
        if (format == null) {
            throw new IOException("Not OGG Vorbis or MP3");
        }
        return format == AudioFiles.Format.OGG ? new Ogg(bytes) : new Mp3(bytes);
    }

    private static final class Ogg implements PcmSource {
        private final JOrbisAudioStream stream;

        Ogg(byte[] bytes) throws IOException {
            stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes));
        }

        @Override
        public int rate() {
            return (int) stream.getFormat().getSampleRate();
        }

        @Override
        public int channels() {
            return stream.getFormat().getChannels();
        }

        @Override
        public boolean read(FloatConsumer output) throws IOException {
            return stream.readChunk(output);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private static final class Mp3 implements PcmSource {
        private final Bitstream bitstream;
        private final Decoder decoder = new Decoder();
        // The first frame, decoded to learn the rate and channels, given out first.
        private @Nullable SampleBuffer pending;
        private final int rate, channels;

        Mp3(byte[] bytes) throws IOException {
            bitstream = new Bitstream(new ByteArrayInputStream(bytes));
            pending = next();
            if (pending == null) {
                throw new IOException("No MP3 frames");
            }
            rate = decoder.getOutputFrequency();
            channels = decoder.getOutputChannels();
        }

        private @Nullable SampleBuffer next() throws IOException {
            try {
                Header header = bitstream.readFrame();
                if (header == null) {
                    return null;
                }
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                return samples;
            } catch (BitstreamException | DecoderException | ArrayIndexOutOfBoundsException e) {
                throw new IOException("MP3 decoding failed", e);
            }
        }

        @Override
        public int rate() {
            return rate;
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public boolean read(FloatConsumer output) throws IOException {
            SampleBuffer samples = pending != null ? pending : next();
            pending = null;
            if (samples == null) {
                return false;
            }
            short[] buffer = samples.getBuffer();
            int length = samples.getBufferLength();
            for (int i = 0; i < length; i++) {
                output.accept(buffer[i] / 32768.0F);
            }
            return true;
        }

        @Override
        public void close() throws IOException {
            try {
                bitstream.close();
            } catch (BitstreamException e) {
                throw new IOException(e);
            }
        }
    }

    // The sound engine's stream: mono 16-bit at the source's rate.
    static final class MonoStream implements AudioStream {
        private final byte[] bytes;
        private final boolean loop;
        private final AudioFormat format;
        private final int channels;
        private final long maxFrames;
        private PcmSource source;
        private long toSkip, written, passFrames;
        private boolean ended;
        // The frame being mixed down.
        private float sum;
        private int filled;
        private @Nullable ByteBuffer target;

        MonoStream(byte[] bytes, double skipSeconds, boolean loop, double maxSeconds) throws IOException {
            this.bytes = bytes;
            this.loop = loop;
            this.source = open(bytes);
            this.channels = Math.max(1, source.channels());
            this.format = new AudioFormat(source.rate(), 16, 1, true, false);
            this.toSkip = (long) (Math.max(0, skipSeconds) * source.rate());
            this.maxFrames = loop ? Long.MAX_VALUE : (long) (Math.max(0, maxSeconds) * source.rate());
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        private void sample(float value) {
            sum += value;
            if (++filled < channels) {
                return;
            }
            float mono = sum / channels;
            sum = 0;
            filled = 0;
            passFrames++;
            if (toSkip > 0) {
                toSkip--;
                return;
            }
            if (target != null && target.remaining() >= 2 && written < maxFrames) {
                target.putShort((short) Math.clamp((int) (mono * 32767.5F - 0.5F), -32768, 32767));
                written++;
            }
        }

        @Override
        public ByteBuffer read(int expectedSize) throws IOException {
            ByteBuffer buffer = BufferUtils.createByteBuffer(expectedSize + 65536);
            target = buffer;
            while (!ended && buffer.position() < expectedSize && written < maxFrames) {
                if (source.read(this::sample)) {
                    continue;
                }
                // At the end: looped, again from the top - unless that pass gave nothing.
                if (!loop || passFrames == 0) {
                    ended = true;
                    break;
                }
                source.close();
                source = open(bytes);
                sum = 0;
                filled = 0;
                passFrames = 0;
            }
            target = null;
            buffer.flip();
            return buffer;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
