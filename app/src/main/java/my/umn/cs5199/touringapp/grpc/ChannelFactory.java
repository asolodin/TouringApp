package my.umn.cs5199.touringapp.grpc;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.android.AndroidChannelBuilder;

public class ChannelFactory {

    private static final ConcurrentHashMap<URI, Consumer<Metadata>>
            interceptors = new ConcurrentHashMap<>();

    private static final ChannelFactory INSTANCE = new ChannelFactory();

    public static ChannelFactory getInstance() {
        return INSTANCE;
    }

    LoadingCache<URI, Channel> channels = CacheBuilder.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(4, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<URI, Channel>() {
                        public Channel load(URI key) {
                            return openChannel(key);
                        }
                    });

    private Channel openChannel(URI uri) {
        Channel channel = AndroidChannelBuilder.forAddress(uri.getHost(), uri.getPort()).build();
        channel = ClientInterceptors.intercept(channel, new ClientInterceptor() {
            @Override
            public ClientCall interceptCall(MethodDescriptor method,
                                            CallOptions callOptions, Channel next) {
                ClientCall call = next.newCall(method, callOptions);
                call = new ForwardingClientCall.SimpleForwardingClientCall(call) {
                    @Override
                    public void start(Listener responseListener, Metadata headers) {
                        interceptors.get(uri).accept(headers);
                        super.start(responseListener, headers);
                    }
                };
                return call;
            }
        });
        return channel;
    }

    public Channel getChannel(URI uri, Consumer<Metadata> interceptor) {
        interceptors.computeIfAbsent(uri, k -> interceptor);
        try {
            return channels.get(uri);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
