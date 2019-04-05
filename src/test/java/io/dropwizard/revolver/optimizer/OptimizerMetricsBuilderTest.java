package io.dropwizard.revolver.optimizer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.msgpack.jackson.dataformat.Tuple;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.dropwizard.revolver.optimizer.utils.OptimizerUtils.PREFIX;
import static io.dropwizard.revolver.optimizer.utils.OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS;

/***
 Created by nitish.goyal on 30/03/19
 ***/
public class OptimizerMetricsBuilderTest extends BaseRevolverTest {

    @Before
    public void setup()
            throws CertificateException, InterruptedException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
                   KeyManagementException, IOException {
        super.setup();
        MetricRegistry metrics = optimizerMetricsBuilder.getMetrics();
        metrics.gauge(PREFIX + ".test-without-pool.test." + ROLLING_MAX_ACTIVE_THREADS, new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                return new Gauge() {
                    @Override
                    public Object getValue() {
                        return 10;
                    }
                };
            }
        });
        metrics.gauge(PREFIX + ".test." + ROLLING_MAX_ACTIVE_THREADS, new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                return new Gauge() {
                    @Override
                    public Object getValue() {
                        return 10;
                    }
                };
            }
        });

    }

    @Test
    public void testMetricsBuilder() {
        optimizerMetricsBuilder.run();
        Map<Tuple<Long, String>, OptimizerMetrics> cache = optimizerMetricsCache.getCache();
        AtomicBoolean metricFound = new AtomicBoolean(false);
        cache.forEach((k, v) -> {
            if(v.getMetrics()
                       .containsKey(ROLLING_MAX_ACTIVE_THREADS) && v.getMetrics()
                                                                           .get(ROLLING_MAX_ACTIVE_THREADS)
                                                                           .intValue() == 10 && OptimizerUtils.getMetricsToRead()
                       .contains(ROLLING_MAX_ACTIVE_THREADS)) {
                metricFound.set(true);
            }
        });
        Assert.assertTrue(metricFound.get());
    }


    @Test
    public void testOptimizerConfigUpdate(){
        optimizerMetricsBuilder.run();
        optimizerConfigUpdater.run();
        Assert.assertEquals(RevolverBundle.getServiceConfig().get("test").getConnectionPoolSize(), 10);
    }
}
