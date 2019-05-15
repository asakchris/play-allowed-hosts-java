package filters;

import akka.util.ByteString;
import play.http.HttpErrorHandler;
import play.libs.streams.Accumulator;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class AllowedHostsFilter extends EssentialFilter {

    private final AllowedHostsConfig config;
    private final HttpErrorHandler errorHandler;

    private final List<HostMatcher> hostMatchers;

    @Inject
    public AllowedHostsFilter(AllowedHostsConfig config, HttpErrorHandler errorHandler) {
        this.config = config;
        this.errorHandler = errorHandler;
        this.hostMatchers = config.getAllowed().stream().map(HostMatcher::new).collect(toList());
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return new EssentialAction() {
            @Override
            public Accumulator<ByteString, Result> apply(Http.RequestHeader requestHeader) {
                if (config.byPassAllowedHosts(requestHeader) || allowedHost(requestHeader)) {
                    return next.apply(requestHeader);
                }

                return Accumulator.done(
                    errorHandler.onClientError(requestHeader, Http.Status.BAD_REQUEST, "Host not allowed: " + requestHeader.host())
                );
            }
        };
    }

    private boolean allowedHost(Http.RequestHeader requestHeader) {
        return this.hostMatchers
                .stream()
                .anyMatch(hostMatcher -> hostMatcher.match(requestHeader.host()));
    }

    private static class HostMatcher {
        private final boolean isSuffix;
        private final HostAndPort patternHostAndPort;

        HostMatcher(String pattern) {
            this.isSuffix = pattern.startsWith(".");
            this.patternHostAndPort = HostAndPort.get(pattern);
        }

        boolean match(String hostHeader) {
            HostAndPort requestHostAndPort = HostAndPort.get(hostHeader);
            boolean hostMatches = isSuffix ? ("." + requestHostAndPort.host).endsWith(patternHostAndPort.host)
                                           : requestHostAndPort.host.equals(patternHostAndPort.host);

            boolean portMatches = requestHostAndPort.port != null &&
                                  (patternHostAndPort.port == null || patternHostAndPort.port.equals(requestHostAndPort.port));

            return hostMatches && portMatches;
        }
    }

    private static class HostAndPort {
        private final String host;
        private final Integer port;

        private HostAndPort(String host, Integer port) {
            this.host = normalizeHost(host);
            this.port = port;
        }

        private static String normalizeHost(String host) {
            String normalizedHost = host.toLowerCase(java.util.Locale.ENGLISH);
            if (normalizedHost.endsWith(".")) {
                return normalizedHost.substring(0, normalizedHost.length() - 1);
            }
            return normalizedHost;
        }

        static HostAndPort get(String hostHeader) {
            String[] split = hostHeader.trim().split(":", 2);

            // If it has host and maybe a port
            if (split.length == 2) {
                String host = split[0];
                String port = split[1];

                if (isNumber(port)) {
                    // Both host and port are available
                    return new HostAndPort(host, Integer.parseInt(port));
                }

                // Port is not available
                return new HostAndPort(host, null);
            }

            // Only the host is available
            return new HostAndPort(split[0], null);
        }

        private static boolean isNumber(String s) {
            int size = s.length();
            for (int i = 0; i < size; i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
