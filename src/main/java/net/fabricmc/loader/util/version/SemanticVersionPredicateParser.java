package net.fabricmc.loader.util.version;

import java.util.function.Predicate;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public final class SemanticVersionPredicateParser {
	public static Predicate<SemanticVersionImpl> create(String text) throws VersionParsingException {
		VersionPredicate predicate = VersionPredicate.parse(text);

		return v -> predicate.test(v);
	}
}
