package dev.emi.emi.jemi;

import java.util.List;
import java.util.Optional;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotsView;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Adapts a JEI recipe transfer handler into an EMI craft-fill handler, so JEI-bridged recipes get
 * a working fill button in the menus their mods support.
 */
public class JemiRecipeHandler<T extends AbstractContainerMenu, R> implements EmiRecipeHandler<T> {
	private final IRecipeType<R> type;
	public final IRecipeTransferHandler<T, R> handler;

	public JemiRecipeHandler(IRecipeTransferHandler<T, R> handler) {
		this.handler = handler;
		this.type = handler.getRecipeType();
	}

	@Override
	public boolean alwaysDisplaySupport(EmiRecipe recipe) {
		return type != null;
	}

	@Override
	public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
		return new EmiPlayerInventory(List.of());
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		return (type == null || getRawRecipe(recipe) != null) && recipe.supportsRecipeTree();
	}

	@Override
	public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
		IRecipeTransferError err = jeiCraft(recipe, context, false, null);
		return err == null || err.getType().allowsTransfer;
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
		IRecipeTransferError err = jeiCraft(recipe, context, true, null);
		if (err == null || err.getType().allowsTransfer) {
			Minecraft.getInstance().setScreenAndShow(context.getScreen());
		}
		return err == null || err.getType().allowsTransfer;
	}

	@Override
	public void render(EmiRecipe recipe, EmiCraftContext<T> context, List<Widget> widgets, GuiGraphicsExtractor raw) {
		EmiDrawContext draw = EmiDrawContext.wrap(raw);
		R rawRecipe = getRawRecipe(recipe);
		JemiRecipeSlotsView view = createSlotsView(recipe, rawRecipe, type, widgets);
		IRecipeTransferError err = jeiCraft(recipe, context, false, view);
		if (err != null) {
			if (err.getType() == IRecipeTransferError.Type.COSMETIC) {
				for (Widget widget : widgets) {
					if (widget instanceof RecipeFillButtonWidget) {
						Bounds b = widget.getBounds();
						draw.fill(b.left(), b.top(), b.width(), b.height(), err.getButtonHighlightColor());
					}
				}
			}
			if (view != null) {
				view.getSlotViews().forEach(v -> {
					if (v instanceof JemiRecipeSlot jrs) {
						jrs.highlight = 0;
					}
				});
				// Render the error off-screen: JEI implementations report missing slots through
				// drawHighlight, which JemiRecipeSlot records instead of drawing.
				draw.push();
				draw.pose().translate(-100000, -100000);
				draw.pose().scale(0, 0);
				err.showError(raw, 0, 0, view, 0, 0);
				draw.pop();
				view.getSlotViews().forEach(v -> {
					if (v instanceof JemiRecipeSlot jrs && jrs.highlight != 0 && !jrs.isEmpty()) {
						draw.fill(jrs.x, jrs.y, 18, 18, jrs.highlight);
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private IRecipeTransferError jeiCraft(EmiRecipe recipe, EmiCraftContext<T> context, boolean craft, JemiRecipeSlotsView view) {
		try {
			Minecraft client = Minecraft.getInstance();
			R rawRecipe = getRawRecipe(recipe);

			if (view == null) {
				view = createSlotsView(recipe, rawRecipe, type, List.of());
			}

			if (view == null) {
				return () -> IRecipeTransferError.Type.INTERNAL;
			}

			return handler.transferRecipe(context.getScreenHandler(), rawRecipe != null ? rawRecipe : (R) recipe, view, client.player, context.getAmount() > 1, craft);
		} catch (Exception e) {
			EmiLog.error("Error executing JEI craft", e);
		}
		return () -> IRecipeTransferError.Type.INTERNAL;
	}

	public static <R> JemiRecipeSlotsView createSlotsView(EmiRecipe recipe, R rawRecipe, IRecipeType<R> type, List<Widget> widgets) {
		if (rawRecipe == null && type != null) {
			return null;
		}

		List<SlotWidget> slotWidgets = widgets.stream().filter(w -> w instanceof SlotWidget).map(w -> (SlotWidget) w).toList();
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		addIngredients(builder, slotWidgets, recipe.getOutputs(), RecipeIngredientRole.OUTPUT);
		int blankedSlots = 0;
		// People assume very specific slot layouts from JEI. Oblige them.
		if (recipe instanceof EmiCraftingRecipe ecr) {
			if (ecr.shapeless) {
				int inputSize = recipe.getInputs().size();
				if (inputSize == 1) {
					addBlankIngredients(builder, slotWidgets, 4);
					blankedSlots += 4;
					addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
				} else if (inputSize < 5) {
					int wrap = 0;
					for (EmiIngredient i : recipe.getInputs()) {
						addIngredients(builder, slotWidgets, List.of(i), RecipeIngredientRole.INPUT);
						wrap++;
						if (wrap >= 2) {
							wrap = 0;
							addBlankIngredients(builder, slotWidgets, 1);
							blankedSlots += 1;
						}
					}
				} else {
					addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
				}
			} else {
				if (ecr.canFit(1, 3)) {
					addBlankIngredients(builder, slotWidgets, 1);
					blankedSlots += 1;
				} else if (ecr.canFit(3, 1) || (ecr.canFit(3, 2) && !ecr.canFit(2, 2))) {
					addBlankIngredients(builder, slotWidgets, 3);
					blankedSlots += 3;
				}
				addIngredients(builder, slotWidgets, recipe.getInputs().subList(0, Math.max(9, recipe.getInputs().size()) - blankedSlots), RecipeIngredientRole.INPUT);
			}
		} else {
			addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
		}
		if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
			for (int i = recipe.getInputs().size() + blankedSlots; i < 9; i++) {
				addIngredients(builder, slotWidgets, List.of(EmiStack.EMPTY), RecipeIngredientRole.INPUT);
			}
		}
		addIngredients(builder, slotWidgets, recipe.getCatalysts(), RecipeIngredientRole.CRAFTING_STATION);

		return new JemiRecipeSlotsView(builder.slots.stream().map(JemiRecipeSlot::new).toList());
	}

	@SuppressWarnings("unchecked")
	private R getRawRecipe(EmiRecipe recipe) {
		try {
			if (type != null && type.getRecipeClass() != null) {
				if (recipe instanceof JemiRecipe<?> jr && jr.recipe != null
						&& type.getRecipeClass().isAssignableFrom(jr.recipe.getClass())) {
					return (R) jr.recipe;
				}
				// TODO(polish): 26.2 removed the client recipe manager; only the harvested
				// server holders are available for id-based lookup.
				RecipeHolder<?> holder = EmiPort.getRecipe(recipe.getId());
				if (holder != null && type.getRecipeClass().isAssignableFrom(holder.getClass())) {
					return (R) holder;
				}
			}
		} catch (Exception e) {
		}
		return null;
	}

	private static void addBlankIngredients(JemiRecipeLayoutBuilder builder, List<SlotWidget> widgets, int amount) {
		for (int i = 0; i < amount; i++) {
			addIngredients(builder, widgets, List.of(EmiStack.EMPTY), RecipeIngredientRole.INPUT);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addIngredients(JemiRecipeLayoutBuilder builder, List<SlotWidget> widgets, List<? extends EmiIngredient> stacks, RecipeIngredientRole role) {
		for (EmiIngredient ing : stacks) {
			int x = 0, y = 0;
			for (SlotWidget w : widgets) {
				if (w.getStack() == ing) {
					x = w.getBounds().x();
					y = w.getBounds().y();
				}
			}
			IIngredientAcceptor acceptor = builder.addSlot(role, x, y);
			for (EmiStack stack : ing.getEmiStacks()) {
				Optional<ITypedIngredient<?>> opt = JemiUtil.getTyped(stack);
				if (opt.isPresent()) {
					ITypedIngredient<?> typed = opt.get();
					acceptor.add((IIngredientType) typed.getType(), typed.getIngredient());
				}
			}
		}
	}
}
