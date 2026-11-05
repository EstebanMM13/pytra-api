package com.estebanmm13.pytra_api.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Every {@link LocalDateTime} the API stores is UTC (the JVM is pinned to UTC at startup), so on the wire it
 * is an instant: written with a {@code Z} so clients never read it as their own local time. Input accepts
 * the same form, a bare local date-time (taken as UTC) or any explicit offset (converted to UTC).
 */
public class UtcDateTimeModule extends SimpleModule {

    public UtcDateTimeModule() {
        super("UtcDateTimeModule");
        addSerializer(LocalDateTime.class, new Serializer());
        addDeserializer(LocalDateTime.class, new Deserializer());
    }

    private static final class Serializer extends ValueSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    private static final class Deserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException noOffset) {
                try {
                    return OffsetDateTime.parse(trimmed).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                } catch (DateTimeParseException invalid) {
                    return (LocalDateTime) ctxt.handleWeirdStringValue(LocalDateTime.class, trimmed,
                            "expected an ISO-8601 date-time");
                }
            }
        }
    }
}
