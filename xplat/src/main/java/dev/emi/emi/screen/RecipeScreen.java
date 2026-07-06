package dev.emi.emi.screen;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The EMI recipe screen: category tabs above a nine-patch panel, paginated recipe displays in the
 * center, workstations along the bottom, EMI's index panel to the side.
 *
 * <p>Port notes for 26.2: the screen renders through {@code extractRenderState} (two-phase GUI
 * pipeline), input arrives as event records, and the arrow buttons are EMI-drawn rather than
 * vanilla button children. Of the per-recipe side buttons only craft fill is present; the BoM
 * resolution button and the tree/default/screenshot buttons return with later rounds.
 */
public class RecipeScreen extends Screen {
	private Map<EmiRecipeCategory, List<EmiRecipe>> recipes;
	public AbstractContainerScreen<?> old;
	private List<RecipeTab> tabs = Lists.newArrayList();
	private int tabPageSize = 6;
	private int tabPage = 0, tab = 0, page = 0;
	private List<SizedButtonWidget> arrows;
	private List<WidgetGroup> currentPage = Lists.newArrayList();
	private int buttonOff = 0, tabOff = 0;
	private Widget hoveredWidget = null, pressedSlot = null;
	private double scrollAcc = 0;
	private int minimumWidth = 176;
	int backgroundWidth = minimumWidth;
	int backgroundHeight = 200;
	int x = (this.width - backgroundWidth) / 2;
	int y = (this.height - backgroundHeight) / 2;

	public RecipeScreen(AbstractContainerScreen<?> old, Map<EmiRecipeCategory, List<EmiRecipe>> recipes) {
		super(EmiPort.translatable("screen.emi.recipe"));
		this.old = old;
		arrows = List.of(
			new SizedButtonWidget(x + 2, y - 18, 12, 12, 0, 0,
				() -> tabs.size() > tabPageSize, w -> setPage(tabPage - 1, tab, page)),
			new SizedButtonWidget(x + backgroundWidth - 14, y - 18, 12, 12, 12, 0,
				() -> tabs.size() > tabPageSize, w -> setPage(tabPage + 1, tab, page)),
			new SizedButtonWidget(x + 5, y + 5, 12, 12, 0, 0,
				() -> tabs.size() > 1, w -> setPage(tabPage, tab - 1, 0)),
			new SizedButtonWidget(x + backgroundWidth - 17, y + 5, 12, 12, 12, 0,
				() -> tabs.size() > 1, w -> setPage(tabPage, tab + 1, 0)),
			new SizedButtonWidget(x + 5, y + 18, 12, 12, 0, 0,
				() -> tabs.get(tab).getPageCount() > 1, w -> setPage(tabPage, tab, page - 1)),
			new SizedButtonWidget(x + backgroundWidth - 17, y + 18, 12, 12, 12, 0,
				() -> tabs.get(tab).getPageCount() > 1, w -> setPage(tabPage, tab, page + 1))
		);
		this.recipes = recipes;
	}

	@Override
	protected void init() {
		super.init();
		minimumWidth = 176;
		backgroundWidth = minimumWidth;
		backgroundHeight = Math.min(256, height - 52 - 20);
		x = (this.width - backgroundWidth) / 2;
		y = (this.height - backgroundHeight) / 2 + 1;
		this.tabPageSize = (minimumWidth - 32) / 24;

		if (recipes != null) {
			EmiRecipe current = null;
			if (tab < tabs.size() && page < tabs.get(tab).getPageCount() && tabs.get(tab).getPage(page).size() > 0) {
				current = tabs.get(tab).getPage(page).get(0).recipe;
			}
			tabs.clear();
			if (!recipes.isEmpty()) {
				for (Map.Entry<EmiRecipeCategory, List<EmiRecipe>> entry : recipes.entrySet().stream()
						.sorted((a, b) -> {
							int ai = EmiApi.getRecipeManager().getCategories().indexOf(a.getKey());
							int bi = EmiApi.getRecipeManager().getCategories().indexOf(b.getKey());
							if (ai < 0) {
								ai = Integer.MAX_VALUE;
							}
							if (bi < 0) {
								bi = Integer.MAX_VALUE;
							}
							return ai - bi;
						}).toList()) {
					List<EmiRecipe> set = entry.getValue();
					if (!set.isEmpty()) {
						RecipeTab tab = new RecipeTab(entry.getKey(), set);
						tab.bakePages(backgroundHeight);
						tabs.add(tab);
					}
				}

				tab = -1;
				setPage(tabPage, 0, 0);
			}
			if (current != null) {
				focusRecipe(current);
			}
		}
		setRecipePageWidth(backgroundWidth);
	}

	private void setRecipePageWidth(int width) {
		if ((width & 1) == 1) {
			width++;
		}
		this.backgroundWidth = width;
		this.x = (this.width - backgroundWidth) / 2;
		this.buttonOff = (backgroundWidth - minimumWidth) / 2;
		int tabExtra = (minimumWidth - 32) % 24 / 2;
		this.tabOff = buttonOff + tabExtra;
		this.arrows.get(0).x = this.x + 2 + buttonOff + tabExtra;
		this.arrows.get(1).x = this.x + minimumWidth - 14 + buttonOff - tabExtra;
		this.arrows.get(2).x = this.x + 5 + buttonOff;
		this.arrows.get(3).x = this.x + minimumWidth - 17 + buttonOff;
		this.arrows.get(4).x = this.x + 5 + buttonOff;
		this.arrows.get(5).x = this.x + minimumWidth - 17 + buttonOff;

		this.arrows.get(0).y = this.y - 18;
		this.arrows.get(1).y = this.y - 18;
		this.arrows.get(2).y = this.y + 5;
		this.arrows.get(3).y = this.y + 5;
		this.arrows.get(4).y = this.y + 19;
		this.arrows.get(5).y = this.y + 19;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		this.extractTransparentBackground(raw);
		EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, x, y, backgroundWidth, backgroundHeight, 0, 0, 4, 1);

		int tp = tabPage * tabPageSize;
		int off = 0;
		for (int i = tp; i < tabs.size() && i < tp + tabPageSize; i++) {
			RecipeTab tab = tabs.get(i);
			int sOff = (i == this.tab ? 2 : 0);
			EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, x + tabOff + off * 24 + 16, y - 24 - sOff, 24, 27 + sOff,
				i == this.tab ? 9 : 18, 0, 4, 1);
			tab.category.render(context.raw(), x + tabOff + off++ * 24 + 20, y - 20 - (i == this.tab ? 2 : 0), delta);
		}

		EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, x + 19 + buttonOff, y + 5, minimumWidth - 38, 12, 0, 16, 3, 6);
		EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, x + 19 + buttonOff, y + 19, minimumWidth - 38, 12, 0, 16, 3, 6);

		boolean categoryHovered = mouseX >= x + 19 + buttonOff && mouseY >= y + 5 && mouseX < x + minimumWidth + buttonOff - 19 && mouseY < y + 5 + 12;
		int categoryNameColor = categoryHovered ? 0xff22ffff : 0xffffffff;

		RecipeTab tab = tabs.get(this.tab);
		Component text = tab.category.getName();
		if (font.width(text) > minimumWidth - 40) {
			int extraWidth = font.width("...");
			text = EmiPort.literal(font.substrByWidth(text, (minimumWidth - 40) - extraWidth).getString() + "...");
		}
		context.drawCenteredTextWithShadow(text, x + backgroundWidth / 2, y + 7, categoryNameColor);
		context.drawCenteredTextWithShadow(EmiRenderHelper.getPageText(this.page + 1, tab.getPageCount(), minimumWidth - 40),
			x + backgroundWidth / 2, y + 21, 0xffffffff);

		// Workstations along the bottom of the panel (EMI's default layout).
		List<EmiIngredient> workstations = EmiApi.getRecipeManager().getWorkstations(tab.category);
		int workstationAmount = Math.min(workstations.size(), getMaxWorkstations());
		if (workstationAmount > 0) {
			Bounds bounds = getWorkstationBounds(0);
			EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, bounds.x() - 5, bounds.y() - 5,
				10 + 18 * workstationAmount, 28, 58, 0, 5, 1);
		}

		for (WidgetGroup group : currentPage) {
			int mx = mouseX - group.x();
			int my = mouseY - group.y();
			context.push();
			context.pose().translate(group.x(), group.y());
			try {
				for (Widget widget : group.widgets) {
					widget.render(context.raw(), mx, my, delta);
				}
			} catch (Throwable e) {
				EmiLog.error("Error rendering widget", e);
				group.error(e);
			}
			renderFillFeedback(group, context, mx, my);
			context.pop();
		}

		for (SizedButtonWidget arrow : arrows) {
			arrow.render(context, mouseX, mouseY, delta);
		}

		// EMI's index panel + search, to the side of the recipe panel.
		EmiScreenManager.render(raw, this, x, y, backgroundWidth, backgroundHeight, mouseX, mouseY, delta);

		if (categoryHovered) {
			EmiRenderHelper.drawTooltip(context, List.of(
				ClientTooltipComponent.create(EmiPort.ordered(tab.category.getName())),
				ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("emi.view_all_recipes")))
			), mouseX, mouseY);
		}
		hoveredWidget = null;
		outer:
		for (WidgetGroup group : currentPage) {
			try {
				int mx = mouseX - group.x();
				int my = mouseY - group.y();
				for (Widget widget : group.widgets) {
					if (widget.getBounds().contains(mx, my)) {
						List<ClientTooltipComponent> tooltip = widget.getTooltip(mx, my);
						if (!tooltip.isEmpty()) {
							EmiRenderHelper.drawTooltip(context, tooltip, mouseX, mouseY);
							hoveredWidget = widget;
							break outer;
						}
					}
				}
			} catch (Throwable e) {
				EmiLog.error("Error rendering widget group", e);
				group.error(e);
			}
		}

		RecipeTab rTab = getTabAt(mouseX, mouseY);
		if (rTab != null) {
			EmiRenderHelper.drawTooltip(context, rTab.category.getTooltip(), mouseX, mouseY);
		}
	}

	// On hover over the fill button, the handler paints availability feedback (red overlay on
	// missing ingredient slots) over the recipe's own widgets.
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void renderFillFeedback(WidgetGroup group, EmiDrawContext context, int mx, int my) {
		for (Widget widget : group.widgets) {
			if (widget instanceof RecipeFillButtonWidget) {
				if (widget.getBounds().contains(mx, my)) {
					AbstractContainerScreen hs = EmiApi.getHandledScreen();
					if (hs != null && group.recipe != null) {
						try {
							EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(group.recipe, hs);
							if (handler != null) {
								handler.render(group.recipe, new EmiCraftContext(hs, handler.getInventory(hs),
									EmiCraftContext.Type.FILL_BUTTON), group.widgets, context.raw());
							}
						} catch (Throwable e) {
							EmiLog.error("Error rendering fill feedback", e);
						}
					}
					break;
				}
			}
		}
	}

	public EmiIngredient getHoveredStack() {
		if (hoveredWidget instanceof SlotWidget slot) {
			return slot.getStack();
		}
		return EmiStack.EMPTY;
	}

	/** The hovered slot's recipe context, for favoriting recipe outputs with their recipe attached. */
	public EmiRecipe getHoveredRecipeContext() {
		if (hoveredWidget instanceof SlotWidget slot) {
			return slot.getRecipe();
		}
		return null;
	}

	public RecipeTab getTabAt(int mx, int my) {
		if (mx >= x + 16 + tabOff && mx < x + backgroundWidth && my >= y - 24 && my < y) {
			int n = (mx - x - 16 - tabOff) / 24 + tabPage * tabPageSize;
			if (n < tabs.size() && n >= tabPage * tabPageSize && n < (tabPage + 1) * tabPageSize) {
				return tabs.get(n);
			}
		}
		return null;
	}

	public int getMaxWorkstations() {
		return (this.backgroundWidth - 18) / 18;
	}

	public Bounds getWorkstationBounds(int i) {
		return new Bounds(this.x + 5 + i * 18, this.y + this.backgroundHeight - 23, 18, 18);
	}

	public EmiRecipeCategory getFocusedCategory() {
		return tabs.get(tab).category;
	}

	public void focusCategory(EmiRecipeCategory category) {
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).category == category) {
				setPage(tabPage, i, 0);
				return;
			}
		}
	}

	public void focusRecipe(EmiRecipe recipe) {
		for (int i = 0; i < tabs.size(); i++) {
			RecipeTab tab = tabs.get(i);
			for (int j = 0; j < tab.getPageCount(); j++) {
				for (RecipeDisplay d : tab.getPage(j)) {
					if (d.recipe == recipe) {
						setPage(tabPage, i, j);
						return;
					}
				}
			}
		}
	}

	public void setPage(int tp, int t, int p) {
		currentPage.clear();
		if (tabs.isEmpty()) {
			return;
		}
		boolean snapTabPage = tp == tabPage && t != tab;
		tab = wrap(t, tabs.size());
		if (snapTabPage) {
			tp = (tab) / tabPageSize;
		}
		tabPage = wrap(tp, (tabs.size() - 1) / tabPageSize + 1);
		RecipeTab tab = tabs.get(this.tab);
		page = wrap(p, tab.getPageCount());
		if (page < tab.getPageCount()) {
			int width = Math.max(minimumWidth - 16, tab.getWidth());
			setRecipePageWidth(width + 16);
			currentPage = Lists.newArrayList();
			currentPage.addAll(tab.constructWidgets(page, x, y, backgroundWidth, backgroundHeight));
			List<EmiIngredient> workstations = EmiApi.getRecipeManager().getWorkstations(tab.category);
			if (!workstations.isEmpty()) {
				WidgetGroup widgets = new WidgetGroup(null, 0, 0, 0, 0);
				int maxWorkstations = getMaxWorkstations();
				for (int i = 0; i < workstations.size() && i < maxWorkstations; i++) {
					Bounds bounds = getWorkstationBounds(i);
					if (i == maxWorkstations - 1 && workstations.size() > maxWorkstations) {
						EmiIngredient ingredient = EmiIngredient.of(workstations.subList(i, workstations.size()));
						widgets.add(new SlotWidget(ingredient, bounds.x(), bounds.y()));
					} else {
						widgets.add(new SlotWidget(workstations.get(i), bounds.x(), bounds.y()));
					}
				}
				currentPage.add(widgets);
			}
		}
	}

	public int wrap(int value, int size) {
		if (value >= size) {
			return 0;
		} else if (value < 0) {
			return size - 1;
		}
		return value;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();
		int button = event.button();
		pressedSlot = null;
		for (SizedButtonWidget arrow : arrows) {
			if (arrow.mouseClicked(mx, my, button)) {
				playButtonSound();
				return true;
			}
		}
		if (mx >= x + 19 + buttonOff && my >= y + 5 && mx < x + minimumWidth + buttonOff - 19 && my <= y + 5 + 12) {
			EmiApi.displayAllRecipes();
			playButtonSound();
			return true;
		}
		for (WidgetGroup group : currentPage) {
			try {
				int ox = mx - group.x();
				int oy = my - group.y();
				for (Widget widget : group.widgets) {
					if (widget.getBounds().contains(ox, oy)) {
						if (widget instanceof SlotWidget) {
							if (pressedSlot == null) {
								pressedSlot = widget;
							}
						} else {
							if (widget.mouseClicked(ox, oy, button)) {
								return true;
							}
						}
					}
				}
			} catch (Throwable e) {
				EmiLog.error("Error handling widget input", e);
				group.error(e);
			}
		}
		if (pressedSlot != null) {
			return true;
		}
		if (EmiScreenManager.mouseClicked(event, doubleClick)) {
			return true;
		}
		RecipeTab rTab = getTabAt(mx, my);
		if (rTab != null) {
			playButtonSound();
			focusCategory(rTab.category);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		// A press on a recipe slot becomes a drag source once the cursor leaves the slot bounds.
		if (pressedSlot instanceof SlotWidget slot && event.button() == 0) {
			WidgetGroup group = getGroup(slot);
			if (group != null) {
				int ox = ((int) event.x()) - group.x();
				int oy = ((int) event.y()) - group.y();
				if (!slot.getBounds().contains(ox, oy) && !slot.getStack().isEmpty()) {
					EmiScreenManager.startDrag(slot.getStack());
					pressedSlot = null;
				}
			}
		}
		EmiScreenManager.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (EmiScreenManager.mouseReleased(event.x(), event.y(), event.button())) {
			pressedSlot = null;
			return true;
		}
		if (pressedSlot instanceof SlotWidget slot) {
			WidgetGroup group = getGroup(slot);
			if (group != null) {
				try {
					int ox = ((int) event.x()) - group.x();
					int oy = ((int) event.y()) - group.y();
					if (slot.getBounds().contains(ox, oy)) {
						if (slot.mouseClicked(ox, oy, event.button())) {
							pressedSlot = null;
							return true;
						}
					}
				} catch (Throwable e) {
					EmiLog.error("Error handling widget input", e);
					group.error(e);
				}
			}
			pressedSlot = null;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
		if (EmiScreenManager.mouseScrolled(mouseX, mouseY, amount)) {
			return true;
		} else if (mouseX > x && mouseX < x + backgroundWidth && mouseY < y + backgroundHeight) {
			scrollAcc += amount;
			int sa = (int) scrollAcc;
			scrollAcc %= 1;
			if (mouseY < this.y) {
				setPage(tabPage, tab - sa, 0);
			} else {
				setPage(tabPage, tab, page - sa);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, amount);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (EmiScreenManager.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256) { // GLFW_KEY_ESCAPE
			this.onClose();
			return true;
		} else if (EmiScreenManager.keyPressed(event)) {
			return true;
		} else if (minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		} else if (EmiConfig.back.matchesKey(event.key(), event.scancode())) {
			EmiHistory.pop();
			return true;
		} else if (EmiConfig.forward.matchesKey(event.key(), event.scancode())) {
			EmiHistory.forward();
			return true;
		}
		if (event.key() == 263) { // GLFW_KEY_LEFT
			setPage(tabPage, tab - 1, 0);
			return true;
		} else if (event.key() == 262) { // GLFW_KEY_RIGHT
			setPage(tabPage, tab + 1, 0);
			return true;
		}
		return super.keyPressed(event);
	}

	public WidgetGroup getGroup(Widget widget) {
		for (WidgetGroup group : currentPage) {
			if (group.widgets.contains(widget)) {
				return group;
			}
		}
		return null;
	}

	private void playButtonSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	@Override
	public void onClose() {
		EmiHistory.popUntil(s -> !(s instanceof RecipeScreen), old);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
