package dev.emi.emi.api.stack;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

@ApiStatus.Internal
public class ItemEmiStack extends EmiStack {
	private final Item item;
	private final DataComponentPatch componentChanges;

	public ItemEmiStack(ItemStack stack) {
		this(stack, stack.getCount());
	}

	public ItemEmiStack(ItemStack stack, long amount) {
		this(stack.getItem(), stack.getComponentsPatch(), amount);
	}

	public ItemEmiStack(Item item, DataComponentPatch components, long amount) {
		this.item = item;
		this.componentChanges = components;
		this.amount = amount;
	}

	@Override
	public ItemStack getItemStack() {
		return new ItemStack(EmiPort.getItemRegistry().wrapAsHolder(this.item), (int) this.amount, componentChanges);
	}

	@Override
	public EmiStack copy() {
		EmiStack e = new ItemEmiStack(item, componentChanges, amount);
		e.setChance(chance);
		e.setRemainder(getRemainder().copy());
		e.comparison = comparison;
		return e;
	}

	@Override
	public boolean isEmpty() {
		return amount == 0 || item == Items.AIR;
	}

	@Override
	public DataComponentPatch getComponentChanges() {
		return this.componentChanges;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> type) {
		// Check the changes first
		for (var entry : this.componentChanges.entrySet()) {
			if (entry.getKey() == type) {
				return (T) entry.getValue().orElse(null);
			}
		}
		// Then the item's default components
		return this.item.components().get(type);
	}

	@Override
	public Object getKey() {
		return item;
	}

	@Override
	public Identifier getId() {
		return EmiPort.getItemRegistry().getKey(item);
	}

	@Override
	public void render(GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		ItemStack stack = getItemStack();
		if ((flags & RENDER_ICON) != 0) {
			// item() resolves the model (incl. modded) through ItemModelResolver/ItemStackRenderState
			// internally; the empty count label suppresses the vanilla count so EMI draws its own amount.
			draw.item(stack, x, y);
			draw.itemDecorations(Minecraft.getInstance().font, stack, x, y, "");
		}
		if ((flags & RENDER_AMOUNT) != 0) {
			String count = amount != 1 ? String.valueOf(amount) : "";
			EmiRenderHelper.renderAmount(context, x, y, EmiPort.literal(count));
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, draw, x, y);
		}
	}

	@Override
	public List<Component> getTooltipText() {
		Minecraft client = Minecraft.getInstance();
		return getItemStack().getTooltipLines(Item.TooltipContext.of(client.level), client.player,
			EmiUtil.showAdvancedTooltips() ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
	}

	@Override
	public Component getName() {
		if (isEmpty()) {
			return EmiPort.literal("");
		}
		return getItemStack().getHoverName();
	}
}
