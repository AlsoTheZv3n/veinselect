package dev.veinselect.client;

import dev.veinselect.VeinMode;
import dev.veinselect.VeinSelect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Einstellungen von Vein Select, geöffnet über Mod Menu. */
public class VeinSelectConfigScreen extends Screen {
	private static final int INVALID_COLOR = 0xFFFF5555;

	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private EditBox maxBlocksBox;
	private Button doneButton;
	/** Erst mit „Fertig“ übernommen, wie die Blockanzahl. */
	private VeinMode mode = VeinSelectClientConfig.mode();

	public VeinSelectConfigScreen(Screen parent) {
		super(Component.translatable("screen.veinselect.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(title, font);

		LinearLayout content = layout.addToContents(LinearLayout.vertical().spacing(8));
		content.defaultCellSetting().alignHorizontallyCenter();

		content.addChild(Button.builder(modeLabel(), button -> {
			mode = mode.cycle(1);
			button.setMessage(modeLabel());
		}).width(200).build());
		content.addChild(new StringWidget(Component.translatable("screen.veinselect.mode_hint", VeinSelectClient.selectKeyName())
				.withStyle(ChatFormatting.GRAY), font));

		Component label = Component.translatable("screen.veinselect.max_blocks", VeinSelect.HARD_LIMIT);
		content.addChild(new StringWidget(label, font));
		maxBlocksBox = content.addChild(new EditBox(font, 200, 20, label));
		maxBlocksBox.setMaxLength(String.valueOf(VeinSelect.HARD_LIMIT).length());
		maxBlocksBox.setValue(String.valueOf(VeinSelectClientConfig.maxBlocks()));
		maxBlocksBox.setResponder(value -> updateValidity());
		content.addChild(new StringWidget(limitInfo(), font));

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).build());
		doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> save()).build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		updateValidity();
		setInitialFocus(maxBlocksBox);
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private Component modeLabel() {
		return Component.translatable("screen.veinselect.mode", mode.displayName());
	}

	private static Component limitInfo() {
		int allowed = VeinSelectClient.serverAllowed();
		Component info = allowed > 0
				? Component.translatable("screen.veinselect.server_allowed", allowed)
				: Component.translatable("screen.veinselect.no_server", VeinSelect.DEFAULT_LIMIT);
		return info.copy().withStyle(ChatFormatting.GRAY);
	}

	/** @return eingegebene Zahl oder -1, wenn ungültig */
	private int parsedValue() {
		try {
			int value = Integer.parseInt(maxBlocksBox.getValue().trim());
			return value >= 1 && value <= VeinSelect.HARD_LIMIT ? value : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void updateValidity() {
		boolean valid = parsedValue() > 0;
		maxBlocksBox.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
		doneButton.active = valid;
	}

	private void save() {
		int value = parsedValue();
		if (value > 0) VeinSelectClient.setMaxBlocks(value);
		if (mode != VeinSelectClientConfig.mode()) VeinSelectClient.setMode(mode);
		onClose();
	}
}
