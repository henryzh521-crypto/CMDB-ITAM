package com.dcim.security;

public interface WecomApiClient {
    String getAccessToken();

    WecomApiClient.WecomUserInfo getUserInfoByCode(String code);

    public static record WecomUserInfo(String userid, String name) {
    }
}
