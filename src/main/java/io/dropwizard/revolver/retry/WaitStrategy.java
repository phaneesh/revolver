package io.dropwizard.revolver.retry;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public enum WaitStrategy {

    FIXED,
    EXPONENTIAL,
    FIBONACCI,
    INCREMENTAL,
    NO_WAIT

}
