package ru.ticket.journey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

@Service
@Primary
public class FallbackRoadRouter implements RoadRouter {
    private static final Logger log = LoggerFactory.getLogger(FallbackRoadRouter.class);

    private final ru.ticket.journey.BRouterHttpRoadRouter brouterHttp;
    private final ValhallaRoadRouter valhalla;
    private final OsrmFossgisRoadRouter osrm;
    private final BRouterRoadRouter brouter;

    public FallbackRoadRouter(
            @Autowired(required = false) ru.ticket.journey.BRouterHttpRoadRouter brouterHttp,
            @Autowired(required = false) ValhallaRoadRouter valhalla,
            @Autowired(required = false) OsrmFossgisRoadRouter osrm,
            @Autowired(required = false) BRouterRoadRouter brouter) {
        this.brouterHttp = brouterHttp;
        this.valhalla = valhalla;
        this.osrm = osrm;
        this.brouter = brouter;
        if (brouterHttp == null && valhalla == null && osrm == null && brouter == null) {
            log.warn("router.none_configured");
        } else {
            log.info(
                    "router.chain brouter_http={} valhalla={} osrm={} brouter_jar={}",
                    brouterHttp == null ? "disabled" : "enabled",
                    valhalla == null ? "disabled" : "enabled",
                    osrm == null ? "disabled" : "enabled",
                    brouter == null ? "disabled" : "enabled");
        }
    }

    @Override
    public Road route(Point start, Point end, String mode) {
        BRouterRoadRouter.validate(start);
        BRouterRoadRouter.validate(end);
        if (mode == null || !java.util.Set.of("car", "foot").contains(mode))
            throw ApiException.badRequest("journey.route.invalid-route");
        ApiException last = null;

        if (brouterHttp != null) {
            try { return brouterHttp.route(start, end, mode); }
            catch (ApiException e) {
                if (isInputLimit(e)) throw e;
                log.warn("router.brouter_http_failed status={} reason={}", e.status(), e.getMessage());
                last = e;
            }
        }

        if (valhalla != null) {
            try { return valhalla.route(start, end, mode); }
            catch (ApiException e) {
                if (isInputLimit(e)) throw e;
                log.warn("router.valhalla_failed status={} reason={}", e.status(), e.getMessage());
                last = e;
            }
        }

        if (osrm != null) {
            try { return osrm.route(start, end, mode); }
            catch (ApiException e) {
                if (isInputLimit(e)) throw e;
                log.warn("router.osrm_failed status={} reason={}", e.status(), e.getMessage());
                last = e;
            }
        }

        if (brouter != null) {
            try { return brouter.route(start, end, mode); }
            catch (ApiException e) {
                log.warn("router.brouter_jar_failed status={} reason={}", e.status(), e.getMessage());
                last = e;
            }
        }

        if (last != null && last.status()==HttpStatus.BAD_REQUEST)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"journey.route.no-path");
        if (last != null) throw last;
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
    }

    private static boolean isInputLimit(ApiException e) {
        return java.util.Set.of("journey.route.too-long","journey.route.walk-too-long").contains(e.getMessage());
    }
}
