package com.dcim.workorder.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.file.FileStorage;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.workorder.entity.AttachmentEntity;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.mapper.AttachmentMapper;
import com.dcim.workorder.mapper.WorkorderMapper;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkorderAttachmentService {
    
    private static final Logger log = LoggerFactory.getLogger(WorkorderAttachmentService.class);
    public static final String OWNER_WORKORDER = "workorder";
    static final long MAX_SIZE = 52428800L;
    private static final Set<String> OK_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "zip", "rar", "doc", "docx", "xls", "xlsx", "txt");
    private static final Set<String> OK_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "application/pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "text/txt",
        "application/octet-stream"
    );
    private final AttachmentMapper attachmentMapper;
    private final WorkorderMapper workorderMapper;
    private final FileStorage fileStorage;
    private final WorkorderService workorderService;

    public Map<String, Object> upload(String woNo, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 52428800L) {
                throw BusinessException.badRequest("附件大小不能超过 50MB");
            } else {
                String original = sanitizeName(file.getOriginalFilename());
                String ext = extOf(original);
                if (ext != null && OK_EXT.contains(ext)) {
                    String ct = file.getContentType();
                    if (ct != null && !ct.isBlank() && !OK_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
                        throw BusinessException.badRequest("不支持的内容类型：" + ct);
                    } else {
                        this.workorderService.requireUploadableWo(woNo);
                        String storeCt = ct != null && !ct.isBlank() ? ct : "application/octet-stream";
                        String key = "workorder/" + woNo + "/" + UUID.randomUUID() + "." + ext;

                        try (InputStream in = file.getInputStream()) {
                            this.fileStorage.store(key, in, file.getSize(), storeCt);
                        } catch (BusinessException var16) {
                            throw var16;
                        } catch (Exception var17) {
                            log.error("附件存储失败 key={}", key, var17);
                            throw new BusinessException(500, "附件存储失败");
                        }

                        LoginUser user = CurrentUser.get();
                        AttachmentEntity row = new AttachmentEntity();
                        row.setOwnerType("workorder");
                        row.setOwnerId(woNo);
                        row.setOriginalName(original);
                        row.setContentType(storeCt);
                        row.setSizeBytes(file.getSize());
                        row.setStorageKey(key);
                        row.setUploadedBy(user == null ? null : user.username());
                        row.setCreatedAt(LocalDateTime.now());

                        try {
                            this.attachmentMapper.insert(row);
                        } catch (Exception var14) {
                            try {
                                this.fileStorage.delete(key);
                            } catch (Exception var12) {
                                log.warn("附件元数据落库失败后清理对象失败 key={}", key, var12);
                            }

                            throw var14;
                        }

                        return toRow(row);
                    }
                } else {
                    throw BusinessException.badRequest(
                        "不支持的附件类型：" + (ext == null ? "无扩展名" : "." + ext) + "（仅允许 jpg/jpeg/png/gif/webp/pdf/zip/rar/doc/docx/xls/xlsx/txt）"
                    );
                }
            }
        } else {
            throw BusinessException.badRequest("附件文件不能为空");
        }
    }

    public List<Map<String, Object>> list(String woNo) {
        this.requireWo(woNo);
        return this.attachmentMapper
            .selectList(
                ((Wrappers.<AttachmentEntity>lambdaQuery().eq(AttachmentEntity::getOwnerType, "workorder"))
                        .eq(AttachmentEntity::getOwnerId, woNo))
                    .orderByAsc(AttachmentEntity::getId)
            )
            .stream()
            .map(WorkorderAttachmentService::toRow)
            .toList();
    }

    public ResponseEntity<Resource> download(String woNo, Long id) {
        this.requireWo(woNo);
        AttachmentEntity a = this.requireAttachment(woNo, id);

        InputStream in;
        try {
            in = this.fileStorage.open(a.getStorageKey());
        } catch (Exception var8) {
            log.error("附件读取失败 id={} key={}", new Object[]{id, a.getStorageKey(), var8});
            throw new BusinessException(500, "附件读取失败");
        }

        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (a.getContentType() != null && !a.getContentType().isBlank()) {
            try {
                mt = MediaType.parseMediaType(a.getContentType());
            } catch (Exception var7) {
            }
        }

        String encoded = URLEncoder.encode(a.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ((BodyBuilder)ResponseEntity.ok().header("Content-Disposition", new String[]{"attachment; filename*=UTF-8''" + encoded}))
            .contentType(mt)
            .contentLength(a.getSizeBytes())
            .body(new InputStreamResource(in));
    }

    public void delete(String woNo, Long id) {
        // v3.5：删除附件同样走终态检查（已完成/已关闭冻结，409 提示）
        this.workorderService.requireUploadableWo(woNo);
        AttachmentEntity a = this.requireAttachment(woNo, id);
        LoginUser user = CurrentUser.get();
        if (user == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else if (!user.isAdmin() && !user.username().equals(a.getUploadedBy())) {
            throw BusinessException.forbidden("只能删除自己上传的附件");
        } else {
            this.attachmentMapper.deleteById(a.getId());

            try {
                this.fileStorage.delete(a.getStorageKey());
            } catch (Exception var6) {
                log.warn("附件对象删除失败 key={}", a.getStorageKey(), var6);
            }
        }
    }

    private WorkorderEntity requireWo(String woNo) {
        // v3.5：复用工单数据范围校验（SELF/DEPT），不可见与不存在同样按 notFound 处理，避免泄露工单存在性
        return this.workorderService.requireWo(woNo);
    }

    private AttachmentEntity requireAttachment(String woNo, Long id) {
        AttachmentEntity a = (AttachmentEntity)this.attachmentMapper.selectById(id);
        if (a != null && "workorder".equals(a.getOwnerType()) && woNo.equals(a.getOwnerId())) {
            return a;
        } else {
            throw BusinessException.notFound("附件不存在：" + id);
        }
    }

    private static String sanitizeName(String raw) {
        if (raw != null && !raw.isBlank()) {
            String name = raw.replace('\\', '/');
            int slash = name.lastIndexOf(47);
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }

            name = name.replaceAll("[\\p{Cntrl}]", "").trim();
            if (name.isEmpty()) {
                return "file";
            } else {
                if (name.length() > 200) {
                    name = name.substring(name.length() - 200);
                }

                return name;
            }
        } else {
            return "file";
        }
    }

    private static String extOf(String filename) {
        int dot = filename.lastIndexOf(46);
        if (dot >= 0 && dot != filename.length() - 1) {
            String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
            return ext.matches("[a-z0-9]{1,10}") ? ext : null;
        } else {
            return null;
        }
    }

    private static Map<String, Object> toRow(AttachmentEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("originalName", a.getOriginalName());
        m.put("contentType", a.getContentType());
        m.put("sizeBytes", a.getSizeBytes());
        m.put("uploadedBy", a.getUploadedBy());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    
    public WorkorderAttachmentService(
        final AttachmentMapper attachmentMapper, final WorkorderMapper workorderMapper, final FileStorage fileStorage, final WorkorderService workorderService
    ) {
        this.attachmentMapper = attachmentMapper;
        this.workorderMapper = workorderMapper;
        this.fileStorage = fileStorage;
        this.workorderService = workorderService;
    }
}
