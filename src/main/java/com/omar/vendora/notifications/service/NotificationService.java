package com.omar.vendora.notifications.service;

import java.util.UUID;

public interface NotificationService {

    void notify(UUID userId, String subject, String content);
}
