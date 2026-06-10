package me.codeleep.victor.core.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL jsonb TypeHandler
 * 通过 TypeReference 保留泛型信息，正确反序列化 List&lt;T&gt;、Map&lt;K,V&gt; 等参数化类型
 *
 * <p>用法（无参构造，兼容已有 @TableField 注解）：</p>
 * <pre>{@code
 * @TableField(typeHandler = JsonbTypeHandler.class)
 * private Map<String, Object> extra;
 * }</pre>
 *
 * <p>用法（指定泛型，适用于 List&lt;T&gt; 等参数化类型）：</p>
 * <pre>{@code
 * @TableField(typeHandler = JsonbTypeHandler.class)
 * private List<TeamMemberInfo> members;
 * }</pre>
 */
@Slf4j
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TypeReference<T> typeReference;

    public JsonbTypeHandler() {
        this.typeReference = new TypeReference<>() {};
    }

    public JsonbTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize object to JSON", e);
        }
        ps.setObject(i, pgObject);
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private T parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.warn("JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
