package me.neonjava.in.limboqueue.velocity.messaging;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import me.neonjava.in.limboqueue.velocity.LimboQueueVelocity;

public class VelocityMessageListener {

    private final LimboQueueVelocity plugin;

    public VelocityMessageListener(LimboQueueVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(LimboQueueVelocity.SYNC_CHANNEL)) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(event.getData());
            DataInputStream in = new DataInputStream(bytes);
            String subchannel = in.readUTF();

            if (subchannel.equals("perm_response")) {
                UUID playerUuid = UUID.fromString(in.readUTF());
                boolean bypass = in.readBoolean();
                int weight = in.readInt();
                String group = in.readUTF();

                this.plugin.getQueueManager().setPlayerPermissions(playerUuid, bypass, weight, group);
                this.plugin.getLogger().debug("Synced LP permissions for UUID " + playerUuid + ": bypass=" + bypass + ", weight=" + weight);
            }
        } catch (IOException e) {
            this.plugin.getLogger().error("Failed to parse plugin messaging sync package", e);
        }
    }
}
