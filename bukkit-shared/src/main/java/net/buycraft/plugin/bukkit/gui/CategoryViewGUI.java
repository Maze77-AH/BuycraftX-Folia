package net.buycraft.plugin.bukkit.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.bukkit.tasks.SendCheckoutLink;
import net.buycraft.plugin.bukkit.util.GUIUtil;
import net.buycraft.plugin.data.Category;
import net.buycraft.plugin.data.Package;
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

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static net.buycraft.plugin.bukkit.util.GUIUtil.withName;

public class CategoryViewGUI {
    private final BuycraftPluginBase plugin;
    private final Map<Integer, List<GUIImpl>> categoryMenus = new HashMap<>();

    public CategoryViewGUI(final BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    private static int calculatePages(Category category) {
        int pagesWithSubcats = (int) Math.ceil(category.getSubcategories().size() / 9D);
        int pagesWithPackages = (int) Math.ceil(category.getPackages().size() / 36D);
        return Math.max(pagesWithSubcats, pagesWithPackages);
    }

    public GUIImpl getFirstPage(Category category) {
        List<GUIImpl> guis = categoryMenus.get(category.getId());
        if (guis == null) return null;
        return Iterables.getFirst(guis, null);
    }

    public void update() {
        if (plugin.getApiClient() == null || plugin.getServerInformation() == null) {
            plugin.getLogger().warning("No secret key or server info available, can't update inventories.");
            return;
        }

        Listing listing = plugin.getListingUpdateTask().getListing();
        if (listing == null) {
            plugin.getLogger().warning("No listing found, can't update inventories.");
            return;
        }

        List<Integer> foundIds = new ArrayList<>();
        for (Category category : listing.getCategories()) {
            foundIds.add(category.getId());
            for (Category category1 : category.getSubcategories()) {
                foundIds.add(category1.getId());
            }
        }

        for (Iterator<Map.Entry<Integer, List<GUIImpl>>> it = categoryMenus.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, List<GUIImpl>> next = it.next();
            if (!foundIds.contains(next.getKey())) {
                for (GUIImpl gui : next.getValue()) {
                    gui.destroy();
                }
                it.remove();
            }
        }

        for (Category category : listing.getCategories()) {
            doUpdate(null, category);
        }
    }

    private void doUpdate(Category parent, Category category) {
        List<GUIImpl> pages = categoryMenus.get(category.getId());
        if (pages == null) {
            pages = new ArrayList<>();
            categoryMenus.put(category.getId(), pages);
            for (int i = 0; i < calculatePages(category); i++) {
                GUIImpl gui = new GUIImpl(parent != null ? parent.getId() : null, i, category);
                plugin.getServer().getPluginManager().registerEvents(gui, plugin);
                pages.add(gui);
            }
        } else {
            int allPages = calculatePages(category);
            int toRemove = pages.size() - allPages;
            if (toRemove > 0) {
                List<GUIImpl> prune = pages.subList(pages.size() - toRemove, pages.size());
                for (GUIImpl gui : prune) {
                    gui.destroy();
                }
                prune.clear();
            } else if (toRemove < 0) {
                int toAdd = -toRemove;
                for (int i = 0; i < toAdd; i++) {
                    GUIImpl gui = new GUIImpl(parent != null ? parent.getId() : null, pages.size(), category);
                    plugin.getServer().getPluginManager().registerEvents(gui, plugin);
                    pages.add(gui);
                }
            }

            for (int i = 0; i < pages.size(); i++) {
                GUIImpl gui = pages.get(i);
                if (gui.requiresResize(category)) {
                    HandlerList.unregisterAll(gui);
                    GUIImpl tmpGui = new GUIImpl(parent != null ? parent.getId() : null, i, category);
                    plugin.getServer().getPluginManager().registerEvents(tmpGui, plugin);
                    pages.set(i, tmpGui);

                    GUIUtil.replaceInventory(gui.inventory, tmpGui.inventory);
                } else {
                    gui.update(category);
                }
            }
        }

        for (Category category1 : category.getSubcategories()) {
            doUpdate(category, category1);
        }
    }

    public class GUIImpl implements Listener {
        private final Inventory inventory;
        private final Integer parentId;
        private final int page;
        private Category category;

        public GUIImpl(Integer parentId, int page, Category category) {
            this.inventory = Bukkit.createInventory(null, calculateSize(category, page),
                GUIUtil.trimName("Tebex: " + category.getName()));
            this.parentId = parentId;
            this.page = page;
            update(category);
        }

        private int calculateSize(Category category, int page) {
            // A rough approach: 45 for bottom row, plus maybe 9 if subcategories
            int needed = 45;
            if (!category.getSubcategories().isEmpty()) {
                int pagesWithSubcats = (int) Math.ceil(category.getSubcategories().size() / 9D);
                if (pagesWithSubcats >= page) {
                    needed += 9;
                }
            }
            return needed;
        }

        public boolean requiresResize(Category category) {
            return calculateSize(category, page) != inventory.getSize();
        }

        public void destroy() {
            HandlerList.unregisterAll(this);
            closeAll();
        }

        public void closeAll() {
            for (HumanEntity entity : ImmutableList.copyOf(inventory.getViewers())) {
                entity.closeInventory();
            }
        }

        public void open(Player player) {
            player.openInventory(inventory);
        }

        public void update(Category category) {
            this.category = category;
            inventory.clear();

            List<List<Category>> subcatPartition;
            if (!category.getSubcategories().isEmpty()) {
                subcatPartition = Lists.partition(category.getSubcategories(), 45);
                if (subcatPartition.size() - 1 >= page) {
                    List<Category> subcats = subcatPartition.get(page);
                    subcats.sort(Comparator.comparingInt(Category::getOrder));
                    for (int i = 0; i < subcats.size(); i++) {
                        Category subcat = subcats.get(i);

                        ItemStack stack = plugin.getPlatform().createItemFromMaterialString(subcat.getGuiItem());
                        if (stack == null) {
                            stack = new ItemStack(Material.CHEST);
                        }

                        inventory.setItem(i, withName(stack, ChatColor.YELLOW + subcat.getName()));
                    }
                }
            } else {
                subcatPartition = Collections.emptyList();
            }

            List<List<Package>> packagePartition = Lists.partition(category.getPackages(), 36);
            int base = subcatPartition.isEmpty() ? 0 : 9;

            if (packagePartition.size() - 1 >= page) {
                List<Package> packages = packagePartition.get(page);
                packages.sort(Comparator.comparingInt(Package::getOrder));
                for (int i = 0; i < packages.size(); i++) {
                    Package p = packages.get(i);

                    ItemStack stack = plugin.getPlatform().createItemFromMaterialString(p.getGuiItem());
                    if (stack == null) {
                        stack = new ItemStack(Material.PAPER);
                    }

                    ItemMeta meta = stack.getItemMeta();
                    meta.setDisplayName(ChatColor.GREEN + p.getName());

                    List<String> lore = new ArrayList<>();
                    NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
                    format.setCurrency(Currency.getInstance(plugin.getServerInformation().getAccount().getCurrency().getIso4217()));

                    String price = ChatColor.GRAY +
                            plugin.getI18n().get("price") + ": " +
                            ChatColor.DARK_GREEN + ChatColor.BOLD +
                            format.format(p.getEffectivePrice());
                    lore.add(price);

                    if (p.getSale() != null && p.getSale().isActive()) {
                        lore.add(ChatColor.RED + plugin.getI18n().get("amount_off", format.format(p.getSale().getDiscount())));
                    }

                    meta.setLore(lore);
                    stack.setItemMeta(meta);
                    inventory.setItem(base + i, stack);
                }
            }

            int bottomBase = base + 36;
            if (page > 0) {
                inventory.setItem(bottomBase + 1, withName(new ItemStack(Material.NETHER_STAR),
                        ChatColor.AQUA + plugin.getI18n().get("previous_page")));
            }
            if (subcatPartition.size() - 1 > page || packagePartition.size() - 1 > page) {
                inventory.setItem(bottomBase + 7, withName(new ItemStack(Material.NETHER_STAR),
                        ChatColor.AQUA + plugin.getI18n().get("next_page")));
            }

            // parent/back or "view all categories"
            ItemStack parent = new ItemStack(plugin.getPlatform().getGUIViewAllMaterial());
            ItemMeta meta = parent.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + (parentId == null ?
                    plugin.getI18n().get("view_all_categories") :
                    plugin.getI18n().get("back_to_parent")));
            parent.setItemMeta(meta);
            inventory.setItem(bottomBase + 4, parent);
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
                if (category == null) return;
                ItemStack stack = clickedInventory.getItem(event.getSlot());
                if (stack == null) return;

                String displayName = stack.getItemMeta().getDisplayName();
                if (displayName.startsWith(ChatColor.YELLOW.toString())) {
                    // Subcategory
                    for (final Category subcat : category.getSubcategories()) {
                        if (subcat.getName().equals(ChatColor.stripColor(displayName))) {
                            final GUIImpl gui = getFirstPage(subcat);
                            if (gui == null) {
                                player.sendMessage(ChatColor.RED + plugin.getI18n().get("nothing_in_category"));
                                return;
                            }
                            // Folia: run synchronous immediate
                            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> gui.open(player), 0L);
                            return;
                        }
                    }
                } else if (displayName.startsWith(ChatColor.GREEN.toString())) {
                    // Package
                    for (Package p : category.getPackages()) {
                        if (p.getName().equals(ChatColor.stripColor(displayName))) {
                            GUIUtil.closeInventoryLater(player);
                            if (plugin.getBuyNowSignListener().getSettingUpSigns().containsKey(player.getUniqueId())) {
                                plugin.getBuyNowSignListener().doSignSetup(player, p);
                            } else {
                                // Folia: run async immediate
                                Bukkit.getAsyncScheduler().runDelayed(
                                    plugin,
                                    scheduledTask -> new SendCheckoutLink(plugin, p.getId(), player).run(),
                                    0L, TimeUnit.MILLISECONDS
                                );
                            }
                            return;
                        }
                    }
                } else if (displayName.equals(ChatColor.AQUA + plugin.getI18n().get("previous_page"))) {
                    // synchronous immediate
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                        categoryMenus.get(category.getId()).get(page - 1).open(player);
                    }, 0L);
                } else if (displayName.equals(ChatColor.AQUA + plugin.getI18n().get("next_page"))) {
                    // synchronous immediate
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                        categoryMenus.get(category.getId()).get(page + 1).open(player);
                    }, 0L);
                } else if (stack.getType() == plugin.getPlatform().getGUIViewAllMaterial()) {
                    if (parentId != null) {
                        // synchronous immediate
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                            categoryMenus.get(parentId).get(0).open(player);
                        }, 0L);
                    } else {
                        // synchronous immediate
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                            plugin.getViewCategoriesGUI().open(player);
                        }, 0L);
                    }
                }
            } else if (event.getView().getTopInventory() == inventory) {
                event.setCancelled(true);
            }
        }
    }
}
