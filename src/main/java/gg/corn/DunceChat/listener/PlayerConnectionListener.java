package gg.corn.DunceChat.listener;

import gg.corn.DunceChat.service.DunceService;
import gg.corn.DunceChat.service.IPTrackingService;
import gg.corn.DunceChat.service.PlayerService;
import gg.corn.DunceChat.service.PreferencesService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles player join and quit events with async optimization
 * Separated from ChatListener for better separation of concerns and performance
 */
public class PlayerConnectionListener implements Listener {

    private final DunceService dunceService;
    private final PlayerService playerService;
    private final PreferencesService preferencesService;
    private final IPTrackingService ipTrackingService;
    private final Plugin plugin;
    private static final Logger logger = Logger.getLogger("DunceChat");

    public PlayerConnectionListener(DunceService dunceService, PlayerService playerService,
                                   PreferencesService preferencesService, IPTrackingService ipTrackingService,
                                   Plugin plugin) {
        this.dunceService = dunceService;
        this.playerService = playerService;
        this.preferencesService = preferencesService;
        this.ipTrackingService = ipTrackingService;
        this.plugin = plugin;
    }

    /**
     * Handle player join with optimized async operations
     * Priority: MONITOR to run after other plugins
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // === SYNCHRONOUS OPERATIONS (must run on main thread) ===

        // Pre-load preferences into cache immediately for faster chat access
        // This is lightweight and needs to be available immediately when player chats
        preferencesService.loadIntoCache(playerUuid);

        // Check if player's dunce has expired (lightweight cache check)
        // This must run sync to ensure dunce status is correct before any chat
        dunceService.checkAndProcessExpiredDunceOnLogin(playerUuid);

        // === ASYNC OPERATIONS (moved to background thread) ===

        // Run expensive database operations asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Update player record in database (DB write operation)
                playerService.handlePlayerJoin(player);

                // Handle IP tracking, alt detection, watchlist, and auto-dunce (very expensive: ~9 seconds)
                // This does multiple DB queries and checks, so it's crucial to run async
                ipTrackingService.handlePlayerJoin(player);

                // Send any pending messages (DB read operation)
                // Must run after other operations to ensure player is fully initialized
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Switch back to main thread to send messages (Bukkit API requirement)
                    if (player.isOnline()) {
                        dunceService.sendPendingMessages(playerUuid);
                    }
                });

            } catch (Exception e) {
                logger.severe("[DunceChat] Error processing player join for " + player.getName());
                e.printStackTrace();
            }
        });
    }

    /**
     * Handle player quit with async database operations
     * Priority: MONITOR to run after other plugins
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // === SYNCHRONOUS OPERATIONS ===

        // Clean up caches immediately to free memory
        preferencesService.invalidateCache(playerUuid);
        dunceService.invalidateCache(playerUuid);

        // === ASYNC OPERATIONS ===

        // Update quit time in database asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                playerService.handlePlayerQuit(player);
            } catch (Exception e) {
                logger.severe("[DunceChat] Error processing player quit for " + player.getName());
                e.printStackTrace();
            }
        });
    }
}

