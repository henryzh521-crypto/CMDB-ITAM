package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.system.entity.NotificationEntity;
import com.dcim.system.mapper.NotificationMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/notifications"})
public class NotificationController {
    private final NotificationMapper notificationMapper;

    @GetMapping({"/my"})
    public ApiResult<PageResult<Map<String, Object>>> my(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @RequestParam(required = false) Boolean unread
    ) {
        // 修复：size 未收敛上限时传超大值会拉取全量通知（仿 UserController.list 的 1..200 收敛）
        Page<NotificationEntity> p = new Page(page, Math.min(Math.max(size, 1L), 200L));
        this.notificationMapper.selectPage(p, this.buildWrapper(unread));
        return ApiResult.ok(PageResult.of(p, NotificationController::toRow));
    }

    @GetMapping({"/unread-count"})
    public ApiResult<Map<String, Object>> unreadCount() {
        Long count = this.notificationMapper
            .selectCount(
                (Wrappers.<NotificationEntity>lambdaQuery().eq(NotificationEntity::getUsername, this.currentUser()))
                    .eq(NotificationEntity::getReadFlag, false)
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count == null ? 0L : count);
        return ApiResult.ok(out);
    }

    @PutMapping({"/{id}/read"})
    @Transactional
    public ApiResult<Void> markRead(@PathVariable Long id) {
        NotificationEntity n = this.requireMine(id);
        if (!Boolean.TRUE.equals(n.getReadFlag())) {
            n.setReadFlag(true);
            this.notificationMapper.updateById(n);
        }

        return ApiResult.ok();
    }

    @PutMapping({"/read-all"})
    @Transactional
    public ApiResult<Void> readAll() {
        this.notificationMapper
            .update(
                null,
                ((Wrappers.<NotificationEntity>lambdaUpdate().eq(NotificationEntity::getUsername, this.currentUser()))
                        .eq(NotificationEntity::getReadFlag, false))
                    .set(NotificationEntity::getReadFlag, true)
            );
        return ApiResult.ok();
    }

    private LambdaQueryWrapper<NotificationEntity> buildWrapper(Boolean unread) {
        LambdaQueryWrapper<NotificationEntity> qw = (LambdaQueryWrapper<NotificationEntity>)Wrappers.<NotificationEntity>lambdaQuery()
            .eq(NotificationEntity::getUsername, this.currentUser());
        if (Boolean.TRUE.equals(unread)) {
            qw.eq(NotificationEntity::getReadFlag, false);
        }

        (qw.orderByDesc(NotificationEntity::getCreatedAt)).orderByDesc(NotificationEntity::getId);
        return qw;
    }

    private NotificationEntity requireMine(Long id) {
        NotificationEntity n = (NotificationEntity)this.notificationMapper.selectById(id);
        if (n != null && this.currentUser().equals(n.getUsername())) {
            return n;
        } else {
            throw BusinessException.notFound("通知不存在");
        }
    }

    private String currentUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u.username();
        }
    }

    private static Map<String, Object> toRow(NotificationEntity n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("username", n.getUsername());
        row.put("type", n.getType());
        row.put("title", n.getTitle());
        row.put("content", n.getContent());
        row.put("refId", n.getRefId());
        row.put("readFlag", n.getReadFlag());
        row.put("createdAt", n.getCreatedAt());
        return row;
    }

    
    public NotificationController(final NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }
}
