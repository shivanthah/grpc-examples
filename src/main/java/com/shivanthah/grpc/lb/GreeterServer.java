package com.shivanthah.grpc.lb;

import com.shivanthah.grpc.Greet;
import com.shivanthah.grpc.Greeted;
import com.shivanthah.grpc.GreeterGrpc;
import com.shivanthah.grpc.lb.zookeeperlb.ZookeeperConnection;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.logging.Logger;


public class GreeterServer {


    static String portStr;
    private static final Logger logger = Logger.getLogger(GreeterServer.class.getName());

    private Server server;

    private void start(String port) throws IOException {
        server = ServerBuilder.forPort(Integer.parseInt(port))
                .addService(new GreeterImpl())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                GreeterServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }


    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        /* Argument parsing */
        if (args.length != 2) {
            System.out.println("Usage: helloworld_server PORT zk://ADDR:PORT");
            return;
        }

        String zk_addr;

        try {
            portStr = new String(args[0]);
            zk_addr = new String(args[1]);
        } catch (Exception e) {
            System.out.println("Usage: helloworld_server PORT zk://ADDR:PORT");
            return;
        }

        ZookeeperConnection zk_conn = new ZookeeperConnection();
        if (!zk_conn.connect(zk_addr, "localhost", portStr)) {
            return;
        }
       /* MyDataCenterInstanceConfig instanceConfig = new MyDataCenterInstanceConfig();
        InstanceInfo instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();
        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(instanceConfig, instanceInfo);
        EurekaClient eurekaClient = new DiscoveryClient(applicationInfoManager, new DefaultEurekaClientConfig());*/

        //  eurekaClient.getNextServerFromEureka(zk_addr, false);

        final GreeterServer server = new GreeterServer();
        server.start(portStr);
        server.blockUntilShutdown();
    }


    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        public void sayHello(Greet req,
                             StreamObserver<Greeted> responseObserver) {
            Greeted reply = Greeted.newBuilder().setMessage(
                    "Hello " + req.getName() + " from " + portStr).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }


}