package net.vulkanic.gui;

public enum GuiRenderStratum {
	/** Flat semantic GUI rectangles, kept below specialized HUD strata. */
	GUI_RECTANGLES("gui.rectangles", 100),
	GUI_POST_EFFECT("gui.post-effect", 80),
	GUI_PANORAMA("gui.panorama", 50),
	GUI_CROSSHAIR("gui.crosshair", 200),
	GUI_HOTBAR_BASE("gui.hotbar.base", 300),
	GUI_HOTBAR_SELECTION("gui.hotbar.selection", 310),
	GUI_ARMOR("gui.armor", 350),
	GUI_PLAYER_HEALTH("gui.player-health", 360),
	GUI_HUNGER("gui.hunger", 370),
	GUI_AIR("gui.air", 380),
	GUI_MOUNT_HEALTH("gui.mount-health", 390),
	GUI_EXPERIENCE_BAR_BACKGROUND("gui.experience.background", 400),
	GUI_EXPERIENCE_BAR_PROGRESS("gui.experience.progress", 410),
	GUI_ATTACK_CROSSHAIR_BACKGROUND("gui.attack.crosshair.background", 500),
	GUI_ATTACK_CROSSHAIR_PROGRESS("gui.attack.crosshair.progress", 510),
	GUI_ATTACK_HOTBAR_BACKGROUND("gui.attack.hotbar.background", 520),
	GUI_ATTACK_HOTBAR_PROGRESS("gui.attack.hotbar.progress", 530),
	GUI_BOSS_BAR_BACKGROUND("gui.boss.background", 600),
	GUI_BOSS_BAR_PROGRESS("gui.boss.progress", 610),
	GUI_ITEM("gui.item", 650),
	GUI_TEXT("gui.text", 700),
	GUI_FILE_BACKED_BLIT("gui.file-backed.blit", 750),
	/** Textured GUI blits whose pipeline explicitly disables blending. */
	GUI_OPAQUE_BLIT("gui.opaque.blit", 760),
	/** Vanilla vignette blits using inverse-source-color compositing. */
	GUI_VIGNETTE_BLIT("gui.vignette.blit", 770),
	/** GUI rectangles using the pipeline’s inverse-color blend. */
	GUI_INVERT_RECTANGLE("gui.invert.rectangle", 780);


	private final String id;
	private final int order;

	GuiRenderStratum(String id, int order) {
		this.id = id;
		this.order = order;
	}

	public String id() {
		return id;
	}

	public int order() {
		return order;
	}

	public boolean supportedForPartialFrame() {
		return true;
	}
}
