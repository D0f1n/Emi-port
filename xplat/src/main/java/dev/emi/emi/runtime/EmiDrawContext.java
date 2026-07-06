package dev.emi.emi.runtime;

import org.joml.Matrix3x2fStack;

import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The EMI drawing context, rebuilt for 26.2.
 *
 * <p>On 26.1+ the vanilla {@code GuiGraphics} type was renamed to {@link GuiGraphicsExtractor} and
 * rebuilt on the two-phase {@code GuiRenderState} model (draw calls are recorded, then replayed and
 * batched). This wraps that context (has-a, like the original wrapped {@code DrawContext}) and only calls
 * its public surface, so no access-widener is needed.
 *
 * <p>Stage 4 scope: the render primitives the stack types need. The original's {@code setShaderColor} /
 * depth / blend wrappers are dropped — the new pipeline has no per-call shader color and the item path
 * needs no manual depth/blend. They return with the screen round if a call site wants them.
 */
public class EmiDrawContext {
	private final Minecraft client = Minecraft.getInstance();
	private final GuiGraphicsExtractor context;

	private EmiDrawContext(GuiGraphicsExtractor context) {
		this.context = context;
	}

	public static EmiDrawContext wrap(GuiGraphicsExtractor context) {
		return new EmiDrawContext(context);
	}

	public GuiGraphicsExtractor raw() {
		return context;
	}

	/** The GUI transform stack. On 26.2 this is a 2D affine stack ({@link Matrix3x2fStack}). */
	public Matrix3x2fStack pose() {
		return context.pose();
	}

	public void push() {
		pose().pushMatrix();
	}

	public void pop() {
		pose().popMatrix();
	}

	public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
		drawTexture(texture, x, y, width, height, u, v, width, height, 256, 256);
	}

	public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v,
			int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
			regionWidth, regionHeight, textureWidth, textureHeight);
	}

	/** Blit with an ARGB tint; the 26.2 pipeline has no global shader color, the color rides the blit. */
	public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height, int color) {
		context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, width, height, 256, 256, color);
	}

	public void fill(int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + height, color);
	}

	/**
	 * 1.21.1's text renderer promoted alpha-0 colors (e.g. plain 0xFFFFFF) to opaque; 26.2 renders
	 * them literally, i.e. invisible. Restore the old semantics for EMI's call sites.
	 */
	private static int opaque(int color) {
		if ((color & 0xFC000000) == 0) {
			color |= 0xFF000000;
		}
		return color;
	}

	public void drawText(Component text, int x, int y) {
		drawText(text, x, y, -1);
	}

	public void drawText(Component text, int x, int y, int color) {
		context.text(font(), text, x, y, opaque(color), false);
	}

	public void drawTextWithShadow(Component text, int x, int y) {
		drawTextWithShadow(text, x, y, -1);
	}

	public void drawTextWithShadow(Component text, int x, int y, int color) {
		context.text(font(), text, x, y, opaque(color), true);
	}

	public void drawText(FormattedCharSequence text, int x, int y, int color) {
		context.text(font(), text, x, y, opaque(color), false);
	}

	public void drawCenteredTextWithShadow(Component text, int x, int y, int color) {
		context.centeredText(font(), text, x, y, opaque(color));
	}

	public void drawTextWithShadow(FormattedCharSequence text, int x, int y, int color) {
		context.text(font(), text, x, y, opaque(color), true);
	}

	public void drawStack(EmiIngredient stack, int x, int y) {
		stack.render(context, x, y, delta());
	}

	public void drawStack(EmiIngredient stack, int x, int y, int flags) {
		stack.render(context, x, y, delta(), flags);
	}

	public void drawStack(EmiIngredient stack, int x, int y, float delta, int flags) {
		stack.render(context, x, y, delta, flags);
	}

	private Font font() {
		return client.font;
	}

	private float delta() {
		return client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}
}
