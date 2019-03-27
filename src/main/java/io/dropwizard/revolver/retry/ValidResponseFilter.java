package io.dropwizard.revolver.retry;


import com.google.common.base.Predicate;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import javax.annotation.Nullable;

/***
 Created by nitish.goyal on 25/02/19
 ***/
@Slf4j
public class ValidResponseFilter<T> implements Predicate<T> {


    @Override
    public boolean apply(@Nullable Object o) {
        return validateResponse(o);
    }

    @Override
    public boolean test(@Nullable Object input) {
        return validateResponse(input);
    }

    private boolean validateResponse(Object object) {
        if(!(object instanceof Response)) {
            return false;
        }
        Response response = (Response)object;
        if(response.code() >= 500){
            log.error("5xxErrorOccurred inside retryer + " + response.code());
        }
        return response.code() >= 500;
    }
}
