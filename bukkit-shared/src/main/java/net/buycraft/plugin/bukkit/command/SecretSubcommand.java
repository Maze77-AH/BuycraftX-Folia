package net.buycraft.plugin.bukkit.command;

import net.buycraft.plugin.BuyCraftAPI;
import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.data.responses.ServerInformation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SecretSubcommand implements Subcommand {
    private final BuycraftPluginBase plugin;

    public SecretSubcommand(final BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("secret_console_only"));
            return;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + plugin.getI18n().get("secret_need_key"));
            return;
        }

        // Folia: schedule asynchronously with no delay
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> {
            String currentKey = plugin.getConfiguration().getServerKey();
            BuyCraftAPI client = BuyCraftAPI.create(args[0], plugin.getHttpClient());
            try {
                plugin.updateInformation(client);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unable to verify secret", e);
                sender.sendMessage(ChatColor.RED + plugin.getI18n().get("secret_does_not_work"));
                return;
            }

            ServerInformation information = plugin.getServerInformation();
            plugin.setApiClient(client);
            plugin.getListingUpdateTask().run();
            plugin.getConfiguration().setServerKey(args[0]);
            try {
                plugin.saveConfiguration();
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + plugin.getI18n().get("secret_cant_be_saved"));
            }

            sender.sendMessage(ChatColor.GREEN + plugin.getI18n().get("secret_success",
                    information.getServer().getName(), information.getAccount().getName()));

            boolean repeatChecks = false;
            if (currentKey.equals("INVALID")) {
                repeatChecks = true;
            }

            plugin.getDuePlayerFetcher().run(repeatChecks);
        }, 0L, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getDescription() {
        return plugin.getI18n().get("usage_secret");
    }
}
