package com.locallife.backend.metadata.infrastructure;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Convertisseur de lecture colonne {@code jsonb} → JSON texte (LL-11009)
 * — pendant de {@link StringToJsonbConverter}, voir
 * {@code docs/02_Architecture/ADR-0004-metadata-jsonb.md}.
 */
@ReadingConverter
public class JsonbToStringConverter implements Converter<PGobject, String> {

    @Override
    public String convert(PGobject source) {
        return source.getValue();
    }
}
