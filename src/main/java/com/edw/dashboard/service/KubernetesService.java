package com.edw.dashboard.service;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <pre>
 *  com.edw.dashboard.service.KubernetesService
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 09 Nov 2026 12:38
 */
@ApplicationScoped
public class KubernetesService {

    @Inject
    KubernetesClient client;

    public record Usage(int pods, BigDecimal cpuCores, BigDecimal memoryBytes) {}

    public Usage getUsage(String namespace, String deploymentName) {
        Deployment dep = client.apps().deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get();
        if (dep == null) {
            throw new NotFoundException("Deployment not found: " + deploymentName);
        }

        // Pods owned by this Deployment, via its selector (handles matchLabels + matchExpressions)
        Set<String> podNames = client.pods()
                .inNamespace(namespace)
                .withLabelSelector(dep.getSpec().getSelector())
                .list().getItems().stream()
                .map(p -> p.getMetadata().getName())
                .collect(Collectors.toSet());

        // Metrics for the namespace, filtered down to our pods
        List<PodMetrics> metrics = client.top().pods().metrics(namespace).getItems().stream()
                .filter(m -> podNames.contains(m.getMetadata().getName()))
                .toList();

        BigDecimal cpu = BigDecimal.ZERO;
        BigDecimal mem = BigDecimal.ZERO;
        for (PodMetrics pm : metrics) {
            for (ContainerMetrics c : pm.getContainers()) {
                cpu = cpu.add(c.getUsage().get("cpu").getNumericalAmount());       // cores
                mem = mem.add(c.getUsage().get("memory").getNumericalAmount());    // bytes
            }
        }
        return new Usage(metrics.size(), cpu, mem);
    }

}
