package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Routes keyboard input to the EMI search bar at the single global dispatch point. {@code charTyped} is not
 * declared on {@code AbstractContainerScreen} (only the {@code ContainerEventHandler} interface default,
 * which a mixin inject does NOT fire on), and {@code keyPress} processes key bindings and the per-screen
 * shortcuts (e.g. the creative inventory's type-to-search) before/around the screen — so a per-screen
 * {@code keyPressed} inject can't reliably suppress them. EMI therefore hooks {@code KeyboardHandler}, as
 * the original EMI did with its keyboard mixin. The guard is strict: input is only absorbed while the EMI
 * search bar is focused (set by clicking it on a container screen, dropped on screen change/close), so
 * vanilla text fields and key bindings are untouched otherwise.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

	@Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
	private void emi$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (EmiScreenManager.isSearchFocused()) {
			if (action != 0) { // not GLFW_RELEASE — press or repeat
				EmiScreenManager.keyPressed(event);
			}
			// Suppress all vanilla key handling (key bindings, screen shortcuts) while typing in search.
			ci.cancel();
		} else if (action == 1 && EmiScreenManager.handleFavoriteKey(event)) { // GLFW_PRESS only
			ci.cancel();
		}
	}

	@Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
	private void emi$charTyped(long window, CharacterEvent event, CallbackInfo ci) {
		if (EmiScreenManager.isSearchFocused()) {
			EmiScreenManager.charTyped(event);
			ci.cancel();
		}
	}
}
