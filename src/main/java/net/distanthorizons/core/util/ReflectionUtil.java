package net.distanthorizons.core.util;

import java.lang.reflect.Field;

public class ReflectionUtil
{
	
	public static String getAllFieldValuesAsString(Object obj)
	{
		StringBuilder stringBuilder = new StringBuilder();
		
		Field[] fields = obj.getClass().getDeclaredFields();
		for (Field field : fields)
		{
			String fieldName = field.getName();;
			String fieldStringValue;
			try
			{
				field.setAccessible(true);
				fieldStringValue = field.get(obj) + "";
			}
			catch (Exception e)
			{
				fieldStringValue = "ERROR:[" + e.getMessage() + "]";
			}
			
			stringBuilder.append(fieldName+" - "+fieldStringValue+"\n");
		}
		
		return stringBuilder.toString();
	}
	
}
