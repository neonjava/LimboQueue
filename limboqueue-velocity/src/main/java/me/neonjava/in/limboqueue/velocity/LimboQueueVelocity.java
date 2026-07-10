package me.neonjava.in.limboqueue.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import me.neonjava.in.limboqueue.velocity.queue.QueueManager;
import me.neonjava.in.limboqueue.velocity.messaging.VelocityMessageListener;
import org.slf4j.Logger;

@Plugin(id = "limboqueue", name = "LimboQueue", version = "1.0.0", authors = {"neonjava"})
public class LimboQueueVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private LimboFactory limboFactory;
    private Limbo limboInstance;
    private QueueManager queueManager;
    
    public static final MinecraftChannelIdentifier SYNC_CHANNEL = MinecraftChannelIdentifier.from("limboqueue:sync");

    @Inject
    public LimboQueueVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.limboFactory = (LimboFactory) this.server.getPluginManager()
                .getPlugin("limboapi")
                .flatMap(PluginContainer::getInstance)
                .orElseThrow(() -> new IllegalStateException("LimboAPI plugin was not found!"));

        VirtualWorld queueWorld = this.limboFactory.createVirtualWorld(
                Dimension.THE_END, // Black end void theme
                0.0, 64.0, 0.0,
                90.0f, 0.0f
        );

        this.limboInstance = this.limboFactory.createLimbo(queueWorld)
                .setName("LimboQueue")
                .setWorldTime(18000); // Night time for dark theme

        this.queueManager = new QueueManager(this);
        
        this.server.getChannelRegistrar().register(SYNC_CHANNEL);
        this.server.getEventManager().register(this, new VelocityMessageListener(this));
        this.server.getEventManager().register(this, this.queueManager);

        // Start dummy TCP server on port 25567 to accept queue redirection connections
        this.server.getScheduler().buildTask(this, () -> {
            try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(25567)) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        java.net.Socket socket = serverSocket.accept();
                        // Just discard/close the socket immediately, Velocity only checks if port is listening
                        socket.close();
                    } catch (java.io.IOException ignored) {}
                }
            } catch (java.io.IOException e) {
                this.logger.error("Failed to start dummy redirection TCP server on port 25567", e);
            }
        }).scheduleAsync();

        // Schedule queue task ticker
        this.server.getScheduler().buildTask(this, () -> this.queueManager.tickQueue())
                .repeat(1, TimeUnit.SECONDS)
                .schedule();

        this.logger.info("LimboQueue Velocity plugin has been initialized successfully!");
    }

    public ProxyServer getServer() {
        return this.server;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public Limbo getLimboInstance() {
        return this.limboInstance;
    }

    public QueueManager getQueueManager() {
        return this.queueManager;
    }
}
