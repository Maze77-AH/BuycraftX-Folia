package net.buycraft.plugin.bukkit.command;

import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.bukkit.tasks.RecentPurchaseSignUpdateFetcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.concurrent.TimeUnit;

public class SignUpdateSubcommand implements Subcommand {
    private final BuycraftPluginBase plugin;

    public SignUpdateSubcommand(final BuycraftPluginBase plugin) {
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

        // Folia: run asynchronously with zero ms delay
        Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            scheduledTask -> new RecentPurchaseSignUpdateFetcher(plugin).run(),
            0L,
            TimeUnit.MILLISECONDS
        );

        sender.sendMessage(ChatColor.GREEN + plugin.getI18n().get("sign_update_queued"));
    }

    @Override
    public String getDescription() {
        return plugin.getI18n().get("usage_signupdate");
    }
}
