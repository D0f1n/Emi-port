package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The original's {@code DrawContextMixin}: remembers the last ItemStack any screen requested a
 * tooltip for, giving EMI a hover fallback on screens that expose no slots or stack providers.
 * 26.2 defers tooltip rendering, so the capture point is the ItemStack overload of
 * {@code setTooltipForNextFrame} — screens still call it synchronously while extracting their
 * render state, which is all the fallback needs. Cleared at the start of each screen extraction
 * by {@code ScreenMixin}, so input events between frames see the stack captured last frame.
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

	@Inject(at = @At("HEAD"),
		method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V")
	private void emi$setTooltipForNextFrame(Font font, ItemStack stack, int x, int y, CallbackInfo info) {
		EmiScreenManager.lastStackTooltipRendered = stack;
	}
}
