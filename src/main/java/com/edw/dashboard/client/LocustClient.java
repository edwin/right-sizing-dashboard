package com.edw.dashboard.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "locust-api")
public interface LocustClient {

    @POST
    @Path("/swarm")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    void swarm(@FormParam("user_count") int userCount,
               @FormParam("spawn_rate") int spawnRate,
               @FormParam("host") String host,
               @FormParam("run_time") String runTime);

    @GET
    @Path("/stats/requests")
    @Produces(MediaType.APPLICATION_JSON)
    LocustStats getStats();

    class LocustStats {
        @JsonProperty("stats")
        public List<StatsEntry> stats;
        
        @JsonProperty("stats_total")
        public StatsEntry statsTotal;
        
        @JsonProperty("user_count")
        public int userCount;
        
        @JsonProperty("state")
        public String state;
        
        @JsonProperty("worker_count")
        public int workerCount;

        @JsonProperty("fail_ratio")
        public double failRatio;

        @JsonProperty("total_rps")
        public double totalRps;

        @JsonProperty("total_fail_per_sec")
        public double totalFailPerSec;

        public StatsEntry getEffectiveStatsTotal() {
            if (statsTotal != null) {
                return statsTotal;
            }
            if (stats != null) {
                return stats.stream()
                        .filter(s -> "Aggregated".equals(s.name))
                        .findFirst()
                        .orElse(null);
            }
            return null;
        }
    }

    class StatsEntry {
        @JsonProperty("method")
        public String method;
        
        @JsonProperty("name")
        public String name;
        
        @JsonProperty("num_requests")
        public long numRequests;
        
        @JsonProperty("num_failures")
        public long numFailures;
        
        @JsonProperty("avg_response_time")
        public double avgResponseTime;
        
        @JsonProperty("response_time_percentile_0.95")
        public double n95ResponseTime;
        
        @JsonProperty("response_time_percentile_0.99")
        public double n99ResponseTime;
        
        @JsonProperty("current_rps")
        public double currentRps;
        
        @JsonProperty("current_fail_per_sec")
        public double currentFailPerSec;
        
        @JsonProperty("total_rps")
        public double totalRps;
        
        @JsonProperty("total_fail_per_sec")
        public double totalFailPerSec;

        public double getFailRatio() {
            if (numRequests == 0) return 0;
            return (double) numFailures / numRequests;
        }
    }
}
