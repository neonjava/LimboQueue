package me.neonjava.in.limboqueue.folia;

import org.bukkit.plugin.java.JavaPlugin;
import me.neonjava.in.limboqueue.folia.messaging.FoliaMessageListener;

public class LimboQueueFolia extends JavaPlugin {

    public static final String SYNC_CHANNEL = "limboqueue:sync";

    @Override
    public void onEnable() {
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, SYNC_CHANNEL);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, SYNC_CHANNEL, new FoliaMessageListener(this));
        
        this.getLogger().info("LimboQueue Folia/Canvas companion plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, SYNC_CHANNEL);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, SYNC_CHANNEL);
    }
}
