package net.buycraft.plugin.bukkit.command;

import net.buycraft.plugin.bukkit.BuycraftPluginBase;
import net.buycraft.plugin.shared.util.ReportBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ReportCommand implements Subcommand {
    private final BuycraftPluginBase plugin;

    public ReportCommand(BuycraftPluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + plugin.getI18n().get("report_wait"));

        // Folia: run asynchronously with no delay
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> {
            ReportBuilder builder = ReportBuilder.builder()
                    .client(plugin.getHttpClient())
                    .configuration(plugin.getConfiguration())
                    .platform(plugin.getPlatform())
                    .duePlayerFetcher(plugin.getDuePlayerFetcher())
                    .ip(Bukkit.getIp())
                    .port(Bukkit.getPort())
                    .listingUpdateTask(plugin.getListingUpdateTask())
                    .serverOnlineMode(Bukkit.getOnlineMode())
                    .build();

            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
            String filename = "report-" + f.format(new Date()) + ".txt";
            Path p = plugin.getDataFolder().toPath().resolve(filename);
            String generated = builder.generate();

            try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                w.write(generated);
                sender.sendMessage(ChatColor.YELLOW + plugin.getI18n().get("report_saved", p.toAbsolutePath().toString()));
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + plugin.getI18n().get("report_cant_save"));
                plugin.getLogger().info(generated);
            }
        }, 0L, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getDescription() {
        return plugin.getI18n().get("usage_report");
    }
}
