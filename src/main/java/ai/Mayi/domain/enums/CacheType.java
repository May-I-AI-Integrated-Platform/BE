package ai.Mayi.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheType {
    TOKEN_INFO("token", 12, 10000);

    private final String cacheName;
    private final int expiredAfterWrite;
    private final int maximumSize;
}