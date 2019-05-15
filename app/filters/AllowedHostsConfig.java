package filters;

import com.typesafe.config.Config;
import play.api.routing.HandlerDef;
import play.mvc.Http;
import play.routing.Router;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Optional;

public class AllowedHostsConfig {
    private final List<String> allowed;
    private final List<String> whiteListModifier;
    private final List<String> blackListModifier;

    public AllowedHostsConfig(List<String> allowed, List<String> whiteListModifier, List<String> blackListModifier) {
        this.allowed = allowed;
        this.whiteListModifier = whiteListModifier;
        this.blackListModifier = blackListModifier;
    }

    public List<String> getAllowed() {
        return allowed;
    }

    public boolean byPassAllowedHosts(Http.RequestHeader req) {
        Optional<HandlerDef> handlerDef = req.attrs().getOptional(Router.Attrs.HANDLER_DEF);

        // If there are whiteList modifiers, lets check them first.
        if (whiteListModifier.size() > 0) {

            return whiteListModifier.stream()
                    // If any of the whiteList modifiers are present in the current request,
                    // then let it by pass the allowed hosts filter, otherwise, it CANNOT by
                    // pass the allowed hosts filter.
                    .anyMatch(modifier ->
                        handlerDef.map(def -> def.getModifiers().contains(modifier))
                                  .orElse(false)
                    );
        } else {
            // If the black list is empty, then there is nothing to check
            return blackListModifier.isEmpty() ||
                    blackListModifier.stream()
                            // But if there are blackList modifiers, let if by pass the allowed hosts filter if none
                            // of the modifiers for the current request are present in the black listed modifiers.
                            .noneMatch(modifier ->
                                handlerDef.map(def -> def.getModifiers().contains(modifier))
                                          .orElse(false)
                            );
        }
    }

    public static class AllowedHostsConfigProvider implements Provider<AllowedHostsConfig> {

        private final Config config;

        @Inject
        public AllowedHostsConfigProvider(Config config) {
            this.config = config;
        }

        @Override
        public AllowedHostsConfig get() {
            List<String> allowed = config.getStringList("play.filters.hosts.allowed");
            List<String> whiteList = config.getStringList("play.filters.hosts.routeModifiers.whiteList");
            List<String> blackList = config.getStringList("play.filters.hosts.routeModifiers.blackList");
            return new AllowedHostsConfig(
                    allowed,
                    whiteList,
                    blackList
            );
        }
    }
}
