package com.edw.dashboard;

import com.edw.dashboard.client.LocustClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ReproductionTest {

    @InjectMock
    @RestClient
    LocustClient locustClient;

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testStressTestWithMissingStatsTotal() throws IOException {
        String jsonResponse = """
                {
                  "current_response_time_percentiles": {
                    "response_time_percentile_0.5": 2,
                    "response_time_percentile_0.95": 6
                  },
                  "errors": [],
                  "fail_ratio": 1.0,
                  "state": "running",
                  "stats": [
                    {
                      "avg_content_length": 0.0,
                      "avg_response_time": 2.36,
                      "current_fail_per_sec": 2955.4,
                      "current_rps": 2955.4,
                      "max_response_time": 77.0,
                      "median_response_time": 2,
                      "method": "GET",
                      "name": "/",
                      "num_failures": 190446,
                      "num_requests": 190446,
                      "response_time_percentile_0.95": 5,
                      "response_time_percentile_0.99": 7,
                      "total_fail_per_sec": 3312.8,
                      "total_rps": 3312.8
                    },
                    {
                      "avg_content_length": 0.0,
                      "avg_response_time": 2.36,
                      "current_fail_per_sec": 2955.4,
                      "current_rps": 2955.4,
                      "max_response_time": 77.0,
                      "median_response_time": 2,
                      "method": "",
                      "name": "Aggregated",
                      "num_failures": 190446,
                      "num_requests": 190446,
                      "response_time_percentile_0.95": 5,
                      "response_time_percentile_0.99": 7,
                      "total_fail_per_sec": 3312.8,
                      "total_rps": 3312.8
                    }
                  ],
                  "total_fail_per_sec": 3312.8,
                  "total_rps": 3312.8,
                  "user_count": 10,
                  "worker_count": 2
                }
                """;

        LocustClient.LocustStats stats = objectMapper.readValue(jsonResponse, LocustClient.LocustStats.class);
        
        // This will simulate the bug as stats.statsTotal will be null
        when(locustClient.getStats()).thenReturn(stats, stats);

        given()
          .formParam("appName", "TestApp")
          .formParam("url", "http://test.com")
          .formParam("userCount", 10)
          .formParam("spawnRate", 1)
          .formParam("duration", 0) // Set duration to 0 to avoid long wait
          .when().post("/stress-test")
          .then()
             .statusCode(200);
    }
}
