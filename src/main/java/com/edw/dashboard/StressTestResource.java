package com.edw.dashboard;

import com.edw.dashboard.service.AsyncStressTestService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
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
    private final AsyncStressTestService asyncStressTestService;

    public StressTestResource(Template index, Template results,
                              AsyncStressTestService asyncStressTestService) {
        this.index = index;
        this.results = results;
        this.asyncStressTestService = asyncStressTestService;
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
    public String runStressTest(
            @FormParam("appName") String appName,
            @FormParam("url") String url,
            @FormParam("userCount") int userCount,
            @FormParam("spawnRate") int spawnRate,
            @FormParam("duration") int duration) {

        LOG.debug("Starting async stress test for app: {}, url: {}, users: {}, spawnRate: {}, duration: {}s",
                appName, url, userCount, spawnRate, duration);

        asyncStressTestService.runStressTest(appName, url, userCount, spawnRate, duration);

        return "<div hx-get=\"/stress-test/status?appName=" + appName + "\" hx-trigger=\"every 5s\" hx-target=\"this\" hx-swap=\"outerHTML\">" +
                "<div aria-busy=\"true\">Stress test for " + appName + " is running, please wait...</div>" +
                "</div>";
    }

    @GET
    @Path("/stress-test/status")
    @Produces(MediaType.TEXT_HTML)
    public Object getStatus(@QueryParam("appName") String appName) {
        AsyncStressTestService.TestStatus status = asyncStressTestService.getStatus(appName);

        if (status == null) {
            return "<div>No test found for " + appName + "</div>";
        }

        if (status.isFailed()) {
            return "<div class=\"error\">Test failed: " + status.getErrorMessage() + "</div>";
        }

        if (status.isCompleted()) {
            return results.data("results", status.getResult());
        }

        return "<div hx-get=\"/stress-test/status?appName=" + appName + "\" hx-trigger=\"every 5s\" hx-target=\"this\" hx-swap=\"outerHTML\">" +
                "<div aria-busy=\"true\">Stress test for " + appName + " is running, please wait...</div>" +
                "</div>";
    }
}
