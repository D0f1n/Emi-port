package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Exposes the container-screen GUI geometry. This is exactly what the EMI overlay needs to position its
 * panel next to the inventory window; the screen round reuses it as-is.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

	@Accessor("leftPos")
	int getLeftPos();

	@Accessor("topPos")
	int getTopPos();

	@Accessor("imageWidth")
	int getImageWidth();

	@Accessor("imageHeight")
	int getImageHeight();
}
