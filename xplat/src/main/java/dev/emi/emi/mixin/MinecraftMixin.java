package dev.emi.emi.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;

/**
 * Re-runs the EMI reload after a resource reload (F3+T, pack screen changes): the EmiData reload
 * listeners have refreshed their data by the time the returned future completes, so the rebuilt
 * index/search pick it up. The world guard skips the startup resource reload; a reload already in
 * flight folds via the reload worker's restart flag.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(at = @At("RETURN"), method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;")
	private void emi$reloadResourcePacks(boolean force, GameLoadCookie cookie, CallbackInfoReturnable<CompletableFuture<Void>> info) {
		CompletableFuture<Void> future = info.getReturnValue();
		if (future != null) {
			future.thenRun(() -> {
				if (Minecraft.getInstance().level != null) {
					EmiReloadManager.reload();
				}
			});
		}
	}
}
