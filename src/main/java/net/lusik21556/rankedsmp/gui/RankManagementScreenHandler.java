package net.lusik21556.rankedsmp.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Read-only grid of player heads for admins to inspect/reorganise ranks.
 * Every top-inventory slot rejects normal item handling; clicks are instead
 * forwarded to {@link RankManagementGui#handleClick} which implements the
 * actual select/move/remove logic and re-renders the grid afterwards.
 */
public class RankManagementScreenHandler extends ScreenHandler {
	private final SimpleInventory inventory;
	private final int rows;
	private final RankManagementGui gui;

	public RankManagementScreenHandler(int syncId, PlayerInventory playerInventory, int rows, RankManagementGui gui) {
		super(typeForRows(rows), syncId);
		this.rows = rows;
		this.gui = gui;
		this.inventory = new SimpleInventory(rows * 9);

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new DisplaySlot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		addPlayerSlots(playerInventory, 8, 18 + rows * 18 + 13);
	}

	private static ScreenHandlerType<?> typeForRows(int rows) {
		return switch (rows) {
			case 1 -> ScreenHandlerType.GENERIC_9X1;
			case 2 -> ScreenHandlerType.GENERIC_9X2;
			case 3 -> ScreenHandlerType.GENERIC_9X3;
			case 4 -> ScreenHandlerType.GENERIC_9X4;
			case 5 -> ScreenHandlerType.GENERIC_9X5;
			default -> ScreenHandlerType.GENERIC_9X6;
		};
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (actionType == SlotActionType.PICKUP && slotIndex >= 0 && slotIndex < rows * 9 && player instanceof ServerPlayerEntity admin) {
			if (button == 0) {
				gui.handleClick(admin, slotIndex, true);
			} else if (button == 1) {
				gui.handleClick(admin, slotIndex, false);
			}
			return;
		}
		super.onSlotClick(slotIndex, button, actionType, player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (player instanceof ServerPlayerEntity admin) {
			gui.onClosed(admin);
		}
	}

	public void setSlotDisplay(int index, ItemStack stack) {
		inventory.setStack(index, stack);
	}

	public int getRows() {
		return rows;
	}

	/** A slot that never accepts insertion or extraction; purely for display. */
	private static class DisplaySlot extends Slot {
		DisplaySlot(SimpleInventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public boolean canTakeItems(PlayerEntity player) {
			return false;
		}
	}
}
