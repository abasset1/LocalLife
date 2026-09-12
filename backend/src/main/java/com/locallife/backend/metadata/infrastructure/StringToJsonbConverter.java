package com.locallife.backend.metadata.infrastructure;

import com.locallife.backend.metadata.domain.JsonbData;
import java.sql.SQLException;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Convertisseur d'écriture JSON texte → colonne {@code jsonb} (LL-11009)
 * — voir {@code docs/02_Architecture/ADR-0004-metadata-jsonb.md}.
 * {@link PGobject} est la façon standard (pilote JDBC PostgreSQL) de
 * indiquer explicitement le type de colonne cible ; sans ce
 * convertisseur, Spring Data JDBC enverrait le texte comme
 * {@code varchar}, que PostgreSQL refuse d'assigner directement à une
 * colonne {@code jsonb} (erreur de type au niveau SQL).
 */
@WritingConverter
public class StringToJsonbConverter implements Converter<JsonbData, PGobject> {

    @Override
    public PGobject convert(JsonbData source) {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(source.value());
        } catch (SQLException exception) {
            // Ne peut arriver en pratique : ActivityMetadataService#create valide déjà que
            // `source` est un JSON syntaxiquement correct avant d'atteindre ce convertisseur.
            throw new IllegalStateException("JSON invalide pour une colonne jsonb : " + source.value(), exception);
        }
        return jsonObject;
    }
}
