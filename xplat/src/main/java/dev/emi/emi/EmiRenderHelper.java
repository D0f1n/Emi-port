package dev.emi.emi;

import java.text.DecimalFormat;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * The EMI render helper: the small widget-overlay drawers used by the stack render methods (amount
 * text + the 4x4 corner sprites from {@code widgets.png}), plus the recipe screen drawing (nine
 * patch, page text, tooltips).
 */
public class EmiRenderHelper {
	public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("#,###.##");
	public static final Component EMPTY_TEXT = EmiPort.literal("");
	public static final Minecraft CLIENT = Minecraft.getInstance();
	public static final Identifier WIDGETS = EmiPort.id("emi", "textures/gui/widgets.png");
	public static final Identifier BUTTONS = EmiPort.id("emi", "textures/gui/buttons.png");
	public static final Identifier BACKGROUND = EmiPort.id("emi", "textures/gui/background.png");
	public static final Identifier CONFIG = EmiPort.id("emi", "textures/gui/config.png");

	public static Component getEmiText() {
		return
			EmiPort.append(
				EmiPort.append(
					EmiPort.literal("E", net.minecraft.network.chat.Style.EMPTY.withColor(0xeb7bfc)),
					EmiPort.literal("M", net.minecraft.network.chat.Style.EMPTY.withColor(0x7bfca2))),
				EmiPort.literal("I", net.minecraft.network.chat.Style.EMPTY.withColor(0x7bebfc)));
	}

	/**
	 * The fluid model vanilla baked for the given fluid. On 26.2 fluid sprites and tint live in the
	 * vanilla {@code FluidStateModelSet} on both loaders (the loader-specific sprite getters —
	 * Fabric's {@code FluidVariantRendering.getSprites} and NeoForge's
	 * {@code IClientFluidTypeExtensions.getStillTexture} — were removed).
	 */
	public static @Nullable FluidModel getFluidModel(Fluid fluid) {
		return CLIENT.getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
	}

	public static @Nullable TextureAtlasSprite getFluidStillSprite(Fluid fluid) {
		FluidModel model = getFluidModel(fluid);
		return model == null ? null : model.stillMaterial().sprite();
	}

	/**
	 * The tint of a fluid from its vanilla model. Untinted fluids (lava) have a null tint source; for
	 * tinted ones the context-free {@code color(state)} is white for biome-tinted fluids (water), so
	 * prefer the in-world color at the player when a world is loaded.
	 */
	public static int getFluidTint(Fluid fluid) {
		FluidModel model = getFluidModel(fluid);
		if (model == null || model.tintSource() == null) {
			return -1;
		}
		BlockState state = fluid.defaultFluidState().createLegacyBlock();
		if (CLIENT.level != null && CLIENT.player != null) {
			return model.tintSource().colorInWorld(state, CLIENT.level, CLIENT.player.blockPosition());
		}
		return model.tintSource().color(state);
	}

	/**
	 * Draws a {@code width}x{@code height} region of a 16x16 sprite starting at texel
	 * ({@code xOff}, {@code yOff}), tinted by {@code color} (alpha forced opaque) — the original
	 * {@code drawTintedSprite}. The old path built a custom position-color-texture buffer; on 26.2
	 * partial regions draw the full sprite shifted and scissor-clipped instead of recomputing UVs.
	 */
	public static void drawTintedSprite(EmiDrawContext context, TextureAtlasSprite sprite, int color,
			int x, int y, int xOff, int yOff, int width, int height) {
		if (sprite == null) {
			return;
		}
		color |= 0xFF000000;
		if (xOff == 0 && yOff == 0 && width == 16 && height == 16) {
			context.raw().blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, color);
		} else {
			context.raw().enableScissor(x, y, x + width, y + height);
			context.raw().blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - xOff, y - yOff, 16, 16, color);
			context.raw().disableScissor();
		}
	}

	public static Component getAmountText(EmiIngredient stack) {
		return getAmountText(stack, stack.getAmount());
	}

	public static Component getAmountText(EmiIngredient stack, long amount) {
		if (stack.isEmpty() || amount == 0) {
			return EMPTY_TEXT;
		}
		if (stack.getEmiStacks().get(0).getKey() instanceof Fluid) {
			return getFluidAmount(amount);
		}
		return EmiPort.literal(TEXT_FORMAT.format(amount));
	}

	public static Component getAmountText(EmiIngredient stack, double amount) {
		if (stack.isEmpty() || amount == 0) {
			return EMPTY_TEXT;
		}
		if (stack.getEmiStacks().get(0).getKey() instanceof Fluid) {
			return EmiConfig.fluidUnit.translate(amount);
		}
		return EmiPort.literal(TEXT_FORMAT.format(amount));
	}

	public static Component getFluidAmount(long amount) {
		return EmiConfig.fluidUnit.translate(amount);
	}

	public static int getAmountOverflow(Component amount) {
		int width = CLIENT.font.width(amount);
		if (width > 14) {
			return width - 14;
		} else {
			return 0;
		}
	}

	public static void renderAmount(EmiDrawContext context, int x, int y, Component amount) {
		int tx = x + 17 - Math.min(14, CLIENT.font.width(amount));
		context.drawTextWithShadow(amount, tx, y + 9, -1);
	}

	public static void renderIngredient(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		context.drawTexture(WIDGETS, x, y, 8, 252, 4, 4);
	}

	public static void renderTag(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		if (ingredient.getEmiStacks().size() > 1) {
			context.drawTexture(WIDGETS, x, y + 12, 0, 252, 4, 4);
		}
	}

	public static void renderRemainder(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		for (EmiStack stack : ingredient.getEmiStacks()) {
			EmiStack remainder = stack.getRemainder();
			if (!remainder.isEmpty()) {
				if (remainder.equals(ingredient)) {
					renderCatalyst(ingredient, context, x, y);
				} else {
					context.drawTexture(WIDGETS, x + 12, y, 4, 252, 4, 4);
				}
				return;
			}
		}
	}

	public static void renderCatalyst(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		context.drawTexture(WIDGETS, x + 12, y, 12, 252, 4, 4);
	}

	// The original lifted this badge by z 200 on the 3D pose stack; the 26.2 2D pose has no z, and
	// the badge draws after the stack like the ingredient/catalyst markers above, so a plain draw works.
	public static void renderRecipeFavorite(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		context.drawTexture(WIDGETS, x + 12, y, 16, 252, 4, 4);
	}

	public static void drawNinePatch(EmiDrawContext context, Identifier texture, int x, int y, int w, int h, int u, int v, int cornerLength, int centerLength) {
		int cor = cornerLength;
		int cen = centerLength;
		int corcen = cor + cen;
		int innerWidth = w - cornerLength * 2;
		int innerHeight = h - cornerLength * 2;
		int coriw = cor + innerWidth;
		int corih = cor + innerHeight;
		// TL
		context.drawTexture(texture, x,         y,         cor,        cor,         u,          v,          cor, cor, 256, 256);
		// T
		context.drawTexture(texture, x + cor,   y,         innerWidth, cor,         u + cor,    v,          cen, cor, 256, 256);
		// TR
		context.drawTexture(texture, x + coriw, y,         cor,        cor,         u + corcen, v,          cor, cor, 256, 256);
		// L
		context.drawTexture(texture, x,         y + cor,   cor,        innerHeight, u,          v + cor,    cor, cen, 256, 256);
		// C
		context.drawTexture(texture, x + cor,   y + cor,   innerWidth, innerHeight, u + cor,    v + cor,    cen, cen, 256, 256);
		// R
		context.drawTexture(texture, x + coriw, y + cor,   cor,        innerHeight, u + corcen, v + cor,    cor, cen, 256, 256);
		// BL
		context.drawTexture(texture, x,         y + corih, cor,        cor,         u,          v + corcen, cor, cor, 256, 256);
		// B
		context.drawTexture(texture, x + cor,   y + corih, innerWidth, cor,         u + cor,    v + corcen, cen, cor, 256, 256);
		// BR
		context.drawTexture(texture, x + coriw, y + corih, cor,        cor,         u + corcen, v + corcen, cor, cor, 256, 256);
	}

	public static void drawScroll(EmiDrawContext context, int x, int y, int width, int height, int progress, int total, int color) {
		if (total <= 1) {
			return;
		}
		int start = x + width * progress / total;
		int end = start + Math.max(width / total, 1);
		if (progress == total - 1) {
			end = x + width;
			start = end - Math.max(width / total, 1);
		}
		context.fill(start, y, end - start, height, color);
	}

	public static Component getPageText(int page, int total, int maxWidth) {
		Component text = EmiPort.translatable("emi.page", page, total);
		if (CLIENT.font.width(text) > maxWidth) {
			text = EmiPort.translatable("emi.page.short", page, total);
			if (CLIENT.font.width(text) > maxWidth) {
				text = EmiPort.literal("" + page);
				if (CLIENT.font.width(text) > maxWidth) {
					text = EmiPort.literal("");
				}
			}
		}
		return text;
	}

	/**
	 * Draws a tooltip through the two-phase context. The original's wrap-to-width reflow of long
	 * lines is polish-round work.
	 */
	public static void drawTooltip(EmiDrawContext context, List<ClientTooltipComponent> components, int x, int y) {
		if (components.isEmpty()) {
			return;
		}
		context.raw().tooltip(CLIENT.font, components, x, Math.max(16, y), DefaultTooltipPositioner.INSTANCE, null);
	}
}
