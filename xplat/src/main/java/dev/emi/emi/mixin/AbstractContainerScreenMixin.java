package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.mixin.accessor.AbstractContainerScreenAccessor;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Draws the EMI overlay on top of the container screen. The panel is positioned beside the GUI using the
 * geometry from {@link AbstractContainerScreenAccessor}; mouse coordinates come straight from the render
 * parameters, so the hover tooltip works without any mouse mixin.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
	private void emi$renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) (Object) this;
		EmiScreenManager.render(graphics, (Screen) (Object) this, accessor.getLeftPos(), accessor.getTopPos(),
			accessor.getImageWidth(), accessor.getImageHeight(), mouseX, mouseY, partialTick);
	}

	@Inject(method = "removed()V", at = @At("HEAD"))
	private void emi$removed(CallbackInfo ci) {
		// Drop search focus when the container screen closes, so in-game typing isn't absorbed.
		EmiScreenManager.onScreenRemoved();
	}
}
