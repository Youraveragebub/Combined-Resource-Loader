package com.youraveragebub.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PaintModeSession {
	private String activePackId;
	private final LinkedHashSet<String> queuedTextures = new LinkedHashSet<>();

	public boolean isActive() {
		return activePackId != null;
	}

	public String activePackId() {
		return activePackId;
	}

	public void activate(String packId) {
		activePackId = packId;
	}

	public void turnOff() {
		activePackId = null;
		queuedTextures.clear();
	}

	public boolean toggleTexture(String resourceId) {
		if (!isActive()) {
			return false;
		}
		if (!queuedTextures.add(resourceId)) {
			queuedTextures.remove(resourceId);
			return false;
		}
		return true;
	}

	public boolean isQueued(String resourceId) {
		return queuedTextures.contains(resourceId);
	}

	public int queuedCount() {
		return queuedTextures.size();
	}

	public Set<String> queuedTextures() {
		return Set.copyOf(queuedTextures);
	}

	public SelectionState selectionState(PackCatalog.TextureInfo texture) {
		if (!queuedTextures.contains(texture.resourceId())) {
			return SelectionState.NONE;
		}
		return texture.isProvidedBy(activePackId) ? SelectionState.COMPATIBLE : SelectionState.INCOMPATIBLE;
	}

	public String previewPackId(PackCatalog.TextureInfo texture, String fallbackPackId) {
		return selectionState(texture) == SelectionState.COMPATIBLE ? activePackId : fallbackPackId;
	}

	public Map<String, String> applyTo(Map<String, String> baseOverrides) {
		Map<String, String> result = new LinkedHashMap<>(baseOverrides);
		if (!isActive()) {
			return result;
		}
		for (String resourceId : queuedTextures) {
			result.put(resourceId, activePackId);
		}
		return result;
	}

	public enum SelectionState {
		NONE,
		COMPATIBLE,
		INCOMPATIBLE
	}
}
