package com.edw.dashboard;

import com.edw.dashboard.client.LocustClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
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
        LocustClient.LocustStats statsRunning = new LocustClient.LocustStats();
        statsRunning.state = "running";
        statsRunning.workerCount = 3;
        statsRunning.userCount = 10;
        statsRunning.statsTotal = new LocustClient.StatsEntry();
        statsRunning.statsTotal.numRequests = 50;
        statsRunning.statsTotal.numFailures = 0;
        statsRunning.statsTotal.totalRps = 10.0;
        statsRunning.statsTotal.avgResponseTime = 50.0;
        statsRunning.statsTotal.n95ResponseTime = 60.0;
        statsRunning.statsTotal.n99ResponseTime = 70.0;

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

        when(locustClient.getStats()).thenReturn(statsRunning, statsStopped);

        given()
          .formParam("appName", "TestApp")
          .formParam("url", "http://test.com")
          .formParam("method", "GET")
          .formParam("userCount", 10)
          .formParam("spawnRate", 1)
          .formParam("duration", 60)
          .when().post("/stress-test")
          .then()
             .statusCode(200)
             .body(containsString("Test Execution Time"))
             .body(containsString("Start Time"))
             .body(containsString("Stop Time"))
             .body(containsString("Worker Count"))
             .body(containsString("Total RPS"));
    }
}
