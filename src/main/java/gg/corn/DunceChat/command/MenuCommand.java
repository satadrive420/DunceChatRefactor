package gg.corn.DunceChat.command;

import gg.corn.DunceChat.gui.DunceGUIBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command to open the DunceChat GUI
 */
public class MenuCommand implements CommandExecutor {

    private final DunceGUIBuilder guiBuilder;

    public MenuCommand(DunceGUIBuilder guiBuilder) {
        this.guiBuilder = guiBuilder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        guiBuilder.openGUI(player);
        return true;
    }
}

