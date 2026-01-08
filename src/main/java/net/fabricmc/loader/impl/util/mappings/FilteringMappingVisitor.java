package net.fabricmc.loader.impl.util.mappings;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

// Filter out all method arguments, local variable names and comments
public class FilteringMappingVisitor extends ForwardingMappingVisitor {
	public FilteringMappingVisitor(MappingVisitor next) {
		super(next);
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		// ignored
		return false;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		// ignored
		return false;
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
		// ignored
	}
}
