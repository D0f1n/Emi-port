package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Routes container-screen mouse input to the EMI overlay. HEAD-cancellable so EMI swallows clicks on grid
 * stacks / the search bar and scroll over the panel before vanilla processes them; otherwise it passes
 * through untouched. Keyboard input ({@code keyPress}/{@code charTyped}) is handled globally by
 * {@link KeyboardHandlerMixin} so it works uniformly across all screens (including the creative inventory,
 * which overrides key handling).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenInputMixin {

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
		at = @At("HEAD"), cancellable = true)
	private void emi$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (EmiScreenManager.mouseClicked(event, doubleClick)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
	private void emi$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (EmiScreenManager.mouseScrolled(mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
		at = @At("HEAD"), cancellable = true)
	private void emi$mouseDragged(MouseButtonEvent event, double deltaX, double deltaY,
			CallbackInfoReturnable<Boolean> cir) {
		if (EmiScreenManager.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
		at = @At("HEAD"), cancellable = true)
	private void emi$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (EmiScreenManager.mouseReleased(event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}
}
