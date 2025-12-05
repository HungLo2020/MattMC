package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public interface ProblemReporter {
	ProblemReporter DISCARDING = new ProblemReporter() {
		@Override
		public ProblemReporter forChild(ProblemReporter.PathElement pathElement) {
			return this;
		}

		@Override
		public void report(ProblemReporter.Problem problem) {
		}
	};

	ProblemReporter forChild(ProblemReporter.PathElement pathElement);

	void report(ProblemReporter.Problem problem);

	public static class Collector implements ProblemReporter {
		public static final ProblemReporter.PathElement EMPTY_ROOT = () -> "";
		@Nullable
		private final ProblemReporter.Collector parent;
		private final ProblemReporter.PathElement element;
		private final Set<ProblemReporter.Collector.Entry> problems;

		public Collector() {
			this(EMPTY_ROOT);
		}

		public Collector(ProblemReporter.PathElement pathElement) {
			this.parent = null;
			this.problems = new LinkedHashSet();
			this.element = pathElement;
		}

		private Collector(ProblemReporter.Collector collector, ProblemReporter.PathElement pathElement) {
			this.problems = collector.problems;
			this.parent = collector;
			this.element = pathElement;
		}

		@Override
		public ProblemReporter forChild(ProblemReporter.PathElement pathElement) {
			return new ProblemReporter.Collector(this, pathElement);
		}

		@Override
		public void report(ProblemReporter.Problem problem) {
			this.problems.add(new ProblemReporter.Collector.Entry(this, problem));
		}

		public boolean isEmpty() {
			return this.problems.isEmpty();
		}

		public void forEach(BiConsumer<String, ProblemReporter.Problem> biConsumer) {
			List<ProblemReporter.PathElement> list = new ArrayList();
			StringBuilder stringBuilder = new StringBuilder();

			for (ProblemReporter.Collector.Entry entry : this.problems) {
				for (ProblemReporter.Collector collector = entry.source; collector != null; collector = collector.parent) {
					list.add(collector.element);
				}

				for (int i = list.size() - 1; i >= 0; i--) {
					stringBuilder.append(((ProblemReporter.PathElement)list.get(i)).get());
				}

				biConsumer.accept(stringBuilder.toString(), entry.problem());
				stringBuilder.setLength(0);
				list.clear();
			}
		}

		public String getReport() {
			Multimap<String, ProblemReporter.Problem> multimap = HashMultimap.create();
			this.forEach(multimap::put);
			return (String)multimap.asMap()
				.entrySet()
				.stream()
				.map(
					entry -> " at "
						+ (String)entry.getKey()
						+ ": "
						+ (String)((Collection<ProblemReporter.Problem>)entry.getValue()).stream().map(problem -> problem.description()).collect(Collectors.joining("; "))
				)
				.collect(Collectors.joining("\n"));
		}

		public String getTreeReport() {
			List<ProblemReporter.PathElement> list = new ArrayList();
			ProblemReporter.Collector.ProblemTreeNode problemTreeNode = new ProblemReporter.Collector.ProblemTreeNode(this.element);

			for (ProblemReporter.Collector.Entry entry : this.problems) {
				for (ProblemReporter.Collector collector = entry.source; collector != this; collector = collector.parent) {
					list.add(collector.element);
				}

				ProblemReporter.Collector.ProblemTreeNode problemTreeNode2 = problemTreeNode;

				for (int i = list.size() - 1; i >= 0; i--) {
					problemTreeNode2 = problemTreeNode2.child((ProblemReporter.PathElement)list.get(i));
				}

				list.clear();
				problemTreeNode2.problems.add(entry.problem);
			}

			return String.join("\n", problemTreeNode.getLines());
		}

		record Entry(ProblemReporter.Collector source, ProblemReporter.Problem problem) {
		}

		record ProblemTreeNode(
			ProblemReporter.PathElement element,
			List<ProblemReporter.Problem> problems,
			Map<ProblemReporter.PathElement, ProblemReporter.Collector.ProblemTreeNode> children
		) {

			public ProblemTreeNode(ProblemReporter.PathElement pathElement) {
				this(pathElement, new ArrayList(), new LinkedHashMap());
			}

			public ProblemReporter.Collector.ProblemTreeNode child(ProblemReporter.PathElement pathElement) {
				return (ProblemReporter.Collector.ProblemTreeNode)this.children.computeIfAbsent(pathElement, ProblemReporter.Collector.ProblemTreeNode::new);
			}

			public List<String> getLines() {
				int i = this.problems.size();
				int j = this.children.size();
				if (i == 0 && j == 0) {
					return List.of();
				} else if (i == 0 && j == 1) {
					List<String> list = new ArrayList();
					this.children.forEach((pathElement, problemTreeNode) -> list.addAll(problemTreeNode.getLines()));
					list.set(0, this.element.get() + (String)list.get(0));
					return list;
				} else if (i == 1 && j == 0) {
					return List.of(this.element.get() + ": " + ((ProblemReporter.Problem)this.problems.getFirst()).description());
				} else {
					List<String> list = new ArrayList();
					this.children.forEach((pathElement, problemTreeNode) -> list.addAll(problemTreeNode.getLines()));
					list.replaceAll(string -> "  " + string);

					for (ProblemReporter.Problem problem : this.problems) {
						list.add("  " + problem.description());
					}

					list.addFirst(this.element.get() + ":");
					return list;
				}
			}
		}
	}

	public record ElementReferencePathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "->{" + this.id.location() + "@" + this.id.registry() + "}";
		}
	}

	public record FieldPathElement(String name) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "." + this.name;
		}
	}

	public record IndexedFieldPathElement(String name, int index) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "." + this.name + "[" + this.index + "]";
		}
	}

	public record IndexedPathElement(int index) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "[" + this.index + "]";
		}
	}

	@FunctionalInterface
	public interface PathElement {
		String get();
	}

	public interface Problem {
		String description();
	}

	public record RootElementPathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "{" + this.id.location() + "@" + this.id.registry() + "}";
		}
	}

	public record RootFieldPathElement(String name) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return this.name;
		}
	}

	public static class ScopedCollector extends ProblemReporter.Collector implements AutoCloseable {
		private final Logger logger;

		public ScopedCollector(Logger logger) {
			this.logger = logger;
		}

		public ScopedCollector(ProblemReporter.PathElement pathElement, Logger logger) {
			super(pathElement);
			this.logger = logger;
		}

		public void close() {
			if (!this.isEmpty()) {
				this.logger.warn("[{}] Serialization errors:\n{}", this.logger.getName(), this.getTreeReport());
			}
		}
	}
}
