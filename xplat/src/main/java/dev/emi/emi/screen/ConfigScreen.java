package dev.emi.emi.screen;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.com.unascribed.qdcss.QDCSS;
import dev.emi.emi.config.ConfigEnum;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.EmiConfig.Comment;
import dev.emi.emi.config.EmiConfig.ConfigGroup;
import dev.emi.emi.config.EmiConfig.ConfigGroupEnd;
import dev.emi.emi.config.EmiConfig.ConfigValue;
import dev.emi.emi.config.IntGroup;
import dev.emi.emi.config.ScreenAlign;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarSubpanels;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.input.EmiBind.ModifiedKey;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import dev.emi.emi.screen.widget.config.EmiNameWidget;
import dev.emi.emi.screen.widget.config.EnumWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntGroupWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ScreenAlignWidget;
import dev.emi.emi.screen.widget.config.SidebarPagesWidget;
import dev.emi.emi.screen.widget.config.SidebarSubpanelsWidget;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class ConfigScreen extends Screen {
	private static final int maxWidth = 240;
	private Screen last;
	private ConfigSearch search;
	public ListWidget list;
	public EmiBind activeBind;
	public int activeBindOffset;
	public int activeModifiers;
	public int lastModifier;
	public String originalConfig;
	public Button resetButton;
	/** EMI-side buttons (global toggle + jump buttons); drawn and hit-tested by the screen itself. */
	private final List<SizedButtonWidget> emiButtons = Lists.newArrayList();

	public ConfigScreen(Screen last) {
		super(EmiPort.translatable("screen.emi.config"));
		this.last = last;
		originalConfig = EmiConfig.getSavedConfig();
	}

	public void setActiveBind(EmiBind bind, int offset) {
		activeBind = bind;
		activeBindOffset = offset;
		activeModifiers = 0;
		lastModifier = 0;
	}

	@Override
	public void onClose() {
		EmiConfig.writeConfig();
		Minecraft.getInstance().gui.setScreen(last);
	}

	public static List<ClientTooltipComponent> getFieldTooltip(Field field) {
		ConfigValue annot = field.getAnnotation(ConfigValue.class);
		String key = "config.emi.tooltip." + annot.value().replace('-', '_');
		Comment comment = field.getAnnotation(Comment.class);
		String raw;
		if (Language.getInstance().has(key)) {
			raw = I18n.get(key);
		} else if (comment != null) {
			raw = comment.value();
		} else {
			return List.of();
		}
		Minecraft client = Minecraft.getInstance();
		return Arrays.stream(raw.split("\n"))
			.flatMap(s -> client.font.split(EmiPort.literal(s), maxWidth).stream())
			.map(ClientTooltipComponent::create).toList();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void init() {
		super.init();
		emiButtons.clear();

		// Persistent-ish state
		int scroll = 0;
		String query = "";
		Set<String> collapsed = Sets.newHashSet();
		if (list != null) {
			scroll = (int) list.getScrollAmount();
			query = search.getSearch();
			for (ListWidget.Entry e : list.children()) {
				if (e instanceof GroupNameWidget g) {
					if (g.collapsed) {
						collapsed.add(g.text.getString());
					}
				}
			}
		}

		list = new ListWidget(minecraft, width, height, 40, height - 60);
		this.addRenderableOnly(new EmiNameWidget(width / 2, 16));
		int w = Math.min(400, width - 40) / 4 * 4;
		int x = (width - w) / 2;
		search = new ConfigSearch(x + 3, height - 51, w / 2 - 4, 18);
		this.addRenderableWidget(search.field);
		this.resetButton = EmiPort.newButton(x + 2, height - 30, w / 2 - 2, 20, EmiPort.translatable("gui.done"), button -> {
			EmiConfig.loadConfig(QDCSS.load("revert", originalConfig));
			this.init(this.width, this.height);
		});
		this.addRenderableWidget(EmiPort.newButton(x + w / 2 + 2, height - 30, w / 2 - 2, 20, EmiPort.translatable("gui.done"), button -> {
			this.onClose();
		}));
		emiButtons.add(new SizedButtonWidget(x + w - 20, height - 52, 20, 20, 164, 0, () -> true, widget -> {
			EmiConfig.setGlobalState(!EmiConfig.useGlobalConfig);
			this.init(this.width, this.height);
		}, () -> (EmiConfig.useGlobalConfig ? 40 : 0), () -> {
			return Arrays.stream(I18n.get("tooltip.emi.config.global").split("\n"))
				.map(s -> minecraft.font.getSplitter().splitLines(FormattedText.of(s), maxWidth, Style.EMPTY))
				.flatMap(l -> l.stream()).map(v -> (Component) EmiPort.literal(v.getString())).toList();
		}));
		this.addRenderableWidget(resetButton);

		try {
			String lastGroup = "";
			GroupNameWidget lastGroupWidget = null;
			ConfigGroup currentGroup = null;
			SubGroupNameWidget currentSubGroupWidget = null;
			Supplier<String> searchSupplier = () -> search.getSearch();
			for (Field field : EmiConfig.class.getFields()) {
				ConfigValue annot = field.getAnnotation(ConfigValue.class);
				if (annot != null) {
					String group = annot.value().split("\\.")[0];
					if (group.equals("persistent")) {
						continue;
					}
					if (!group.equals(lastGroup)) {
						lastGroup = group;
						Component text = EmiPort.translatable("config.emi.group." + group.replace('-', '_'));
						lastGroupWidget = new GroupNameWidget(group, text);
						if (collapsed.contains(text.getString())) {
							lastGroupWidget.collapsed = true;
						}
						list.addEntry(lastGroupWidget);
					}
					ConfigGroup configGroup = field.getAnnotation(ConfigGroup.class);
					if (configGroup != null) {
						currentGroup = configGroup;
						Component text = EmiPort.translatable("config.emi.group." + configGroup.value().replace('-', '_'));
						currentSubGroupWidget = new SubGroupNameWidget(configGroup.value(), text);
						if (collapsed.contains(text.getString())) {
							currentSubGroupWidget.collapsed = true;
						}
						currentSubGroupWidget.parent = lastGroupWidget;
						list.addEntry(currentSubGroupWidget);
					}
					Predicate<?> predicate = EmiConfig.FILTERS.getOrDefault(annot.value(), v -> true);
					Component translation = EmiPort.translatable("config.emi." + annot.value().replace('-', '_'));
					ConfigEntryWidget entry = null;
					if (field.getType() == boolean.class) {
						entry = new BooleanWidget(translation, getFieldTooltip(field), searchSupplier, new Mutator<Boolean>() {

							public Boolean getValue() {
								try {
									return field.getBoolean(null);
								} catch(Exception e) {}
								return false;
							}

							public void setValue(Boolean value) {
								try {
									field.setBoolean(null, value);
								} catch (Exception e) {}
							}
						});
					} else if (field.getType() == int.class) {
						entry = new IntWidget(translation, getFieldTooltip(field), searchSupplier, new Mutator<Integer>() {

							public Integer getValue() {
								try {
									return field.getInt(null);
								} catch(Exception e) {}
								return -1;
							}

							public void setValue(Integer value) {
								try {
									field.setInt(null, value);
								} catch (Exception e) {}
							}
						});
					} else if (field.getType() == EmiBind.class) {
						entry = new EmiBindWidget(this, getFieldTooltip(field), searchSupplier, (EmiBind) field.get(null));
					} else if (field.getType() == ScreenAlign.class) {
						entry = new ScreenAlignWidget(translation, getFieldTooltip(field), searchSupplier, objectMutator(field));
					} else if (field.getType() == SidebarPages.class) {
						entry = new SidebarPagesWidget(translation, getFieldTooltip(field), searchSupplier, objectMutator(field));
					} else if (field.getType() == SidebarSubpanels.class) {
						entry = new SidebarSubpanelsWidget(translation, getFieldTooltip(field), searchSupplier, objectMutator(field));
					} else if (IntGroup.class.isAssignableFrom(field.getType())) {
						entry = new IntGroupWidget(translation, getFieldTooltip(field), searchSupplier, objectMutator(field));
					} else if (ConfigEnum.class.isAssignableFrom(field.getType())) {
						entry = new EnumWidget(translation, getFieldTooltip(field), searchSupplier, objectMutator(field), (Predicate<ConfigEnum>) predicate);
					}
					boolean endGroup = field.getAnnotation(ConfigGroupEnd.class) != null;
					if (entry != null) {
						entry.group = currentGroup;
						entry.endGroup = endGroup;
						list.addEntry(entry);
						if (lastGroupWidget != null) {
							lastGroupWidget.children.add(entry);
							entry.parentGroups.add(lastGroupWidget);
						}
						if (currentSubGroupWidget != null) {
							currentSubGroupWidget.children.add(entry);
							entry.parentGroups.add(currentSubGroupWidget);
						}
					}
					if (endGroup) {
						currentGroup = null;
						currentSubGroupWidget = null;
					}
				}
			}
		} catch (Exception e) {
			EmiLog.error("Error initializing config screen", e);
		}

		list.setScrollAmount(scroll);
		search.setText(query);
		addJumpButtons();
		updateChanges();
	}

	private void addJumpButtons() {
		List<String> jumps = Lists.newArrayList(
			"general", "general.search",
			"ui", "ui.left-sidebar", "ui.right-sidebar", "ui.top-sidebar", "ui.bottom-sidebar",
			"binds", "binds.crafts", "binds.cheats",
			"dev"
		);
		List<List<String>> removes = List.of(
			List.of("binds.cheats"),
			List.of("general.search"),
			List.of("ui.top-sidebar", "ui.bottom-sidebar"),
			List.of("binds.crafts"),
			List.of("ui.left-sidebar", "ui.right-sidebar")
		);
		int space = list.getLogicalHeight() - 10;
		for (List<String> r : removes) {
			if (jumps.size() * 16 > space) {
				jumps.removeAll(r);
			}
		}
		int y = 40 + (list.getLogicalHeight() - jumps.size() * 16) / 2;
		int u = 0, v = -16;
		for (String s : jumps) {
			boolean newGroup = !s.contains(".");
			if (newGroup) {
				v += 16;
				u = 0;
			} else {
				u += 16;
			}
			emiButtons.add(new ConfigJumpButton(
				2 + (newGroup ? 0 : 8), y, u, v, w -> jump(s),
				List.of(EmiPort.translatable("config.emi.group." + s.replace('-', '_')))));
			y += 16;
		}
	}

	public void jump(String jump) {
		for (ListWidget.Entry e : list.children()) {
			if (e instanceof ConfigEntryWidget c) {
				for (GroupNameWidget p : c.parentGroups) {
					if (p.id.equals(jump)) {
						list.centerScrollOn(e);
						return;
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T> Mutator<T> objectMutator(Field field) {
		return new Mutator<T>() {
			public T getValue() {
				try {
					return (T) field.get(null);
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			}

			public void setValue(T en) {
				try {
					field.set(null, en);
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public void updateChanges() {
		// Split on the blank lines between config options
		String[] oLines = originalConfig.split("\n\n");
		String[] cLines = EmiConfig.getSavedConfig().split("\n\n");
		int different = 0;
		for (int i = 0; i < oLines.length; i++) {
			if (i >= cLines.length) {
				break;
			}
			if (!oLines[i].equals(cLines[i])) {
				different++;
			}
		}
		this.resetButton.active = different > 0;
		this.resetButton.setMessage(EmiPort.translatable("screen.emi.config.reset", different));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		list.setScrollAmount(list.getScrollAmount());
		super.extractRenderState(raw, mouseX, mouseY, delta);
		list.render(raw, mouseX, mouseY, delta);
		for (SizedButtonWidget button : emiButtons) {
			button.render(context, mouseX, mouseY, delta);
		}
		if (list.getHoveredEntry() != null) {
			EmiRenderHelper.drawTooltip(context, list.getHoveredEntry().getTooltip(mouseX, mouseY), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (activeBind != null) {
			pushModifier(0);
			activeBind.setBind(activeBindOffset, new ModifiedKey(InputConstants.Type.MOUSE.getOrCreate(event.button()), activeModifiers));
			activeBind = null;
			updateChanges();
			return true;
		}
		for (SizedButtonWidget button : emiButtons) {
			if (button.mouseClicked((int) event.x(), (int) event.y(), event.button())) {
				return true;
			}
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		return list.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		list.mouseReleased(event.x(), event.y(), event.button());
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (super.mouseDragged(event, deltaX, deltaY)) {
			return true;
		}
		return list.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
		if (list.isMouseOver(mouseX, mouseY)) {
			return list.mouseScrolled(mouseX, mouseY, amount);
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, amount);
	}

	private void pushModifier(int lastModifier) {
		activeModifiers |= EmiInput.maskFromCode(this.lastModifier);
		this.lastModifier = lastModifier;
		activeModifiers &= ~EmiInput.maskFromCode(lastModifier);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		if (activeBind != null) {
			if (EmiInput.maskFromCode(keyCode) != 0) {
				pushModifier(keyCode);
			} else {
				pushModifier(0);
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					activeBind.setBind(activeBindOffset, new ModifiedKey(InputConstants.UNKNOWN, 0));
				} else {
					activeBind.setBind(activeBindOffset, new ModifiedKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode), activeModifiers));
				}
				activeBind = null;
				updateChanges();
			}
			return true;
		} else {
			// Element nesting causes crashing for cycling, for some reason
			if (keyCode == GLFW.GLFW_KEY_TAB) {
				return false;
			}
			// Text fields nested in list entries are not screen children; feed them directly.
			EditBox nested = list.getFocusedTextField();
			if (nested != null) {
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					EmiPort.focus(nested, false);
					return true;
				}
				if (nested.keyPressed(event)) {
					return true;
				}
			}
			if (super.keyPressed(event)) {
				return true;
			}
			if (this.getFocused() instanceof EditBox tfw && tfw.isFocused()) {
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					EmiPort.focus(tfw, false);
					return true;
				}
			} else if (nested == null) {
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					this.onClose();
					return true;
				} else if (minecraft.options.keyInventory.matches(event)) {
					this.onClose();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		EditBox nested = list.getFocusedTextField();
		if (nested != null && nested.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (activeBind != null) {
			activeModifiers &= ~EmiInput.maskFromCode(event.key());
			if (event.key() == lastModifier) {
				activeBind.setBind(activeBindOffset, new ModifiedKey(InputConstants.Type.KEYSYM.getOrCreate(event.key()), activeModifiers));
				activeBind = null;
				updateChanges();
			}
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	public abstract class Mutator<T> {
		protected abstract T getValue();
		protected abstract void setValue(T value);

		public T get() {
			return getValue();
		}

		public void set(T value) {
			setValue(value);
			updateChanges();
		}
	}
}
