package com.youraveragebub.client;

import java.util.Locale;

public final class ResourceNames {
	private ResourceNames() {
	}

	public static String friendlyName(String resourceId) {
		int separator = resourceId.indexOf(':');
		String path = separator >= 0 ? resourceId.substring(separator + 1) : resourceId;
		int textures = path.indexOf("textures/");
		if (textures >= 0) {
			path = path.substring(textures + "textures/".length());
		}
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		if (path.toLowerCase(Locale.ROOT).endsWith(".png")) {
			path = path.substring(0, path.length() - 4);
		}

		StringBuilder result = new StringBuilder(path.length());
		boolean capitalize = true;
		for (char character : path.toCharArray()) {
			if (character == '_' || character == '-' || character == '.') {
				result.append(' ');
				capitalize = true;
			} else {
				result.append(capitalize ? Character.toUpperCase(character) : character);
				capitalize = false;
			}
		}
		return result.toString().trim();
	}
}
