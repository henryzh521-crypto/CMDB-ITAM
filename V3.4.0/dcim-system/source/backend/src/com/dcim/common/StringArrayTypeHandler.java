package com.dcim.common;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        Array array = ps.getConnection().createArrayOf("text", parameter.toArray(new String[0]));
        ps.setArray(i, array);
    }

    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.toList(rs.getArray(columnName));
    }

    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.toList(rs.getArray(columnIndex));
    }

    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.toList(cs.getArray(columnIndex));
    }

    private List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return null;
        } else {
            Object raw = array.getArray();
            if (raw instanceof String[] s) {
                return new ArrayList<>(Arrays.asList(s));
            } else {
                List<String> out = new ArrayList<>();
                if (raw instanceof Object[] objs) {
                    for (Object o : objs) {
                        out.add(o == null ? null : o.toString());
                    }
                }

                return out;
            }
        }
    }
}
