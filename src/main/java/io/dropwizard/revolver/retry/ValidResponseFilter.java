package io.dropwizard.revolver.retry;


import com.google.common.base.Predicate;
import okhttp3.Response;

import javax.annotation.Nullable;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class ValidResponseFilter implements Predicate<Response> {


    @Override
    public boolean apply(@Nullable Response o) {
        return validateResponse(o);
    }

    @Override
    public boolean test(@Nullable Response input) {
        return validateResponse(input);
    }

    private boolean validateResponse(@Nullable Response response) {
        if (response == null) return false;
        return response.code() == 503 || response.code() == 504;
    }
}
