package dev.emi.emi.platform.neoforge;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

import org.objectweb.asm.Type;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforgespi.language.ModFileScanData;

public class EmiAgnosNeoForge extends EmiAgnos {

	static {
		EmiAgnos.delegate = new EmiAgnosNeoForge();
	}

	@Override
	protected String getLoaderNameAgnos() {
		return "neoforge";
	}

	@Override
	protected java.nio.file.Path getConfigDirectoryAgnos() {
		return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
	}

	@Override
	protected boolean isDevelopmentEnvironmentAgnos() {
		return !net.neoforged.fml.loading.FMLEnvironment.isProduction();
	}

	@Override
	protected boolean isModLoadedAgnos(String id) {
		return ModList.get().isLoaded(id);
	}

	@Override
	protected boolean isForgeAgnos() {
		return true;
	}

	@Override
	protected boolean isEnchantableAgnos(ItemStack stack, Holder<Enchantment> enchantment) {
		// isBookEnchantable is gone on 26.2; supportsEnchantment is the per-stack override hook.
		return stack.supportsEnchantment(enchantment);
	}

	@Override
	protected Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return new FluidStack(fluid, 1000, componentChanges).getHoverName();
	}

	@Override
	protected boolean isFloatyFluidAgnos(FluidEmiStack stack) {
		return ((Fluid) stack.getKey()).getFluidType().isLighterThanAir();
	}

	@Override
	protected void renderFluidAgnos(FluidEmiStack stack, GuiGraphicsExtractor draw, int x, int y, float delta,
			int xOff, int yOff, int width, int height) {
		Fluid fluid = (Fluid) stack.getKey();
		TextureAtlasSprite sprite = EmiRenderHelper.getFluidStillSprite(fluid);
		if (sprite == null) {
			return;
		}
		// IClientFluidTypeExtensions lost getStillTexture/getTintColor on 26.2; both the sprite and
		// the tint come from the vanilla fluid model.
		int color = EmiRenderHelper.getFluidTint(fluid);
		EmiRenderHelper.drawTintedSprite(EmiDrawContext.wrap(draw), sprite, color, x, y, xOff, yOff, width, height);
	}

	@Override
	protected String getModNameAgnos(String namespace) {
		if (namespace.equals("c")) {
			return "Common";
		}
		if (namespace.equals("minecraft")) {
			return "Minecraft";
		}
		Optional<? extends ModContainer> container = ModList.get().getModContainerById(namespace);
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		container = ModList.get().getModContainerById(namespace.replace('_', '-'));
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		return namespace;
	}

	@Override
	protected boolean canSendToPlayerAgnos(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection.hasChannel(type);
	}

	@Override
	protected List<EmiPluginContainer> getPluginsAgnos() {
		List<EmiPluginContainer> containers = Lists.newArrayList();
		Type entrypointType = Type.getType(EmiEntrypoint.class);
		for (ModFileScanData data : ModList.get().getAllScanData()) {
			for (ModFileScanData.AnnotationData annot : data.getAnnotations()) {
				try {
					if (entrypointType.equals(annot.annotationType())) {
						Class<?> clazz = Class.forName(annot.memberName());
						if (EmiPlugin.class.isAssignableFrom(clazz)) {
							Class<? extends EmiPlugin> pluginClass = clazz.asSubclass(EmiPlugin.class);
							EmiPlugin plugin = pluginClass.getConstructor().newInstance();
							String id = data.getIModInfoData().get(0).getMods().get(0).getModId();
							containers.add(new EmiPluginContainer(plugin, id));
						} else {
							EmiLog.error("EmiEntrypoint " + annot.memberName() + " does not implement EmiPlugin");
						}
					}
				} catch (Throwable t) {
					EmiLog.error("Exception constructing entrypoint:", t);
				}
			}
		}
		return containers;
	}
}
