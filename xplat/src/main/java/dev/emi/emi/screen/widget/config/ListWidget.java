package dev.emi.emi.screen.widget.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Shamelessly modified vanilla lists to support variable width.
 *
 * <p>Port note for 26.2: vanilla lists moved to the two-phase render-state model with fixed item
 * heights, which fights EMI's variable/zero-height filtered entries — so this is now a fully
 * self-managed EMI widget. The owning screen drives it explicitly from {@code extractRenderState}
 * and its input overrides; entries hold vanilla widgets ({@code Button}/{@code EditBox}) and EMI's
 * {@code SizedButtonWidget}, extracted per-frame and hit-tested here.
 */
public class ListWidget {
	private static final Identifier MENU_LIST_BACKGROUND_TEXTURE = EmiPort.id("minecraft", "textures/gui/menu_list_background.png");
	private static final Identifier INWORLD_MENU_LIST_BACKGROUND_TEXTURE = EmiPort.id("minecraft", "textures/gui/inworld_menu_list_background.png");

	protected final Minecraft client;
	private final List<Entry> children = Lists.newArrayList();
	protected int width;
	protected int height;
	protected int top;
	protected int bottom;
	protected int right;
	protected int left;
	private double scrollAmount;
	private boolean scrolling;
	private Entry hoveredEntry;
	private Entry focusedEntry;
	public int padding = 4;

	public ListWidget(Minecraft client, int width, int height, int top, int bottom) {
		this.client = client;
		this.width = width;
		this.height = height;
		this.top = top;
		this.bottom = bottom;
		this.left = 0;
		this.right = width;
	}

	public int getRowWidth() {
		return Math.min(400, width - 60);
	}

	public int getLogicalHeight() {
		return bottom - top;
	}

	public final List<Entry> children() {
		return this.children;
	}

	protected final void clearEntries() {
		this.children.clear();
	}

	protected void replaceEntries(Collection<Entry> newEntries) {
		this.children.clear();
		this.children.addAll(newEntries);
	}

	protected Entry getEntry(int index) {
		return this.children().get(index);
	}

	public int addEntry(Entry entry) {
		this.children.add(entry);
		entry.parentList = this;
		return this.children.size() - 1;
	}

	protected int getEntryCount() {
		return this.children().size();
	}

	@Nullable
	protected final Entry getEntryAtPosition(double x, double y) {
		int rowWidth = this.getRowWidth() / 2;
		int mid = this.left + this.width / 2;
		int rowLeft = mid - rowWidth;
		int rowRight = mid + rowWidth;
		int m = Mth.floor(y - (double) this.top) + (int) this.getScrollAmount() - 4;
		if (x < this.getScrollbarPositionX() && x >= rowLeft && x <= rowRight && m >= 0) {
			int h = 0;
			for (int i = 0; i < this.getEntryCount(); i++) {
				int eh = getEntryHeight(i);
				if (m >= h && m < h + eh - padding) {
					return this.getEntry(i);
				}
				h += eh;
			}
		}
		return null;
	}

	public void updateSize(int width, int height, int top, int bottom) {
		this.width = width;
		this.height = height;
		this.top = top;
		this.bottom = bottom;
		this.left = 0;
		this.right = width;
	}

	protected int getMaxPosition() {
		return this.getTotalHeight();
	}

	public void render(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		this.hoveredEntry = this.isMouseOver(mouseX, mouseY) ? this.getEntryAtPosition(mouseX, mouseY) : null;

		{	// Render background
			Identifier identifier = this.client.level == null ? MENU_LIST_BACKGROUND_TEXTURE : INWORLD_MENU_LIST_BACKGROUND_TEXTURE;
			context.drawTexture(identifier, left, top, right - left, bottom - top,
				right, bottom + (int) scrollAmount, right - left, bottom - top, 32, 32);
		}

		draw.enableScissor(left, top, right, bottom);
		this.renderList(draw, this.getRowLeft(), this.top + 4 - (int) this.getScrollAmount(), mouseX, mouseY, delta);
		draw.disableScissor();

		{	// Render header & footer separators
			Identifier identifier = this.client.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
			Identifier identifier2 = this.client.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
			context.drawTexture(identifier, left, top - 2, width, 2, 0f, 0f, width, 2, 32, 2);
			context.drawTexture(identifier2, left, bottom, width, 2, 0f, 0f, width, 2, 32, 2);
		}

		int maxScroll = this.getMaxScroll();
		if (maxScroll > 0) {
			int i = this.getScrollbarPositionX();
			int j = i + 6;
			int m = (int) ((float) ((this.bottom - this.top) * (this.bottom - this.top)) / (float) this.getMaxPosition());
			m = Mth.clamp(m, 32, this.bottom - this.top - 8);
			int n = (int) this.getScrollAmount() * (this.bottom - this.top - m) / maxScroll + this.top;
			if (n < this.top) {
				n = this.top;
			}
			context.fill(i, this.top, j - i, this.bottom - this.top, 0xFF000000);
			context.fill(i, n, j - i, m, 0xFF808080);
			context.fill(i, n, j - i - 1, m - 1, 0xFFC0C0C0);
		}
	}

	public void centerScrollOn(Entry entry) {
		int i = 0;
		for (Entry e : this.children()) {
			if (e == entry) {
				this.setScrollAmount(i - 42);
				return;
			}
			i += getEntryHeight(e);
		}
	}

	public double getScrollAmount() {
		return this.scrollAmount;
	}

	public void setScrollAmount(double amount) {
		this.scrollAmount = Mth.clamp(amount, 0.0, (double) this.getMaxScroll());
	}

	public int getMaxScroll() {
		return Math.max(0, this.getMaxPosition() - (this.bottom - this.top - 4) + 40);
	}

	protected void updateScrollingState(double mouseX, double mouseY, int button) {
		this.scrolling = button == 0 && mouseX >= (double) this.getScrollbarPositionX() && mouseX < (double) (this.getScrollbarPositionX() + 6);
	}

	protected int getScrollbarPositionX() {
		return this.width - 6;
	}

	public void unfocusTextField() {
		for (Entry e : this.children) {
			for (Object el : e.children()) {
				if (el instanceof EditBox tfw) {
					EmiPort.focus(tfw, false);
				}
			}
		}
	}

	public EditBox getFocusedTextField() {
		for (Entry e : this.children) {
			for (Object el : e.children()) {
				if (el instanceof EditBox tfw) {
					if (tfw.isFocused()) {
						return tfw;
					}
				}
			}
		}
		return null;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		this.updateScrollingState(mouseX, mouseY, button);
		unfocusTextField();
		if (!this.isMouseOver(mouseX, mouseY)) {
			return false;
		}
		Entry entry = this.getEntryAtPosition(mouseX, mouseY);
		if (entry != null) {
			if (entry.mouseClicked(mouseX, mouseY, button)) {
				this.focusedEntry = entry;
				return true;
			}
		}
		return this.scrolling;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.focusedEntry != null) {
			this.focusedEntry.mouseReleased(mouseX, mouseY, button);
		}
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button != 0 || !this.scrolling) {
			return false;
		}
		if (mouseY < (double) this.top) {
			this.setScrollAmount(0.0);
		} else if (mouseY > (double) this.bottom) {
			this.setScrollAmount(this.getMaxScroll());
		} else {
			double d = Math.max(1, this.getMaxScroll());
			int i = this.bottom - this.top;
			int j = Mth.clamp((int) ((float) (i * i) / (float) this.getMaxPosition()), 32, i - 8);
			double e = Math.max(1.0, d / (double) (i - j));
			this.setScrollAmount(this.getScrollAmount() + deltaY * e);
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		this.setScrollAmount(this.getScrollAmount() - amount * 22);
		return true;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseY >= (double) this.top && mouseY <= (double) this.bottom && mouseX >= (double) this.left && mouseX <= (double) this.right;
	}

	protected void renderList(GuiGraphicsExtractor draw, int x, int y, int mouseX, int mouseY, float delta) {
		int i = this.getEntryCount();
		for (int j = 0; j < i; ++j) {
			int k = this.getRowTop(j);
			int l = this.getRowBottom(j);
			if (l < this.top || k > this.bottom) continue;
			int n = getEntryHeight(j);
			if (n == 0) {
				continue;
			}
			n -= 4;
			Entry entry = this.getEntry(j);
			int o = this.getRowWidth();
			int p = this.getRowLeft();
			entry.render(draw, j, k, p, o - 3, n, mouseX, mouseY, Objects.equals(this.hoveredEntry, entry), delta);
		}
	}

	public int getRowLeft() {
		return this.left + this.width / 2 - this.getRowWidth() / 2 + 2;
	}

	public int getRowRight() {
		return this.getRowLeft() + this.getRowWidth();
	}

	private int getEntryHeight(int i) {
		return getEntryHeight(this.getEntry(i));
	}

	private int getEntryHeight(Entry entry) {
		int h = entry.getHeight();
		if (h == 0) {
			return 0;
		}
		return h + padding;
	}

	protected int getRowTop(int index) {
		int height = 0;
		for (int i = 0; i < index; i++) {
			height += getEntryHeight(i);
		}
		return this.top + 4 - (int) this.getScrollAmount() + height;
	}

	private int getRowBottom(int index) {
		return this.getRowTop(index) + this.getEntry(index).getHeight();
	}

	@Nullable
	public Entry getHoveredEntry() {
		return this.hoveredEntry;
	}

	public int getTotalHeight() {
		int height = 0;
		for (int i = 0; i < this.getEntryCount(); i++) {
			height += getEntryHeight(i);
		}
		if (height > 0) {
			height -= padding;
		}
		return height;
	}

	public static abstract class Entry {
		public ListWidget parentList;

		public abstract void render(GuiGraphicsExtractor draw, int index, int y, int x, int width, int height, int mouseX, int mouseY,
			boolean hovered, float delta);

		public boolean isMouseOver(double mouseX, double mouseY) {
			return Objects.equals(this.parentList.getEntryAtPosition(mouseX, mouseY), this);
		}

		public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
			return List.of();
		}

		public abstract int getHeight();

		/** Vanilla widgets ({@code AbstractWidget}) and EMI {@code SizedButtonWidget}s, mixed. */
		public List<?> children() {
			return List.of();
		}

		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			for (Object child : children()) {
				if (clickChild(child, mouseX, mouseY, button)) {
					return true;
				}
			}
			return false;
		}

		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			return false;
		}

		protected static boolean clickChild(Object child, double mouseX, double mouseY, int button) {
			if (child instanceof SizedButtonWidget sbw) {
				return sbw.mouseClicked((int) mouseX, (int) mouseY, button);
			} else if (child instanceof AbstractWidget w) {
				MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY,
					new MouseButtonInfo(button, EmiInput.getCurrentModifiers()));
				if (w.mouseClicked(event, false)) {
					if (w instanceof EditBox eb) {
						EmiPort.focus(eb, true);
					}
					return true;
				}
			}
			return false;
		}

		/** Extracts one nested child through whichever render model it uses. */
		protected static void renderChild(Object child, GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
			if (child instanceof SizedButtonWidget sbw) {
				sbw.render(EmiDrawContext.wrap(draw), mouseX, mouseY, delta);
			} else if (child instanceof AbstractWidget w) {
				w.extractRenderState(draw, mouseX, mouseY, delta);
			}
		}
	}
}
