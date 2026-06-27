package dev.emi.emi;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Trimmed data-side helpers for the Stage 3 layer. Recipe/screen/inventory helpers from the original
 * return with their respective rounds.
 */
public class EmiUtil {
	public static final Random RANDOM = new Random();

	public static String subId(Identifier id) {
		return id.getNamespace() + "/" + id.getPath();
	}

	public static String subId(Block block) {
		return subId(EmiPort.getBlockRegistry().getKey(block));
	}

	public static String subId(Item item) {
		return subId(EmiPort.getItemRegistry().getKey(item));
	}

	public static String subId(Fluid fluid) {
		return subId(EmiPort.getFluidRegistry().getKey(fluid));
	}

	public static boolean showAdvancedTooltips() {
		Minecraft client = Minecraft.getInstance();
		return client.options.advancedItemTooltips;
	}

	public static String translateId(String prefix, Identifier id) {
		return prefix + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}
}
