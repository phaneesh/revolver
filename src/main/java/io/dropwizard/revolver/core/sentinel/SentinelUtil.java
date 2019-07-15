package io.dropwizard.revolver.core.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.google.common.collect.Lists;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelCommandConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelFlowControlConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@Slf4j
public class SentinelUtil {

    public static void initializeSentinel(RevolverConfig revolverConfig) {

        List<String> rulesInitialized = Lists.newArrayList();
        SentinelRules sentinelRules = new SentinelRules();
        for (RevolverServiceConfig config : revolverConfig.getServices()) {
            if (config.getSentinelCommandConfig() == null) {
                continue;
            }
            SentinelCommandConfig sentinelCommandConfig = config.getSentinelCommandConfig();
            addRules(sentinelCommandConfig, sentinelRules, rulesInitialized);
            String type = config.getType();
            switch (type) {
                case "http":
                    addRulesForApis((RevolverHttpServiceConfig) config, sentinelRules, rulesInitialized);
                    break;
                default:
                    log.warn("Unsupported Service type: " + type);

            }
        }
        initializeRules(sentinelRules);
    }

    private static void initializeRules(SentinelRules sentinelRules) {
        FlowRuleManager.loadRules(sentinelRules.getFlowRules());
    }

    private static void addRules(SentinelCommandConfig sentinelCommandConfig, SentinelRules sentinelRules,
            List<String> poolsInitialized) {

        if (sentinelCommandConfig == null) {
            return;
        }
        addFlowRules(sentinelCommandConfig.getFlowControlConfig(), sentinelRules, poolsInitialized);


    }

    private static void addFlowRules(SentinelFlowControlConfig flowControlConfig, SentinelRules sentinelRules,
            List<String> poolsInitialized) {

        if (flowControlConfig == null || StringUtils.isEmpty(flowControlConfig.getPoolName()) || poolsInitialized
                .contains(flowControlConfig.getPoolName())) {
            return;
        }
        poolsInitialized.add(flowControlConfig.getPoolName());
        FlowRule flowRule = new FlowRule();
        flowRule.setResource(flowControlConfig.getPoolName());
        flowRule.setCount(flowControlConfig.getConcurrency());
        setGrade(flowRule, flowControlConfig);
        setControlBehaviour(flowRule, flowControlConfig);

        sentinelRules.getFlowRules().add(flowRule);
    }

    private static void setGrade(FlowRule flowRule, SentinelFlowControlConfig flowControlConfig) {
        if (flowControlConfig.getGrade() == null) {
            //Default behaviour is thread
            flowRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
            return;
        }
        switch (flowControlConfig.getGrade()) {
            case FLOW_GRADE_QPS:
                flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
                break;
            case FLOW_GRADE_THREAD:
                flowRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
                break;
        }
    }

    private static void setControlBehaviour(FlowRule flowRule, SentinelFlowControlConfig flowControlConfig) {
        if (flowControlConfig.getSentinelControlBehavior() == null) {
            flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            return;
        }
        switch (flowControlConfig.getSentinelControlBehavior()) {
            case CONTROL_BEHAVIOR_DEFAULT:
                flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
                break;
            case CONTROL_BEHAVIOR_WARM_UP:
                flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
                break;
            case CONTROL_BEHAVIOR_RATE_LIMITER:
                flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
                break;
        }
    }

    private static void addRulesForApis(RevolverHttpServiceConfig config,
            SentinelRules sentinelRules, List<String> rulesInitialized) {
        config.getApis().forEach(revolverHttpApiConfig -> {
            addRules(revolverHttpApiConfig.getSentinelRunTime(), sentinelRules, rulesInitialized);
        });
    }

}
