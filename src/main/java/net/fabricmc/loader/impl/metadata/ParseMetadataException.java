package net.fabricmc.loader.impl.metadata;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.impl.lib.gson.JsonReader;

@SuppressWarnings("serial")
public class ParseMetadataException extends Exception {
	private List<String> modPaths;

	public ParseMetadataException(String message) {
		super(message);
	}

	public ParseMetadataException(String message, JsonReader reader) {
		this(message + " Error was located at: " + reader.locationString());
	}

	public ParseMetadataException(String message, Throwable throwable) {
		super(message, throwable);
	}

	public ParseMetadataException(Throwable t) {
		super(t);
	}

	public void setModPaths(String modPath, List<String> modParentPaths) {
		modPaths = new ArrayList<>(modParentPaths);
		modPaths.add(modPath);
	}

	@Override
	public String getMessage() {
		String ret = "Error reading fabric.mod.json file for mod at ";

		if (modPaths == null) {
			ret += "unknown location";
		} else {
			ret += String.join(" -> ", modPaths);
		}

		String msg = super.getMessage();

		if (msg != null) {
			ret += ": "+msg;
		}

		return ret;
	}

	public static class MissingField extends ParseMetadataException {
		public MissingField(String field) {
			super(String.format("Missing required field \"%s\".", field));
		}
	}
}
