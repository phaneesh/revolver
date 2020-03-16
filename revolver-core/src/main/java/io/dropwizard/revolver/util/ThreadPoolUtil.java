package io.dropwizard.revolver.util;

import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import org.apache.commons.lang3.StringUtils;

/***
 Created by nitish.goyal on 06/03/20
 ***/
public class ThreadPoolUtil {

    public static final String DELIMITER = "-";

    public static String getThreadPoolName(RevolverServiceConfig revolverServiceConfig,
            ThreadPoolConfig threadPoolConfig) {
        if (StringUtils.isEmpty(threadPoolConfig.getThreadPoolName())) {
            return StringUtils.EMPTY;
        }
        return getThreadPoolNameForService(revolverServiceConfig.getService(), threadPoolConfig.getThreadPoolName());
    }

    public static String getThreadPoolNameForService(String service, String threadPoolName) {
        return service + DELIMITER + threadPoolName;
    }

}
