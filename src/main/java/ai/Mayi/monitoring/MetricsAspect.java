package ai.Mayi.monitoring;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
public class MetricsAspect {

    private final PerformanceMetrics performanceMetrics;

    public MetricsAspect(PerformanceMetrics performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    @Around("execution(* ai.Mayi.web.controller..*(..))")
    public Object measureMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        String endpoint = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed(); // 실제 메서드 실행
        } catch (Exception e) {
            performanceMetrics.recordError(endpoint, e.getClass().getSimpleName());
            throw e;
        } finally {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - start);
            performanceMetrics.recordResponseTime(endpoint, duration);
        }
    }
}
