package dev.emi.emi.jemi;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiIngredientAcceptor;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import dev.emi.emi.jemi.impl.extras.JemiRecipeExtrasBuilder;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import dev.emi.emi.jemi.widget.JemiTankWidget;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * A JEI recipe shown inside EMI. Inputs/outputs come from replaying the category's
 * {@code setRecipe} into a capturing layout builder; the display renders the category's own
 * {@code draw} plus EMI slots laid over the JEI slot positions, then the category's extras.
 */
public class JemiRecipe<T> implements EmiRecipe {
	public final List<EmiIngredient> inputs = Lists.newArrayList();
	public final List<EmiIngredient> catalysts = Lists.newArrayList();
	public final List<EmiStack> outputs = Lists.newArrayList();
	public final EmiRecipeCategory recipeCategory;
	public Identifier originalId, id;
	public final IRecipeCategory<T> category;
	public final T recipe;
	public boolean allowTree = true;

	public JemiRecipe(EmiRecipeCategory recipeCategory, IRecipeCategory<T> category, T recipe) {
		this.recipeCategory = recipeCategory;
		this.category = category;
		this.recipe = recipe;
		this.originalId = category.getIdentifier(recipe);
		if (this.originalId != null) {
			this.id = EmiPort.id("jei", "/" + EmiUtil.subId(this.originalId));
		}
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.richTooltipCallback, jrsb.renderers);
		}
		for (JemiIngredientAcceptor acceptor : builder.ingredients) {
			EmiIngredient stack = acceptor.build();
			if (acceptor.role == RecipeIngredientRole.INPUT) {
				inputs.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.CRAFTING_STATION) {
				catalysts.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.OUTPUT) {
				if (stack.getEmiStacks().size() > 1) {
					allowTree = false;
				}
				outputs.addAll(stack.getEmiStacks());
			}
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return recipeCategory;
	}

	@Override
	public @Nullable RecipeHolder<?> getBackingRecipe() {
		return EmiPort.getRecipe(originalId);
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiIngredient> getCatalysts() {
		return catalysts;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public int getDisplayWidth() {
		return category.getWidth();
	}

	@Override
	public int getDisplayHeight() {
		return category.getHeight();
	}

	@Override
	public boolean supportsRecipeTree() {
		return allowTree && EmiRecipe.super.supportsRecipeTree();
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		Optional<IRecipeLayoutDrawable<T>> opt = JemiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
			JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.richTooltipCallback, jrsb.renderers);
		}
		if (opt.isPresent()) {
			widgets.add(new JemiWidget(0, 0, getDisplayWidth(), getDisplayHeight(), opt.get()));
			for (JemiRecipeSlotBuilder sb : builder.slots) {
				JemiRecipeSlot slot = new JemiRecipeSlot(sb);
				if (slot.tankInfo != null) {
					widgets.add(new JemiTankWidget(slot, this));
				} else {
					widgets.add(new JemiSlotWidget(slot, this));
				}
			}
		}
		try {
			JemiRecipeExtrasBuilder extras = new JemiRecipeExtrasBuilder();
			category.createRecipeExtras(extras, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
			for (JemiWidgetBuilder b : extras.widgets) {
				b.addWidgets(widgets);
			}
		} catch (Throwable t) {
			EmiLog.error("Exception adding JEMI extras", t);
		}
	}

	public class JemiWidget extends Widget {

		private final IRecipeLayoutDrawable<T> recipeLayoutDrawable;
		private final Bounds bounds;
		private final int x, y;

		public JemiWidget(int x, int y, int w, int h, IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
			this.recipeLayoutDrawable = recipeLayoutDrawable;
			this.bounds = new Bounds(x, y, w, h);
			this.x = x;
			this.y = y;
		}

		@Override
		public Bounds getBounds() {
			return bounds;
		}

		@Override
		public void render(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
			EmiDrawContext context = EmiDrawContext.wrap(raw);
			context.push();
			context.pose().translate(x, y);
			category.draw(recipe, recipeLayoutDrawable.getRecipeSlotsView(), context.raw(), mouseX, mouseY);
			context.pop();
		}

		@Override
		public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
			JemiTooltipBuilder builder = new JemiTooltipBuilder();
			category.getTooltip(builder, recipe, recipeLayoutDrawable.getRecipeSlotsView(), mouseX, mouseY);
			return builder.tooltip;
		}

		// TODO(polish): JEI 30.x moved category input handling to extras input handlers
		// (IJeiInputHandler); clicks inside JEI-drawn areas are not forwarded yet.
	}
}
