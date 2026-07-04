package dev.emi.emi.api.widget;

/**
 * Holder that recipes add their display widgets to.
 *
 * <p>Checkpoint A scope: the bare holder contract, so the recipe model compiles. The concrete
 * widget factory defaults (slots, textures, text, arrows) land with the vanilla category displays.
 */
public interface WidgetHolder {

	int getWidth();

	int getHeight();

	<T extends Widget> T add(T widget);
}
