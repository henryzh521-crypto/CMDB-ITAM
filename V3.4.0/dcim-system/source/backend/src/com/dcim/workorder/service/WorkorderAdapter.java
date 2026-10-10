package com.dcim.workorder.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.workorder.WorkorderPort;
import com.dcim.security.LoginUser;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.mapper.WorkorderMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkorderAdapter implements WorkorderPort {
    private final WorkorderService workorderService;
    private final WorkorderMapper workorderMapper;

    @Override
    public boolean hasOpenWoByTitle(String title) {
        Long n = this.workorderMapper
            .selectCount(
                (Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getTitle, title))
                    .notIn(WorkorderEntity::getStatus, List.of("已完成", "已关闭"))
            );
        return n != null && n > 0L;
    }

    @Transactional
    @Override
    public String createSystemWo(String woType, String title, String description, String priority, List<String> relatedAssets, List<Object> details) {
        LoginUser system = new LoginUser("system", "系统", false, List.of("ADMIN"), true, Map.of(), Map.of(), Map.of(), Map.of());
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(system, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        String var9;
        try {
            WorkorderEntity wo = this.workorderService
                .create(woType, title, description, priority, toLegacyLines(relatedAssets), null, null, null, null, null, null, details, null, null, null, null);
            var9 = wo.getWoNo();
        } finally {
            SecurityContextHolder.clearContext();
        }

        return var9;
    }

    private static List<WoAssetLineInput> toLegacyLines(List<String> relatedAssets) {
        if (relatedAssets != null && !relatedAssets.isEmpty()) {
            List<WoAssetLineInput> out = new ArrayList<>();

            for (String a : relatedAssets) {
                if (a != null && !a.isBlank()) {
                    WoAssetLineInput line = new WoAssetLineInput();
                    line.setAssetNo(a.trim());
                    out.add(line);
                }
            }

            return out;
        } else {
            return List.of();
        }
    }

    
    public WorkorderAdapter(final WorkorderService workorderService, final WorkorderMapper workorderMapper) {
        this.workorderService = workorderService;
        this.workorderMapper = workorderMapper;
    }
}
