package my.umn.cs5199.touringapp.grpc;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.maps.routing.v2.ComputeRouteMatrixRequest;
import com.google.maps.routing.v2.ComputeRoutesRequest;
import com.google.maps.routing.v2.ComputeRoutesResponse;
import com.google.maps.routing.v2.Location;
import com.google.maps.routing.v2.PolylineQuality;
import com.google.maps.routing.v2.RouteMatrixDestination;
import com.google.maps.routing.v2.RouteMatrixOrigin;
import com.google.maps.routing.v2.RouteModifiers;
import com.google.maps.routing.v2.RouteTravelMode;
import com.google.maps.routing.v2.RoutesGrpc;
import com.google.maps.routing.v2.RoutingPreference;
import com.google.maps.routing.v2.Waypoint;
import com.google.type.LatLng;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import my.umn.cs5199.touringapp.BuildConfig;

public class RoutesClient {

    private static Metadata.Key API_KEY_HEADER = Metadata.Key.of("x-goog-api-key",
            Metadata.ASCII_STRING_MARSHALLER);
    private static Metadata.Key FIELD_MASK_HEADER = Metadata.Key.of("x-goog-fieldmask",
            Metadata.ASCII_STRING_MARSHALLER);

    private static final URI serviceUri;
    private static final String API_KEY;
    static {
        try {
            serviceUri = new URI("https://routes.googleapis.com:443");
            API_KEY = BuildConfig.MAPS_API_KEY;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Logger logger = Logger.getLogger(RoutesClient.class.getName());
    private final RoutesGrpc.RoutesBlockingStub blockingStub;
    private final RoutesGrpc.RoutesFutureStub futureStub;


    private RoutesClient(Channel channel) {
        blockingStub = RoutesGrpc.newBlockingStub(channel);
        futureStub = RoutesGrpc.newFutureStub(channel);
    }

    public RoutesClient() {
        Channel channel = ChannelFactory.getInstance().getChannel(serviceUri, m -> {
                    m.put(API_KEY_HEADER, API_KEY);
                    // Note that setting the field mask to * is OK for testing, but discouraged in
                    // production.
                    // For example, for ComputeRoutes, set the field mask to
                    // "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"
                    // in order to get the route distances, durations, and encoded polylines.
                    m.put(FIELD_MASK_HEADER, "*");
                }
                );
        blockingStub = RoutesGrpc.newBlockingStub(channel);
        futureStub = RoutesGrpc.newFutureStub(channel);
    }

    public static Waypoint createWaypointForLatLng(double lat, double lng) {
        return Waypoint.newBuilder()
                .setLocation(Location.newBuilder().setLatLng(LatLng.newBuilder().setLatitude(lat).setLongitude(lng)))
                .build();
    }

    public void computeRoute(com.google.android.gms.maps.model.LatLng origin,
                             com.google.android.gms.maps.model.LatLng destination,
                             FutureCallback<ComputeRoutesResponse> callback) {
        ComputeRoutesRequest request = ComputeRoutesRequest.newBuilder()
                .setOrigin(createWaypointForLatLng(origin.latitude, origin.longitude))
                .setDestination(createWaypointForLatLng(destination.latitude, destination.longitude))
                .setTravelMode(RouteTravelMode.BICYCLE)
                .setComputeAlternativeRoutes(false)
                .setPolylineQuality(PolylineQuality.HIGH_QUALITY).build();

        Futures.addCallback(futureStub.computeRoutes(request), callback, ForkJoinPool.commonPool());
    }

    private void computeRoutes() {
        ComputeRoutesRequest request = ComputeRoutesRequest.newBuilder()
                .setOrigin(createWaypointForLatLng(37.420761, -122.081356))
                .setDestination(createWaypointForLatLng(37.420999, -122.086894)).setTravelMode(RouteTravelMode.DRIVE)
                .setRoutingPreference(RoutingPreference.TRAFFIC_AWARE).setComputeAlternativeRoutes(true)
                .setRouteModifiers(
                        RouteModifiers.newBuilder().setAvoidTolls(false).setAvoidHighways(true).setAvoidFerries(true))
                .setPolylineQuality(PolylineQuality.OVERVIEW).build();
        ComputeRoutesResponse response;
        try {
            logger.info("About to send request: " + request.toString());
            response = blockingStub.withDeadlineAfter(2000, TimeUnit.MILLISECONDS).computeRoutes(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Response: " + response.toString());
    }

    private void computeRouteMatrix() {
        ComputeRouteMatrixRequest request = ComputeRouteMatrixRequest.newBuilder()
                .addOrigins(RouteMatrixOrigin.newBuilder().setWaypoint(createWaypointForLatLng(37.420761, -122.081356))
                        .setRouteModifiers(RouteModifiers.newBuilder().setAvoidTolls(false).setAvoidHighways(true)
                                .setAvoidFerries(true)))
                .addOrigins(RouteMatrixOrigin.newBuilder().setWaypoint(createWaypointForLatLng(37.403184, -122.097371)))
                .addDestinations(RouteMatrixDestination.newBuilder()
                        .setWaypoint(createWaypointForLatLng(37.420999, -122.086894)))
                .addDestinations(RouteMatrixDestination.newBuilder()
                        .setWaypoint(createWaypointForLatLng(37.383047, -122.044651)))
                .setTravelMode(RouteTravelMode.DRIVE).setRoutingPreference(RoutingPreference.TRAFFIC_AWARE).build();
        Iterator elements;
        try {
            logger.info("About to send request: " + request.toString());
            elements = blockingStub.withDeadlineAfter(2000, TimeUnit.MILLISECONDS).computeRouteMatrix(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }

        while (elements.hasNext()) {
            logger.info("Element response: " + elements.next().toString());
        }
    }

    /*
    public static void main(String[] args) throws Exception {
        String apiKey = "";

        // The standard TLS port is 443
        Channel channel = NettyChannelBuilder.forAddress("routes.googleapis.com", 443).build();
        channel = ClientInterceptors.intercept(channel, new RoutesInterceptor(apiKey));

        RoutesClient client = new RoutesClient(channel);
        client.computeRoutes();
        client.computeRouteMatrix();
    }*/
}