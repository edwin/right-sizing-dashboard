package com.edw.dashboard;

import com.edw.dashboard.client.LocustClient;
import com.edw.dashboard.model.StressTestResult;
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

@Path("/")
public class StressTestResource {

    private static final Logger LOG = LoggerFactory.getLogger(StressTestResource.class);

    private final Template index;
    private final Template results;
    private final LocustClient locustClient;
    private final int pollingInterval;

    public StressTestResource(Template index, Template results, @RestClient LocustClient locustClient,
                              @ConfigProperty(name = "stress-test.polling.interval", defaultValue = "30000") int pollingInterval) {
        this.index = index;
        this.results = results;
        this.locustClient = locustClient;
        this.pollingInterval = pollingInterval;
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

        while (true) {
            if (duration > 0) {
                Thread.sleep(pollingInterval);
            }
            currentStats = locustClient.getStats();
            LocustClient.StatsEntry statsTotal = currentStats.getEffectiveStatsTotal();
            LOG.debug("Polling stats, current state: {}, current request count: {}", currentStats.state, statsTotal.numRequests);

            if ("stopped".equals(currentStats.state)) {
                LOG.info("Load testing is done.");
                break;
            }
        }

        String stopTime = LocalDateTime.now().format(formatter);

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
                stopTime
        );

        return results.data("results", result);
    }
}
