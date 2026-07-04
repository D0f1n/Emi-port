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
 * Draws the EMI overlay on top of container screens. Hooked on the final
 * {@code extractRenderStateWithTooltipAndSubtitles}, which {@code Gui} invokes for the active screen every
 * frame, rather than on {@code AbstractContainerScreen.extractRenderState}: subclasses like
 * {@code AbstractRecipeBookScreen} (crafting table, furnaces, survival inventory) re-implement that method
 * without calling the base one, so a hook there never runs for them. The injection point is right before
 * {@code extractDeferredElements} — after the screen's own content, under deferred tooltips.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
	private void emi$renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		if ((Object) this instanceof AbstractContainerScreen<?> screen) {
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
			EmiScreenManager.render(graphics, screen, accessor.getLeftPos(), accessor.getTopPos(),
				accessor.getImageWidth(), accessor.getImageHeight(), mouseX, mouseY, partialTick);
		}
	}

}
