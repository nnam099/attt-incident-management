package com.attt.incident.entity;

/**
 * Loại Indicator of Compromise (Dấu hiệu thỏa hiệp).
 */
public enum IoCType {
    IPV4,
    DOMAIN,
    URL,
    MD5_HASH,
    SHA256_HASH,
    EMAIL_ADDRESS,
    FILE_PATH
}
