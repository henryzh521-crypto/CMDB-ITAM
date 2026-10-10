package com.dcim.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BusinessException.class})
    public ResponseEntity<ApiResult<Void>> business(BusinessException e) {
        return ResponseEntity.status(HttpStatus.valueOf(e.getCode())).body(ApiResult.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResult<Void>> invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResult.error(400, msg));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResult<Void>> missingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, "缺少参数：" + e.getParameterName()));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiResult<Void>> maxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, "文件大小超过限制（单文件最大 50MB）"));
    }

    @ExceptionHandler({MultipartException.class})
    public ResponseEntity<ApiResult<Void>> multipart(MultipartException e) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, "上传解析失败：请求体超过限制或格式不正确"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class})
    public ResponseEntity<ApiResult<Void>> missingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, "缺少上传文件：" + e.getRequestPartName()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResult<Void>> notReadable(HttpMessageNotReadableException e) {
        String msg = "请求体格式错误";
        if (e.getCause() instanceof JsonMappingException jme) {
            List<Reference> refs = jme.getPath();
            if (refs != null && !refs.isEmpty()) {
                String field = String.valueOf(refs.get(refs.size() - 1).getFieldName());
                msg = "字段 " + field + " 格式不正确";
            }
        }

        return ResponseEntity.badRequest().body(ApiResult.error(400, msg));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, "参数 " + e.getName() + " 类型不正确"));
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiResult<Void>> mediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResult.error(415, "不支持的内容类型"));
    }

    @ExceptionHandler({NoResourceFoundException.class})
    public ResponseEntity<ApiResult<Void>> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.error(404, "资源不存在"));
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResult<Void>> methodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResult.error(405, "请求方法不支持"));
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ApiResult<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(ApiResult.error(403, "没有执行该操作的权限"));
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<ApiResult<Void>> auth(AuthenticationException e) {
        return ResponseEntity.status(401).body(ApiResult.error(401, "未登录或会话已过期"));
    }

    /**
     * 数据库约束冲突（v3.3）：并发重复提交、唯一键冲突、外键引用等不再落入 500。
     * 典型场景：两人同时录入同一 SN，此前先到者成功、其余全部“服务器内部错误”。
     */
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<ApiResult<Void>> integrity(org.springframework.dao.DataIntegrityViolationException e) {
        String state = sqlState(e);
        String detail = pgDetail(e);
        if ("23505".equals(state) || e instanceof org.springframework.dao.DuplicateKeyException) {
            log.info("[db] 唯一约束冲突：{}", detail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.error(409, "数据已存在" + friendlyKey(detail) + "，请刷新后重试"));
        }
        if ("23503".equals(state)) {
            log.info("[db] 外键约束冲突：{}", detail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.error(409, "存在关联数据或引用的数据不存在，操作未执行"));
        }
        if ("23514".equals(state) || "23502".equals(state) || "22001".equals(state) || "22P02".equals(state)) {
            log.info("[db] 数据校验失败 {}：{}", state, detail);
            return ResponseEntity.badRequest().body(ApiResult.error(400, "数据不符合约束（取值越界、超长或必填项为空）"));
        }
        log.error("数据完整性异常 [{}]", requestId(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.error(409, "数据冲突，请刷新后重试"));
    }

    /** 行锁 / 死锁 / 语句超时：提示重试而不是 500。 */
    @ExceptionHandler({
        org.springframework.dao.PessimisticLockingFailureException.class,
        org.springframework.dao.QueryTimeoutException.class,
        org.springframework.dao.TransientDataAccessResourceException.class,
        org.springframework.jdbc.CannotGetJdbcConnectionException.class,
        org.springframework.transaction.CannotCreateTransactionException.class
    })
    public ResponseEntity<ApiResult<Void>> busy(Exception e) {
        log.warn("[db] 并发冲突或超时 [{}]：{}", requestId(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "3")
            .body(ApiResult.error(503, "系统繁忙（数据正被其他操作占用或查询超时），请稍后重试"));
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResult<Void>> optimistic(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.error(409, "数据已被他人修改，请刷新后重试"));
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<ApiResult<Void>> unknown(Exception e) {
        String rid = requestId();
        log.error("未处理异常 [{}]", rid, e);
        // 返回请求号，用户反馈时可据此在日志中精确定位
        return ResponseEntity.internalServerError().body(ApiResult.error(500, "服务器内部错误" + (rid == null ? "" : "（请求号 " + rid + "）")));
    }

    private static String requestId() {
        return org.slf4j.MDC.get("requestId");
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException se && se.getSQLState() != null) {
                return se.getSQLState();
            }
        }
        return null;
    }

    private static String pgDetail(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.postgresql.util.PSQLException pe && pe.getServerErrorMessage() != null) {
                String d = pe.getServerErrorMessage().getDetail();
                return d == null ? pe.getServerErrorMessage().getConstraint() : d;
            }
        }
        return null;
    }

    /** "Key (sn)=(ABC) already exists." → "（sn=ABC）" */
    static String friendlyKey(String detail) {
        if (detail == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Key \\((.+?)\\)=\\((.*)\\) already exists").matcher(detail);
        if (m.find()) {
            String v = m.group(2);
            if (v.length() > 60) {
                v = v.substring(0, 60) + "…";
            }
            return "（" + m.group(1) + "=" + v + "）";
        }
        return "";
    }
}
