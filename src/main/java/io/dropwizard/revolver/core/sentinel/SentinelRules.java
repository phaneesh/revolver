package io.dropwizard.revolver.core.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 19/07/19
 ***/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SentinelRules {

    private List<FlowRule> flowRules = Lists.newArrayList();

}
