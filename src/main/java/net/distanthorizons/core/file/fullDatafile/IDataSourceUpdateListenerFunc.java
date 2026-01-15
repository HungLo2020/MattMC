package net.distanthorizons.core.file.fullDatafile;

@FunctionalInterface
public interface IDataSourceUpdateListenerFunc<TDataSource>
{
	void OnDataSourceUpdated(TDataSource updatedFullDataSource);
}
