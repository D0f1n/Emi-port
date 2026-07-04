package dev.emi.emi.mixin.accessor;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The brewing mix lists are private and there is no public enumeration, so EMI reads them directly
 * (same approach as the original's per-loader brewing access).
 */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {

	@Accessor("containers")
	List<Ingredient> emi$getContainers();

	@Accessor("potionMixes")
	List<?> emi$getPotionMixes();

	@Accessor("containerMixes")
	List<?> emi$getContainerMixes();
}
