package me.neonjava.in.limboqueue.velocity.queue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import me.neonjava.in.limboqueue.velocity.LimboQueueVelocity;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class QueueManager {

    private final LimboQueueVelocity plugin;
    
    private final List<LimboPlayer> bypassQueue = new CopyOnWriteArrayList<>();
    private final List<LimboPlayer> ranksQueue = new CopyOnWriteArrayList<>();
    private final List<LimboPlayer> regularQueue = new CopyOnWriteArrayList<>();

    private final Map<UUID, PlayerMetadata> metadataMap = new ConcurrentHashMap<>();
    private final Map<UUID, LimboPlayer> limboPlayers = new ConcurrentHashMap<>();

    private boolean backendFull = false;

    public QueueManager(LimboQueueVelocity plugin) {
        this.plugin = plugin;
    }

    public static class PlayerMetadata {
        public final UUID uuid;
        public boolean bypass = false;
        public int weight = 0;
        public String group = "default";
        public long joinTime = System.currentTimeMillis();

        public PlayerMetadata(UUID uuid) {
            this.uuid = uuid;
        }
    }

    public void setPlayerPermissions(UUID uuid, boolean bypass, int weight, String group) {
        PlayerMetadata meta = this.metadataMap.computeIfAbsent(uuid, PlayerMetadata::new);
        meta.bypass = bypass;
        meta.weight = weight;
        meta.group = group;

        LimboPlayer limboPlayer = this.limboPlayers.get(uuid);
        if (limboPlayer != null) {
            this.bypassQueue.remove(limboPlayer);
            this.ranksQueue.remove(limboPlayer);
            this.regularQueue.remove(limboPlayer);

            if (bypass) {
                this.bypassQueue.add(limboPlayer);
            } else if (weight > 0) {
                this.ranksQueue.add(limboPlayer);
                this.ranksQueue.sort((a, b) -> {
                    PlayerMetadata metaA = this.metadataMap.get(a.getProxyPlayer().getUniqueId());
                    PlayerMetadata metaB = this.metadataMap.get(b.getProxyPlayer().getUniqueId());
                    if (metaA == null || metaB == null) return 0;
                    if (metaB.weight != metaA.weight) {
                        return Integer.compare(metaB.weight, metaA.weight);
                    }
                    return Long.compare(metaA.joinTime, metaB.joinTime);
                });
            } else {
                this.regularQueue.add(limboPlayer);
            }
        }
    }

    private boolean isFullKickReason(Component component) {
        if (component == null) return false;
        try {
            String json = GsonComponentSerializer.gson().serialize(component);
            this.plugin.getLogger().info("Parsing kick reason component JSON: " + json);
            String lower = json.toLowerCase();
            if (lower.contains("full") || lower.contains("max") || lower.contains("limit") || lower.contains("server_full") || lower.contains("disconnect.server_full")) {
                return true;
            }
        } catch (Exception e) {
            this.plugin.getLogger().error("Error serializing kick reason component", e);
        }
        return false;
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        this.plugin.getLogger().info("KickedFromServerEvent fired for player: " + event.getPlayer().getUsername() + 
            ", reason: " + event.getServerKickReason().map(Object::toString).orElse("none"));

        Component reasonComponent = event.getServerKickReason().orElse(null);
        if (isFullKickReason(reasonComponent)) {
            Optional<RegisteredServer> queueServer = this.plugin.getServer().getServer("queue");
            if (queueServer.isPresent()) {
                this.plugin.getLogger().info("Redirecting " + event.getPlayer().getUsername() + " to dummy queue server!");
                // Set the result to Redirect to the dummy queue server to keep the connection alive
                event.setResult(KickedFromServerEvent.RedirectPlayer.create(queueServer.get()));
                
                // Immediately spawn the player in Limbo
                this.plugin.getServer().getScheduler().buildTask(this.plugin, () -> {
                    queuePlayer(event.getPlayer());
                }).schedule();
            } else {
                this.plugin.getLogger().warn("Queue redirection target server 'queue' was not found in velocity.toml!");
            }
        }
    }

    public void queuePlayer(Player player) {
        RegisteredServer target = this.plugin.getServer().getServer("lobby").orElse(null);
        if (target != null && !target.getPlayersConnected().isEmpty()) {
            sendPermissionQuery(player, target);
        }
        this.plugin.getLimboInstance().spawnPlayer(player, new QueueSessionHandler(this.plugin, player));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.metadataMap.remove(uuid);
        LimboPlayer lp = this.limboPlayers.remove(uuid);
        if (lp != null) {
            this.bypassQueue.remove(lp);
            this.ranksQueue.remove(lp);
            this.regularQueue.remove(lp);
        }
    }

    public void addLimboPlayer(LimboPlayer limboPlayer) {
        UUID uuid = limboPlayer.getProxyPlayer().getUniqueId();
        this.limboPlayers.put(uuid, limboPlayer);
        PlayerMetadata meta = this.metadataMap.computeIfAbsent(uuid, PlayerMetadata::new);

        if (meta.bypass) {
            this.bypassQueue.add(limboPlayer);
        } else if (meta.weight > 0) {
            this.ranksQueue.add(limboPlayer);
        } else {
            this.regularQueue.add(limboPlayer);
        }
    }

    public void removeLimboPlayer(LimboPlayer limboPlayer) {
        UUID uuid = limboPlayer.getProxyPlayer().getUniqueId();
        this.limboPlayers.remove(uuid);
        this.bypassQueue.remove(limboPlayer);
        this.ranksQueue.remove(limboPlayer);
        this.regularQueue.remove(limboPlayer);
    }

    public int getQueuePosition(UUID uuid) {
        LimboPlayer lp = this.limboPlayers.get(uuid);
        if (lp == null) return -1;

        if (this.bypassQueue.contains(lp)) {
            return this.bypassQueue.indexOf(lp) + 1;
        }

        int pos = this.bypassQueue.size();
        if (this.ranksQueue.contains(lp)) {
            return pos + this.ranksQueue.indexOf(lp) + 1;
        }

        pos += this.ranksQueue.size();
        if (this.regularQueue.contains(lp)) {
            return pos + this.regularQueue.indexOf(lp) + 1;
        }

        return -1;
    }

    public int getTotalQueueSize() {
        return this.bypassQueue.size() + this.ranksQueue.size() + this.regularQueue.size();
    }

    public void tickQueue() {
        Optional<RegisteredServer> targetOpt = this.plugin.getServer().getServer("lobby");
        if (!targetOpt.isPresent()) return;

        RegisteredServer target = targetOpt.get();
        checkBackendServerFull(target);

        if (!this.backendFull && getTotalQueueSize() > 0) {
            LimboPlayer nextPlayer = null;
            if (!this.bypassQueue.isEmpty()) {
                nextPlayer = this.bypassQueue.remove(0);
            } else if (!this.ranksQueue.isEmpty()) {
                nextPlayer = this.ranksQueue.remove(0);
            } else if (!this.regularQueue.isEmpty()) {
                nextPlayer = this.regularQueue.remove(0);
            }

            if (nextPlayer != null) {
                LimboPlayer finalPlayer = nextPlayer;
                this.plugin.getServer().getScheduler().buildTask(this.plugin, () -> {
                    finalPlayer.disconnect();
                    finalPlayer.getProxyPlayer().createConnectionRequest(target).fireAndForget();
                }).schedule();
            }
        }
    }

    private void checkBackendServerFull(RegisteredServer target) {
        try {
            ServerPing ping = target.ping().get();
            if (ping.getPlayers().isPresent()) {
                ServerPing.Players pInfo = ping.getPlayers().get();
                this.backendFull = pInfo.getOnline() >= pInfo.getMax();
            } else {
                this.backendFull = false;
            }
        } catch (InterruptedException | ExecutionException e) {
            this.backendFull = false;
        }
    }

    private void sendPermissionQuery(Player player, RegisteredServer server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("perm_query");
            out.writeUTF(player.getUniqueId().toString());

            server.sendPluginMessage(LimboQueueVelocity.SYNC_CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) {}
    }
}
