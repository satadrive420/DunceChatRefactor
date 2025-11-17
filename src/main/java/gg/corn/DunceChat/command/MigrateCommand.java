package gg.corn.DunceChat.command;

import gg.corn.DunceChat.database.SchemaManager;
import gg.corn.DunceChat.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Command to migrate from old schema to new schema
 */
public class MigrateCommand implements CommandExecutor {

    private final SchemaManager schemaManager;
    private final MessageManager messageManager;
    private boolean migrationInProgress = false;

    public MigrateCommand(SchemaManager schemaManager, MessageManager messageManager) {
        this.schemaManager = schemaManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("duncechat.admin")) {
            sender.sendMessage(messageManager.get("no_permission"));
            return true;
        }

        if (migrationInProgress) {
            sender.sendMessage(messageManager.get("migration_in_progress"));
            return true;
        }

        sender.sendMessage(messageManager.get("migration_starting"));
        sender.sendMessage(messageManager.get("migration_info"));
        sender.sendMessage(messageManager.get("migration_backup_info"));

        migrationInProgress = true;

        // Run migration asynchronously
        new Thread(() -> {
            try {
                boolean success = schemaManager.migrateFromOldSchema();

                if (success) {
                    sender.sendMessage(messageManager.get("migration_success"));
                    sender.sendMessage(messageManager.get("migration_backup_success"));
                } else {
                    sender.sendMessage(messageManager.get("migration_failed"));
                }
            } catch (Exception e) {
                sender.sendMessage(messageManager.get("migration_failed"));
                e.printStackTrace();
            } finally {
                migrationInProgress = false;
            }
        }).start();

        return true;
    }
}

