package net.fabricmc.loader.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.mappingio.tree.MappingTree;

class MappingResolverImpl implements MappingResolver {
	private final MappingTree mappings;
	private final String targetNamespace;
	private final int targetNamespaceId;

	MappingResolverImpl(MappingTree mappings, String targetNamespace) {
		this.mappings = mappings;
		this.targetNamespace = targetNamespace;
		this.targetNamespaceId = mappings.getNamespaceId(targetNamespace);
	}

	@Override
	public Collection<String> getNamespaces() {
		HashSet<String> namespaces = new HashSet<>(mappings.getDstNamespaces());
		namespaces.add(mappings.getSrcNamespace());
		return Collections.unmodifiableSet(namespaces);
	}

	@Override
	public String getCurrentRuntimeNamespace() {
		return targetNamespace;
	}

	@Override
	public String mapClassName(String namespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return replaceSlashesWithDots(mappings.mapClassName(replaceDotsWithSlashes(className), mappings.getNamespaceId(namespace), targetNamespaceId));
	}

	@Override
	public String unmapClassName(String namespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return replaceSlashesWithDots(mappings.mapClassName(replaceDotsWithSlashes(className), targetNamespaceId, mappings.getNamespaceId(namespace)));
	}

	@Override
	public String mapFieldName(String namespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}

		MappingTree.FieldMapping field = mappings.getField(replaceDotsWithSlashes(owner), name, descriptor, mappings.getNamespaceId(namespace));
		return field == null ? name : field.getName(targetNamespaceId);
	}

	@Override
	public String mapMethodName(String namespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}

		MappingTree.MethodMapping method = mappings.getMethod(replaceDotsWithSlashes(owner), name, descriptor, mappings.getNamespaceId(namespace));
		return method == null ? name : method.getName(targetNamespaceId);
	}

	private static String replaceSlashesWithDots(String cname) {
		return cname.replace('/', '.');
	}

	private static String replaceDotsWithSlashes(String cname) {
		return cname.replace('.', '/');
	}
}
