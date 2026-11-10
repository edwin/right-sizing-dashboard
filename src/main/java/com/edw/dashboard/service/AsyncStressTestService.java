package com.edw.dashboard.service;

import com.edw.dashboard.client.LocustClient;
import com.edw.dashboard.model.StressTestResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class AsyncStressTestService {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncStressTestService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm:ss");

    private final LocustClient locustClient;
    private final KubernetesService kubernetesService;
    private final int pollingInterval;
    private final String namespace;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<String, TestStatus> tests = new ConcurrentHashMap<>();

    public AsyncStressTestService(@RestClient LocustClient locustClient,
                                   KubernetesService kubernetesService,
                                   @ConfigProperty(name = "stress-test.polling.interval", defaultValue = "30000") int pollingInterval,
                                   @ConfigProperty(name = "stress-test.kubernetes.namespace", defaultValue = "default") String namespace) {
        this.locustClient = locustClient;
        this.kubernetesService = kubernetesService;
        this.pollingInterval = pollingInterval;
        this.namespace = namespace;
    }

    public void runStressTest(String appName, String url, int userCount, int spawnRate, int duration) {
        TestStatus status = new TestStatus(appName);
        tests.put(appName, status);

        executor.submit(() -> {
            try {
                performTest(status, appName, url, userCount, spawnRate, duration);
            } catch (Exception e) {
                LOG.error("Stress test failed for " + appName, e);
                status.markFailed(e.getMessage());
            }
        });
    }

    private void performTest(TestStatus status, String appName, String url, int userCount, int spawnRate, int duration) throws InterruptedException {
        LOG.info("Starting background stress test for app: {}", appName);
        String startTime = LocalDateTime.now().format(FORMATTER);

        locustClient.swarm(userCount, spawnRate, url, duration + "s");

        BigDecimal maxCpu = BigDecimal.ZERO;
        BigDecimal maxMem = BigDecimal.ZERO;
        LocustClient.LocustStats currentStats = null;

        while (true) {
            Thread.sleep(pollingInterval);
            try {
                currentStats = locustClient.getStats();
                if (currentStats != null) {
                    LocustClient.StatsEntry statsTotal = currentStats.getEffectiveStatsTotal();
                    if (statsTotal != null) {
                        LOG.debug("Polling stats for {}, state: {}, requests: {}", appName, currentStats.state, statsTotal.numRequests);
                    }

                    try {
                        KubernetesService.Usage usage = kubernetesService.getUsage(namespace, appName);
                        if (usage.cpuCores().compareTo(maxCpu) > 0) {
                            maxCpu = usage.cpuCores();
                        }
                        if (usage.memoryBytes().compareTo(maxMem) > 0) {
                            maxMem = usage.memoryBytes();
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to get resource utilization for {}: {}", appName, e.getMessage());
                    }

                    if ("stopped".equals(currentStats.state)) {
                        LOG.info("Load testing done for {}", appName);
                        break;
                    }
                }
            } catch (Exception e) {
                LOG.error("Error while polling Locust stats for {}: {}", appName, e.getMessage());
            }
        }

        String stopTime = LocalDateTime.now().format(FORMATTER);
        StressTestResult result = calculateResult(currentStats, startTime, stopTime, maxCpu, maxMem);
        status.markCompleted(result);
    }

    private StressTestResult calculateResult(LocustClient.LocustStats currentStats, String startTime, String stopTime, BigDecimal maxCpu, BigDecimal maxMem) {
        BigDecimal cpuRequest = maxCpu.multiply(BigDecimal.valueOf(1.2)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal cpuLimit = maxCpu.multiply(BigDecimal.valueOf(1.5)).setScale(3, RoundingMode.HALF_UP);

        BigDecimal memRequest = maxMem.multiply(BigDecimal.valueOf(1.2)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal memLimit = maxMem.multiply(BigDecimal.valueOf(1.5)).setScale(0, RoundingMode.HALF_UP);

        LocustClient.StatsEntry statsTotal = (currentStats != null) ? currentStats.getEffectiveStatsTotal() : null;

        return new StressTestResult(
                (currentStats != null) ? currentStats.workerCount : 0,
                (currentStats != null) ? currentStats.userCount : 0,
                (statsTotal != null) ? statsTotal.getFailRatio() : 0,
                (statsTotal != null) ? BigDecimal.valueOf(statsTotal.totalRps).setScale(2, RoundingMode.HALF_UP).doubleValue() : 0,
                (statsTotal != null) ? statsTotal.totalFailPerSec : 0,
                (statsTotal != null) ? statsTotal.numRequests : 0,
                (statsTotal != null) ? statsTotal.numFailures : 0,
                (statsTotal != null) ? BigDecimal.valueOf(statsTotal.avgResponseTime).setScale(2, RoundingMode.HALF_UP).doubleValue() : 0,
                (statsTotal != null) ? statsTotal.n95ResponseTime : 0,
                (statsTotal != null) ? statsTotal.n99ResponseTime : 0,
                startTime,
                stopTime,
                maxCpu.doubleValue(),
                maxMem.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).doubleValue(),
                cpuRequest.toPlainString() + " cores",
                cpuLimit.toPlainString() + " cores",
                memRequest.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).toPlainString() + " MiB",
                memLimit.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).toPlainString() + " MiB"
        );
    }

    public TestStatus getStatus(String appName) {
        return tests.get(appName);
    }

    public static class TestStatus {
        private final String appName;
        private boolean completed = false;
        private boolean failed = false;
        private String errorMessage;
        private StressTestResult result;

        public TestStatus(String appName) {
            this.appName = appName;
        }

        public synchronized void markCompleted(StressTestResult result) {
            this.completed = true;
            this.result = result;
        }

        public synchronized void markFailed(String message) {
            this.failed = true;
            this.errorMessage = message;
        }

        public String getAppName() { return appName; }
        public boolean isCompleted() { return completed; }
        public boolean isFailed() { return failed; }
        public String getErrorMessage() { return errorMessage; }
        public StressTestResult getResult() { return result; }
    }
}
