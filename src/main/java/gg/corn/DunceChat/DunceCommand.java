package gg.corn.DunceChat;

import com.google.common.collect.Lists;
import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.*;

import static gg.corn.DunceChat.DunceChat.baseColor;
import static gg.corn.DunceChat.DunceChat.highlightColor;
import static gg.corn.DunceChat.DunceGUI.constructDunceGUI;


public class DunceCommand implements CommandExecutor, TabCompleter {

	public static DunceChat plugin = gg.corn.DunceChat.DunceChat.getPlugin(DunceChat.class);
	private NBTHandler nbtHandler;

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
							 @NotNull String[] args) {

		if (label.equalsIgnoreCase("dunce")) {
			if (args.length >= 1 && sender.hasPermission("duncechat.admin"))
				tryDunce(args[0], args, sender);
			else if (args.length == 0 && sender.hasPermission("duncechat.admin")){
				sender.sendMessage(Component.text("DunceChat Commands:", highlightColor()));
				sender.sendMessage(Component.text("/dunce <player> <duration> <reason>", highlightColor())
						.append(Component.text("- Dunce a player. Duration and Reason are optional.", baseColor())));
				sender.sendMessage(Component.text("/undunce <player>", highlightColor())
						.append(Component.text("- Undunce a player.", baseColor())));
				sender.sendMessage(Component.text("/clearchat", highlightColor())
						.append(Component.text("- Clear the chat.", baseColor())));
				sender.sendMessage(Component.text("/dcon ", highlightColor())
						.append(Component.text("- Show the Dunce Chat.", baseColor())));
				sender.sendMessage(Component.text("/dcoff ", highlightColor())
						.append(Component.text("- Hide the Dunce Chat.", baseColor())));
				sender.sendMessage(Component.text("/duncemenu ", highlightColor())
						.append(Component.text("- Open the DunceChat Menu.", baseColor())));
				sender.sendMessage(Component.text("/duncelookup", highlightColor())
						.append(Component.text("- Look up dunce information.", baseColor())));
			}
			else {
				sender.sendMessage(Component.text("DunceChat Commands:", highlightColor()));
				sender.sendMessage(Component.text("/dcon ", highlightColor())
						.append(Component.text("- Show the Dunce Chat", baseColor())));
				sender.sendMessage(Component.text("/dcoff ", highlightColor())
						.append(Component.text("- Hide the Dunce Chat", baseColor())));
				sender.sendMessage(Component.text("/duncemenu ", highlightColor())
						.append(Component.text("- Open the DunceChat Menu", baseColor())));
			}

		}

		else if (label.equalsIgnoreCase("dc")) {
			if (sender instanceof Player player) {

				if (args.length == 0)
					constructDunceGUI((Player) sender);
				else {
					boolean inDunceChat = gg.corn.DunceChat.DunceChat.getInDunceChat(player);

					if (!inDunceChat)
						gg.corn.DunceChat.DunceChat.setInDunceChat(player, true);

					((Player) sender).chat(concat(args));

					if (!inDunceChat)
						gg.corn.DunceChat.DunceChat.setInDunceChat(player, false);
				}
			}
		}
		else if (label.equalsIgnoreCase("undunce")) {
			if (args.length >= 1 && sender.hasPermission("duncechat.admin"))
				unduncePlayer(args[0], sender);
			else
				sender.sendMessage(Component.text("Insufficient Permissions!", NamedTextColor.RED));
		}

		return true;
	}


	@Override
	public List<String> onTabComplete(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
		List<String> list = new ArrayList<>();

		if (sender.hasPermission("duncechat.admin")) {
			if (cmd.getName().equalsIgnoreCase("dunce") && args.length == 1) {
				for (Player player : Bukkit.getOnlinePlayers())
					if (!player.hasPermission("duncechat.admin") && player.getName().toLowerCase().startsWith(args[0]))
						list.add(player.getName());

				Collections.sort(list);

			}
			else if (cmd.getName().equalsIgnoreCase("dunce") && args.length == 2
					&& plugin.getServer().getPlayer(args[0]) != null) {
				list.addAll(Lists.newArrayList("1s", "1m", "1h", "1d", "1w"));
				Collections.sort(list);
			}
			else if (cmd.getName().equalsIgnoreCase("dunce") && args.length == 3
					&& plugin.getServer().getPlayer(args[0]) != null) {
				list.addAll(Lists.newArrayList("Racism/Nazism/Hate Speech", "Spam", "Blockgame Lawyer"));
				Collections.sort(list);
			}
			else if (sender.hasPermission("duncechat.admin")) {
				if (cmd.getName().equalsIgnoreCase("undunce") && args.length == 1) {
					for (Player player : Bukkit.getOnlinePlayers())
						if (!player.hasPermission("duncechat.admin") && player.getName().toLowerCase().startsWith(args[0]))
							list.add(player.getName());

					Collections.sort(list);

				}
			}
		}

		return list;
	}

	public static void tryDunce(String name, String[] args, CommandSender sender) {
		UUID uuid = MySQLHandler.getUUIDByName(name);
		UUID staff = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
		StringBuilder reason;
		Timestamp expiry = null; // Default to null

		int reasonStartIndex = 1;
		boolean durationProvided = args.length >= 2 && (args[1].endsWith("s") || args[1].endsWith("m") || args[1].endsWith("h") ||
				args[1].endsWith("d") || args[1].endsWith("w")) && Character.isDigit(args[1].charAt(0));

		// Check if a duration was provided
		if (durationProvided) {
			String duration = args[1];
			reasonStartIndex = 2;

			long durationInSeconds = 0;

			if (duration.endsWith("s")) {
				durationInSeconds = Long.parseLong(duration.substring(0, duration.length() - 1));
			} else if (duration.endsWith("m")) {
				durationInSeconds = Long.parseLong(duration.substring(0, duration.length() - 1)) * 60;
			} else if (duration.endsWith("h")) {
				durationInSeconds = Long.parseLong(duration.substring(0, duration.length() - 1)) * 3600;
			} else if (duration.endsWith("d")) {
				durationInSeconds = Long.parseLong(duration.substring(0, duration.length() - 1)) * 86400;
			} else if (duration.endsWith("w")) {
				durationInSeconds = Long.parseLong(duration.substring(0, duration.length() - 1)) * 604800;
			}
			// Create a Calendar object with the current date and time
			Calendar calendar = Calendar.getInstance();

			// Add the duration to the calendar
			calendar.add(Calendar.SECOND, (int) durationInSeconds);

			// Create a Timestamp object with the time in the future
			expiry = new Timestamp(calendar.getTimeInMillis());
		} else {
			// If the second argument is not a duration, it is part of the reason
			reasonStartIndex = 1;
		}

		reason = new StringBuilder();
		for (int i = reasonStartIndex; i < args.length; i++)
			reason.append(" ").append(args[i]);

		reason = new StringBuilder(reason.toString().trim());



		if (uuid == null)
			sender.sendMessage(Component.text(name + " has never played here before, or has changed their name since their last login. Check namemc.com if you believe their name has changed.", NamedTextColor.RED));
		else if (plugin.getServer().getPlayer(uuid) != null
				&& Objects.requireNonNull(plugin.getServer().getPlayer(uuid)).hasPermission("duncechat.admin"))
			sender.sendMessage(Component.text("That player cannot be dunced.", NamedTextColor.RED));
		else

		if (gg.corn.DunceChat.DunceChat.isDunced(uuid)) {
			sender.sendMessage(Component.text("Target is already dunced! Use /undunce <player> to undunce a player.", NamedTextColor.RED));
		} else if (!reason.isEmpty()) {
			DunceChat.setDunced(uuid, true, String.valueOf(reason), staff, expiry);
		} else {
			DunceChat.setDunced(uuid, true, null, staff, expiry);
		}
	}

	public static void unduncePlayer(String name, CommandSender sender){
		UUID uuid = MySQLHandler.getUUIDByName(name);
		UUID staff = sender instanceof Player ? ((Player) sender).getUniqueId() : null;

		if (!gg.corn.DunceChat.DunceChat.isDunced(uuid)) {
			sender.sendMessage(Component.text("That player is not dunced!", NamedTextColor.RED));
		} else
			gg.corn.DunceChat.DunceChat.setDunced(uuid, false, null, staff, null);

	}

	public static String concat(String[] args) {
		StringBuilder string = new StringBuilder();

		for (String stringg : args)
			string.append(stringg).append(" ");

		return string.toString().trim();
	}
}