package io.dropwizard.revolver.core.config.hystrix;

import com.google.common.base.Strings;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet;
import com.netflix.hystrix.strategy.HystrixPlugins;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 23/11/19
 ***/
public class HystrixUtil {

    public static void initializeHystrix(Environment environment, HystrixCodaHaleMetricsPublisher metricsPublisher,
            RevolverConfig revolverConfig) {
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
        if (Strings.isNullOrEmpty(revolverConfig.getHystrixStreamPath())) {
            environment.getApplicationContext()
                    .addServlet(HystrixMetricsStreamServlet.class, "/hystrix.stream");
        } else {
            environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class,
                    revolverConfig.getHystrixStreamPath());
        }
    }

}
