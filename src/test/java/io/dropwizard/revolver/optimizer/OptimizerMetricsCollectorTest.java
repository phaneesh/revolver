package io.dropwizard.revolver.optimizer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.dropwizard.revolver.optimizer.utils.OptimizerUtils.*;

/***
 Created by nitish.goyal on 30/03/19
 ***/
public class OptimizerMetricsCollectorTest extends BaseRevolverTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new RevolverRequestResource(environment.getObjectMapper(), RevolverBundle.msgPackObjectMapper,
                                                     inMemoryPersistenceProvider, callbackHandler, new MetricRegistry(), revolverConfig
            ))
            .build();

    @Before
    public void setup()
            throws CertificateException, InterruptedException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
                   KeyManagementException, IOException {
        super.setup();
        MetricRegistry metrics = optimizerMetricsCollector.getMetrics();
        metrics.gauge(THREAD_POOL_PREFIX + ".test-without-pool.test." + ROLLING_MAX_ACTIVE_THREADS,
                      new MetricRegistry.MetricSupplier<Gauge>() {
                          @Override
                          public Gauge newMetric() {
                              return new Gauge() {
                                  @Override
                                  public Object getValue() {
                                      return 10;
                                  }
                              };
                          }
                      }
                     );

        metrics.gauge(THREAD_POOL_PREFIX + ".test." + ROLLING_MAX_ACTIVE_THREADS, new MetricRegistry.MetricSupplier<Gauge>() {
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

        metrics.gauge("test" + ".test.test." + LATENCY_PERCENTILE_99, new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                return new Gauge() {
                    @Override
                    public Object getValue() {
                        return 200;
                    }
                };
            }
        });


        metrics.gauge("test" + ".test.test." + LATENCY_PERCENTILE_50, new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                return new Gauge() {
                    @Override
                    public Object getValue() {
                        return 100;
                    }
                };
            }
        });

        metrics.gauge("test" + ".test.test." + LATENCY_PERCENTILE_75, new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                return new Gauge() {
                    @Override
                    public Object getValue() {
                        return 150;
                    }
                };
            }
        });

    }

    @Test
    public void testMetricsBuilder() {
        optimizerMetricsCollector.run();
        Map<OptimizerCacheKey, OptimizerMetrics> cache = optimizerMetricsCache.getCache();
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
    public void testRevolverConfigUpdate() {
        optimizerMetricsCollector.run();
        revolverConfigUpdater.run();
        Assert.assertEquals(RevolverBundle.getServiceConfig()
                                    .get("test")
                                    .getConnectionPoolSize(), 21);
    }

    @Test
    public void testRevolverConfigTimeUpdate() {
        optimizerMetricsCollector.run();
        revolverConfigUpdater.run();
        stubFor(get(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(200)
                                                               .withHeader("Content-Type", "application/json")));
        Assert.assertEquals(resources.client()
                                    .target("/apis/test/v1/test")
                                    .request()
                                    .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                                            .toString())
                                    .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                                            .toString())
                                    .get()
                                    .getStatus(), 200);
    }
}
