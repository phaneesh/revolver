package io.dropwizard.revolver.confighandler;

public interface ConfigUpdateEventListener {

    void configUpdated(ConfigUpdateEvent configUpdateEvent);


}
