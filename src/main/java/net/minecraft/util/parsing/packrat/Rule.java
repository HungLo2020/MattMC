package net.minecraft.util.parsing.packrat;

import org.jetbrains.annotations.Nullable;

public interface Rule<S, T> {
	@Nullable
	T parse(ParseState<S> parseState);

	static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.RuleAction<S, T> ruleAction) {
		return new Rule.WrappedTerm<>(ruleAction, term);
	}

	static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.SimpleRuleAction<S, T> simpleRuleAction) {
		return new Rule.WrappedTerm<>(simpleRuleAction, term);
	}

	@FunctionalInterface
	public interface RuleAction<S, T> {
		@Nullable
		T run(ParseState<S> parseState);
	}

	@FunctionalInterface
	public interface SimpleRuleAction<S, T> extends Rule.RuleAction<S, T> {
		T run(Scope scope);

		@Override
		default T run(ParseState<S> parseState) {
			return this.run(parseState.scope());
		}
	}

	public record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {
		@Nullable
		@Override
		public T parse(ParseState<S> parseState) {
			Scope scope = parseState.scope();
			scope.pushFrame();

			Object var3;
			try {
				if (!this.child.parse(parseState, scope, Control.UNBOUND)) {
					return null;
				}

				var3 = this.action.run(parseState);
			} finally {
				scope.popFrame();
			}

			return (T)var3;
		}
	}
}
