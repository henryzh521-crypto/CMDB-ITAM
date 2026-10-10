package com.dcim.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes({Map.class})
@MappedJdbcTypes({JdbcType.OTHER})
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<Map<String, Object>>() {
    };

    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setObject(i, MAPPER.writeValueAsString(parameter), 1111);
        } catch (JsonProcessingException var6) {
            throw new SQLException("JSONB 序列化失败", var6);
        }
    }

    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.parse(rs.getString(columnName));
    }

    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.parse(rs.getString(columnIndex));
    }

    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) {
        if (json != null && !json.isBlank()) {
            try {
                return (Map<String, Object>)MAPPER.readValue(json, TYPE);
            } catch (Exception var3) {
                return new HashMap<>();
            }
        } else {
            return new HashMap<>();
        }
    }
}
