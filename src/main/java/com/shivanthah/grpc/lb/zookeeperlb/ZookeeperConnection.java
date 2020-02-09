package com.shivanthah.grpc.lb.zookeeperlb;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class ZookeeperConnection {
    private static final Logger logger = Logger.getLogger("ZooKeeper");
    private ZooKeeper zoo;

    public void ZookeeperConnection() {
    }

    public boolean connect(String zkUriStr, String serverIp, String portStr)
            throws IOException, InterruptedException {
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        String zkhostport;
        try {
            URI zkUri = new URI(zkUriStr);
            zkhostport = zkUri.getHost().toString() + ":" + Integer.toString(zkUri.getPort());
        } catch (Exception e) {
            logger.severe("Could not parse zk URI " + zkUriStr);
            return false;
        }

        zoo = new ZooKeeper(zkhostport, 5000, new Watcher() {
            public void process(WatchedEvent we) {
                if (we.getState() == Event.KeeperState.SyncConnected) {
                    connectedSignal.countDown();
                }
            }
        });
        /* Wait for zookeeper connection */
        connectedSignal.await();

        String path = "/grpc_greeter_service";
        Stat stat;
        String currTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        try {
            stat = zoo.exists(path, true);
            if (stat == null) {
                zoo.create(path, currTime.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            logger.severe("Failed to create path");
            return false;
        }

        String server_addr = path + "/" + serverIp + ":" + portStr;
        try {
            stat = zoo.exists(server_addr, true);
            if (stat == null) {
                try {
                    zoo.create(server_addr, currTime.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                } catch (Exception e) {
                    logger.severe("Failed to create server_data");
                    return false;
                }
            } else {
                try {
                    zoo.setData(server_addr, currTime.getBytes(), stat.getVersion());
                } catch (Exception e) {
                    logger.severe("Failed to update server_data");
                    return false;
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to add server_data");
            return false;
        }
        return true;
    }

    // Method to disconnect from zookeeper server
    public void close() throws InterruptedException {
        zoo.close();
    }
}
