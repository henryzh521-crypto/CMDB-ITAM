package com.dcim.common.notify;

public interface NotificationPort {
    void notifyUser(String username, String type, String title, String content, String refId);

    void notifyRole(String roleCode, String type, String title, String content, String refId);
}
