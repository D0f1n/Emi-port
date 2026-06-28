package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.debug.EmiDebugRender;
import dev.emi.emi.mixin.accessor.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The container-screen render hook — this is where the EMI overlay will draw. For now it drives the debug
 * harness, positioned relative to the GUI via {@link AbstractContainerScreenAccessor}, to prove the inject
 * and the accessor on both loaders.
 *
 * <p>TODO(screen): remove debug render harness — the real overlay replaces the {@code EmiDebugRender} call;
 * the mixin and accessor stay as the overlay's entry point and geometry source.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
	private void emi$renderDebugOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) (Object) this;
		EmiDebugRender.renderDebug(graphics, accessor.getLeftPos() - 24, accessor.getTopPos());
	}
}
