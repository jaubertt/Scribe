package scribe.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UnsavedChangesScreen extends Screen {
	private final BookEditorScreen editor;
	private boolean leavingToEditor;
	private boolean decided;

	public UnsavedChangesScreen(BookEditorScreen editor) {
		super(Component.translatable("scribe.unsaved.title"));
		this.editor = editor;
	}

	@Override
	protected void init() {
		int center = this.width / 2;
		int y = this.height / 2 + 4;
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.unsaved.save"), b -> {
			this.decided = true;
			this.editor.saveAndClose();
		}).bounds(center - 154, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.unsaved.discard"), b -> {
			this.decided = true;
			this.editor.finishWithoutSaving();
		}).bounds(center - 50, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.unsaved.keep_writing"), b -> this.onClose())
			.bounds(center + 54, y, 100, 20).build());
	}

	@Override
	public void onClose() {
		this.leavingToEditor = true;
		this.minecraft.gui.setScreen(this.editor);
	}

	@Override
	public void removed() {
		super.removed();
		if (!this.leavingToEditor && !this.decided) {
			this.editor.autosaveFromChild();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 28, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("scribe.unsaved.message"), this.width / 2, this.height / 2 - 14, 0xFFA0A0A0);
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
