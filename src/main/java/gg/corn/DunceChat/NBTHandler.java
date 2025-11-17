package gg.corn.DunceChat;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class NBTHandler {

	private static JavaPlugin plugin;



	public static @NotNull ItemStack addString(ItemStack item, NamespacedKey key, String value) {
		ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			PersistentDataContainer container = meta.getPersistentDataContainer();
			container.set(key, PersistentDataType.STRING, value);
			item.setItemMeta(meta);
		}

		return item;
	}

	public static boolean hasString(@NotNull ItemStack item, NamespacedKey key) {
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			PersistentDataContainer container = meta.getPersistentDataContainer();
			return container.has(key, PersistentDataType.STRING);
		}
		return false;
	}

	public static @Nullable String getString(ItemStack item, NamespacedKey key) {
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			PersistentDataContainer container = meta.getPersistentDataContainer();
			if (container.has(key, PersistentDataType.STRING)) {
				return container.get(key, PersistentDataType.STRING);
			}
		}
		return null;
	}
}