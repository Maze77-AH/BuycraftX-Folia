package net.buycraft.plugin.bukkit.command;

import org.apache.commons.lang3.StringUtils;

import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.bukkit.tasks.SendCheckoutLink;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class SendLinkSubcommand implements Subcommand {
    private final BuycraftPluginBase plugin;

    public SendLinkSubcommand(final BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("buycraft.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("no_permission"));
            return;
        }

        if (args.length != 3 || 
            !(args[1].equalsIgnoreCase("package") || args[1].equalsIgnoreCase("category")) ||
            !StringUtils.isNumeric(args[2])) {
            sender.sendMessage(ChatColor.RED + "Incorrect syntax: /tebex sendlink <player> package|category <id>");
            return;
        }

        Player p = Bukkit.getPlayer(args[0]);
        if (p == null || !p.isOnline()) {
            sender.sendMessage(ChatColor.RED + "That player is not online!");
            return;
        }

        boolean isCategory = args[1].equalsIgnoreCase("category");
        int id = Integer.parseInt(args[2]);

        // Folia: run asynchronous with no delay
        Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            scheduledTask -> new SendCheckoutLink(plugin, id, p, isCategory, sender).run(),
            0L,
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public String getDescription() {
        return plugin.getI18n().get("usage_sendlink");
    }
}
