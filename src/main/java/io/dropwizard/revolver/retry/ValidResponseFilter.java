package io.dropwizard.revolver.retry;


import com.google.common.base.Predicate;
import org.apache.http.client.methods.CloseableHttpResponse;

import javax.annotation.Nullable;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class ValidResponseFilter implements Predicate<Object> {


    @Override
    public boolean apply(@Nullable Object o) {
        return validateResponse(o);
    }

    @Override
    public boolean test(@Nullable Object input) {
        return validateResponse(input);
    }

    private boolean validateResponse(Object object) {
        if(!(object instanceof CloseableHttpResponse)) {
            return false;
        }
        CloseableHttpResponse response = (CloseableHttpResponse)object;
        if(response.getStatusLine() == null) {
            return false;
        }
        int statusCode = response.getStatusLine()
                .getStatusCode();

        return !(statusCode >= 200 && statusCode < 300);
    }
}
