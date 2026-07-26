package net.lusik21556.rankedsmp.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.lusik21556.rankedsmp.RankedSMP;
import net.lusik21556.rankedsmp.gui.RankManagementGui;
import net.lusik21556.rankedsmp.inventory.InventoryManager;
import net.lusik21556.rankedsmp.item.HierarchyHammer;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Port of RankedSMPCommand.java. `/rankedsmp` and its `/rsmp` alias expose
 * the admin subcommands; `/extrainventory` and `/einv` are the player-facing
 * shortcut (unchanged from the original, which registered it as its own
 * command as well as a `/rankedsmp extrainventory` subcommand).
 */
public class RankedCommand {
	/** Equivalent of the plugin.yml permission "rankedsmp.admin" (default: op). */
	private static final PermissionCheck ADMIN_CHECK = new PermissionCheck.Require(DefaultPermissions.GAMEMASTERS);

	public static void register(RankManager rankManager, InventoryManager inventoryManager, RankManagementGui gui) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(buildRoot("rankedsmp", rankManager, inventoryManager, gui));
			dispatcher.register(buildRoot("rsmp", rankManager, inventoryManager, gui));

			dispatcher.register(CommandManager.literal("extrainventory")
					.executes(ctx -> openExtraInventory(ctx, inventoryManager)));
			dispatcher.register(CommandManager.literal("einv")
					.executes(ctx -> openExtraInventory(ctx, inventoryManager)));
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildRoot(
			String name, RankManager rankManager, InventoryManager inventoryManager, RankManagementGui gui) {
		return CommandManager.literal(name)
				.executes(ctx -> sendHelp(ctx))
				.then(CommandManager.literal("help").executes(ctx -> sendHelp(ctx)))
				.then(CommandManager.literal("start")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.executes(ctx -> {
							rankManager.startRankedSMP(ctx.getSource().getServer());
							return 1;
						}))
				.then(CommandManager.literal("reload")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.executes(ctx -> reload(ctx, rankManager)))
				.then(CommandManager.literal("manage")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.executes(ctx -> openManage(ctx, gui)))
				.then(CommandManager.literal("extrainventory")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.executes(ctx -> openExtraInventory(ctx, inventoryManager)))
				.then(CommandManager.literal("ei")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.executes(ctx -> openExtraInventory(ctx, inventoryManager)))
				.then(CommandManager.literal("rank")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("player", StringArgumentType.word())
										.suggests(ONLINE_PLAYERS)
										.then(CommandManager.argument("rank", IntegerArgumentType.integer(1, 20))
												.executes(ctx -> rankSet(ctx, rankManager)))))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("player", StringArgumentType.word())
										.suggests(ONLINE_PLAYERS)
										.executes(ctx -> rankRemove(ctx, rankManager)))))
				.then(CommandManager.literal("give")
						.requires(CommandManager.requirePermissionLevel(ADMIN_CHECK))
						.then(CommandManager.literal("mace")
								.then(CommandManager.argument("player", StringArgumentType.word())
										.suggests(ONLINE_PLAYERS)
										.executes(RankedCommand::giveMace))));
	}

	private static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYERS = (ctx, builder) -> {
		String remaining = builder.getRemaining().toLowerCase();
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			String name = player.getGameProfile().name();
			if (name.toLowerCase().startsWith(remaining)) {
				builder.suggest(name);
			}
		}
		return builder.buildFuture();
	};

	private static int sendHelp(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		source.sendFeedback(() -> Text.literal("RankedSMP Commands").formatted(Formatting.GOLD), false);
		source.sendFeedback(() -> line("/rankedsmp help", "- Show this help menu"), false);
		source.sendFeedback(() -> line("/rankedsmp start", "- Start ranked mode and assign ranks"), false);
		source.sendFeedback(() -> line("/rankedsmp reload", "- Reload configuration"), false);
		source.sendFeedback(() -> line("/rankedsmp manage", "- Open rank management GUI"), false);
		source.sendFeedback(() -> line("/rankedsmp rank set <player> <rank>", "- Set a player's rank"), false);
		source.sendFeedback(() -> line("/rankedsmp rank remove <player>", "- Remove a player's rank"), false);
		source.sendFeedback(() -> line("/rankedsmp give mace <player>", "- Give Hierarchy Hammer to a player"), false);
		source.sendFeedback(() -> line("/rankedsmp extrainventory", "- Open your extra inventory"), false);
		return 1;
	}

	private static Text line(String command, String description) {
		return Text.literal(command + " ").formatted(Formatting.AQUA).append(Text.literal(description).formatted(Formatting.GRAY));
	}

	private static int reload(CommandContext<ServerCommandSource> ctx, RankManager rankManager) {
		ServerCommandSource source = ctx.getSource();
		MinecraftServer server = source.getServer();
		RankedSMP mod = RankedSMP.getInstance();
		mod.reloadConfig();
		source.sendFeedback(() -> Text.literal("Configuration reloaded successfully!").formatted(Formatting.GREEN), false);
		rankManager.refreshAllOnlinePlayers(server);
		int count = server.getPlayerManager().getPlayerList().size();
		source.sendFeedback(() -> Text.literal("Updated " + count + " online players.").formatted(Formatting.YELLOW), false);
		return 1;
	}

	private static int openManage(CommandContext<ServerCommandSource> ctx, RankManagementGui gui) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
			return 0;
		}
		gui.openGui(player);
		return 1;
	}

	private static int openExtraInventory(CommandContext<ServerCommandSource> ctx, InventoryManager inventoryManager) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can use this command!").formatted(Formatting.RED));
			return 0;
		}
		inventoryManager.openExtraInventory(player);
		return 1;
	}

	private static int rankSet(CommandContext<ServerCommandSource> ctx, RankManager rankManager) {
		ServerCommandSource source = ctx.getSource();
		MinecraftServer server = source.getServer();
		String name = StringArgumentType.getString(ctx, "player");
		int rank = IntegerArgumentType.getInteger(ctx, "rank");

		ResolvedPlayer target = resolvePlayer(server, name);
		if (target == null) {
			source.sendError(Text.literal("Player not found!").formatted(Formatting.RED));
			return 0;
		}

		UUID currentHolder = rankManager.getPlayerWithRank(server, rank);
		if (currentHolder != null && !currentHolder.equals(target.uuid())) {
			String holderName = resolveName(server, currentHolder);
			source.sendError(Text.literal("Rank #" + rank + " is already taken by " + holderName + "!").formatted(Formatting.RED));
			return 0;
		}

		if (target.online() != null) {
			rankManager.setPlayerRank(target.online(), rank);
			target.online().sendMessage(Text.literal("Your rank has been set to ")
					.formatted(Formatting.GREEN)
					.append(Text.literal("#" + rank).formatted(Formatting.YELLOW))
					.append(Text.literal("!").formatted(Formatting.GREEN)), false);
		} else {
			rankManager.setOfflineRank(server, target.uuid(), rank);
		}
		source.sendFeedback(() -> Text.literal("Set " + target.name() + "'s rank to #" + rank).formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int rankRemove(CommandContext<ServerCommandSource> ctx, RankManager rankManager) {
		ServerCommandSource source = ctx.getSource();
		MinecraftServer server = source.getServer();
		String name = StringArgumentType.getString(ctx, "player");

		ResolvedPlayer target = resolvePlayer(server, name);
		if (target == null) {
			source.sendError(Text.literal("Player not found!").formatted(Formatting.RED));
			return 0;
		}

		if (target.online() != null) {
			rankManager.removePlayerRank(target.online());
			target.online().sendMessage(Text.literal("Your rank has been removed!").formatted(Formatting.YELLOW), false);
		} else {
			rankManager.removeOfflineRank(server, target.uuid());
		}
		source.sendFeedback(() -> Text.literal("Removed " + target.name() + "'s rank!").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int giveMace(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "player");
		ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(name);
		if (target == null) {
			source.sendError(Text.literal("Player not found!").formatted(Formatting.RED));
			return 0;
		}
		ItemStack mace = HierarchyHammer.create();
		target.getInventory().insertStack(mace);
		if (!mace.isEmpty()) {
			target.dropItem(mace, false);
		}
		target.sendMessage(Text.literal("You received the Hierarchy Hammer!").formatted(Formatting.GREEN), false);
		source.sendFeedback(() -> Text.literal("Gave Hierarchy Hammer to " + target.getGameProfile().name()).formatted(Formatting.GREEN), false);
		return 1;
	}

	private static ResolvedPlayer resolvePlayer(MinecraftServer server, String name) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
		if (online != null) {
			return new ResolvedPlayer(online.getUuid(), online.getGameProfile().name(), online);
		}
		return server.getApiServices().nameToIdCache().findByName(name)
				.map(entry -> new ResolvedPlayer(entry.id(), entry.name(), null))
				.orElse(null);
	}

	private static String resolveName(MinecraftServer server, UUID uuid) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			return online.getGameProfile().name();
		}
		return server.getApiServices().nameToIdCache().getByUuid(uuid)
				.map(PlayerConfigEntry::name)
				.orElse(uuid.toString());
	}

	private record ResolvedPlayer(UUID uuid, String name, ServerPlayerEntity online) {
	}
}
