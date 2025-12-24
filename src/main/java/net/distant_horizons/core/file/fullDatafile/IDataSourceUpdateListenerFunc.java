package net.distant_horizons.core.file.fullDatafile;

@FunctionalInterface
public interface IDataSourceUpdateListenerFunc<TDataSource>
{
	void OnDataSourceUpdated(TDataSource updatedFullDataSource);
}
