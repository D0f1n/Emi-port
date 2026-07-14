package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;

/**
 * Exposes the crafting grid backing a vanilla result slot — how the coerced recipe handler finds
 * the grid of a modded crafting table that registered no EMI handler.
 */
@Mixin(ResultSlot.class)
public interface ResultSlotAccessor {

	@Accessor("craftSlots")
	CraftingContainer getCraftSlots();
}
