package io.dropwizard.revolver.retry;


import com.google.common.base.Predicate;
import okhttp3.Response;

import javax.annotation.Nullable;

/***
 Created by nitish.goyal on 25/02/19
 ***/
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
        return response.code() >= 500;
    }
}
