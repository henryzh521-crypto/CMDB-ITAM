package com.dcim.security.ldap;

import com.dcim.common.BusinessException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.AuthenticationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LdapAuthService {
    
    private static final Logger log = LoggerFactory.getLogger(LdapAuthService.class);
    private final boolean enabled;
    private final String url;
    private final String baseDn;
    private final String bindDn;
    private final String bindPassword;
    private final String userFilter;
    private final String nameAttr;
    private final String emailAttr;
    private final String phoneAttr;
    private final String deptAttr;
    private final String groupRoleMap;
    private final int timeoutMs;
    private static final Pattern USERNAME = Pattern.compile("^[\\p{L}\\p{N}._@-]{1,64}$");
    private static final Pattern LOGIN_ATTR = Pattern.compile("\\(([A-Za-z][\\w-]*)=\\{0}\\)");

    public LdapAuthService(
        @Value("${dcim.ldap.enabled:false}") boolean enabled,
        @Value("${dcim.ldap.url:}") String url,
        @Value("${dcim.ldap.base-dn:}") String baseDn,
        @Value("${dcim.ldap.bind-dn:}") String bindDn,
        @Value("${dcim.ldap.bind-password:}") String bindPassword,
        @Value("${dcim.ldap.user-filter:(&(objectClass=user)(sAMAccountName={0}))}") String userFilter,
        @Value("${dcim.ldap.name-attr:displayName}") String nameAttr,
        @Value("${dcim.ldap.email-attr:mail}") String emailAttr,
        @Value("${dcim.ldap.phone-attr:telephoneNumber}") String phoneAttr,
        @Value("${dcim.ldap.department-attr:department}") String deptAttr,
        @Value("${dcim.ldap.group-role-map:}") String groupRoleMap,
        @Value("${dcim.ldap.timeout-ms:5000}") int timeoutMs
    ) {
        this.enabled = enabled && url != null && !url.isBlank();
        this.url = url;
        this.baseDn = baseDn;
        this.bindDn = bindDn;
        this.bindPassword = bindPassword;
        this.userFilter = userFilter;
        this.nameAttr = nameAttr;
        this.emailAttr = emailAttr;
        this.phoneAttr = phoneAttr;
        this.deptAttr = deptAttr;
        this.groupRoleMap = groupRoleMap;
        this.timeoutMs = timeoutMs;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public LdapAuthService.DirUser authenticate(String username, String password) {
        if (this.enabled && username != null && !username.isBlank() && password != null && !password.isEmpty()) {
            LdapAuthService.DirUser u = this.find(username);
            if (u == null) {
                return null;
            } else {
                DirContext ctx = null;

                Object var6;
                try {
                    ctx = this.bind(u.dn(), password);
                    return u;
                } catch (AuthenticationException var11) {
                    var6 = null;
                } catch (NamingException var12) {
                    log.warn("[ldap] 用户绑定失败（目录服务异常）：{}", var12.getMessage());
                    throw BusinessException.badRequest("目录服务暂时不可用，请稍后重试或联系管理员");
                } finally {
                    close(ctx);
                }

                return (LdapAuthService.DirUser)var6;
            }
        } else {
            return null;
        }
    }

    public LdapAuthService.DirUser find(String username) {
        if (this.enabled && username != null && USERNAME.matcher(username.trim()).matches()) {
            DirContext ctx = null;

            try {
                ctx = this.bind(this.bindDn != null && !this.bindDn.isBlank() ? this.bindDn : null, this.bindPassword);
                SearchControls sc = new SearchControls();
                sc.setSearchScope(2);
                sc.setCountLimit(2L);
                sc.setTimeLimit(this.timeoutMs);
                String loginAttr = this.loginAttr();
                sc.setReturningAttributes(
                    loginAttr == null
                        ? new String[]{this.nameAttr, this.emailAttr, this.phoneAttr, this.deptAttr, "memberOf", "cn"}
                        : new String[]{this.nameAttr, this.emailAttr, this.phoneAttr, this.deptAttr, "memberOf", "cn", loginAttr}
                );
                String filter = this.userFilter.replace("{0}", escapeFilter(username.trim()));
                NamingEnumeration<SearchResult> rs = ctx.search(this.baseDn, filter, sc);
                if (!rs.hasMore()) {
                    return null;
                } else {
                    SearchResult r = rs.next();
                    if (rs.hasMore()) {
                        log.warn("[ldap] 用户名 {} 匹配到多个条目，拒绝登录（请收紧 user-filter）", username);
                        return null;
                    } else {
                        Attributes a = r.getAttributes();
                        if (loginAttr != null) {
                            String actual = first(a.get(loginAttr));
                            if (actual == null || !actual.equalsIgnoreCase(username.trim())) {
                                log.warn("[ldap] 搜索结果登录名 {} 与输入 {} 不一致，拒绝", actual, username);
                                return null;
                            }
                        }

                        List<String> groups = all(a.get("memberOf"));
                        String name = first(a.get(this.nameAttr));
                        if (name == null) {
                            name = first(a.get("cn"));
                        }

                        return new LdapAuthService.DirUser(
                            r.getNameInNamespace(),
                            username.trim(),
                            name == null ? username.trim() : name,
                            first(a.get(this.emailAttr)),
                            first(a.get(this.phoneAttr)),
                            first(a.get(this.deptAttr)),
                            groups,
                            this.mapRoles(groups)
                        );
                    }
                }
            } catch (AuthenticationException var16) {
                log.error("[ldap] 服务账号绑定失败，请检查 dcim.ldap.bind-dn / bind-password：{}", var16.getMessage());
                throw BusinessException.badRequest("目录服务配置错误（服务账号认证失败），请联系管理员");
            } catch (NamingException var17) {
                log.warn("[ldap] 目录查询失败：{}", var17.getMessage());
                throw BusinessException.badRequest("目录服务暂时不可用，请稍后重试或联系管理员");
            } finally {
                close(ctx);
            }
        } else {
            return null;
        }
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", this.enabled);
        m.put("url", this.url);
        m.put("baseDn", this.baseDn);
        m.put("bindDn", this.bindDn);
        m.put("bindPasswordSet", this.bindPassword != null && !this.bindPassword.isEmpty());
        m.put("userFilter", this.userFilter);
        m.put("nameAttr", this.nameAttr);
        m.put("groupRoleMap", this.parseMap().toString());
        return m;
    }

    String loginAttr() {
        Matcher m = LOGIN_ATTR.matcher(this.userFilter);
        return m.find() ? m.group(1) : null;
    }

    private DirContext bind(String dn, String password) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
        env.put("java.naming.provider.url", this.url);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(this.timeoutMs));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(this.timeoutMs));
        env.put("java.naming.referral", "ignore");
        if (dn != null) {
            env.put("java.naming.security.authentication", "simple");
            env.put("java.naming.security.principal", dn);
            env.put("java.naming.security.credentials", password == null ? "" : password);
        } else {
            env.put("java.naming.security.authentication", "none");
        }

        return new InitialDirContext(env);
    }

    Set<String> mapRoles(List<String> groups) {
        Map<String, String> map = this.parseMap();
        Set<String> roles = new LinkedHashSet<>();

        for (String g : groups) {
            String r = map.get(normalizeDn(g));
            if (r != null) {
                roles.add(r);
            }
        }

        return roles;
    }

    private Map<String, String> parseMap() {
        Map<String, String> m = new LinkedHashMap<>();
        if (this.groupRoleMap != null && !this.groupRoleMap.isBlank()) {
            for (String pair : this.groupRoleMap.split(";")) {
                int i = pair.lastIndexOf(61);
                if (i > 0 && i < pair.length() - 1) {
                    m.put(normalizeDn(pair.substring(0, i)), pair.substring(i + 1).trim());
                }
            }

            return m;
        } else {
            return m;
        }
    }

    private static String normalizeDn(String dn) {
        return dn.trim().replaceAll("\\s*,\\s*", ",").toLowerCase(Locale.ROOT);
    }

    static String escapeFilter(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            switch (c) {
                case '\u0000':
                    sb.append("\\00");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '\\':
                    sb.append("\\5c");
                    break;
                default:
                    sb.append(c);
            }
        }

        return sb.toString();
    }

    private static String first(Attribute a) throws NamingException {
        return a != null && a.size() != 0 ? String.valueOf(a.get(0)) : null;
    }

    private static List<String> all(Attribute a) throws NamingException {
        List<String> out = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.size(); i++) {
                out.add(String.valueOf(a.get(i)));
            }
        }

        return out;
    }

    private static void close(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException var2) {
            }
        }
    }

    public static record DirUser(
        String dn, String username, String name, String email, String phone, String department, List<String> groups, Set<String> mappedRoles
    ) {
    }
}
