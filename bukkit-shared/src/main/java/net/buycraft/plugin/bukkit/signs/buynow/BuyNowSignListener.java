package net.buycraft.plugin.bukkit.signs.buynow;

import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.bukkit.tasks.BuyNowSignUpdater;
import net.buycraft.plugin.bukkit.tasks.RecentPurchaseSignUpdateApplication;
import net.buycraft.plugin.bukkit.tasks.SendCheckoutLink;
import net.buycraft.plugin.bukkit.util.BukkitSerializedBlockLocation;
import net.buycraft.plugin.data.Package;
import net.buycraft.plugin.shared.config.signs.storage.SavedBuyNowSign;
import net.buycraft.plugin.shared.config.signs.storage.SerializedBlockLocation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class BuyNowSignListener implements Listener {
    private static final long COOLDOWN_MS = 250; // 5 ticks in ms
    private final Map<UUID, SerializedBlockLocation> settingUpSigns = new HashMap<>();
    private final BuycraftPluginBase plugin;
    private final Map<UUID, Long> signCooldowns = new HashMap<>();

    public BuyNowSignListener(BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        boolean relevant;
        try {
            relevant = Arrays.asList("[buycraft_buy]", "[tebex_buy]").contains(event.getLine(0).toLowerCase());
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        if (!relevant) return;
        if (!event.getPlayer().hasPermission("buycraft.admin")) {
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to create this sign.");
            return;
        }

        for (int i = 0; i < 4; i++) {
            event.setLine(i, "");
        }

        settingUpSigns.put(event.getPlayer().getUniqueId(), BukkitSerializedBlockLocation.create(event.getBlock().getLocation()));
        event.getPlayer().sendMessage(ChatColor.GREEN + "Navigate to the item you want to set this sign for.");
        plugin.getViewCategoriesGUI().open(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block b = event.getClickedBlock();
            if (!(plugin.getPlatform().getSignMaterials().contains(b.getType()))) return;
            SerializedBlockLocation sbl = BukkitSerializedBlockLocation.create(b.getLocation());
            for (SavedBuyNowSign s : plugin.getBuyNowSignStorage().getSigns()) {
                if (s.getLocation().equals(sbl)) {
                    // Sign rate limit
                    Long ts = signCooldowns.get(event.getPlayer().getUniqueId());
                    long now = System.currentTimeMillis();
                    if (ts == null || ts + COOLDOWN_MS <= now) {
                        signCooldowns.put(event.getPlayer().getUniqueId(), now);

                        // Folia: run async immediately
                        plugin.getServer().getAsyncScheduler().runDelayed(
                            plugin,
                            scheduledTask -> new SendCheckoutLink(plugin, s.getPackageId(), event.getPlayer()).run(),
                            0L,
                            TimeUnit.MILLISECONDS
                        );
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!settingUpSigns.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        // Folia: run sync delayed (3 ticks)
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            if ((event.getPlayer().getOpenInventory().getTopInventory() == null
                    || !event.getView().getTitle().startsWith("Tebex: "))
                && settingUpSigns.remove(event.getPlayer().getUniqueId()) != null
                && event.getPlayer() instanceof Player) {
                event.getPlayer().sendMessage(ChatColor.RED + "Buy sign set up cancelled.");
            }
        }, 3L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPlatform().getSignMaterials().contains(event.getBlock().getType())) {
            SerializedBlockLocation location = BukkitSerializedBlockLocation.create(event.getBlock().getLocation());
            if (plugin.getBuyNowSignStorage().containsLocation(location)) {
                if (!event.getPlayer().hasPermission("buycraft.admin")) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to break this sign.");
                    event.setCancelled(true);
                    return;
                }

                if (plugin.getBuyNowSignStorage().removeSign(location)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Removed buy now sign!");
                }
            }
            return;
        }
        for (BlockFace face : RecentPurchaseSignUpdateApplication.FACES) {
            Location onFace = event.getBlock().getRelative(face).getLocation();
            SerializedBlockLocation onFaceSbl = BukkitSerializedBlockLocation.create(onFace);
            if (plugin.getBuyNowSignStorage().containsLocation(onFaceSbl)) {
                if (!event.getPlayer().hasPermission("buycraft.admin")) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to break this sign.");
                    event.setCancelled(true);
                    return;
                }

                if (plugin.getBuyNowSignStorage().removeSign(onFaceSbl)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Removed buy now sign!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        settingUpSigns.remove(event.getPlayer().getUniqueId());
        signCooldowns.remove(event.getPlayer().getUniqueId());
    }

    public void doSignSetup(Player player, Package p) {
        SerializedBlockLocation sbl = settingUpSigns.remove(player.getUniqueId());
        if (sbl == null) return;
        Block b = BukkitSerializedBlockLocation.toBukkit(sbl).getBlock();
        if (!(plugin.getPlatform().getSignMaterials().contains(b.getType()))) return;
        plugin.getBuyNowSignStorage().addSign(new SavedBuyNowSign(sbl, p.getId()));

        // Folia: run sync immediate
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> new BuyNowSignUpdater(plugin).run(), 0L);
    }

    public Map<UUID, SerializedBlockLocation> getSettingUpSigns() {
        return this.settingUpSigns;
    }
}
