package com.dcim.security;

import com.dcim.common.BusinessException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

@Component
@ConditionalOnProperty(
    name = {"wecom.enabled"},
    havingValue = "true"
)
public class HttpWecomApiClient implements WecomApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(HttpWecomApiClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<Map<String, Object>>() {
    };
    private final RestClient rest;
    private final WecomProperties props;
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public HttpWecomApiClient(WecomProperties props, Builder builder) {
        this.props = props;
        this.rest = builder.baseUrl("https://qyapi.weixin.qq.com").build();
    }

    @Override
    public String getAccessToken() {
        String token = this.cachedToken;
        if (token != null && System.currentTimeMillis() < this.tokenExpireAt) {
            return token;
        } else {
            synchronized (this) {
                if (this.cachedToken != null && System.currentTimeMillis() < this.tokenExpireAt) {
                    return this.cachedToken;
                } else {
                    Map<String, Object> resp = (Map<String, Object>)this.rest
                        .get()
                        .uri(
                            uri -> uri.path("/cgi-bin/gettoken")
                                    .queryParam("corpid", new Object[]{this.props.getCorpId()})
                                    .queryParam("corpsecret", new Object[]{this.props.getSecret()})
                                    .build(new Object[0])
                        )
                        .retrieve()
                        .body(MAP_TYPE);
                    int errcode = this.intOf(resp, "errcode");
                    if (resp != null && errcode == 0) {
                        this.cachedToken = String.valueOf(resp.get("access_token"));
                        int expiresIn = this.intOf(resp, "expires_in");
                        this.tokenExpireAt = System.currentTimeMillis() + (long)Math.max(expiresIn - 120, 60) * 1000L;
                        return this.cachedToken;
                    } else {
                        log.warn("[wecom] 获取 access_token 失败: errcode={}, errmsg={}", errcode, resp == null ? null : resp.get("errmsg"));
                        throw BusinessException.badRequest("获取企微访问凭证失败：" + (resp == null ? "企微接口空响应" : resp.get("errmsg")));
                    }
                }
            }
        }
    }

    @Override
    public WecomApiClient.WecomUserInfo getUserInfoByCode(String code) {
        Map<String, Object> resp = (Map<String, Object>)this.rest
            .get()
            .uri(
                uri -> uri.path("/cgi-bin/auth/getuserinfo")
                        .queryParam("access_token", new Object[]{this.getAccessToken()})
                        .queryParam("code", new Object[]{code})
                        .build(new Object[0])
            )
            .retrieve()
            .body(MAP_TYPE);
        int errcode = this.intOf(resp, "errcode");
        if (resp != null && errcode == 0) {
            String userid = resp.get("userid") == null ? null : String.valueOf(resp.get("userid"));
            if (userid != null && !userid.isBlank()) {
                String name = resp.get("name") == null ? null : String.valueOf(resp.get("name"));
                return new WecomApiClient.WecomUserInfo(userid, name);
            } else {
                throw BusinessException.unauthorized("企微返回的成员信息缺少 userid");
            }
        } else {
            log.warn("[wecom] code 换取成员信息失败: errcode={}, errmsg={}", errcode, resp == null ? null : resp.get("errmsg"));
            throw BusinessException.unauthorized("企微登录失败：" + (resp == null ? "企微接口空响应" : resp.get("errmsg")));
        }
    }

    private int intOf(Map<String, Object> resp, String key) {
        return resp != null && resp.get(key) instanceof Number n ? n.intValue() : -1;
    }
}
