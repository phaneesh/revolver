package io.dropwizard.revolver.confighandler;

import java.util.Map;

public interface ConfigUpdateEventListener {

    void configUpdated(ConfigUpdateEvent configUpdateEvent);

    void initConfigLoadInfo(Map<String, ConfigLoadInfo> initialConfigLoadInfos);

}
