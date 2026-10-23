package com.edw.dashboard;

public class StressTestResult {
    private final int workerCount;
    private final int userCount;
    private final double failRatio;
    private final double totalRps;
    private final double failPerSecond;
    private final long requests;
    private final long failures;
    private final double avgResponseTime;
    private final double p95;
    private final double p99;

    public StressTestResult(int workerCount, int userCount, double failRatio, double totalRps, double failPerSecond, 
                            long requests, long failures, double avgResponseTime, double p95, double p99) {
        this.workerCount = workerCount;
        this.userCount = userCount;
        this.failRatio = failRatio;
        this.totalRps = totalRps;
        this.failPerSecond = failPerSecond;
        this.requests = requests;
        this.failures = failures;
        this.avgResponseTime = avgResponseTime;
        this.p95 = p95;
        this.p99 = p99;
    }

    public int getWorkerCount() { return workerCount; }
    public int getUserCount() { return userCount; }
    public double getFailRatio() { return failRatio; }
    public double getTotalRps() { return totalRps; }
    public double getFailPerSecond() { return failPerSecond; }
    public long getRequests() { return requests; }
    public long getFailures() { return failures; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public double getP95() { return p95; }
    public double getP99() { return p99; }
}
