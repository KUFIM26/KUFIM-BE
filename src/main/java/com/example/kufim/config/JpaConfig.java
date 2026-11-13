package com.example.kufim.config;

import com.example.kufim.common.util.ApiClock;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * created_at / updated_at 을 KST로 기록합니다.
     *
     * 이 설정이 없으면 JPA Auditing이 JVM 기본 시간대(배포 컨테이너는 UTC)로 값을 남기는데,
     * 응답에서는 ApiClock.offset()이 그 값에 +09:00을 그대로 붙이기 때문에
     * 실제보다 9시간 이른 시각이 내려갑니다. 다른 시각 컬럼은 이미 ApiClock.now()를 쓰고 있어
     * 여기만 맞추면 전체가 KST 기준으로 통일됩니다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of((TemporalAccessor) ApiClock.now());
    }
}
