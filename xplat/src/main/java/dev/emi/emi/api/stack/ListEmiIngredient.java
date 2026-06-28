package dev.emi.emi.api.stack;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

@ApiStatus.Internal
public class ListEmiIngredient implements EmiIngredient {
	private final List<? extends EmiIngredient> ingredients;
	private final List<EmiStack> fullList;
	private long amount;
	private float chance = 1;

	@ApiStatus.Internal
	public ListEmiIngredient(List<? extends EmiIngredient> ingredients, long amount) {
		this.ingredients = ingredients;
		this.fullList = ingredients.stream().flatMap(i -> i.getEmiStacks().stream()).toList();
		if (fullList.isEmpty()) {
			throw new IllegalArgumentException("ListEmiIngredient cannot be empty");
		}
		this.amount = amount;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ListEmiIngredient other) {
			return other.getEmiStacks().equals(this.getEmiStacks());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return fullList.hashCode();
	}

	@Override
	public EmiIngredient copy() {
		EmiIngredient stack = new ListEmiIngredient(ingredients, amount);
		stack.setChance(chance);
		return stack;
	}

	@Override
	public String toString() {
		return "Ingredient" + getEmiStacks();
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return fullList;
	}

	@Override
	public long getAmount() {
		return amount;
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		this.amount = amount;
		return this;
	}

	@Override
	public float getChance() {
		return chance;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		this.chance = chance;
		return this;
	}

	@ApiStatus.Internal
	public List<? extends EmiIngredient> getIngredients() {
		return ingredients;
	}

	@Override
	public void render(GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		if ((flags & RENDER_ICON) != 0 && !fullList.isEmpty()) {
			// TODO(screen): the original cycles through the alternatives over time; the primitive round
			// renders the first stack's icon.
			fullList.get(0).render(draw, x, y, delta, -1 ^ RENDER_AMOUNT);
		}
		if ((flags & RENDER_AMOUNT) != 0) {
			String count = amount != 1 ? String.valueOf(amount) : "";
			EmiRenderHelper.renderAmount(context, x, y, EmiPort.literal(count));
		}
		if ((flags & RENDER_INGREDIENT) != 0) {
			EmiRender.renderIngredientIcon(this, draw, x, y);
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, draw, x, y);
		}
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		// TODO(screen): EMI's list tooltip (contained-stacks grid).
		return Lists.newArrayList();
	}
}
