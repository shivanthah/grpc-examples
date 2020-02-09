package com.shivanthah.grpc.lb;

import com.netflix.discovery.DefaultEurekaClientConfig;
import com.shivanthah.grpc.Greet;
import com.shivanthah.grpc.Greeted;
import com.shivanthah.grpc.GreeterGrpc;

import com.shivanthah.grpc.lb.eureka.EurekaNameResolver;
import com.shivanthah.grpc.lb.eureka.EurekaNameResolverProvider;
import com.shivanthah.grpc.lb.zookeeperlb.ZkNameResolverProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GreetedClient {
    private static final Logger logger = Logger.getLogger("Client");

    private final ManagedChannel channel;
    private final GreeterGrpc.GreeterBlockingStub blockingStub;

    public GreetedClient(String addr) {
        this(ManagedChannelBuilder.forTarget(addr)
                .defaultLoadBalancingPolicy("round_robin")
                .nameResolverFactory(new ZkNameResolverProvider())
                //.nameResolverFactory(new EurekaNameResolverProvider(new DefaultEurekaClientConfig(),"grpc.port"))
                .usePlaintext());
    }


    GreetedClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }


    public void greet() {
        Greet request = Greet.newBuilder().setName("Shivantha").build();
        Greeted response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: greeted_client zk://ADDR:PORT");
            return;
        }
        GreetedClient client = new GreetedClient(args[0]);
        try {
            while (true) {
                client.greet();
                Thread.sleep(1000);
            }
        } finally {
            client.shutdown();
        }
    }
}