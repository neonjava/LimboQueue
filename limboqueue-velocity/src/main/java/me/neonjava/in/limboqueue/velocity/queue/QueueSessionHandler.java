package me.neonjava.in.limboqueue.velocity.queue;

import com.velocitypowered.api.proxy.Player;
import me.neonjava.in.limboqueue.velocity.LimboQueueVelocity;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class QueueSessionHandler implements LimboSessionHandler {

    private final LimboQueueVelocity plugin;
    private final Player player;
    private LimboPlayer limboPlayer;
    private com.velocitypowered.api.scheduler.ScheduledTask actionbarTask;

    public QueueSessionHandler(LimboQueueVelocity plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void onSpawn(Limbo server, LimboPlayer player) {
        this.limboPlayer = player;
        this.limboPlayer.disableFalling();
        this.plugin.getQueueManager().addLimboPlayer(player);

        // Show a dark void welcome title
        Title title = Title.title(
                Component.text("VOID LIMBO QUEUE").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD),
                Component.text("Connecting to backend server...").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500))
        );
        this.player.showTitle(title);

        // Periodic actionbar updates
        this.actionbarTask = this.plugin.getServer().getScheduler().buildTask(this.plugin, () -> {
            int position = this.plugin.getQueueManager().getQueuePosition(this.player.getUniqueId());
            int total = this.plugin.getQueueManager().getTotalQueueSize();
            if (position != -1) {
                this.player.sendActionBar(Component.text()
                        .append(Component.text("Queue Position: ").color(NamedTextColor.YELLOW))
                        .append(Component.text(position).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                        .append(Component.text(" / ").color(NamedTextColor.GRAY))
                        .append(Component.text(total).color(NamedTextColor.GRAY))
                        .build()
                );
            }
        }).repeat(1, TimeUnit.SECONDS).schedule();
    }

    @Override
    public void onDisconnect() {
        if (this.actionbarTask != null) {
            this.actionbarTask.cancel();
        }
        this.plugin.getQueueManager().removeLimboPlayer(this.limboPlayer);
    }
}
