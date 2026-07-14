package dev.emi.emi.platform.fabric;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
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

public class EmiAgnosFabric extends EmiAgnos {

	static {
		EmiAgnos.delegate = new EmiAgnosFabric();
	}

	@Override
	protected String getLoaderNameAgnos() {
		return "fabric";
	}

	@Override
	protected java.nio.file.Path getConfigDirectoryAgnos() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	protected boolean isDevelopmentEnvironmentAgnos() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	protected boolean isModLoadedAgnos(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	@Override
	protected boolean isForgeAgnos() {
		return false;
	}

	@Override
	protected boolean isEnchantableAgnos(ItemStack stack, Holder<Enchantment> enchantment) {
		return true;
	}

	@Override
	protected Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return FluidVariantAttributes.getName(FluidVariant.of(fluid, componentChanges));
	}

	@Override
	protected boolean isFloatyFluidAgnos(FluidEmiStack stack) {
		FluidVariant fluid = FluidVariant.of((Fluid) stack.getKey(), stack.getComponentChanges());
		return FluidVariantAttributes.isLighterThanAir(fluid);
	}

	@Override
	protected void renderFluidAgnos(FluidEmiStack stack, GuiGraphicsExtractor draw, int x, int y, float delta,
			int xOff, int yOff, int width, int height) {
		Fluid fluid = (Fluid) stack.getKey();
		TextureAtlasSprite sprite = EmiRenderHelper.getFluidStillSprite(fluid);
		if (sprite == null) {
			return;
		}
		// FluidVariantRendering.getSprites is gone on 26.2 (the sprite is vanilla now), but the color
		// handlers survived and honor modded fluid tints.
		int color = FluidVariantRendering.getColor(FluidVariant.of(fluid, stack.getComponentChanges()));
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
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
		if (container.isPresent()) {
			return container.get().getMetadata().getName();
		}
		container = FabricLoader.getInstance().getModContainer(namespace.replace('_', '-'));
		if (container.isPresent()) {
			return container.get().getMetadata().getName();
		}
		return namespace;
	}

	@Override
	protected boolean canSendToPlayerAgnos(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	@Override
	protected List<EmiPluginContainer> getPluginsAgnos() {
		List<EmiPluginContainer> list = Lists.newArrayList();
		for (EntrypointContainer<EmiPlugin> container : FabricLoader.getInstance()
				.getEntrypointContainers("emi", EmiPlugin.class)) {
			try {
				list.add(new EmiPluginContainer(container.getEntrypoint(),
					container.getProvider().getMetadata().getId()));
			} catch (Throwable t) {
				EmiLog.error("Critical exception thrown when constructing EMI Plugin from mod "
					+ container.getProvider().getMetadata().getId(), t);
			}
		}
		return list;
	}
}
