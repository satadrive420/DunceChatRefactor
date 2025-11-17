package gg.corn.DunceChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ClearChat implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, @NotNull Command arg1, @NotNull String arg2, String[] arg3) {
		if (sender.hasPermission("dunce.clearchat"))
			for (Player player : Bukkit.getOnlinePlayers())
				for (int i = 0; i < 100; i++)
					player.sendMessage(Component.empty());
		else
			sender.sendMessage(Component.text("Insufficient Permissions!", NamedTextColor.RED));
		return true;
	}

}
