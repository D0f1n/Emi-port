package dev.emi.emi.screen.widget.config;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

import dev.emi.emi.EmiPort;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

public class IntEdit {
	private static final Pattern NUMBER = Pattern.compile("^-?[0-9]*$");
	public final EditBox text;
	public final SizedButtonWidget up, down;

	public IntEdit(int width, IntSupplier getter, IntConsumer setter) {
		Minecraft client = Minecraft.getInstance();
		// 26.2 dropped EditBox's whole-value filter; validate in insertText and roll back bad edits.
		text = new EditBox(client.font, 0, 0, width - 14, 18, EmiPort.literal("")) {
			@Override
			public void insertText(String string) {
				String previous = getValue();
				super.insertText(string);
				if (!NUMBER.matcher(getValue()).matches()) {
					setValue(previous);
				}
			}
		};
		text.setValue("" + getter.getAsInt());
		text.setResponder(string -> {
			try {
				if (string.isBlank()) {
					setter.accept(0);
				} else {
					setter.accept(Integer.parseInt(string));
				}
			} catch (Exception e) {
			}
		});

		up = new SizedButtonWidget(150, 0, 12, 10, 232, 48, () -> true, button -> {
			setter.accept(getter.getAsInt() + getInc());
			text.setValue("" + getter.getAsInt());
		});
		down = new SizedButtonWidget(150, 10, 12, 10, 244, 48, () -> true, button -> {
			setter.accept(getter.getAsInt() - getInc());
			text.setValue("" + getter.getAsInt());
		});
	}

	public boolean contains(int x, int y) {
		return x > text.getX() && x < up.x + up.getWidth() && y > text.getY() && y < text.getY() + text.getHeight();
	}

	public int getInc() {
		if (EmiInput.isShiftDown()) {
			return 10;
		} else if (EmiInput.isControlDown()) {
			return 5;
		}
		return 1;
	}

	public void setPosition(int x, int y) {
		text.setX(x + 1);
		text.setY(y + 1);
		up.x = x + text.getWidth() + 2;
		up.y = y;
		down.x = up.x;
		down.y = y + 10;
	}
}
