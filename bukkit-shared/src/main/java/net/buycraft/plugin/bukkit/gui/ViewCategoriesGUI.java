package net.buycraft.plugin.bukkit.gui;

import com.google.gson.Gson;
import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.bukkit.util.GUIUtil;
import net.buycraft.plugin.data.Category;
import net.buycraft.plugin.data.responses.Listing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.*;
import java.util.Objects;
import java.util.logging.Level;

import static net.buycraft.plugin.bukkit.util.GUIUtil.withName;

public class ViewCategoriesGUI implements Listener {
    private final BuycraftPluginBase plugin;
    private Inventory inventory;

    public ViewCategoriesGUI(BuycraftPluginBase plugin) {
        this.plugin = plugin;
        inventory = Bukkit.createInventory(null, 9, GUIUtil.trimName("Tebex: " +
                plugin.getI18n().get("categories")));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (inventoryNeedsReloading()) {
            Bukkit.getLogger().info("Inventory is empty, reading from gui.cache...");
            try (BufferedReader reader = new BufferedReader(new FileReader(plugin.getDataFolder() + "/gui.cache"))) {
                String jsonString = reader.readLine();
                Listing listing = new Gson().fromJson(jsonString, Listing.class);
                if (listing != null) {
                    listing.order();
                }
                inventory = Bukkit.createInventory(null, 9, GUIUtil.trimName("Tebex: " + plugin.getI18n().get("categories")));
                this.createInventoryFromListing(listing);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        player.openInventory(inventory);
    }

    private boolean inventoryNeedsReloading() {
        if (this.inventory == null) {
            return true;
        }
        for (ItemStack is : this.inventory.getContents()) {
            if (is != null) return false;
        }
        return true;
    }

    private int roundNine(int s) {
        int sz = s - 1;
        return Math.max(9, sz - (sz % 9) + 9);
    }

    public void update() {
        inventory.clear();

        if (plugin.getApiClient() == null || plugin.getServerInformation() == null) {
            plugin.getLogger().warning("No secret key or server info, can't update inventories.");
            return;
        }

        Listing listing = plugin.getListingUpdateTask().getListing();
        if (listing == null) {
            plugin.getLogger().warning("No listing found, can't update inventories.");
            return;
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(plugin.getDataFolder() + "/gui.cache"))) {
            bw.write(new Gson().toJson(listing));
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.createInventoryFromListing(listing);
    }

    private void createInventoryFromListing(Listing listing) {
        if (roundNine(listing.getCategories().size()) != inventory.getSize()) {
            Inventory work = Bukkit.createInventory(null, roundNine(listing.getCategories().size()),
                    GUIUtil.trimName("Tebex: " + plugin.getI18n().get("categories")));
            GUIUtil.replaceInventory(inventory, work);
            inventory = work;
        }

        for (Category category : listing.getCategories()) {
            ItemStack stack = plugin.getPlatform().createItemFromMaterialString(category.getGuiItem());
            if (stack == null) {
                stack = new ItemStack(Material.CHEST);
            }
            inventory.setItem(inventory.firstEmpty(), withName(stack, ChatColor.YELLOW + category.getName()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = GUIUtil.getClickedInventory(event);
        if (clickedInventory != null && Objects.equals(inventory, clickedInventory)) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            final Player player = (Player) event.getWhoClicked();
            Listing listing = plugin.getListingUpdateTask().getListing();
            if (listing == null) {
                return;
            }

            if (event.getSlot() >= listing.getCategories().size()) {
                return;
            }

            Category category = listing.getCategories().get(event.getSlot());
            if (category == null) {
                return;
            }

            final CategoryViewGUI.GUIImpl gui = plugin.getCategoryViewGUI().getFirstPage(category);
            if (gui == null) {
                player.sendMessage(ChatColor.RED + plugin.getI18n().get("nothing_in_category"));
                return;
            }

            // Folia: schedule synchronous immediate
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                gui.open(player);
            }, 0L);

        } else if (event.getView().getTopInventory() == inventory) {
            event.setCancelled(true);
        }
    }
}
