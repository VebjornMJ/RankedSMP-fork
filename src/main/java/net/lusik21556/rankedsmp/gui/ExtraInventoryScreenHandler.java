package net.lusik21556.rankedsmp.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

/**
 * A vanilla-rendered chest-like inventory (no client mod required) that gives
 * a player extra storage slots. Slots beyond {@code usableSlots} are locked:
 * they display a "Locked Slot" barrier placed by {@link net.lusik21556.rankedsmp.inventory.InventoryManager}
 * and reject both insertion and extraction, mirroring the original plugin's
 * barrier-filled slots.
 */
public class ExtraInventoryScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final int rows;
	private final int usableSlots;
	private final Runnable onClose;

	public ExtraInventoryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, int rows, int usableSlots, Runnable onClose) {
		super(typeForRows(rows), syncId);
		checkSize(inventory, rows * 9);
		this.inventory = inventory;
		this.rows = rows;
		this.usableSlots = usableSlots;
		this.onClose = onClose;
		inventory.onOpen(playerInventory.player);

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				addSlot(new LockableSlot(inventory, index, 8 + col * 18, 18 + row * 18, index < usableSlots));
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
		return inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot != null && slot.hasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();
			if (slotIndex < rows * 9) {
				if (!insertItem(stack, rows * 9, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (!insertItem(stack, 0, usableSlots, false)) {
					return ItemStack.EMPTY;
				}
			}
			if (stack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return result;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		inventory.onClose(player);
		onClose.run();
	}

	public Inventory backingInventory() {
		return inventory;
	}

	private static class LockableSlot extends Slot {
		private final boolean usable;

		LockableSlot(Inventory inventory, int index, int x, int y, boolean usable) {
			super(inventory, index, x, y);
			this.usable = usable;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return usable;
		}

		@Override
		public boolean canTakeItems(PlayerEntity player) {
			return usable;
		}
	}
}
