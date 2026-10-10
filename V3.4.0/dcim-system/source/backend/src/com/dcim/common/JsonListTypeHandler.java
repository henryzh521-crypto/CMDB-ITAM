package com.dcim.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes({List.class})
@MappedJdbcTypes({JdbcType.OTHER})
public class JsonListTypeHandler extends BaseTypeHandler<List<Object>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> TYPE = new TypeReference<List<Object>>() {
    };

    public void setNonNullParameter(PreparedStatement ps, int i, List<Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setObject(i, MAPPER.writeValueAsString(parameter), 1111);
        } catch (JsonProcessingException var6) {
            throw new SQLException("JSONB 数组序列化失败", var6);
        }
    }

    public List<Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.parse(rs.getString(columnName));
    }

    public List<Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.parse(rs.getString(columnIndex));
    }

    public List<Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.parse(cs.getString(columnIndex));
    }

    private List<Object> parse(String json) {
        if (json != null && !json.isBlank()) {
            try {
                return (List<Object>)MAPPER.readValue(json, TYPE);
            } catch (Exception var3) {
                return new ArrayList<>();
            }
        } else {
            return new ArrayList<>();
        }
    }
}
