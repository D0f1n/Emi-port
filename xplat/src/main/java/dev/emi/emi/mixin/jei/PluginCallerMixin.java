package dev.emi.emi.mixin.jei;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.Sets;

import dev.emi.emi.EmiPort;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.Identifier;

/**
 * Filters which JEI plugins run on which loading stages, so JEI never builds its own GUI: the
 * jemi bridge supplies the runtime pieces instead. Targets a JEI-internal class ({@code @Pseudo},
 * applied only when JEI is present); plugin uids and stage titles below were read from the
 * JEI 30.6 jars.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public class PluginCallerMixin {
	@Unique
	private static final Set<Identifier> SKIPPED = Sets.newHashSet(
		EmiPort.id("jei", "minecraft"), EmiPort.id("jei", "internal"), EmiPort.id("jei", "gui"),
		EmiPort.id("jei", "fabric_gui"), EmiPort.id("jei", "neoforge_gui")
	);
	// TODO(polish): the original also skipped whole plugins for mods that ship a native EMI
	// plugin (SKIPPED_MODS via the loader plugin registry); returns with plugin discovery.

	@Redirect(at = @At(value = "INVOKE", target = "java/util/function/Consumer.accept(Ljava/lang/Object;)V"),
		method = "callOnPlugins", remap = false)
	private static void callOnPlugins(Consumer<IModPlugin> target, Object value, String title, List<IModPlugin> plugins, Consumer<IModPlugin> func) {
		IModPlugin plugin = (IModPlugin) value;
		Identifier uid = plugin.getPluginUid();
		if (SKIPPED.contains(uid)) {
			switch (title) {
				case "Registering categories" -> {}
				case "Registering ingredients" -> {}
				case "Registering vanilla category extensions" -> {}
				case "Sending Runtime" -> {}
				case "Sending Runtime Unavailable" -> {}
				default -> {
					return;
				}
			}
		}
		target.accept(plugin);
	}
}
