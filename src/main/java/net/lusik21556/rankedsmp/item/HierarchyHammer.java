package net.lusik21556.rankedsmp.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of items/HierarchyHammer.java. It's a plain vanilla Mace marked with a
 * bit of custom NBT data plus custom model data (for a resource pack to hook
 * into, same as the original relied on a texture pack for its look) so the
 * combat handler can recognise it without adding a new Item type - meaning no
 * client-side mod is required to hold or swing it.
 */
public final class HierarchyHammer {
	private static final String MARKER_KEY = "hierarchy_hammer";
	private static final float CUSTOM_MODEL_DATA = 543.0f;

	private HierarchyHammer() {
	}

	public static ItemStack create() {
		ItemStack item = new ItemStack(Items.MACE);

		item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("HIERARCHY MACE").formatted(Formatting.GOLD, Formatting.BOLD));

		List<Text> lore = new ArrayList<>();
		lore.add(Text.literal("A powerful hammer that manifested").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		lore.add(Text.literal("as a result of improper judgement.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		lore.add(Text.literal(""));
		lore.add(Text.literal("SENTENCING ").formatted(Formatting.GOLD).append(Text.literal("RMB").formatted(Formatting.WHITE)));
		lore.add(Text.literal("Launch forward, allowing you to initiate").formatted(Formatting.GRAY));
		lore.add(Text.literal("hits with the weapon. This dash will stop").formatted(Formatting.GRAY));
		lore.add(Text.literal("Missing an attack will put it on cooldown.").formatted(Formatting.GRAY));
		lore.add(Text.literal(""));
		lore.add(Text.literal("VERDICT").formatted(Formatting.GOLD));
		lore.add(Text.literal("After hitting 4 consecutive smash hits without").formatted(Formatting.GRAY));
		lore.add(Text.literal("missing, your next hit executes the verdict.").formatted(Formatting.GRAY));
		item.set(DataComponentTypes.LORE, new LoreComponent(lore));

		item.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
				List.of(CUSTOM_MODEL_DATA), List.of(), List.of(), List.of()));

		NbtCompound marker = new NbtCompound();
		marker.putBoolean(MARKER_KEY, true);
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, item, marker);

		return item;
	}

	public static boolean isHierarchyHammer(ItemStack item) {
		if (item == null || !item.isOf(Items.MACE)) {
			return false;
		}
		return item.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt().getBoolean(MARKER_KEY, false);
	}

}
