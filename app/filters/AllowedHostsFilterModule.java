package filters;

import com.google.inject.AbstractModule;

public class AllowedHostsFilterModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AllowedHostsConfig.class).toProvider(AllowedHostsConfig.AllowedHostsConfigProvider.class);
    }
}
