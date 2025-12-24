package net.minecraft.world.level;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class BaseCommandBlock {
	private static final Component DEFAULT_NAME = Component.literal("@");
	private static final int NO_LAST_EXECUTION = -1;
	private long lastExecution = -1L;
	private boolean updateLastExecution = true;
	private int successCount;
	private boolean trackOutput = true;
	@Nullable
	Component lastOutput;
	private String command = "";
	@Nullable
	private Component customName;

	public int getSuccessCount() {
		return this.successCount;
	}

	public void setSuccessCount(int i) {
		this.successCount = i;
	}

	public Component getLastOutput() {
		return this.lastOutput == null ? CommonComponents.EMPTY : this.lastOutput;
	}

	public void save(ValueOutput valueOutput) {
		valueOutput.putString("Command", this.command);
		valueOutput.putInt("SuccessCount", this.successCount);
		valueOutput.storeNullable("CustomName", ComponentSerialization.CODEC, this.customName);
		valueOutput.putBoolean("TrackOutput", this.trackOutput);
		if (this.trackOutput) {
			valueOutput.storeNullable("LastOutput", ComponentSerialization.CODEC, this.lastOutput);
		}

		valueOutput.putBoolean("UpdateLastExecution", this.updateLastExecution);
		if (this.updateLastExecution && this.lastExecution != -1L) {
			valueOutput.putLong("LastExecution", this.lastExecution);
		}
	}

	public void load(ValueInput valueInput) {
		this.command = valueInput.getStringOr("Command", "");
		this.successCount = valueInput.getIntOr("SuccessCount", 0);
		this.setCustomName(BlockEntity.parseCustomNameSafe(valueInput, "CustomName"));
		this.trackOutput = valueInput.getBooleanOr("TrackOutput", true);
		if (this.trackOutput) {
			this.lastOutput = BlockEntity.parseCustomNameSafe(valueInput, "LastOutput");
		} else {
			this.lastOutput = null;
		}

		this.updateLastExecution = valueInput.getBooleanOr("UpdateLastExecution", true);
		if (this.updateLastExecution) {
			this.lastExecution = valueInput.getLongOr("LastExecution", -1L);
		} else {
			this.lastExecution = -1L;
		}
	}

	public void setCommand(String string) {
		this.command = string;
		this.successCount = 0;
	}

	public String getCommand() {
		return this.command;
	}

	public boolean performCommand(Level level) {
		if (level.isClientSide() || level.getGameTime() == this.lastExecution) {
			return false;
		} else if ("Searge".equalsIgnoreCase(this.command)) {
			this.lastOutput = Component.literal("#itzlipofutzli");
			this.successCount = 1;
			return true;
		} else {
			this.successCount = 0;
			MinecraftServer minecraftServer = this.getLevel().getServer();
			if (minecraftServer.isCommandBlockEnabled() && !StringUtil.isNullOrEmpty(this.command)) {
				try {
					this.lastOutput = null;

					try (BaseCommandBlock.CloseableCommandBlockSource closeableCommandBlockSource = this.createSource()) {
						CommandSource commandSource = (CommandSource)Objects.requireNonNullElse(closeableCommandBlockSource, CommandSource.NULL);
						CommandSourceStack commandSourceStack = this.createCommandSourceStack(commandSource).withCallback((bl, i) -> {
							if (bl) {
								this.successCount++;
							}
						});
						minecraftServer.getCommands().performPrefixedCommand(commandSourceStack, this.command);
					}
				} catch (Throwable var8) {
					CrashReport crashReport = CrashReport.forThrowable(var8, "Executing command block");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Command to be executed");
					crashReportCategory.setDetail("Command", this::getCommand);
					crashReportCategory.setDetail("Name", (CrashReportDetail<String>)(() -> this.getName().getString()));
					throw new ReportedException(crashReport);
				}
			}

			if (this.updateLastExecution) {
				this.lastExecution = level.getGameTime();
			} else {
				this.lastExecution = -1L;
			}

			return true;
		}
	}

	@Nullable
	private BaseCommandBlock.CloseableCommandBlockSource createSource() {
		return this.trackOutput ? new BaseCommandBlock.CloseableCommandBlockSource() : null;
	}

	public Component getName() {
		return this.customName != null ? this.customName : DEFAULT_NAME;
	}

	@Nullable
	public Component getCustomName() {
		return this.customName;
	}

	public void setCustomName(@Nullable Component component) {
		this.customName = component;
	}

	public abstract ServerLevel getLevel();

	public abstract void onUpdated();

	public void setLastOutput(@Nullable Component component) {
		this.lastOutput = component;
	}

	public void setTrackOutput(boolean bl) {
		this.trackOutput = bl;
	}

	public boolean isTrackOutput() {
		return this.trackOutput;
	}

	public InteractionResult usedBy(Player player) {
		if (!player.canUseGameMasterBlocks()) {
			return InteractionResult.PASS;
		} else {
			if (player.level().isClientSide()) {
				player.openMinecartCommandBlock(this);
			}

			return InteractionResult.SUCCESS;
		}
	}

	public abstract Vec3 getPosition();

	public abstract CommandSourceStack createCommandSourceStack(CommandSource commandSource);

	public abstract boolean isValid();

	protected class CloseableCommandBlockSource implements CommandSource, AutoCloseable {
		private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
		private boolean closed;

		@Override
		public boolean acceptsSuccess() {
			return !this.closed && BaseCommandBlock.this.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
		}

		@Override
		public boolean acceptsFailure() {
			return !this.closed;
		}

		@Override
		public boolean shouldInformAdmins() {
			return !this.closed && BaseCommandBlock.this.getLevel().getGameRules().getBoolean(GameRules.RULE_COMMANDBLOCKOUTPUT);
		}

		@Override
		public void sendSystemMessage(Component component) {
			if (!this.closed) {
				BaseCommandBlock.this.lastOutput = Component.literal("[" + TIME_FORMAT.format(ZonedDateTime.now()) + "] ").append(component);
				BaseCommandBlock.this.onUpdated();
			}
		}

		public void close() throws Exception {
			this.closed = true;
		}
	}
}
