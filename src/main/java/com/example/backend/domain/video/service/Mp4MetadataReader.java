package com.example.backend.domain.video.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
@Component
public class Mp4MetadataReader {

    public record MediaInfo(
            Integer durationSeconds,
            OffsetDateTime recordedAt
    ) {}

    private static final MediaInfo EMPTY =
            new MediaInfo(null, null);

    private static final int MOOV = 0x6d6f6f76;
    private static final int MVHD = 0x6d766864;

    private static final OffsetDateTime MP4_EPOCH =
            OffsetDateTime.of(
                    1904, 1, 1, 0, 0, 0, 0,
                    ZoneOffset.UTC
            );

    public MediaInfo read(MultipartFile file) {
        try (DataInputStream input =
                     new DataInputStream(file.getInputStream())) {
            return readBoxes(input, file.getSize(), false);
        } catch (IOException | RuntimeException exception) {
            return EMPTY;
        }
    }

    private MediaInfo readBoxes(
            DataInputStream input,
            long remaining,
            boolean insideMoov
    ) throws IOException {
        while (remaining >= 8) {
            long size = Integer.toUnsignedLong(input.readInt());
            int type = input.readInt();
            long headerSize = 8;

            if (size == 1) {
                if (remaining < 16) {
                    return EMPTY;
                }
                size = input.readLong();
                headerSize = 16;
            } else if (size == 0) {
                size = remaining;
            }

            if (size < headerSize || size > remaining) {
                return EMPTY;
            }

            long payloadSize = size - headerSize;

            if (!insideMoov && type == MOOV) {
                return readBoxes(input, payloadSize, true);
            }

            if (insideMoov && type == MVHD) {
                return readMovieHeader(input, payloadSize);
            }

            input.skipNBytes(payloadSize);
            remaining -= size;
        }

        return EMPTY;
    }

    private MediaInfo readMovieHeader(
            DataInputStream input,
            long payloadSize
    ) throws IOException {
        if (payloadSize < 4) {
            return EMPTY;
        }

        int version = input.readUnsignedByte();
        input.skipNBytes(3);

        long creation;
        long timescale;
        long duration;

        if (version == 0 && payloadSize >= 20) {
            creation = Integer.toUnsignedLong(input.readInt());
            input.readInt();
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = Integer.toUnsignedLong(input.readInt());

            if (duration == 0xffffffffL) {
                duration = -1;
            }
        } else if (version == 1 && payloadSize >= 32) {
            creation = input.readLong();
            input.readLong();
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = input.readLong();
        } else {
            return EMPTY;
        }

        Integer seconds = null;

        if (timescale > 0 && duration > 0) {
            double value = Math.ceil((double) duration / timescale);

            if (value <= Integer.MAX_VALUE) {
                seconds = (int) value;
            }
        }

        OffsetDateTime recordedAt = null;

        if (creation > 0) {
            try {
                OffsetDateTime candidate =
                        MP4_EPOCH.plusSeconds(creation);

                if (candidate.getYear() >= 1970
                        && !candidate.isAfter(
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
                )) {
                    recordedAt = candidate;
                }
            } catch (RuntimeException ignored) {
            }
        }

        return new MediaInfo(seconds, recordedAt);
    }
}