package io.dropwizard.revolver.confighandler;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigLoadInfo {

    @JsonProperty("hash")
    private String previousConfigHash;

    @JsonProperty("loadTime")
    private Date previousLoadTime;

    public ConfigLoadInfo(Date previousLoadTime) {
        this.previousLoadTime = previousLoadTime;
    }

    public ConfigLoadInfo copy(){
        return new ConfigLoadInfo(this.previousConfigHash, this.previousLoadTime);
    }
}
