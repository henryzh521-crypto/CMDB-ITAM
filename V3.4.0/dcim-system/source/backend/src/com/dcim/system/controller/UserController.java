package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.csv.CsvSupport;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.LoginUserLoader;
import com.dcim.security.PasswordPolicy;
import com.dcim.security.RbacService;
import com.dcim.security.PrivilegeGuard;
import com.dcim.security.RequirePerm;
import com.dcim.security.TokenRevocationService;
import com.dcim.apitoken.ApiTokenService;
import com.dcim.security.TokenStore;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.RoleEntity;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.entity.UserRoleEntity;
import com.dcim.system.mapper.RoleMapper;
import com.dcim.system.mapper.UserMapper;
import com.dcim.system.mapper.UserRoleMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/users"})
public class UserController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.dcim.security.LoginThrottle loginThrottle;
    @org.springframework.beans.factory.annotation.Value("${dcim.auth.login-max-fail-per-account-global:30}")
    private int loginMaxFailPerAccountGlobal;
    @org.springframework.beans.factory.annotation.Value("${dcim.auth.login-fail-window-minutes:15}")
    private int loginFailWindowMinutes;

    private static final String BUILTIN_ADMIN = "admin";
    private static final List<String> CSV_HEADERS = List.of("用户名", "姓名", "部门", "手机号", "邮箱", "角色", "状态", "初始密码");
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final PrivilegeGuard privilegeGuard;
    private final TokenRevocationService revocation;
    private final ApiTokenService apiTokenService;
    private final RbacService rbacService;
    private final LoginUserLoader loginUserLoader;
    private final TransactionTemplate rowTx;

    @GetMapping
    @RequirePerm(
        module = "system"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @RequestParam(required = false) String keyword
    ) {
        // 修复：size 未设上限时传超大值会触发全表查询，与 RoleController 一致做 1..200 收敛
        Page<UserEntity> p = new Page(page, Math.min(Math.max(size, 1L), 200L));
        LambdaQueryWrapper<UserEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> ((w.like(UserEntity::getUsername, keyword)).or()).like(UserEntity::getName, keyword));
        }

        qw.orderByAsc(UserEntity::getId);
        this.userMapper.selectPage(p, qw);
        List<Long> ids = p.getRecords().stream().map(UserEntity::getId).toList();
        Map<Long, List<String>> roleMap = new HashMap<>();
        if (!ids.isEmpty()) {
            this.userRoleMapper
                .selectList(Wrappers.<UserRoleEntity>lambdaQuery().in(UserRoleEntity::getUserId, ids))
                .forEach(ur -> roleMap.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleCode()));
        }

        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.getUsername());
            row.put("name", u.getName());
            row.put("department", u.getDepartment());
            row.put("phone", u.getPhone());
            row.put("email", u.getEmail());
            row.put("wecomUserid", u.getWecomUserid());
            row.put("authSource", u.getAuthSource());
            row.put("status", u.getStatus());
            row.put("mustChangePwd", u.getMustChangePwd());
            row.put("roles", roleMap.getOrDefault(u.getId(), List.of()));
            row.put("createdAt", u.getCreatedAt());
            // v3.4：账号级登录锁定状态（跨 IP 多次失败），管理员可解锁
            boolean locked = this.loginThrottle.isAccountLocked(u.getUsername(), this.loginMaxFailPerAccountGlobal,
                java.time.Duration.ofMinutes(Math.max(1, this.loginFailWindowMinutes)));
            row.put("loginLocked", locked);
            if (locked) {
                row.put("loginLockedSeconds", this.loginThrottle.accountLockSeconds(u.getUsername()));
            }
            return row;
        }).toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), records));
    }

    @PostMapping
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "CREATE",
        module = "system",
        objType = "user",
        objIdSpEL = "#req.username"
    )
    @Transactional
    public ApiResult<Void> create(@RequestBody @Valid UserController.CreateUserReq req) {
        this.privilegeGuard.assertCanGrantRoles(CurrentUser.get(), req.getRoleCodes());
        PasswordPolicy.validate(req.getPassword(), req.getUsername());
        if (this.selectByUsername(req.getUsername()) != null) {
            throw BusinessException.conflict("用户名已存在");
        } else {
            UserEntity user = new UserEntity();
            user.setUsername(req.getUsername());
            user.setName(req.getName());
            user.setPasswordHash(this.passwordEncoder.encode(req.getPassword()));
            user.setDepartment(req.getDepartment());
            user.setPhone(req.getPhone());
            user.setEmail(req.getEmail());
            user.setStatus(1);
            user.setMustChangePwd(true);
            this.userMapper.insert(user);
            this.bindRoles(user.getId(), req.getRoleCodes());
            this.loginUserLoader.evict(req.getUsername());
            return ApiResult.ok();
        }
    }

    @PutMapping({"/{username}"})
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "UPDATE",
        module = "system",
        objType = "user",
        objIdSpEL = "#username"
    )
    @Transactional
    public ApiResult<Void> update(@PathVariable String username, @RequestBody @Valid UserController.UpdateUserReq req) {
        UserEntity user = this.selectByUsername(username);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        } else {
            LoginUser actor = CurrentUser.get();
            this.privilegeGuard.assertCanManageUser(actor, username);
            if (req.getRoleCodes() != null) {
                this.privilegeGuard.assertCanGrantRoles(actor, req.getRoleCodes());
            }
            if (actor != null && actor.username().equals(username) && req.getStatus() != null && req.getStatus() != 1) {
                throw BusinessException.badRequest("不能停用当前登录账号");
            }
            boolean revoke = false;
            // name/department/phone/email 统一语义（D4 修复）：null（字段未传）保留旧值，
            // 不再被误置 NULL；需清空由前端显式传空串 ""（MyBatis-Plus updateById 跳过
            // null 字段、写入空串，两种意图由此区分）
            if (req.getName() != null) {
                user.setName(req.getName());
            }

            if (req.getDepartment() != null) {
                user.setDepartment(req.getDepartment());
            }
            if (req.getPhone() != null) {
                user.setPhone(req.getPhone());
            }
            if (req.getEmail() != null) {
                user.setEmail(req.getEmail());
            }
            String newWecom = req.getWecomUserid() != null && !req.getWecomUserid().isBlank() ? req.getWecomUserid().trim() : null;
            if (newWecom != null) {
                UserEntity bound = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getWecomUserid, newWecom));
                if (bound != null && !bound.getId().equals(user.getId())) {
                    throw BusinessException.conflict("企微 userid 已绑定其它用户：" + newWecom);
                }
            }

            user.setWecomUserid(newWecom);
            if (req.getStatus() != null) {
                user.setStatus(req.getStatus());
            }

            if (req.getPassword() != null) {
                PasswordPolicy.validate(req.getPassword(), username);
                user.setPasswordHash(this.passwordEncoder.encode(req.getPassword()));
                user.setMustChangePwd(true);
                revoke = true;
            }
            if (req.getStatus() != null && req.getStatus() != 1) {
                revoke = true;
            }

            user.setUpdatedAt(LocalDateTime.now());
            this.userMapper.updateById(user);
            if (newWecom == null) {
                this.userMapper
                    .update(
                        null, (Wrappers.<UserEntity>lambdaUpdate().eq(UserEntity::getId, user.getId())).set(UserEntity::getWecomUserid, null)
                    );
            }

            if (req.getRoleCodes() != null) {
                this.userRoleMapper.delete(Wrappers.<UserRoleEntity>lambdaQuery().eq(UserRoleEntity::getUserId, user.getId()));
                this.bindRoles(user.getId(), req.getRoleCodes());
            }

            if (revoke) {
                // 重置密码 / 停用：该账号此前签发的全部会话立即失效
                this.revocation.revokeAll(username, req.getPassword() != null ? "管理员重置密码" : "账号停用");
                // 修复：同步吊销个人访问令牌（PAT）。原实现仅在删除账号时吊销，
                // 重置密码/停用后旧 PAT 仍可继续调用 OpenAPI
                this.apiTokenService.revokeAllOf(username);
            } else {
                this.loginUserLoader.evict(username);
            }
            return ApiResult.ok();
        }
    }

    @DeleteMapping({"/{username}"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "system",
        objType = "user",
        objIdSpEL = "#username"
    )
    public ApiResult<Void> delete(@PathVariable String username) {
        LoginUser current = CurrentUser.get();
        if (current != null && current.username().equals(username)) {
            throw BusinessException.badRequest("不能删除当前登录账号");
        } else if ("admin".equals(username)) {
            throw BusinessException.forbidden("内置管理员账号不可删除");
        } else {
            UserEntity user = this.selectByUsername(username);
            if (user == null) {
                throw BusinessException.notFound("用户不存在");
            } else if (!this.rbacService.isAdmin(current)) {
                throw BusinessException.forbidden("仅管理员可删除用户");
            } else {
                this.userMapper.deleteById(user.getId());
                this.revocation.revokeAll(username, "账号删除");
                // 同名账号日后重建时不能继承旧账号的 API 令牌
                this.apiTokenService.revokeAllOf(username);
                return ApiResult.ok();
            }
        }
    }

    /** v3.4：解除账号级登录锁定（跨 IP 多次失败触发） */
    @PostMapping({"/{username}/unlock"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UNLOCK",
        module = "system",
        objType = "user",
        objIdSpEL = "#username"
    )
    public ApiResult<Map<String, Object>> unlock(@PathVariable String username) {
        if (this.selectByUsername(username) == null) {
            throw BusinessException.notFound("用户不存在：" + username);
        }
        this.privilegeGuard.assertCanManageUser(CurrentUser.get(), username);
        this.loginThrottle.unlockAccount(username);
        return ApiResult.ok(Map.of("username", username, "loginLocked", false));
    }

    @PutMapping({"/{username}/password"})
    @Audit(
        action = "CHANGE_PWD",
        module = "system",
        objType = "user",
        objIdSpEL = "#username"
    )
    public ApiResult<Void> changePassword(@PathVariable String username, @RequestBody @Valid UserController.ChangePwdReq req) {
        LoginUser current = CurrentUser.get();
        if (current != null && current.username().equals(username)) {
            UserEntity user = this.selectByUsername(username);
            if (user == null) {
                throw BusinessException.notFound("用户不存在");
            } else if ("LDAP".equals(user.getAuthSource())) {
                throw BusinessException.badRequest("目录（LDAP/AD）账号请在域控修改密码");
            } else if (!this.passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
                throw BusinessException.badRequest("原密码不正确");
            } else {
                PasswordPolicy.validate(req.getNewPassword(), username, req.getOldPassword());
                user.setPasswordHash(this.passwordEncoder.encode(req.getNewPassword()));
                user.setMustChangePwd(false);
                user.setUpdatedAt(LocalDateTime.now());
                this.userMapper.updateById(user);
                this.revocation.revokeAll(username, "本人修改密码");
                return ApiResult.ok();
            }
        } else {
            throw BusinessException.forbidden("只能修改本人密码");
        }
    }

    @GetMapping({"/export"})
    @RequirePerm(
        module = "system",
        action = "export"
    )
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        List<UserEntity> users = this.selectExportUsers(keyword);
        Map<Long, List<String>> roleMap = this.loadRolesByUserIds(users);
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');

        for (UserEntity u : users) {
            List<String> roles = roleMap.getOrDefault(u.getId(), List.of());
            List<Object> cells = Arrays.asList(
                u.getUsername(), u.getName(), u.getDepartment(), u.getPhone(), u.getEmail(), String.join(";", roles), u.getStatus(), ""
            );
            csv.append(CsvSupport.line(cells)).append('\n');
        }

        this.writeCsv(response, "用户-" + LocalDate.now() + ".csv", csv.toString());
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "system"
    )
    public void template(HttpServletResponse response) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');
        csv.append(CsvSupport.line(List.of("zhangsan", "张三", "运维部", "13800138000", "zhangsan@example.com", "REQUESTER;OPS_ENGINEER", "1", "YourNewPwd#2026")))
            .append('\n');
        this.writeCsv(response, "用户导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "system",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "system",
        objType = "user"
    )
    public ApiResult<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<List<String>> rows = CsvSupport.parse(text);
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                List<String> header = rows.get(0);
                String firstCell = header.isEmpty() ? "" : header.get(0).trim();
                boolean hasHeader = "用户名".equals(firstCell);
                Map<String, Integer> colIndex = new LinkedHashMap<>();
                if (hasHeader) {
                    for (int i = 0; i < header.size(); i++) {
                        colIndex.put(header.get(i).trim(), i);
                    }
                }

                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                Map<String, RoleEntity> rolesByCode = new HashMap<>();
                Map<String, RoleEntity> rolesByName = new HashMap<>();

                for (RoleEntity r : this.roleMapper.selectList(null)) {
                    rolesByCode.put(r.getRoleCode(), r);
                    rolesByName.putIfAbsent(r.getName(), r);
                }

                Set<String> fileUsernames = new HashSet<>();
                int success = 0;
                List<String> importedUsernames = new ArrayList<>();
                List<Map<String, Object>> errors = new ArrayList<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    int rowNo = (hasHeader ? 1 : 0) + i + 1;

                    try {
                        this.importUserRow(dataRows.get(i), colIndex, rolesByCode, rolesByName, fileUsernames);
                        success++;
                        String imported = blankToNull(cellAt(dataRows.get(i), colIndex.getOrDefault("用户名", 0)));
                        if (imported != null) {
                            importedUsernames.add(imported);
                        }
                    } catch (BusinessException var19) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", var19.getMessage());
                        errors.add(err);
                    } catch (DataIntegrityViolationException var20) {
                        Map<String, Object> errx = new LinkedHashMap<>();
                        errx.put("row", rowNo);
                        errx.put("reason", "数据写入冲突（可能已被并发导入）");
                        errors.add(errx);
                    }
                }

                importedUsernames.forEach(this.loginUserLoader::evict);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("total", dataRows.size());
                out.put("success", success);
                out.put("failed", errors.size());
                out.put("errors", errors);
                return ApiResult.ok(out);
            }
        } else {
            throw BusinessException.badRequest("请上传要导入的 CSV 文件");
        }
    }

    private List<UserEntity> selectExportUsers(String keyword) {
        LambdaQueryWrapper<UserEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> ((w.like(UserEntity::getUsername, keyword)).or()).like(UserEntity::getName, keyword));
        }

        qw.orderByAsc(UserEntity::getId);
        return this.userMapper.selectList(qw);
    }

    private Map<Long, List<String>> loadRolesByUserIds(List<UserEntity> users) {
        Map<Long, List<String>> roleMap = new HashMap<>();
        List<Long> ids = users.stream().map(UserEntity::getId).toList();
        if (!ids.isEmpty()) {
            this.userRoleMapper
                .selectList(Wrappers.<UserRoleEntity>lambdaQuery().in(UserRoleEntity::getUserId, ids))
                .forEach(ur -> roleMap.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleCode()));
        }

        return roleMap;
    }

    private void importUserRow(
        List<String> cells, Map<String, Integer> colIndex, Map<String, RoleEntity> rolesByCode, Map<String, RoleEntity> rolesByName, Set<String> fileUsernames
    ) {
        int[] ix = new int[]{
            col(colIndex, "用户名", 0),
            col(colIndex, "姓名", 1),
            col(colIndex, "部门", 2),
            col(colIndex, "手机号", 3),
            col(colIndex, "邮箱", 4),
            col(colIndex, "角色", 5),
            col(colIndex, "状态", 6),
            col(colIndex, "初始密码", 7)
        };
        String username = blankToNull(cellAt(cells, ix[0]));
        String name = blankToNull(cellAt(cells, ix[1]));
        String department = blankToNull(cellAt(cells, ix[2]));
        String phone = blankToNull(cellAt(cells, ix[3]));
        String email = blankToNull(cellAt(cells, ix[4]));
        String rolesCell = blankToNull(cellAt(cells, ix[5]));
        String statusCell = blankToNull(cellAt(cells, ix[6]));
        String password = blankToNull(cellAt(cells, ix[7]));
        if (username == null) {
            throw BusinessException.badRequest("用户名不能为空");
        } else if (name == null) {
            throw BusinessException.badRequest("姓名不能为空");
        } else if (password == null) {
            throw BusinessException.badRequest("初始密码不能为空");
        } else {
            PasswordPolicy.validate(password, username);
            if (this.selectByUsername(username) != null) {
                throw BusinessException.conflict("用户名已存在：" + username);
            } else if (fileUsernames.contains(username)) {
                throw BusinessException.conflict("用户名已存在（文件内重复）：" + username);
            } else {
                List<String> roleCodes = new ArrayList<>();
                if (rolesCell != null) {
                    for (String part : rolesCell.split("[;,|]")) {
                        String token = part.trim();
                        if (!token.isEmpty()) {
                            RoleEntity byCode = rolesByCode.get(token);
                            RoleEntity byName = rolesByName.get(token);
                            RoleEntity role = byCode != null ? byCode : byName;
                            if (role == null) {
                                throw BusinessException.badRequest("角色不存在：" + token);
                            }

                            if (!roleCodes.contains(role.getRoleCode())) {
                                roleCodes.add(role.getRoleCode());
                            }
                        }
                    }
                }

                this.privilegeGuard.assertCanGrantRoles(CurrentUser.get(), roleCodes);
                int status = parseStatus(statusCell);

                try {
                    this.rowTx.executeWithoutResult(statusTx -> {
                        UserEntity user = new UserEntity();
                        user.setUsername(username);
                        user.setName(name);
                        user.setPasswordHash(this.passwordEncoder.encode(password));
                        user.setDepartment(department);
                        user.setPhone(phone);
                        user.setEmail(email);
                        user.setStatus(status);
                        user.setMustChangePwd(true);
                        user.setCreatedAt(LocalDateTime.now());
                        user.setUpdatedAt(LocalDateTime.now());
                        this.userMapper.insert(user);
                        this.bindRoles(user.getId(), roleCodes);
                    });
                } catch (DuplicateKeyException var24) {
                    throw BusinessException.conflict("用户名已存在：" + username);
                }

                fileUsernames.add(username);
            }
        }
    }

    private static int parseStatus(String s) {
        if (s != null && !s.isBlank()) {
            String t = s.trim().toUpperCase();
            if ("0".equals(t) || "DISABLED".equals(t) || "停用".equals(t) || "禁用".equals(t)) {
                return 0;
            } else if (!"1".equals(t) && !"ENABLED".equals(t) && !"ACTIVE".equals(t) && !"启用".equals(t)) {
                throw BusinessException.badRequest("状态取值不正确（1/0 或 ENABLED/DISABLED）：" + s);
            } else {
                return 1;
            }
        } else {
            return 1;
        }
    }

    private static int col(Map<String, Integer> colIndex, String name, int fallback) {
        Integer i = colIndex.get(name);
        return i == null ? fallback : i;
    }

    private static String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : null;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private void writeCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private UserEntity selectByUsername(String username) {
        return (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
    }

    private void bindRoles(Long userId, List<String> roleCodes) {
        if (roleCodes != null && !roleCodes.isEmpty()) {
            roleCodes.stream().distinct().forEach(rc -> {
                UserRoleEntity ur = new UserRoleEntity();
                ur.setUserId(userId);
                ur.setRoleCode(rc);
                this.userRoleMapper.insert(ur);
            });
        }
    }

    
    public UserController(
        final UserMapper userMapper,
        final UserRoleMapper userRoleMapper,
        final RoleMapper roleMapper,
        final PasswordEncoder passwordEncoder,
        final TokenStore tokenStore,
        final RbacService rbacService,
        final LoginUserLoader loginUserLoader,
        final TransactionTemplate rowTx,
        final PrivilegeGuard privilegeGuard,
        final TokenRevocationService revocation,
        final ApiTokenService apiTokenService
    ) {
        this.privilegeGuard = privilegeGuard;
        this.revocation = revocation;
        this.apiTokenService = apiTokenService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.rbacService = rbacService;
        this.loginUserLoader = loginUserLoader;
        this.rowTx = rowTx;
    }

    public static class ChangePwdReq {
        @NotBlank(
            message = "原密码不能为空"
        )
        private String oldPassword;
        @NotBlank(
            message = "新密码不能为空"
        )
        private String newPassword;

        
        public String getOldPassword() {
            return this.oldPassword;
        }

        
        public String getNewPassword() {
            return this.newPassword;
        }

        
        public void setOldPassword(final String oldPassword) {
            this.oldPassword = oldPassword;
        }

        
        public void setNewPassword(final String newPassword) {
            this.newPassword = newPassword;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof UserController.ChangePwdReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$oldPassword = this.getOldPassword();
                Object other$oldPassword = other.getOldPassword();
                if (this$oldPassword == null ? other$oldPassword == null : this$oldPassword.equals(other$oldPassword)) {
                    Object this$newPassword = this.getNewPassword();
                    Object other$newPassword = other.getNewPassword();
                    return this$newPassword == null ? other$newPassword == null : this$newPassword.equals(other$newPassword);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof UserController.ChangePwdReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $oldPassword = this.getOldPassword();
            result = result * 59 + ($oldPassword == null ? 43 : $oldPassword.hashCode());
            Object $newPassword = this.getNewPassword();
            return result * 59 + ($newPassword == null ? 43 : $newPassword.hashCode());
        }

        
        @Override
        public String toString() {
            return "UserController.ChangePwdReq(oldPassword=" + this.getOldPassword() + ", newPassword=" + this.getNewPassword() + ")";
        }
    }

    public static class CreateUserReq {
        @NotBlank(
            message = "用户名不能为空"
        )
        @Size(
            max = 50,
            message = "用户名最长 50 字符"
        )
        private String username;
        @NotBlank(
            message = "姓名不能为空"
        )
        private String name;
        @NotBlank(
            message = "密码不能为空"
        )
        private String password;
        private String department;
        private String phone;
        private String email;
        private List<String> roleCodes;

        
        public String getUsername() {
            return this.username;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getPassword() {
            return this.password;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getPhone() {
            return this.phone;
        }

        
        public String getEmail() {
            return this.email;
        }

        
        public List<String> getRoleCodes() {
            return this.roleCodes;
        }

        
        public void setUsername(final String username) {
            this.username = username;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setPassword(final String password) {
            this.password = password;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setPhone(final String phone) {
            this.phone = phone;
        }

        
        public void setEmail(final String email) {
            this.email = email;
        }

        
        public void setRoleCodes(final List<String> roleCodes) {
            this.roleCodes = roleCodes;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof UserController.CreateUserReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$username = this.getUsername();
                Object other$username = other.getUsername();
                if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$password = this.getPassword();
                        Object other$password = other.getPassword();
                        if (this$password == null ? other$password == null : this$password.equals(other$password)) {
                            Object this$department = this.getDepartment();
                            Object other$department = other.getDepartment();
                            if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                Object this$phone = this.getPhone();
                                Object other$phone = other.getPhone();
                                if (this$phone == null ? other$phone == null : this$phone.equals(other$phone)) {
                                    Object this$email = this.getEmail();
                                    Object other$email = other.getEmail();
                                    if (this$email == null ? other$email == null : this$email.equals(other$email)) {
                                        Object this$roleCodes = this.getRoleCodes();
                                        Object other$roleCodes = other.getRoleCodes();
                                        return this$roleCodes == null ? other$roleCodes == null : this$roleCodes.equals(other$roleCodes);
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof UserController.CreateUserReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $username = this.getUsername();
            result = result * 59 + ($username == null ? 43 : $username.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $password = this.getPassword();
            result = result * 59 + ($password == null ? 43 : $password.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $phone = this.getPhone();
            result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
            Object $email = this.getEmail();
            result = result * 59 + ($email == null ? 43 : $email.hashCode());
            Object $roleCodes = this.getRoleCodes();
            return result * 59 + ($roleCodes == null ? 43 : $roleCodes.hashCode());
        }

        
        @Override
        public String toString() {
            return "UserController.CreateUserReq(username="
                + this.getUsername()
                + ", name="
                + this.getName()
                + ", password="
                + this.getPassword()
                + ", department="
                + this.getDepartment()
                + ", phone="
                + this.getPhone()
                + ", email="
                + this.getEmail()
                + ", roleCodes="
                + this.getRoleCodes()
                + ")";
        }
    }

    public static class UpdateUserReq {
        private String name;
        private String department;
        private String phone;
        private String email;
        private String wecomUserid;
        private Integer status;
        private String password;
        private List<String> roleCodes;

        
        public String getName() {
            return this.name;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getPhone() {
            return this.phone;
        }

        
        public String getEmail() {
            return this.email;
        }

        
        public String getWecomUserid() {
            return this.wecomUserid;
        }

        
        public Integer getStatus() {
            return this.status;
        }

        
        public String getPassword() {
            return this.password;
        }

        
        public List<String> getRoleCodes() {
            return this.roleCodes;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setPhone(final String phone) {
            this.phone = phone;
        }

        
        public void setEmail(final String email) {
            this.email = email;
        }

        
        public void setWecomUserid(final String wecomUserid) {
            this.wecomUserid = wecomUserid;
        }

        
        public void setStatus(final Integer status) {
            this.status = status;
        }

        
        public void setPassword(final String password) {
            this.password = password;
        }

        
        public void setRoleCodes(final List<String> roleCodes) {
            this.roleCodes = roleCodes;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof UserController.UpdateUserReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$status = this.getStatus();
                Object other$status = other.getStatus();
                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$department = this.getDepartment();
                        Object other$department = other.getDepartment();
                        if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                            Object this$phone = this.getPhone();
                            Object other$phone = other.getPhone();
                            if (this$phone == null ? other$phone == null : this$phone.equals(other$phone)) {
                                Object this$email = this.getEmail();
                                Object other$email = other.getEmail();
                                if (this$email == null ? other$email == null : this$email.equals(other$email)) {
                                    Object this$wecomUserid = this.getWecomUserid();
                                    Object other$wecomUserid = other.getWecomUserid();
                                    if (this$wecomUserid == null ? other$wecomUserid == null : this$wecomUserid.equals(other$wecomUserid)) {
                                        Object this$password = this.getPassword();
                                        Object other$password = other.getPassword();
                                        if (this$password == null ? other$password == null : this$password.equals(other$password)) {
                                            Object this$roleCodes = this.getRoleCodes();
                                            Object other$roleCodes = other.getRoleCodes();
                                            return this$roleCodes == null ? other$roleCodes == null : this$roleCodes.equals(other$roleCodes);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof UserController.UpdateUserReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $phone = this.getPhone();
            result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
            Object $email = this.getEmail();
            result = result * 59 + ($email == null ? 43 : $email.hashCode());
            Object $wecomUserid = this.getWecomUserid();
            result = result * 59 + ($wecomUserid == null ? 43 : $wecomUserid.hashCode());
            Object $password = this.getPassword();
            result = result * 59 + ($password == null ? 43 : $password.hashCode());
            Object $roleCodes = this.getRoleCodes();
            return result * 59 + ($roleCodes == null ? 43 : $roleCodes.hashCode());
        }

        
        @Override
        public String toString() {
            return "UserController.UpdateUserReq(name="
                + this.getName()
                + ", department="
                + this.getDepartment()
                + ", phone="
                + this.getPhone()
                + ", email="
                + this.getEmail()
                + ", wecomUserid="
                + this.getWecomUserid()
                + ", status="
                + this.getStatus()
                + ", password="
                + this.getPassword()
                + ", roleCodes="
                + this.getRoleCodes()
                + ")";
        }
    }
}
