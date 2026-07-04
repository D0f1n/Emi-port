package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Accessor for the package-private {@code PotionBrewing.Mix} record; instances are handled as
 * {@code Object} and cast to this interface.
 */
@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionBrewingMixAccessor {

	@Accessor("from")
	Holder<?> emi$getFrom();

	@Accessor("ingredient")
	Ingredient emi$getIngredient();

	@Accessor("to")
	Holder<?> emi$getTo();
}
