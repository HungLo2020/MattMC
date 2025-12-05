package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ChatComponent {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_CHAT_HISTORY = 100;
	private static final int MESSAGE_NOT_FOUND = -1;
	private static final int MESSAGE_INDENT = 4;
	private static final int MESSAGE_TAG_MARGIN_LEFT = 4;
	private static final int BOTTOM_MARGIN = 40;
	private static final int TIME_BEFORE_MESSAGE_DELETION = 60;
	private static final Component DELETED_CHAT_MESSAGE = Component.translatable("chat.deleted_marker")
		.withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});
	private final Minecraft minecraft;
	private final ArrayListDeque<String> recentChat = new ArrayListDeque(100);
	private final List<GuiMessage> allMessages = Lists.<GuiMessage>newArrayList();
	private final List<GuiMessage.Line> trimmedMessages = Lists.<GuiMessage.Line>newArrayList();
	private int chatScrollbarPos;
	private boolean newMessageSinceScroll;
	@Nullable
	private ChatComponent.Draft latestDraft;
	@Nullable
	private ChatScreen preservedScreen;
	private final List<ChatComponent.DelayedMessageDeletion> messageDeletionQueue = new ArrayList();

	public ChatComponent(Minecraft minecraft) {
		this.minecraft = minecraft;
		this.recentChat.addAll(minecraft.commandHistory().history());
	}

	public void tick() {
		if (!this.messageDeletionQueue.isEmpty()) {
			this.processMessageDeletionQueue();
		}
	}

	private int forEachLine(int i, int j, boolean bl, int k, ChatComponent.LineConsumer lineConsumer) {
		int l = this.getLineHeight();
		int m = 0;

		for (int n = Math.min(this.trimmedMessages.size() - this.chatScrollbarPos, i) - 1; n >= 0; n--) {
			int o = n + this.chatScrollbarPos;
			GuiMessage.Line line = (GuiMessage.Line)this.trimmedMessages.get(o);
			if (line != null) {
				int p = j - line.addedTime();
				float f = bl ? 1.0F : (float)getTimeFactor(p);
				if (f > 1.0E-5F) {
					m++;
					int q = k - n * l;
					int r = q - l;
					lineConsumer.accept(0, r, q, line, n, f);
				}
			}
		}

		return m;
	}

	public void render(GuiGraphics guiGraphics, int i, int j, int k, boolean bl) {
		if (!this.isChatHidden()) {
			int l = this.getLinesPerPage();
			int m = this.trimmedMessages.size();
			if (m > 0) {
				ProfilerFiller profilerFiller = Profiler.get();
				profilerFiller.push("chat");
				float f = (float)this.getScale();
				int n = Mth.ceil(this.getWidth() / f);
				int o = guiGraphics.guiHeight();
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().scale(f, f);
				guiGraphics.pose().translate(4.0F, 0.0F);
				int p = Mth.floor((o - 40) / f);
				int q = this.getMessageEndIndexAt(this.screenToChatX(j), this.screenToChatY(k));
				float g = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
				float h = this.minecraft.options.textBackgroundOpacity().get().floatValue();
				double d = this.minecraft.options.chatLineSpacing().get();
				int r = (int)Math.round(-8.0 * (d + 1.0) + 4.0 * d);
				this.forEachLine(l, i, bl, p, (lx, mx, nx, line, ox, hx) -> {
					guiGraphics.fill(lx - 4, mx, lx + n + 4 + 4, nx, ARGB.color(hx * h, -16777216));
					GuiMessageTag guiMessageTag = line.tag();
					if (guiMessageTag != null) {
						int px = ARGB.color(hx * g, guiMessageTag.indicatorColor());
						guiGraphics.fill(lx - 4, mx, lx - 2, nx, px);
						if (ox == q && guiMessageTag.icon() != null) {
							int qx = this.getTagIconLeft(line);
							int rx = nx + r + 9;
							this.drawTagIcon(guiGraphics, qx, rx, guiMessageTag.icon());
						}
					}
				});
				int s = this.forEachLine(l, i, bl, p, (jx, kx, lx, line, mx, gx) -> {
					int nx = lx + r;
					guiGraphics.drawString(this.minecraft.font, line.content(), jx, nx, ARGB.color(gx * g, -1));
				});
				long t = this.minecraft.getChatListener().queueSize();
				if (t > 0L) {
					int u = (int)(128.0F * g);
					int v = (int)(255.0F * h);
					guiGraphics.pose().pushMatrix();
					guiGraphics.pose().translate(0.0F, p);
					guiGraphics.fill(-2, 0, n + 4, 9, v << 24);
					guiGraphics.drawString(this.minecraft.font, Component.translatable("chat.queue", new Object[]{t}), 0, 1, ARGB.color(u, -1));
					guiGraphics.pose().popMatrix();
				}

				if (bl) {
					int u = this.getLineHeight();
					int v = m * u;
					int w = s * u;
					int x = this.chatScrollbarPos * w / m - p;
					int y = w * w / v;
					if (v != w) {
						int z = x > 0 ? 170 : 96;
						int aa = this.newMessageSinceScroll ? 13382451 : 3355562;
						int ab = n + 4;
						guiGraphics.fill(ab, -x, ab + 2, -x - y, ARGB.color(z, aa));
						guiGraphics.fill(ab + 2, -x, ab + 1, -x - y, ARGB.color(z, 13421772));
					}
				}

				guiGraphics.pose().popMatrix();
				profilerFiller.pop();
			}
		}
	}

	private void drawTagIcon(GuiGraphics guiGraphics, int i, int j, GuiMessageTag.Icon icon) {
		int k = j - icon.height - 1;
		icon.draw(guiGraphics, i, k);
	}

	private int getTagIconLeft(GuiMessage.Line line) {
		return this.minecraft.font.width(line.content()) + 4;
	}

	private boolean isChatHidden() {
		return this.minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
	}

	private static double getTimeFactor(int i) {
		double d = i / 200.0;
		d = 1.0 - d;
		d *= 10.0;
		d = Mth.clamp(d, 0.0, 1.0);
		return d * d;
	}

	public void clearMessages(boolean bl) {
		this.minecraft.getChatListener().flushQueue();
		this.messageDeletionQueue.clear();
		this.trimmedMessages.clear();
		this.allMessages.clear();
		if (bl) {
			this.recentChat.clear();
			this.recentChat.addAll(this.minecraft.commandHistory().history());
		}
	}

	public void addMessage(Component component) {
		this.addMessage(component, null, this.minecraft.isSingleplayer() ? GuiMessageTag.systemSinglePlayer() : GuiMessageTag.system());
	}

	public void addMessage(Component component, @Nullable MessageSignature messageSignature, @Nullable GuiMessageTag guiMessageTag) {
		GuiMessage guiMessage = new GuiMessage(this.minecraft.gui.getGuiTicks(), component, messageSignature, guiMessageTag);
		this.logChatMessage(guiMessage);
		this.addMessageToDisplayQueue(guiMessage);
		this.addMessageToQueue(guiMessage);
	}

	private void logChatMessage(GuiMessage guiMessage) {
		String string = guiMessage.content().getString().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n");
		String string2 = (String)Optionull.map(guiMessage.tag(), GuiMessageTag::logTag);
		if (string2 != null) {
			LOGGER.info("[{}] [CHAT] {}", string2, string);
		} else {
			LOGGER.info("[CHAT] {}", string);
		}
	}

	private void addMessageToDisplayQueue(GuiMessage guiMessage) {
		int i = Mth.floor(this.getWidth() / this.getScale());
		GuiMessageTag.Icon icon = guiMessage.icon();
		if (icon != null) {
			i -= icon.width + 4 + 2;
		}

		List<FormattedCharSequence> list = ComponentRenderUtils.wrapComponents(guiMessage.content(), i, this.minecraft.font);
		boolean bl = this.isChatFocused();

		for (int j = 0; j < list.size(); j++) {
			FormattedCharSequence formattedCharSequence = (FormattedCharSequence)list.get(j);
			if (bl && this.chatScrollbarPos > 0) {
				this.newMessageSinceScroll = true;
				this.scrollChat(1);
			}

			boolean bl2 = j == list.size() - 1;
			this.trimmedMessages.add(0, new GuiMessage.Line(guiMessage.addedTime(), formattedCharSequence, guiMessage.tag(), bl2));
		}

		while (this.trimmedMessages.size() > 100) {
			this.trimmedMessages.remove(this.trimmedMessages.size() - 1);
		}
	}

	private void addMessageToQueue(GuiMessage guiMessage) {
		this.allMessages.add(0, guiMessage);

		while (this.allMessages.size() > 100) {
			this.allMessages.remove(this.allMessages.size() - 1);
		}
	}

	private void processMessageDeletionQueue() {
		int i = this.minecraft.gui.getGuiTicks();
		this.messageDeletionQueue
			.removeIf(
				delayedMessageDeletion -> i >= delayedMessageDeletion.deletableAfter() ? this.deleteMessageOrDelay(delayedMessageDeletion.signature()) == null : false
			);
	}

	public void deleteMessage(MessageSignature messageSignature) {
		ChatComponent.DelayedMessageDeletion delayedMessageDeletion = this.deleteMessageOrDelay(messageSignature);
		if (delayedMessageDeletion != null) {
			this.messageDeletionQueue.add(delayedMessageDeletion);
		}
	}

	@Nullable
	private ChatComponent.DelayedMessageDeletion deleteMessageOrDelay(MessageSignature messageSignature) {
		int i = this.minecraft.gui.getGuiTicks();
		ListIterator<GuiMessage> listIterator = this.allMessages.listIterator();

		while (listIterator.hasNext()) {
			GuiMessage guiMessage = (GuiMessage)listIterator.next();
			if (messageSignature.equals(guiMessage.signature())) {
				int j = guiMessage.addedTime() + 60;
				if (i >= j) {
					listIterator.set(this.createDeletedMarker(guiMessage));
					this.refreshTrimmedMessages();
					return null;
				}

				return new ChatComponent.DelayedMessageDeletion(messageSignature, j);
			}
		}

		return null;
	}

	private GuiMessage createDeletedMarker(GuiMessage guiMessage) {
		return new GuiMessage(guiMessage.addedTime(), DELETED_CHAT_MESSAGE, null, GuiMessageTag.system());
	}

	public void rescaleChat() {
		this.resetChatScroll();
		this.refreshTrimmedMessages();
	}

	private void refreshTrimmedMessages() {
		this.trimmedMessages.clear();

		for (GuiMessage guiMessage : Lists.reverse(this.allMessages)) {
			this.addMessageToDisplayQueue(guiMessage);
		}
	}

	public ArrayListDeque<String> getRecentChat() {
		return this.recentChat;
	}

	public void addRecentChat(String string) {
		if (!string.equals(this.recentChat.peekLast())) {
			if (this.recentChat.size() >= 100) {
				this.recentChat.removeFirst();
			}

			this.recentChat.addLast(string);
		}

		if (string.startsWith("/")) {
			this.minecraft.commandHistory().addCommand(string);
		}
	}

	public void resetChatScroll() {
		this.chatScrollbarPos = 0;
		this.newMessageSinceScroll = false;
	}

	public void scrollChat(int i) {
		this.chatScrollbarPos += i;
		int j = this.trimmedMessages.size();
		if (this.chatScrollbarPos > j - this.getLinesPerPage()) {
			this.chatScrollbarPos = j - this.getLinesPerPage();
		}

		if (this.chatScrollbarPos <= 0) {
			this.chatScrollbarPos = 0;
			this.newMessageSinceScroll = false;
		}
	}

	public boolean handleChatQueueClicked(double d, double e) {
		if (this.isChatFocused() && !this.minecraft.options.hideGui && !this.isChatHidden()) {
			ChatListener chatListener = this.minecraft.getChatListener();
			if (chatListener.queueSize() == 0L) {
				return false;
			} else {
				double f = d - 2.0;
				double g = this.minecraft.getWindow().getGuiScaledHeight() - e - 40.0;
				if (f <= Mth.floor(this.getWidth() / this.getScale()) && g < 0.0 && g > Mth.floor(-9.0 * this.getScale())) {
					chatListener.acceptNextDelayedMessage();
					return true;
				} else {
					return false;
				}
			}
		} else {
			return false;
		}
	}

	@Nullable
	public Style getClickedComponentStyleAt(double d, double e) {
		double f = this.screenToChatX(d);
		double g = this.screenToChatY(e);
		int i = this.getMessageLineIndexAt(f, g);
		if (i >= 0 && i < this.trimmedMessages.size()) {
			GuiMessage.Line line = (GuiMessage.Line)this.trimmedMessages.get(i);
			return this.minecraft.font.getSplitter().componentStyleAtWidth(line.content(), Mth.floor(f));
		} else {
			return null;
		}
	}

	@Nullable
	public GuiMessageTag getMessageTagAt(double d, double e) {
		double f = this.screenToChatX(d);
		double g = this.screenToChatY(e);
		int i = this.getMessageEndIndexAt(f, g);
		if (i >= 0 && i < this.trimmedMessages.size()) {
			GuiMessage.Line line = (GuiMessage.Line)this.trimmedMessages.get(i);
			GuiMessageTag guiMessageTag = line.tag();
			if (guiMessageTag != null && this.hasSelectedMessageTag(f, line, guiMessageTag)) {
				return guiMessageTag;
			}
		}

		return null;
	}

	private boolean hasSelectedMessageTag(double d, GuiMessage.Line line, GuiMessageTag guiMessageTag) {
		if (d < 0.0) {
			return true;
		} else {
			GuiMessageTag.Icon icon = guiMessageTag.icon();
			if (icon == null) {
				return false;
			} else {
				int i = this.getTagIconLeft(line);
				int j = i + icon.width;
				return d >= i && d <= j;
			}
		}
	}

	private double screenToChatX(double d) {
		return d / this.getScale() - 4.0;
	}

	private double screenToChatY(double d) {
		double e = this.minecraft.getWindow().getGuiScaledHeight() - d - 40.0;
		return e / (this.getScale() * this.getLineHeight());
	}

	private int getMessageEndIndexAt(double d, double e) {
		int i = this.getMessageLineIndexAt(d, e);
		if (i == -1) {
			return -1;
		} else {
			while (i >= 0) {
				if (((GuiMessage.Line)this.trimmedMessages.get(i)).endOfEntry()) {
					return i;
				}

				i--;
			}

			return i;
		}
	}

	private int getMessageLineIndexAt(double d, double e) {
		if (this.isChatFocused() && !this.isChatHidden()) {
			if (!(d < -4.0) && !(d > Mth.floor(this.getWidth() / this.getScale()))) {
				int i = Math.min(this.getLinesPerPage(), this.trimmedMessages.size());
				if (e >= 0.0 && e < i) {
					int j = Mth.floor(e + this.chatScrollbarPos);
					if (j >= 0 && j < this.trimmedMessages.size()) {
						return j;
					}
				}

				return -1;
			} else {
				return -1;
			}
		} else {
			return -1;
		}
	}

	public boolean isChatFocused() {
		return this.minecraft.screen instanceof ChatScreen;
	}

	public int getWidth() {
		return getWidth(this.minecraft.options.chatWidth().get());
	}

	public int getHeight() {
		return getHeight(this.isChatFocused() ? this.minecraft.options.chatHeightFocused().get() : this.minecraft.options.chatHeightUnfocused().get());
	}

	public double getScale() {
		return this.minecraft.options.chatScale().get();
	}

	public static int getWidth(double d) {
		int i = 320;
		int j = 40;
		return Mth.floor(d * 280.0 + 40.0);
	}

	public static int getHeight(double d) {
		int i = 180;
		int j = 20;
		return Mth.floor(d * 160.0 + 20.0);
	}

	public static double defaultUnfocusedPct() {
		int i = 180;
		int j = 20;
		return 70.0 / (getHeight(1.0) - 20);
	}

	public int getLinesPerPage() {
		return this.getHeight() / this.getLineHeight();
	}

	private int getLineHeight() {
		return (int)(9.0 * (this.minecraft.options.chatLineSpacing().get() + 1.0));
	}

	public void saveAsDraft(String string) {
		boolean bl = string.startsWith("/");
		this.latestDraft = new ChatComponent.Draft(string, bl ? ChatComponent.ChatMethod.COMMAND : ChatComponent.ChatMethod.MESSAGE);
	}

	public void discardDraft() {
		this.latestDraft = null;
	}

	public <T extends ChatScreen> T createScreen(ChatComponent.ChatMethod chatMethod, ChatScreen.ChatConstructor<T> chatConstructor) {
		return this.latestDraft != null && chatMethod.isDraftRestorable(this.latestDraft)
			? chatConstructor.create(this.latestDraft.text(), true)
			: chatConstructor.create(chatMethod.prefix(), false);
	}

		public void openScreen(ChatComponent.ChatMethod chatMethod, ChatScreen.ChatConstructor<?> chatConstructor) {
		@SuppressWarnings("unchecked")
		ChatScreen.ChatConstructor<ChatScreen> typedConstructor = (ChatScreen.ChatConstructor<ChatScreen>)(ChatScreen.ChatConstructor<?>)chatConstructor;
		this.minecraft.setScreen(this.createScreen(chatMethod, typedConstructor));
	}

	public void preserveCurrentChatScreen() {
		if (this.minecraft.screen instanceof ChatScreen chatScreen) {
			this.preservedScreen = chatScreen;
		}
	}

	@Nullable
	public ChatScreen restoreChatScreen() {
		ChatScreen chatScreen = this.preservedScreen;
		this.preservedScreen = null;
		return chatScreen;
	}

	public ChatComponent.State storeState() {
		return new ChatComponent.State(List.copyOf(this.allMessages), List.copyOf(this.recentChat), List.copyOf(this.messageDeletionQueue));
	}

	public void restoreState(ChatComponent.State state) {
		this.recentChat.clear();
		this.recentChat.addAll(state.history);
		this.messageDeletionQueue.clear();
		this.messageDeletionQueue.addAll(state.delayedMessageDeletions);
		this.allMessages.clear();
		this.allMessages.addAll(state.messages);
		this.refreshTrimmedMessages();
	}

	@Environment(EnvType.CLIENT)
	public static enum ChatMethod {
		MESSAGE("") {
			@Override
			public boolean isDraftRestorable(ChatComponent.Draft draft) {
				return true;
			}
		},
		COMMAND("/") {
			@Override
			public boolean isDraftRestorable(ChatComponent.Draft draft) {
				return this == draft.chatMethod;
			}
		};

		private final String prefix;

		ChatMethod(final String string2) {
			this.prefix = string2;
		}

		public String prefix() {
			return this.prefix;
		}

		public abstract boolean isDraftRestorable(ChatComponent.Draft draft);
	}

	@Environment(EnvType.CLIENT)
	record DelayedMessageDeletion(MessageSignature signature, int deletableAfter) {
	}

	@Environment(EnvType.CLIENT)
	public record Draft(String text, ChatComponent.ChatMethod chatMethod) {
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface LineConsumer {
		void accept(int i, int j, int k, GuiMessage.Line line, int l, float f);
	}

	@Environment(EnvType.CLIENT)
	public static class State {
		final List<GuiMessage> messages;
		final List<String> history;
		final List<ChatComponent.DelayedMessageDeletion> delayedMessageDeletions;

		public State(List<GuiMessage> list, List<String> list2, List<ChatComponent.DelayedMessageDeletion> list3) {
			this.messages = list;
			this.history = list2;
			this.delayedMessageDeletions = list3;
		}
	}
}
