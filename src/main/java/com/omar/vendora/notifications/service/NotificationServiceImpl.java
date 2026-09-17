package com.omar.vendora.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public void notify(UUID userId, String subject, String content) {
        log.info("Notification sent to user [{}]: subject='{}', content='{}'", userId, subject, content);
    }
}
