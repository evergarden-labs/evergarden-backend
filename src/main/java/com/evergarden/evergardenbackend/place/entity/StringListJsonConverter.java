package com.evergarden.evergardenbackend.place.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * {@code List<String>}을 JSON 배열 문자열 컬럼 하나로 저장한다({@code Place.imageUrls}).
 * 사진 URL 목록 하나 저장하려고 별도 테이블을 두기엔 과하다 — 목록 크기가 작고
 * (관광 사진 몇 장), 다른 테이블이 이 목록을 조인해서 쓸 일이 없다.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이미지 URL 목록을 JSON으로 바꾸지 못했다", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 이미지 URL 목록을 읽지 못했다: " + dbData, e);
        }
    }
}
