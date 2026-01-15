package net.distanthorizons.core.util;

import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.logging.DhLogger;

import java.lang.reflect.Field;

public class AnnotationUtil
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	/** A quick method to test if an enum value has specific runtime annotation. */
	public static <TEnum extends Enum<?>, TAnno extends java.lang.annotation.Annotation> boolean doesEnumHaveAnnotation(TEnum enumValue, Class<TAnno> annotationToSearchFor)
	{
		try
		{
			// fields will contain all possible enums
			//  unfortunately James isn't sure of a way to do this without looping through all enum values 
			//  (although since enums should only have ~10 items at most, this shouldn't be a big deal)
			Field[] fields = enumValue.getClass().getFields();
			for (Field field : fields)
			{
				// only test for annotations for the 
				@SuppressWarnings("unchecked")
				TEnum testEnumValue = (TEnum) field.get(enumValue);
				if (testEnumValue == enumValue)
				{
					return field.getAnnotation(annotationToSearchFor) != null;
				}
			}
			
			// should never happen
			// if we got here Java screwed up getting us the enums
			throw new IllegalStateException("Enum missing expected value. Enum: [" + enumValue.getClass() + "] doesn't contain the value: [" + enumValue.name() + "].");
		}
		catch (IllegalAccessException | IllegalArgumentException | ClassCastException e)
		{
			// shouldn't happen, but just in case
			LOGGER.error("Unable to get annotation for enum: [" + enumValue.getClass() + "]. Unexpected exception: [" + e + "], message: [" + e.getMessage() + "].", e);
			return false;
		}
	}
	
}
