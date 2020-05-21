package io.dropwizard.revolver.confighandler;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfigUpdateEvent {

    // NOTE: this is full config for the running application
    private JsonNode updatedConfig;

    private Date updatedAt;
}
