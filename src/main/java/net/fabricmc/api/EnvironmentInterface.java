package net.fabricmc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to declare that a class implements an interface only in the specified environment.
 *
 * <p>Use with caution, as Fabric-loader will remove the interface from {@code implements} declaration
 * of the class in a mismatched environment!</p>
 *
 * <p>Implemented methods are not removed. To remove implemented methods, use {@link Environment}.</p>
 *
 * @see Environment
 */
@Retention(RetentionPolicy.CLASS)
@Repeatable(EnvironmentInterfaces.class)
@Target(ElementType.TYPE)
@Documented
public @interface EnvironmentInterface {
	/**
	 * Returns the environment type that the specific interface is only implemented in.
	 */
	EnvType value();

	/**
	 * Returns the interface class.
	 */
	Class<?> itf();
}
