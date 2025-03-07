package net.buycraft.plugin.bukkit;

import com.google.common.collect.ImmutableSet;
import net.buycraft.plugin.BuyCraftAPI;
import net.buycraft.plugin.IBuycraftPlatform;
import net.buycraft.plugin.UuidUtil;
import net.buycraft.plugin.data.QueuedPlayer;
import net.buycraft.plugin.data.responses.ServerInformation;
import net.buycraft.plugin.execution.placeholder.PlaceholderManager;
import net.buycraft.plugin.execution.strategy.CommandExecutor;
import net.buycraft.plugin.platform.PlatformInformation;
import net.buycraft.plugin.platform.PlatformType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public abstract class BukkitBuycraftPlatformBase implements IBuycraftPlatform {
    private static final int MAXIMUM_USABLE_INVENTORY_SIZE = 36;
    private final BuycraftPluginBase plugin;

    protected BukkitBuycraftPlatformBase(final BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public BuyCraftAPI getApiClient() {
        return plugin.getApiClient();
    }

    @Override
    public PlaceholderManager getPlaceholderManager() {
        return plugin.getPlaceholderManagerInstance();
    }

    @Override
    public void dispatchCommand(String command) {
        // Synchronous command dispatch is still fine. 
        plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Executes a Runnable immediately on a separate thread.
     */
    @Override
    public void executeAsync(Runnable runnable) {
        // Folia: immediate async = runDelayed with 0 ms
        Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            scheduledTask -> runnable.run(),
            0L,
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }

    /**
     * Executes a Runnable after a delay on a separate thread.
     */
    @Override
    public void executeAsyncLater(Runnable runnable, long time, TimeUnit unit) {
        // Folia: async delayed in ms
        long delayMs = unit.toMillis(time);
        Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            scheduledTask -> runnable.run(),
            delayMs,
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }

    /**
     * Executes a Runnable immediately on the main (synchronous) thread.
     */
    @Override
    public void executeBlocking(Runnable runnable) {
        // Folia: immediate sync = runDelayed with 0 ticks
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            runnable.run();
        }, 0L);
    }

    /**
     * Executes a Runnable after a delay on the main (synchronous) thread.
     */
    @Override
    public void executeBlockingLater(Runnable runnable, long time, TimeUnit unit) {
        // Folia synchronous tasks measure delay in ticks, so convert ms -> ticks
        long delayTicks = unit.toMillis(time) / 50;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            runnable.run();
        }, delayTicks);
    }

    private Player getPlayer(QueuedPlayer player) {
        if (player.getUuid() != null && (plugin.getServer().getOnlineMode() || plugin.getConfiguration().isBungeeCord())) {
            return plugin.getServer().getPlayer(UuidUtil.mojangUuidToJavaUuid(player.getUuid()));
        }
        return plugin.getServer().getPlayerExact(player.getName());
    }

    @Override
    public boolean isPlayerOnline(QueuedPlayer player) {
        return getPlayer(player) != null;
    }

    @Override
    public int getFreeSlots(QueuedPlayer player) {
        Player player1 = getPlayer(player);
        if (player1 == null) return -1;
        int s = 0;

        ItemStack[] contents = player1.getInventory().getContents();
        if (contents.length > MAXIMUM_USABLE_INVENTORY_SIZE) {
            // Spigot 1.9+ merges armor/inventory. We only want "real" inventory space.
            contents = Arrays.copyOfRange(contents, 0, MAXIMUM_USABLE_INVENTORY_SIZE);
        }

        for (ItemStack stack : contents) {
            if (stack == null) s++;
        }
        return s;
    }

    public abstract boolean ensureCompatibleServerVersion();

    public abstract Material getPlayerSkullMaterial();

    public abstract ImmutableSet<Material> getSignMaterials();

    public abstract Material getGUIViewAllMaterial();

    public abstract ItemStack createItemFromMaterialString(String materialData);

    @Override
    public void log(Level level, String message) {
        plugin.getLogger().log(level, message);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        plugin.getLogger().log(level, message, throwable);
    }

    @Override
    public CommandExecutor getExecutor() {
        return plugin.getCommandExecutor();
    }

    @Override
    public PlatformInformation getPlatformInformation() {
        return new PlatformInformation(PlatformType.BUKKIT, plugin.getServer().getVersion());
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public ServerInformation getServerInformation() {
        return plugin.getServerInformation();
    }
}
