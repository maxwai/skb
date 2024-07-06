package edu.hm.skb.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.jetbrains.annotations.NotNull;

/**
 * Config Injector to be able to inject the Config interface.
 * <p/>
 * This is needed so that the static block in the Config Class is only executed at runtime
 */
@Startup
@ApplicationScoped
public class ConfigInjector {

    /**
     * @return the word list, not shuffled
     */
    @NotNull
    public Config getConfig() {
        return ConfigInstance.CONFIG;
    }
}
