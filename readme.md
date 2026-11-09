# Right Sizing Dashboard

A Quarkus-based dashboard application designed to trigger stress tests, monitor application performance on OpenShift, and provide optimized resource configuration recommendations.

## Project Overview

The Right Sizing Dashboard helps developers and platform engineers determine the optimal CPU and memory settings for their applications. By integrating with **Locust** for load generation and the **OpenShift/Kubernetes Metrics API** for real-time monitoring, it analyzes how an application behaves under stress and suggests `requests` and `limits` based on actual usage.

## Features

- **Trigger Stress Tests**: Easily start Locust swarm tests directly from the dashboard.
- **Real-time Monitoring**: Tracks CPU and memory utilization of the target application during the test.
- **Resource Recommendations**: Automatically calculates recommended CPU and Memory requests/limits (including safety buffers).
- **Web Interface**: Simple UI to input test parameters and view results.
- **OpenShift Native**: Designed to run on OpenShift Container Platform 4.

## Tech Stack

- **Java 21**
- **Framework**: Red Hat build of Quarkus
- **Build Tool**: Maven
- **Templates**: Qute Template Engine
- **Cloud Native**: Kubernetes Client, MicroProfile REST Client
- **Testing**: Locust

## Prerequisites

- **OpenShift Container Platform 4**
- **Locust** installed and running in the cluster.
- **Maven** with the project's specific `settings.xml`.

## Configuration

The application can be configured via `src/main/resources/application.properties` or environment variables:

| Property | Environment Variable | Default Value | Description |
|----------|----------------------|---------------|-------------|
| `locust-api/mp-rest/url` | `LOCUST_ENDPOINT` | `http://locust-master-api.apps-crc.testing/` | URL of the Locust Master API |
| `stress-test.polling.interval` | - | `10000` (ms) | Interval to poll Locust stats and K8s metrics |
| `stress-test.kubernetes.namespace` | `KUBERNETES_NAMESPACE` | `default` | Namespace where the target application is running |

## How to Build

To build the application, use the provided `settings.xml`:

```powershell
mvn clean build -s settings.xml
```

## How to Run

### Development Mode
```powershell
mvn quarkus:dev -s settings.xml
```

### Running the Container
The project includes a `Dockerfile` based on Red Hat UBI 9.

```powershell
docker build -t right-sizing-dashboard .
docker run -p 8080:8080 right-sizing-dashboard
```

## Usage

1. Access the dashboard at `http://localhost:8080`.
2. Fill in the Stress Test form:
    - **App Name**: The name of the deployment/pod to monitor.
    - **URL**: The target URL for the load test.
    - **User Count**: Number of concurrent users.
    - **Spawn Rate**: Users added per second.
    - **Duration**: Test duration in seconds.
3. Submit the form and wait for the test to complete.
4. Review the results and the generated resource recommendations.

## Project Structure

- `src/main/java/com/edw/dashboard`: Main application logic.
    - `StressTestResource.java`: REST endpoints and orchestration.
    - `service/KubernetesService.java`: Interface with Kubernetes/OpenShift API.
    - `client/LocustClient.java`: REST client for Locust API.
- `src/main/resources/templates`: HTML templates for the dashboard.
- `Dockerfile`: Container image definition.
- `settings.xml`: Maven settings for Red Hat Quarkus repositories.

## Development Conventions

- **Package by feature**: Code is organized by functional area.
- **Constructor Injection**: Strict use of constructor injection for dependency management.
- **Logging**: SLF4J is used for all logging.
- **Testing**: JUnit 5 and AssertJ for unit tests.
