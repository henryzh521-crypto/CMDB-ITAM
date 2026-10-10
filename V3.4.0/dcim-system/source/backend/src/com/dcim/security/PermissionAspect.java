package com.dcim.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {
    private final RbacService rbacService;

    public PermissionAspect(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Around("@annotation(requirePerm)")
    public Object check(ProceedingJoinPoint pjp, RequirePerm requirePerm) throws Throwable {
        LoginUser user = CurrentUser.get();
        boolean allowed = requirePerm.write()
            ? this.rbacService.canWrite(user, requirePerm.module(), requirePerm.action())
            : this.rbacService.canRead(user, requirePerm.module());
        if (!allowed) {
            if (requirePerm.write() && "delete".equals(requirePerm.action()) && user != null && this.rbacService.canWrite(user, requirePerm.module(), "")) {
                throw com.dcim.common.BusinessException.forbidden("当前角色未授予「" + this.rbacService.moduleName(requirePerm.module()) + "」的删除权限，请联系管理员");
            }
            throw new AccessDeniedException("没有执行该操作的权限");
        } else {
            if (!requirePerm.action().isEmpty()) {
                this.rbacService.assertAction(user, requirePerm.module(), requirePerm.action());
            }

            return pjp.proceed();
        }
    }
}
