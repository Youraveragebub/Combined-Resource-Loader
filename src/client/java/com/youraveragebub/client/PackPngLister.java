package com.youraveragebub.client;

import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public interface PackPngLister {
	void combinedResourceLoader$listPngs(Consumer<Identifier> output);
}
