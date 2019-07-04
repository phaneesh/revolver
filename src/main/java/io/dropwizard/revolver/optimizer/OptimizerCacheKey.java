package io.dropwizard.revolver.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 10/04/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptimizerCacheKey {

    private long time;
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OptimizerCacheKey)) return false;

        OptimizerCacheKey that = (OptimizerCacheKey) o;

        if (time != that.time) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = (int) (time ^ (time >>> 32));
        result = 31 * result + name.hashCode();
        return result;
    }
}
