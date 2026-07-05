package dev.emi.emi.api.stack;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ApiStatus.Internal
public class TagEmiIngredient implements EmiIngredient {
	private final Identifier id;
	private List<EmiStack> stacks;
	public final TagKey<?> key;
	private final EmiTagKey<?> tagKey;
	private ItemStack modelStack;
	private long amount;
	private float chance = 1;

	@ApiStatus.Internal
	public TagEmiIngredient(TagKey<?> key, long amount) {
		this(EmiTagKey.of(key), amount);
	}

	@ApiStatus.Internal
	public TagEmiIngredient(TagKey<?> key, List<EmiStack> stacks, long amount) {
		this(EmiTagKey.of(key), stacks, amount);
	}

	@ApiStatus.Internal
	public TagEmiIngredient(EmiTagKey<?> key, long amount) {
		this(key, EmiTags.getValues(key), amount);
	}

	private TagEmiIngredient(EmiTagKey<?> key, List<EmiStack> stacks, long amount) {
		this.id = key.id();
		this.key = key.raw();
		this.tagKey = key;
		this.stacks = stacks;
		this.amount = amount;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TagEmiIngredient tag && tag.key.equals(this.key);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public EmiIngredient copy() {
		EmiIngredient stack = new TagEmiIngredient(tagKey, amount);
		stack.setChance(chance);
		return stack;
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return stacks;
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

	@Override
	public void render(GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		if ((flags & RENDER_ICON) != 0) {
			if (tagKey.hasCustomModel()) {
				// The original rendered the synthetic tag model directly (BakedModel — gone on 26.2).
				// Here the model is a client item definition, so the ordinary item path renders it via
				// a stack carrying the minecraft:item_model component.
				if (modelStack == null) {
					modelStack = new ItemStack(Items.STONE);
					modelStack.set(DataComponents.ITEM_MODEL, tagKey.getCustomModel());
				}
				draw.item(modelStack, x, y);
			} else if (!stacks.isEmpty()) {
				stacks.get(0).render(draw, x, y, delta, -1 ^ RENDER_AMOUNT);
			}
		}
		if ((flags & RENDER_AMOUNT) != 0 && !tagKey.isOf(EmiPort.getFluidRegistry())) {
			String count = amount != 1 ? String.valueOf(amount) : "";
			EmiRenderHelper.renderAmount(context, x, y, EmiPort.literal(count));
		}
		if ((flags & RENDER_INGREDIENT) != 0) {
			EmiRender.renderTagIcon(this, draw, x, y);
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, draw, x, y);
		}
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		list.add(ClientTooltipComponent.create(tagKey.getTagName().getVisualOrderText()));
		if (EmiUtil.showAdvancedTooltips()) {
			list.add(ClientTooltipComponent.create(
				EmiPort.literal("#" + id, ChatFormatting.DARK_GRAY).getVisualOrderText()));
		}
		// TODO(screen): EMI's TagTooltipComponent (contained-stacks grid) + amount/mod-id lines.
		return list;
	}
}
