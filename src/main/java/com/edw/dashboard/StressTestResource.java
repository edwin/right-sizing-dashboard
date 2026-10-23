package com.edw.dashboard;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
public class StressTestResource {

    private static final Logger LOG = LoggerFactory.getLogger(StressTestResource.class);

    private final Template index;
    private final Template results;

    public StressTestResource(Template index, Template results) {
        this.index = index;
        this.results = results;
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
            @FormParam("method") String method,
            @FormParam("userCount") int userCount,
            @FormParam("spawnRate") int spawnRate,
            @FormParam("duration") int duration) {

        LOG.info("Starting stress test for app: {}, url: {}, method: {}, users: {}, spawnRate: {}, duration: {}s",
                appName, url, method, userCount, spawnRate, duration);

        // Dummy response as requested
        StressTestResult dummyResult = new StressTestResult(
                3,              // workerCount
                userCount,      // userCount
                0.5,            // failRatio
                150.0,          // totalRps
                0.75,           // failPerSecond
                9000L,          // requests
                45L,            // failures
                120.5,          // avgResponseTime
                250.0,          // p95
                450.0           // p99
        );

        return results.data("results", dummyResult);
    }
}
