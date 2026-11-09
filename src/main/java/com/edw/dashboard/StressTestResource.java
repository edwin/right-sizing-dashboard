package com.edw.dashboard;

import com.edw.dashboard.client.LocustClient;
import com.edw.dashboard.model.StressTestResult;
import com.edw.dashboard.service.KubernetesService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Path("/")
public class StressTestResource {

    private static final Logger LOG = LoggerFactory.getLogger(StressTestResource.class);

    private final Template index;
    private final Template results;
    private final LocustClient locustClient;
    private final KubernetesService kubernetesService;
    private final int pollingInterval;
    private final String namespace;

    public StressTestResource(Template index, Template results, @RestClient LocustClient locustClient,
                              KubernetesService kubernetesService,
                              @ConfigProperty(name = "stress-test.polling.interval", defaultValue = "30000") int pollingInterval,
                              @ConfigProperty(name = "stress-test.kubernetes.namespace", defaultValue = "default") String namespace) {
        this.index = index;
        this.results = results;
        this.locustClient = locustClient;
        this.kubernetesService = kubernetesService;
        this.pollingInterval = pollingInterval;
        this.namespace = namespace;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getIndex() {
        return index.instance();
    }

    @POST
    @Path("/stress-test")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance runStressTest(
            @FormParam("appName") String appName,
            @FormParam("url") String url,
            @FormParam("userCount") int userCount,
            @FormParam("spawnRate") int spawnRate,
            @FormParam("duration") int duration) throws InterruptedException {

        LOG.debug("Starting stress test for app: {}, url: {}, users: {}, spawnRate: {}, duration: {}s",
                appName, url, userCount, spawnRate, duration);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm:ss");
        String startTime = LocalDateTime.now().format(formatter);

        // 1. Trigger test
        locustClient.swarm(userCount, spawnRate, url, duration + "s");

        // 2. Poll result
        LocustClient.LocustStats currentStats = null;
        BigDecimal maxCpu = BigDecimal.ZERO;
        BigDecimal maxMem = BigDecimal.ZERO;

        while (true) {
            if (duration > 0) {
                Thread.sleep(pollingInterval);
            }
            currentStats = locustClient.getStats();
            LocustClient.StatsEntry statsTotal = currentStats.getEffectiveStatsTotal();
            LOG.debug("Polling stats, current state: {}, current request count: {}", currentStats.state, statsTotal.numRequests);

            try {
                KubernetesService.Usage usage = kubernetesService.getUsage(namespace, appName);
                if (usage.cpuCores().compareTo(maxCpu) > 0) {
                    maxCpu = usage.cpuCores();
                }
                if (usage.memoryBytes().compareTo(maxMem) > 0) {
                    maxMem = usage.memoryBytes();
                }
                LOG.debug("Current resource utilization for {}: CPU {} cores, Mem {} bytes", appName, usage.cpuCores(), usage.memoryBytes());
            } catch (Exception e) {
                LOG.error("Failed to get resource utilization for {}: {}", appName, e.getMessage());
            }

            if ("stopped".equals(currentStats.state)) {
                LOG.info("Load testing is done.");
                break;
            }
        }

        String stopTime = LocalDateTime.now().format(formatter);

        // Calculate recommendations
        // Request = resource + 30% buffer
        // Limit = Request + 50% buffer
        BigDecimal cpuRequest = maxCpu.multiply(BigDecimal.valueOf(1.2)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal cpuLimit = maxCpu.multiply(BigDecimal.valueOf(1.5)).setScale(3, RoundingMode.HALF_UP);

        BigDecimal memRequest = maxMem.multiply(BigDecimal.valueOf(1.2)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal memLimit = maxMem.multiply(BigDecimal.valueOf(1.5)).setScale(0, RoundingMode.HALF_UP);

        LocustClient.StatsEntry statsTotal = currentStats.getEffectiveStatsTotal();
        StressTestResult result = new StressTestResult(
                currentStats.workerCount,
                currentStats.userCount,
                statsTotal.getFailRatio(),
                BigDecimal.valueOf(statsTotal.totalRps)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue(),
                statsTotal.totalFailPerSec,
                statsTotal.numRequests,
                statsTotal.numFailures,
                BigDecimal.valueOf(statsTotal.avgResponseTime)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue(),
                statsTotal.n95ResponseTime,
                statsTotal.n99ResponseTime,
                startTime,
                stopTime,
                maxCpu.doubleValue(),
                maxMem.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).doubleValue(),
                cpuRequest.toPlainString() + " cores",
                cpuLimit.toPlainString() + " cores",
                memRequest.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).toPlainString() + " MiB",
                memLimit.divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP).toPlainString() + " MiB"
        );

        return results.data("results", result);
    }
}
