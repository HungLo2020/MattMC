package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

// TODO why is this exist alongside AbstractDhWorld?
public interface IDhWorld extends Closeable
{
	
	@Nullable
	IDhLevel getOrLoadLevel(@NotNull ILevelWrapper levelWrapper);
	@Nullable
	IDhLevel getLevel(@NotNull ILevelWrapper wrapper);
	Iterable<? extends IDhLevel> getAllLoadedLevels();
	int getLoadedLevelCount();
	
	void unloadLevel(@NotNull ILevelWrapper levelWrapper);
	
}
