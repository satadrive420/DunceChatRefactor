package gg.corn.DunceChat;

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
                    sender.sendMessage(gg.corn.DunceChat.DunceChat.baseColor() +"Dunce chat "+gg.corn.DunceChat.DunceChat.highlightColor()+"visible"+gg.corn.DunceChat.DunceChat.baseColor()+".");
            }
        }

        else if (label.equalsIgnoreCase("dcoff")) {
            if (sender instanceof Player player) {

                boolean visible = gg.corn.DunceChat.DunceChat.dunceVisible(player);
                if (visible)
                    gg.corn.DunceChat.DunceChat.setDunceChatVisible(player, false);
                    sender.sendMessage(gg.corn.DunceChat.DunceChat.baseColor() +"Dunce chat "+gg.corn.DunceChat.DunceChat.highlightColor()+"hidden"+gg.corn.DunceChat.DunceChat.baseColor()+".");
            }
        }

        return true;
    }
}
