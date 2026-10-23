package com.edw.dashboard;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class StressTestResourceTest {

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
          .formParam("method", "GET")
          .formParam("userCount", 10)
          .formParam("spawnRate", 1)
          .formParam("duration", 60)
          .when().post("/stress-test")
          .then()
             .statusCode(200)
             .body(containsString("Locust Standard Result"))
             .body(containsString("Detailed Result"))
             .body(containsString("Worker Count"))
             .body(containsString("Total RPS"));
    }
}
