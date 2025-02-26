package net.buycraft.plugin.bukkit.command;

import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.concurrent.TimeUnit;

public class ForceCheckSubcommand implements Subcommand {
    private final BuycraftPluginBase plugin;

    public ForceCheckSubcommand(final BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("no_params"));
            return;
        }

        if (plugin.getApiClient() == null) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("need_secret_key"));
            return;
        }

        if (plugin.getDuePlayerFetcher().inProgress()) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("already_checking_for_purchases"));
            return;
        }

        // Folia: schedule asynchronously with 0 ms delay
        Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            scheduledTask -> plugin.getDuePlayerFetcher().run(false),
            0L,
            TimeUnit.MILLISECONDS
        );

        sender.sendMessage(ChatColor.GREEN + plugin.getI18n().get("forcecheck_queued"));
    }

    @Override
    public String getDescription() {
        return plugin.getI18n().get("usage_forcecheck");
    }
}
