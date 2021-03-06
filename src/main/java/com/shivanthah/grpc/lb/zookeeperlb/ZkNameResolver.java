package com.shivanthah.grpc.lb.zookeeperlb;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class ZkNameResolver extends NameResolver implements Watcher {

    public static final String PATH = "/grpc_greeter_service";

    public static final int TIMEOUT_MS = 2000;

    private URI zkUri;
    private ZooKeeper zoo;
    private Listener listener;
    private final Logger logger = Logger.getLogger("ZK");

    @Override
    public void process(WatchedEvent we) {
        if (we.getType() == Event.EventType.None) {
            logger.info("Connection expired");
        } else {
            try {
                List<String> servers = zoo.getChildren(PATH, false);
                AddServersToListener(servers);
                zoo.getChildren(PATH, this);
            } catch (Exception ex) {
                logger.info(ex.getMessage());
            }
        }
    }

    private void AddServersToListener(List<String> servers) {
        List<EquivalentAddressGroup> addrs = new ArrayList<EquivalentAddressGroup>();
        logger.info("Updating server list");
        for (String child : servers) {
            try {
                logger.info("Online: " + child);
                URI uri = new URI("dummy://" + child);
                // Convert "host:port" into host and port
                String host = uri.getHost();
                int port = uri.getPort();
                List<SocketAddress> sockaddrs_list = new ArrayList<SocketAddress>();
                sockaddrs_list.add(new InetSocketAddress(host, port));
                addrs.add(new EquivalentAddressGroup(sockaddrs_list));
            } catch (Exception ex) {
                logger.info("Unparsable server address: " + child);
                logger.info(ex.getMessage());
            }
        }
        if (addrs.size() > 0) {
            listener.onAddresses(addrs, Attributes.EMPTY);
        } else {
            logger.info("No servers online. Keep looking");
        }
    }


    public ZkNameResolver(URI zkUri) {
        this.zkUri = zkUri;
    }

    @Override
    public String getServiceAuthority() {
        return zkUri.getAuthority();
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        try {
            String zkaddr = zkUri.getHost().toString() + ":" + Integer.toString(zkUri.getPort());
            logger.info("Connecting to Zookeeper Address " + zkaddr);

            this.zoo = new ZooKeeper(zkaddr, TIMEOUT_MS, new Watcher() {
                public void process(WatchedEvent we) {
                    if (we.getState() == Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
            logger.info("Connected!");
        } catch (Exception e) {
            logger.info("Failed to connect");
            return;
        }


        try {
            Stat stat = zoo.exists(PATH, true);
            if (stat == null) {
                logger.info("PATH does not exist.");
            } else {
                logger.info("PATH exists");
            }
        } catch (Exception e) {
            logger.info("Failed to get stat");
            return;
        }

        try {
            final CountDownLatch connectedSignal1 = new CountDownLatch(1);
            List<String> servers = zoo.getChildren(PATH, this);
            AddServersToListener(servers);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Override
    public void shutdown() {
    }
}