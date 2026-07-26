package net.lusik21556.rankedsmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.lusik21556.rankedsmp.command.RankedCommand;
import net.lusik21556.rankedsmp.config.RankedConfig;
import net.lusik21556.rankedsmp.gui.RankManagementGui;
import net.lusik21556.rankedsmp.inventory.InventoryManager;
import net.lusik21556.rankedsmp.item.HierarchyHammerHandler;
import net.lusik21556.rankedsmp.listener.LocatorHandler;
import net.lusik21556.rankedsmp.listener.PotionEffectHandler;
import net.lusik21556.rankedsmp.listener.RankedPlayerEvents;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.lusik21556.rankedsmp.util.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the RankedSMP Paper plugin (originally by Lusik21556):
 * https://modrinth.com/plugin/ranked-smp
 *
 * "Kill ranked players to steal their position!" - assigns up to 20 online
 * players a shuffled rank 1-20 with escalating health, potion duration, XP
 * gain and extra-inventory-space bonuses. Rank can be stolen by killing a
 * better-ranked player (configurable).
 *
 * This is a server-only mod (see fabric.mod.json's "environment": "server")
 * - nothing here needs a matching client-side install, exactly like the
 * original plugin needed no client resource beyond an optional texture pack.
 */
public class RankedSMP implements ModInitializer {
	public static final String MOD_ID = "rankedsmp";
	public static final Logger LOGGER = LoggerFactory.getLogger("RankedSMP");

	private static RankedSMP instance;

	private RankedConfig config;
	private RankManager rankManager;
	private InventoryManager inventoryManager;
	private RankManagementGui rankManagementGui;
	private final Scheduler scheduler = new Scheduler();

	@Override
	public void onInitialize() {
		instance = this;
		printBanner();

		config = RankedConfig.load();
		rankManager = new RankManager(config);
		inventoryManager = new InventoryManager(rankManager);
		rankManager.setInventoryManager(inventoryManager);
		rankManagementGui = new RankManagementGui(rankManager, scheduler);

		ServerTickEvents.END_SERVER_TICK.register(server -> scheduler.tick());

		new RankedPlayerEvents(rankManager, inventoryManager, scheduler).register();
		new PotionEffectHandler(rankManager).register();
		new LocatorHandler().register();
		new HierarchyHammerHandler(scheduler).register();

		RankedCommand.register(rankManager, inventoryManager, rankManagementGui);

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			inventoryManager.saveAllInventories(server);
			scheduler.clear();
		});

		LOGGER.info("RankedSMP has been enabled!");
	}

	private void printBanner() {
		LOGGER.info("RankedSMP (Fabric port) - originally by Lusik21556 - https://modrinth.com/plugin/ranked-smp");
	}

	public void reloadConfig() {
		config.reload();
	}

	public static RankedSMP getInstance() {
		return instance;
	}

	public RankedConfig getConfig() {
		return config;
	}

	public RankManager getRankManager() {
		return rankManager;
	}

	public InventoryManager getInventoryManager() {
		return inventoryManager;
	}

	public RankManagementGui getRankManagementGui() {
		return rankManagementGui;
	}

	public Scheduler getScheduler() {
		return scheduler;
	}
}
