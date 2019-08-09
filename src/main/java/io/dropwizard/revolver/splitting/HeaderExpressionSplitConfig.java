package io.dropwizard.revolver.splitting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 15/04/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeaderExpressionSplitConfig {

    private String path;

    private String header;
    private String expression;

    //Expressions would be evaluated based on order
    private int order;

}
