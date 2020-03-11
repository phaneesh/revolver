package io.dropwizard.revolver.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 11/03/20
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeadersConfig {

    public static final String DEFAULT_FORWARDED_BY = "API";

    private String forwardedBy = DEFAULT_FORWARDED_BY;

}
