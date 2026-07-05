package dev.emi.emi.jemi;

import java.util.List;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReload;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/**
 * The JEI bridge. Registered with JEI as a regular plugin (annotation scan on NeoForge, the
 * {@code jei_mod_plugin} entrypoint on Fabric) and with EMI as an {@link EmiPlugin} — but only
 * when JEI is actually installed; without JEI this class is never loaded.
 *
 * <p>JEI builds its runtime asynchronously after world join, which can land before or after EMI's
 * own world-join reload. The original waited for the runtime on the reload worker; on 26.2 the
 * reload runs on the client thread, so instead {@link #onRuntimeAvailable} re-schedules the
 * (idempotent) reload and {@link #register} simply skips when the runtime isn't there yet.
 */
@JeiPlugin
public class JemiPlugin implements IModPlugin, EmiPlugin {
	public static IJeiRuntime runtime;

	@Override
	public Identifier getPluginUid() {
		return EmiPort.id("emi:jemi");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		JemiPlugin.runtime = runtime;
		EmiLog.info("[JEMI] JEI runtime available, rebuilding the EMI index");
		EmiReload.scheduleReload();
	}

	@Override
	public void onRuntimeUnavailable() {
		JemiPlugin.runtime = null;
	}

	@Override
	public void register(EmiRegistry registry) {
		if (runtime == null) {
			EmiLog.info("[JEMI] JEI runtime not available yet; skipping the JEI bridge this reload");
			return;
		}
		List<IRecipeCategory<?>> categories = runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().toList();
		EmiLog.info("[JEMI] " + categories.size() + " JEI recipe categories available");
	}
}
