package net.minecraft.client.gui.render.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public class GuiRenderState {
	private static final int DEBUG_RECTANGLE_COLOR = 2000962815;
	private final List<GuiRenderState.Node> strata = new ArrayList();
	private int firstStratumAfterBlur = Integer.MAX_VALUE;
	private GuiRenderState.Node current;
	private final Set<Object> itemModelIdentities = new HashSet();
	@Nullable
	private ScreenRectangle lastElementBounds;

	public GuiRenderState() {
		this.nextStratum();
	}

	public void nextStratum() {
		this.current = new GuiRenderState.Node(null);
		this.strata.add(this.current);
	}

	public void blurBeforeThisStratum() {
		if (this.firstStratumAfterBlur != Integer.MAX_VALUE) {
			throw new IllegalStateException("Can only blur once per frame");
		} else {
			this.firstStratumAfterBlur = this.strata.size() - 1;
		}
	}

	/** Whether this frame requested the vanilla screen-background blur boundary. */
	public boolean hasBlurBeforeStratum() {
		return this.firstStratumAfterBlur != Integer.MAX_VALUE;
	}

	/**
	 * Returns the source stratum index at which the screen-background blur must
	 * run, or {@code -1} when no blur boundary was requested. This is semantic
	 * ordering data only; no Java framebuffer or renderer state crosses the
	 * Vulkan boundary.
	 */
	public int blurBeforeStratumIndex() {
		return this.firstStratumAfterBlur == Integer.MAX_VALUE ? -1 : this.firstStratumAfterBlur;
	}

	public void up() {
		if (this.current.up == null) {
			this.current.up = new GuiRenderState.Node(this.current);
		}

		this.current = this.current.up;
	}

	public void submitItem(GuiItemRenderState guiItemRenderState) {
		if (this.findAppropriateNode(guiItemRenderState)) {
			this.itemModelIdentities.add(guiItemRenderState.itemStackRenderState().getModelIdentity());
			this.current.submitItem(guiItemRenderState);
			this.sumbitDebugRectangleIfEnabled(guiItemRenderState.bounds());
		}
	}

	public void submitText(GuiTextRenderState guiTextRenderState) {
		if (this.findAppropriateNode(guiTextRenderState)) {
			this.current.submitText(guiTextRenderState);
			this.sumbitDebugRectangleIfEnabled(guiTextRenderState.bounds());
		}
	}

	public void submitPicturesInPictureState(PictureInPictureRenderState pictureInPictureRenderState) {
		if (this.findAppropriateNode(pictureInPictureRenderState)) {
			this.current.submitPicturesInPictureState(pictureInPictureRenderState);
			this.sumbitDebugRectangleIfEnabled(pictureInPictureRenderState.bounds());
		}
	}

	public void submitGuiElement(GuiElementRenderState guiElementRenderState) {
		if (this.findAppropriateNode(guiElementRenderState)) {
			this.current.submitGuiElement(guiElementRenderState);
			this.sumbitDebugRectangleIfEnabled(guiElementRenderState.bounds());
		}
	}

	private void sumbitDebugRectangleIfEnabled(@Nullable ScreenRectangle screenRectangle) {
		if (SharedConstants.DEBUG_RENDER_UI_LAYERING_RECTANGLES && screenRectangle != null) {
			this.up();
			this.current
				.submitGuiElement(
					new ColoredRectangleRenderState(
						RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(), 0, 0, 10000, 10000, 2000962815, 2000962815, screenRectangle
					)
				);
		}
	}

	private boolean findAppropriateNode(ScreenArea screenArea) {
		ScreenRectangle screenRectangle = screenArea.bounds();
		if (screenRectangle == null) {
			return false;
		} else {
			if (this.lastElementBounds != null && this.lastElementBounds.encompasses(screenRectangle)) {
				this.up();
			} else {
				this.navigateToAboveHighestElementWithIntersectingBounds(screenRectangle);
			}

			this.lastElementBounds = screenRectangle;
			return true;
		}
	}

	private void navigateToAboveHighestElementWithIntersectingBounds(ScreenRectangle screenRectangle) {
		GuiRenderState.Node node = (GuiRenderState.Node)this.strata.getLast();

		while (node.up != null) {
			node = node.up;
		}

		boolean bl = false;

		while (!bl) {
			bl = this.hasIntersection(screenRectangle, node.elementStates)
				|| this.hasIntersection(screenRectangle, node.itemStates)
				|| this.hasIntersection(screenRectangle, node.textStates)
				|| this.hasIntersection(screenRectangle, node.picturesInPictureStates);
			if (node.parent == null) {
				break;
			}

			if (!bl) {
				node = node.parent;
			}
		}

		this.current = node;
		if (bl) {
			this.up();
		}
	}

	private boolean hasIntersection(ScreenRectangle screenRectangle, @Nullable List<? extends ScreenArea> list) {
		if (list != null) {
			for (ScreenArea screenArea : list) {
				ScreenRectangle screenRectangle2 = screenArea.bounds();
				if (screenRectangle2 != null && screenRectangle2.intersects(screenRectangle)) {
					return true;
				}
			}
		}

		return false;
	}

	public void submitBlitToCurrentLayer(BlitRenderState blitRenderState) {
		this.current.submitGuiElement(blitRenderState);
	}

	public void submitGlyphToCurrentLayer(GuiElementRenderState guiElementRenderState) {
		this.current.submitGlyph(guiElementRenderState);
	}

	public Set<Object> getItemModelIdentities() {
		return this.itemModelIdentities;
	}

	public void forEachElement(Consumer<GuiElementRenderState> consumer, GuiRenderState.TraverseRange traverseRange) {
		GuiRenderState.Node previousNode = this.current;
		this.traverse(currentNode -> {
			this.current = currentNode;
			if (currentNode.elementStates != null || currentNode.glyphStates != null) {
				if (currentNode.elementStates != null) {
					for (GuiElementRenderState guiElementRenderState : currentNode.elementStates) {
						consumer.accept(guiElementRenderState);
					}
				}

				if (currentNode.glyphStates != null) {
					for (GuiElementRenderState guiElementRenderState : currentNode.glyphStates) {
						consumer.accept(guiElementRenderState);
					}
				}
			}
		}, traverseRange);
		this.current = previousNode;
	}

	public void forEachItem(Consumer<GuiItemRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.itemStates != null) {
				this.current = nodex;

				for (GuiItemRenderState guiItemRenderState : nodex.itemStates) {
					consumer.accept(guiItemRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void forEachText(Consumer<GuiTextRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.textStates != null) {
				for (GuiTextRenderState guiTextRenderState : nodex.textStates) {
					this.current = nodex;
					consumer.accept(guiTextRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void forEachPictureInPicture(Consumer<PictureInPictureRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.picturesInPictureStates != null) {
				this.current = nodex;

				for (PictureInPictureRenderState pictureInPictureRenderState : nodex.picturesInPictureStates) {
					consumer.accept(pictureInPictureRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void sortElements(Comparator<GuiElementRenderState> comparator) {
		this.traverse(node -> {
			if (node.elementStates != null) {
				if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
					Collections.shuffle(node.elementStates);
				}

				node.elementStates.sort(comparator);
			}
		}, GuiRenderState.TraverseRange.ALL);
	}

	/**
	 * Returns the deterministic source-layer position of the node currently
	 * being visited by one of the semantic collectors. The phase mirrors the
	 * order in which {@code GuiRenderer.prepare()} materializes a node: ordinary
	 * elements (including prepared item blits), then text glyphs. It is semantic ordering only
	 * and never exposes a renderer object or GPU handle.
	 */
	public int currentSemanticLayerOrder(GuiRenderState.SemanticPhase phase) {
		GuiRenderState.Node selected = this.current;
		if (selected == null) {
			throw new IllegalStateException("semantic GUI layer requested outside traversal");
		}
		int[] nodeOrdinal = new int[] {0};
		int[] selectedOrdinal = new int[] {-1};
		this.traverse(node -> {
			if (node == selected) {
				selectedOrdinal[0] = nodeOrdinal[0];
			}
			nodeOrdinal[0]++;
		}, GuiRenderState.TraverseRange.ALL);
		if (selectedOrdinal[0] < 0) {
			throw new IllegalStateException("current semantic GUI node is not part of this frame");
		}
		return Math.addExact(Math.multiplyExact(selectedOrdinal[0], GuiRenderState.SemanticPhase.values().length), phase.offset());
	}

	private void traverse(Consumer<GuiRenderState.Node> consumer, GuiRenderState.TraverseRange traverseRange) {
		int i = 0;
		int j = this.strata.size();
		if (traverseRange == GuiRenderState.TraverseRange.BEFORE_BLUR) {
			j = Math.min(this.firstStratumAfterBlur, this.strata.size());
		} else if (traverseRange == GuiRenderState.TraverseRange.AFTER_BLUR) {
			i = this.firstStratumAfterBlur;
		}

		for (int k = i; k < j; k++) {
			GuiRenderState.Node node = (GuiRenderState.Node)this.strata.get(k);
			this.traverse(node, consumer);
		}
	}

	private void traverse(GuiRenderState.Node node, Consumer<GuiRenderState.Node> consumer) {
		consumer.accept(node);
		if (node.up != null) {
			this.traverse(node.up, consumer);
		}
	}

	public void reset() {
		this.itemModelIdentities.clear();
		this.strata.clear();
		this.firstStratumAfterBlur = Integer.MAX_VALUE;
		this.nextStratum();
	}

	@Environment(EnvType.CLIENT)
	static class Node {
		@Nullable
		public final GuiRenderState.Node parent;
		@Nullable
		public GuiRenderState.Node up;
		@Nullable
		public List<GuiElementRenderState> elementStates;
		@Nullable
		public List<GuiElementRenderState> glyphStates;
		@Nullable
		public List<GuiItemRenderState> itemStates;
		@Nullable
		public List<GuiTextRenderState> textStates;
		@Nullable
		public List<PictureInPictureRenderState> picturesInPictureStates;

		Node(@Nullable GuiRenderState.Node node) {
			this.parent = node;
		}

		public void submitItem(GuiItemRenderState guiItemRenderState) {
			if (this.itemStates == null) {
				this.itemStates = new ArrayList();
			}

			this.itemStates.add(guiItemRenderState);
		}

		public void submitText(GuiTextRenderState guiTextRenderState) {
			if (this.textStates == null) {
				this.textStates = new ArrayList();
			}

			this.textStates.add(guiTextRenderState);
		}

		public void submitPicturesInPictureState(PictureInPictureRenderState pictureInPictureRenderState) {
			if (this.picturesInPictureStates == null) {
				this.picturesInPictureStates = new ArrayList();
			}

			this.picturesInPictureStates.add(pictureInPictureRenderState);
		}

		public void submitGuiElement(GuiElementRenderState guiElementRenderState) {
			if (this.elementStates == null) {
				this.elementStates = new ArrayList();
			}

			this.elementStates.add(guiElementRenderState);
		}

		public void submitGlyph(GuiElementRenderState guiElementRenderState) {
			if (this.glyphStates == null) {
				this.glyphStates = new ArrayList();
			}

			this.glyphStates.add(guiElementRenderState);
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum TraverseRange {
		ALL,
		BEFORE_BLUR,
		AFTER_BLUR;
	}

	@Environment(EnvType.CLIENT)
	public static enum SemanticPhase {
		ELEMENTS(0),
		ITEMS(1),
		TEXT(2);

		private final int offset;

		SemanticPhase(int offset) {
			this.offset = offset;
		}

		int offset() {
			return this.offset;
		}
	}
}
