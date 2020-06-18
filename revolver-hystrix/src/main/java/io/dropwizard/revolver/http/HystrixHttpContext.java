package io.dropwizard.revolver.http;

import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 05/03/20
 ***/
public class HystrixHttpContext extends RevolverHttpContext {

    @Override
    public void initialize(Environment environment, RevolverConfigHolder revolverConfigHolder,
            MetricRegistry metrics) {
        HystrixCodaHaleMetricsPublisher metricsPublisher = new HystrixCodaHaleMetricsPublisher(
                metrics);
        HystrixUtil.initializeHystrix(environment, metricsPublisher, revolverConfigHolder.getConfig());
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.HYSTRIX;
    }

}
