package me.neonjava.in.limboqueue.folia.messaging;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import me.neonjava.in.limboqueue.folia.LimboQueueFolia;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

public class FoliaMessageListener implements PluginMessageListener {

    private final LimboQueueFolia plugin;

    public FoliaMessageListener(LimboQueueFolia plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(LimboQueueFolia.SYNC_CHANNEL)) return;

        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(message);
            DataInputStream in = new DataInputStream(bytes);
            String subchannel = in.readUTF();

            if (subchannel.equals("perm_query")) {
                String uuidString = in.readUTF();
                UUID targetUuid = UUID.fromString(uuidString);

                // Asynchronously load LuckPerms data (safe for Folia region threading)
                Bukkit.getAsyncScheduler().runNow(this.plugin, (task) -> {
                    try {
                        LuckPerms luckPerms = LuckPermsProvider.get();
                        luckPerms.getUserManager().loadUser(targetUuid).thenAccept(user -> {
                            if (user == null) return;

                            // Retrieve bypass permission
                            boolean bypass = user.getCachedData().getPermissionData().checkPermission("limboqueue.bypass").asBoolean();

                            // Retrieve weight level (meta: queue-priority)
                            int weight = user.getCachedData().getMetaData().getMetaValue("queue-priority", Integer::parseInt).orElse(0);

                            // Retrieve primary group
                            String group = user.getPrimaryGroup();

                            // Send response back using any online player on this Folia region/thread
                            sendResponse(targetUuid, bypass, weight, group);
                        });
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Failed to load LuckPerms user " + targetUuid + ": " + e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to process queue sync plugin message: " + e.getMessage());
        }
    }

    private void sendResponse(UUID uuid, boolean bypass, int weight, String group) {
        Player bridgePlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (bridgePlayer == null) return; // No bridge player online to route packet

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("perm_response");
            out.writeUTF(uuid.toString());
            out.writeBoolean(bypass);
            out.writeInt(weight);
            out.writeUTF(group);

            bridgePlayer.sendPluginMessage(this.plugin, LimboQueueFolia.SYNC_CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) {}
    }
}
