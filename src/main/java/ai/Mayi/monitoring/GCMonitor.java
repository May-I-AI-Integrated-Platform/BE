package ai.Mayi.monitoring;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

@Slf4j
@Component
public class GCMonitor {

    private static final long GC_PAUSE_THRESHOLD_MS = 200;

    @PostConstruct
    public void registerGCListener() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!notification.getType()
                            .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                        return;
                    }

                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo
                            .from((CompositeData) notification.getUserData());

                    long pauseTimeMs = info.getGcInfo().getDuration();
                    String gcName = info.getGcName();
                    String gcAction = info.getGcAction();

                    long memBeforeMb = info.getGcInfo().getMemoryUsageBeforeGc().values().stream()
                            .mapToLong(usage -> usage.getUsed() / 1024 / 1024)
                            .sum();

                    long memAfterMb = info.getGcInfo().getMemoryUsageAfterGc().values().stream()
                            .mapToLong(usage -> usage.getUsed() / 1024 / 1024)
                            .sum();

                    if (pauseTimeMs > GC_PAUSE_THRESHOLD_MS) {
                        log.warn("Long GC pause detected: {}ms, Type: {}, Action: {}, Before: {}MB, After: {}MB",
                                pauseTimeMs, gcName, gcAction, memBeforeMb, memAfterMb);
                    }

                }, null, null);
            }
        }
    }
}