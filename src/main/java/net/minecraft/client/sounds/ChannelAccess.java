package net.minecraft.client.sounds;

import com.google.common.collect.Sets;
import net.blaze3d.audio.Channel;
import net.blaze3d.audio.Library;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChannelAccess {
	private final Set<ChannelAccess.ChannelHandle> channels = Sets.newIdentityHashSet();
	final Library library;
	final SoundEngineExecutor executor;

	public ChannelAccess(Library library, SoundEngineExecutor executor) {
		this.library = library;
		this.executor = executor;
	}

	public CompletableFuture<ChannelAccess.ChannelHandle> createHandle(Library.Pool pool) {
		CompletableFuture<ChannelAccess.ChannelHandle> completableFuture = new CompletableFuture();
		this.executor.execute(() -> {
			Channel channel = this.library.acquireChannel(pool);
			if (channel != null) {
				ChannelAccess.ChannelHandle channelHandle = new ChannelAccess.ChannelHandle(channel);
				this.channels.add(channelHandle);
				completableFuture.complete(channelHandle);
			} else {
				completableFuture.complete(null);
			}
		});
		return completableFuture;
	}

	public void executeOnChannels(Consumer<Stream<Channel>> consumer) {
		this.executor.execute(() -> {
			List<Channel> list = new ArrayList<>();

			for (ChannelAccess.ChannelHandle channelHandle : this.channels) {
				Channel channel = channelHandle.channelOrNull();
				if (channel != null) {
					list.add(channel);
				}
			}

			consumer.accept(list.stream());
		});
	}

	public void scheduleTick() {
		this.executor.execute(() -> {
			List<ChannelAccess.ChannelHandle> stoppedChannels = new ArrayList<>();

			for (ChannelAccess.ChannelHandle channelHandle : this.channels) {
				Channel channel = channelHandle.channelOrNull();
				if (channel == null) {
					stoppedChannels.add(channelHandle);
				} else {
					channel.updateStream();
					if (channel.stopped()) {
						stoppedChannels.add(channelHandle);
					}
				}
			}

			stoppedChannels.forEach(ChannelAccess.ChannelHandle::release);
		});
	}

	public void clear() {
		this.executor.executeBlocking(this::clearOnExecutor);
	}

	private void clearOnExecutor() {
		List<ChannelAccess.ChannelHandle> handles = new ArrayList<>(this.channels);
		handles.forEach(ChannelAccess.ChannelHandle::release);
	}

	@Environment(EnvType.CLIENT)
	public class ChannelHandle {
		@Nullable
		private Channel channel;
		private volatile boolean released;

		public boolean isStopped() {
			return this.isReleased();
		}

		public boolean isReleased() {
			return this.released;
		}

		public ChannelHandle(final Channel channel) {
			this.channel = channel;
		}

		public void execute(Consumer<Channel> consumer) {
			this.execute(consumer, () -> {
			});
		}

		public void execute(Consumer<Channel> consumer, Runnable releasedCallback) {
			ChannelAccess.this.executor.execute(() -> {
				Channel channel = this.channel;
				if (channel != null) {
					consumer.accept(channel);
				} else {
					releasedCallback.run();
				}
			});
		}

		public void failAttachment() {
			ChannelAccess.this.executor.execute(() -> {
				Channel channel = this.channel;
				if (channel != null) {
					channel.failAttachment();
				}

				this.releaseOnExecutor();
			});
		}

		public void release() {
			if (ChannelAccess.this.executor.isSameThread()) {
				this.releaseOnExecutor();
			} else {
				ChannelAccess.this.executor.execute(this::releaseOnExecutor);
			}
		}

		private void releaseOnExecutor() {
			Channel channel = this.detach();
			if (channel != null) {
				ChannelAccess.this.library.releaseChannel(channel);
			}
		}

		@Nullable
		private Channel channelOrNull() {
			return this.channel;
		}

		@Nullable
		private Channel detach() {
			this.released = true;
			Channel channel = this.channel;
			this.channel = null;
			ChannelAccess.this.channels.remove(this);
			return channel;
		}
	}
}
