package io.dropwizard.revolver.http;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.core.SentinelUtil;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 06/03/20
 ***/
public class SentinelHttpContext extends RevolverHttpContext {

    @Override
    public void initialize(Environment environment, RevolverConfig revolverConfig, MetricRegistry metrics) {
        SentinelUtil.initializeSentinel(revolverConfig);
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.SENTINEL;
    }
}
