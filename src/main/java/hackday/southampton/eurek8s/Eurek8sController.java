package hackday.southampton.eurek8s;

import java.util.List;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.cloud.netflix.eureka.InstanceInfoFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ollie Hughes
 */
@RestController
public class Eurek8sController {

	private KubernetesClient kubernetesClient;
	private DiscoveryClient discoveryClient;
	private EurekaClient eurekaClient;
	private EurekaInstanceConfigBean instanceConfig;
	private PeerAwareInstanceRegistry instanceRegistry;
	private K8SWatchService k8SWatchService;
	Logger log = LoggerFactory.getLogger(Eurek8sApplication.class);


	public Eurek8sController(KubernetesClient kubernetesClient, DiscoveryClient discoveryClient, EurekaClient eurekaClient, EurekaInstanceConfigBean instanceConfig, PeerAwareInstanceRegistry instanceRegistry, K8SWatchService k8SWatchService) {
		this.kubernetesClient = kubernetesClient;
		this.discoveryClient = discoveryClient;
		this.eurekaClient = eurekaClient;
		this.instanceConfig = instanceConfig;
		this.instanceRegistry = instanceRegistry;
		this.k8SWatchService = k8SWatchService;
	}

	@GetMapping("/watch")
	public String startWatch() {
		final Watch watch = kubernetesClient.services().watch(new Watcher<Service>() {
			@Override
			public void eventReceived(Action action, Service resource) {
				log.info("Received service event: {}", action.name());
				log.info("Service details {}", resource.getSpec());
				log.info("Service details {}", resource.getMetadata());
				log.info("Service details {}", resource.getAdditionalProperties());
				log.info("Service details {}", resource.getKind());
				log.info("Service details {}", resource.getStatus());
				log.info("Service details {}", resource.getStatus().getLoadBalancer());
				log.info("Service details {}", resource.getApiVersion());
				final List<LoadBalancerIngress> ingresses = resource.getStatus().getLoadBalancer().getIngress();
				for (LoadBalancerIngress ingress : ingresses) {

					instanceConfig.setNonSecurePort(80);
					instanceConfig.setHostname(ingress.getIp());
					instanceConfig.setIpAddress(ingress.getIp());
					instanceConfig.setSecurePortEnabled(false);
					instanceConfig.setNonSecurePortEnabled(true);
					instanceConfig.setAppname(resource.getMetadata().getName());
					instanceConfig.setStatusPageUrl(ingress.getIp());
					instanceConfig.setInstanceId(ingress.getIp());
					final InstanceInfo instanceInfo = new InstanceInfoFactory().create(instanceConfig);
					final ApplicationInfoManager newAppInfo = new ApplicationInfoManager(instanceConfig, instanceInfo);
					newAppInfo.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
					instanceRegistry.register(instanceInfo, false);
				}
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				throw new RuntimeException(cause);
			}

		});
		return watch.toString();
	}


	@GetMapping("/register")
	public String register() {
		instanceConfig.setHostname("somehost");
		instanceConfig.setNonSecurePort(7777);
		instanceConfig.setSecurePortEnabled(false);
		instanceConfig.setNonSecurePortEnabled(true);
		final InstanceInfo instanceInfo = new InstanceInfoFactory().create(instanceConfig);

		final ApplicationInfoManager newAppInfo = new ApplicationInfoManager(instanceConfig, instanceInfo);
		newAppInfo.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
		instanceRegistry.register(instanceInfo, false);
		return instanceRegistry.getApplications().toString();
	}
}
