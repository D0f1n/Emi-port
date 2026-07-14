package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Exposes the container-screen GUI geometry. This is exactly what the EMI overlay needs to position its
 * panel next to the inventory window; the screen round reuses it as-is.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

	// The original read the screen's focused slot through a public getter; 26.2 keeps the
	// hoveredSlot field but exposes no accessor for it.
	@Accessor("hoveredSlot")
	Slot getHoveredSlot();

	@Accessor("leftPos")
	int getLeftPos();

	@Accessor("topPos")
	int getTopPos();

	@Accessor("imageWidth")
	int getImageWidth();

	@Accessor("imageHeight")
	int getImageHeight();
}
