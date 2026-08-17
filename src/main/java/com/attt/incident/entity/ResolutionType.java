package com.attt.incident.entity;

/**
 * Loại kết luận sự cố (True/False Positive).
 */
public enum ResolutionType {
    TRUE_POSITIVE,      // Sự cố thực sự
    FALSE_POSITIVE,     // Báo động giả
    BENIGN,             // Hành vi bình thường bị nhận diện nhầm
    NOT_APPLICABLE      // Không áp dụng / Hủy bỏ
}
