package io.dropwizard.revolver.splitting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 25/02/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SplitConfig {

    private String path;

    //Weighted round robin
    private double wrr;

    private double from;
    private double to;

}
