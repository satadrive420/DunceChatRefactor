package gg.corn.DunceChat;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DunceToggle implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] strings) {

        if (label.equalsIgnoreCase("dcon")) {
            if (sender instanceof Player player) {

                boolean visible = gg.corn.DunceChat.DunceChat.dunceVisible(player);
                if (!visible)
                    gg.corn.DunceChat.DunceChat.setDunceChatVisible(player, true);
                    sender.sendMessage(Component.text("Dunce chat ", DunceChat.baseColor())
                            .append(Component.text("visible", DunceChat.highlightColor()))
                            .append(Component.text(".", DunceChat.baseColor())));
            }
        }

        else if (label.equalsIgnoreCase("dcoff")) {
            if (sender instanceof Player player) {

                boolean visible = gg.corn.DunceChat.DunceChat.dunceVisible(player);
                if (visible)
                    gg.corn.DunceChat.DunceChat.setDunceChatVisible(player, false);
                    sender.sendMessage(Component.text("Dunce chat ", DunceChat.baseColor())
                            .append(Component.text("hidden", DunceChat.highlightColor()))
                            .append(Component.text(".", DunceChat.baseColor())));
            }
        }

        return true;
    }
}
