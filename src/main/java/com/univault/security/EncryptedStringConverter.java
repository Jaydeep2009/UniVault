package com.univault.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that transparently encrypts a String field on the way into
 * the database and decrypts it on the way out.
 *
 * autoApply is left false (the default) so this only applies to fields
 * explicitly annotated with @Convert(converter = EncryptedStringConverter.class) —
 * we don't want every String column in the app silently encrypted.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return AesGcmService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return AesGcmService.decrypt(dbData);
    }
}