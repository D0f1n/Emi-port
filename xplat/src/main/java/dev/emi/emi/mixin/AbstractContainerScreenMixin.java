package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Lifecycle hook for the EMI overlay. {@code removed()} must be hooked here, not on {@code Screen}:
 * {@code AbstractContainerScreen.removed()} overrides the (empty) base method without calling super, so a
 * {@code Screen}-level injection would never run for container screens. The overlay render hook lives in
 * {@link ScreenMixin} for the inverse reason — see there.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "removed()V", at = @At("HEAD"))
	private void emi$removed(CallbackInfo ci) {
		// Drop search focus when the container screen closes, so in-game typing isn't absorbed.
		EmiScreenManager.onScreenRemoved();
	}
}
