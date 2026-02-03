package net.logging.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that all methods return non-null by default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface MethodsReturnNonnullByDefault {
}
