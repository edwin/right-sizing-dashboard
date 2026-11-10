package com.edw.dashboard;

import com.edw.dashboard.client.LocustClient;
import com.edw.dashboard.service.KubernetesService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(StressTestResourceTest.TestProfile.class)
public class StressTestResourceTest {

    public static class TestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Collections.singletonMap("stress-test.polling.interval", "0");
        }
    }

    @InjectMock
    @RestClient
    LocustClient locustClient;

    @InjectMock
    KubernetesService kubernetesService;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
    }

    @Test
    public void testIndexPage() {
        given()
          .when().get("/")
          .then()
             .statusCode(200)
             .body(containsString("Stress Test Dashboard"))
             .body(containsString("Application Name"));
    }

    @Test
    public void testStressTestSubmission() {
        given()
          .formParam("appName", "TestApp")
          .formParam("url", "http://test.com")
          .formParam("userCount", 10)
          .formParam("spawnRate", 1)
          .formParam("duration", 60)
          .when().post("/stress-test")
          .then()
             .statusCode(200)
             .body(containsString("hx-get=\"/stress-test/status?appName=TestApp\""))
             .body(containsString("is running, please wait..."));
    }

    @Test
    public void testStressTestStatusCompleted() {
        LocustClient.LocustStats statsStopped = new LocustClient.LocustStats();
        statsStopped.state = "stopped";
        statsStopped.workerCount = 3;
        statsStopped.userCount = 10;
        statsStopped.statsTotal = new LocustClient.StatsEntry();
        statsStopped.statsTotal.numRequests = 100;
        statsStopped.statsTotal.numFailures = 0;
        statsStopped.statsTotal.totalRps = 10.0;
        statsStopped.statsTotal.avgResponseTime = 50.0;
        statsStopped.statsTotal.n95ResponseTime = 60.0;
        statsStopped.statsTotal.n99ResponseTime = 70.0;

        when(locustClient.getStats()).thenReturn(statsStopped);
        when(kubernetesService.getUsage(anyString(), anyString()))
                .thenReturn(new KubernetesService.Usage(1, BigDecimal.valueOf(0.1), BigDecimal.valueOf(1024 * 1024 * 100)));

        // Trigger test
        given()
                .formParam("appName", "TestApp2")
                .formParam("url", "http://test.com")
                .formParam("userCount", 10)
                .formParam("spawnRate", 1)
                .formParam("duration", 60)
                .post("/stress-test");

        // We need to wait a bit because the service is async, but since we mocked everything and polling interval is 0 in profile
        // it might be fast. However, performTest has Thread.sleep(pollingInterval).
        
        // Wait for completion (busy poll in test)
        long start = System.currentTimeMillis();
        boolean completed = false;
        while (System.currentTimeMillis() - start < 5000) {
            String body = given()
                    .queryParam("appName", "TestApp2")
                    .get("/stress-test/status")
                    .then()
                    .statusCode(200)
                    .extract().body().asString();
            if (body.contains("Locust Standard Result")) {
                completed = true;
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        if (!completed) {
            org.junit.jupiter.api.Assertions.fail("Test did not complete in time");
        }
    }
}
