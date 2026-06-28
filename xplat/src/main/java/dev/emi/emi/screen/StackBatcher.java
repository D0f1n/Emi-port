package dev.emi.emi.screen;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * @author Una "unascribed" Thompson
 *
 * <p>Rebuilt for 26.2 as a thin pass-through. The original hand-rolled a batched renderer on the old
 * immediate-mode model ({@code BufferBuilder} / {@code VertexBuffer} / {@code RenderSystem.setShader}),
 * all of which were deleted in the Blaze3D rewrite. On 26.2 the two-phase {@code GuiRenderState} batches
 * GUI elements (including items) automatically, so per-stack {@link EmiIngredient#render} already goes
 * through a batched path. This keeps the class and the {@link Batchable} marker so the screen round's
 * call sites compile, but every render just forwards to the stack.
 *
 * <p>TODO(screen): revisit if a real manual batcher is ever needed on top of the render-state batching.
 */
public class StackBatcher {

	/** Marker kept for the screen round. The original's {@code renderForBatch}/buffer methods relied on
	 *  deleted APIs and are gone; the render-state model makes them unnecessary. */
	public interface Batchable {
	}

	public static boolean isEnabled() {
		// Manual batching is superseded by the vanilla GuiRenderState; render goes direct.
		return false;
	}

	public boolean isPopulated() {
		return false;
	}

	public void repopulate() {
	}

	public void begin(int x, int y, int z) {
	}

	public void render(EmiIngredient stack, GuiGraphicsExtractor draw, int x, int y, float delta) {
		stack.render(draw, x, y, delta);
	}

	public void render(EmiIngredient stack, GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		stack.render(draw, x, y, delta, flags);
	}

	public void draw() {
	}

	/** Pool of batchers, preserved from the original so the screen round can claim/unclaim. */
	public static class ClaimedCollection {
		private final java.util.Set<StackBatcher> claimed = Sets.newHashSet();
		private final List<StackBatcher> unclaimed = Lists.newArrayList();

		public StackBatcher claim() {
			synchronized (this) {
				StackBatcher batcher = unclaimed.isEmpty() ? new StackBatcher() : unclaimed.remove(unclaimed.size() - 1);
				claimed.add(batcher);
				return batcher;
			}
		}

		public void unclaim(StackBatcher batcher) {
			synchronized (this) {
				claimed.remove(batcher);
				unclaimed.add(batcher);
			}
		}

		public void unclaimAll() {
			synchronized (this) {
				unclaimed.addAll(claimed);
				claimed.clear();
			}
		}
	}
}
