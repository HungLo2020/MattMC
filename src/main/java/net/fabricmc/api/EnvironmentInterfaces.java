package net.fabricmc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A container of multiple {@link EnvironmentInterface} annotations on a class, often defined implicitly.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Documented
public @interface EnvironmentInterfaces {
	/**
	 * Returns the {@link EnvironmentInterface} annotations it holds.
	 */
	EnvironmentInterface[] value();
}
