package com.youraveragebub.client;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TexturePaintModel {
	public static final int TRANSPARENT = 0x00000000;

	private final BufferedImage image;

	public TexturePaintModel(BufferedImage source) {
		this.image = copy(source);
	}

	public BufferedImage image() {
		return image;
	}

	public int width() {
		return image.getWidth();
	}

	public int height() {
		return image.getHeight();
	}

	public int pixel(int x, int y) {
		return image.getRGB(x, y);
	}

	public void pen(int x, int y, int argb) {
		if (!contains(x, y)) {
			return;
		}
		image.setRGB(x, y, argb);
	}

	public void brush(int centerX, int centerY, int diameter, int argb) {
		paintCircle(centerX, centerY, diameter, argb);
	}

	public void erase(int centerX, int centerY, int diameter) {
		paintCircle(centerX, centerY, diameter, TRANSPARENT);
	}

	public void fill(int startX, int startY, int argb) {
		if (!contains(startX, startY)) {
			return;
		}
		int targetColor = image.getRGB(startX, startY);
		if (targetColor == argb) {
			return;
		}
		boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] { startX, startY });
		visited[startY * image.getWidth() + startX] = true;
		while (!queue.isEmpty()) {
			int[] pixel = queue.removeFirst();
			int x = pixel[0];
			int y = pixel[1];
			if (image.getRGB(x, y) != targetColor) {
				continue;
			}
			image.setRGB(x, y, argb);
			addFillNeighbor(queue, visited, x + 1, y);
			addFillNeighbor(queue, visited, x - 1, y);
			addFillNeighbor(queue, visited, x, y + 1);
			addFillNeighbor(queue, visited, x, y - 1);
		}
	}

	private void paintCircle(int centerX, int centerY, int diameter, int argb) {
		int clampedDiameter = Math.max(1, diameter);
		double radius = clampedDiameter / 2.0D;
		double radiusSquared = radius * radius;
		int minimumX = (int) Math.floor(centerX - radius + 0.5D);
		int maximumX = (int) Math.ceil(centerX + radius - 0.5D);
		int minimumY = (int) Math.floor(centerY - radius + 0.5D);
		int maximumY = (int) Math.ceil(centerY + radius - 0.5D);
		for (int y = minimumY; y <= maximumY; y++) {
			for (int x = minimumX; x <= maximumX; x++) {
				double dx = x - centerX;
				double dy = y - centerY;
				if (dx * dx + dy * dy <= radiusSquared) {
					pen(x, y, argb);
				}
			}
		}
	}

	private boolean contains(int x, int y) {
		return x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight();
	}

	private void addFillNeighbor(ArrayDeque<int[]> queue, boolean[] visited, int x, int y) {
		if (!contains(x, y)) {
			return;
		}
		int index = y * image.getWidth() + x;
		if (!visited[index]) {
			visited[index] = true;
			queue.add(new int[] { x, y });
		}
	}

	public static BufferedImage copy(BufferedImage source) {
		BufferedImage copied = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				copied.setRGB(x, y, source.getRGB(x, y));
			}
		}
		return copied;
	}

	public static int opaqueRgb(int red, int green, int blue) {
		return 0xFF000000 | (clampColor(red) << 16) | (clampColor(green) << 8) | clampColor(blue);
	}

	public static String toHex(int argb) {
		return String.format(Locale.ROOT, "#%02X%02X%02X", red(argb), green(argb), blue(argb));
	}

	public static int fromHex(String value, int fallbackArgb) {
		if (value == null) {
			return fallbackArgb;
		}
		String normalized = value.trim();
		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		}
		if (normalized.length() != 6) {
			return fallbackArgb;
		}
		try {
			int rgb = Integer.parseInt(normalized, 16);
			return 0xFF000000 | rgb;
		} catch (NumberFormatException ignored) {
			return fallbackArgb;
		}
	}

	public static int colorWheelArgb(double centerX, double centerY, double x, double y, double radius, int fallbackArgb) {
		double dx = x - centerX;
		double dy = y - centerY;
		double distance = Math.sqrt(dx * dx + dy * dy);
		if (distance > radius || radius <= 0.0D) {
			return fallbackArgb;
		}
		float hue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2.0D) + 1.0D) % 1.0D);
		float saturation = (float) Math.min(1.0D, distance / radius);
		return 0xFF000000 | (Color.HSBtoRGB(hue, saturation, 1.0F) & 0x00FFFFFF);
	}

	public static List<Integer> defaultSavedColors() {
		return new ArrayList<>(List.of(
				opaqueRgb(255, 0, 0),
				opaqueRgb(0, 170, 0),
				opaqueRgb(0, 85, 255),
				opaqueRgb(255, 255, 0),
				opaqueRgb(255, 215, 0),
				opaqueRgb(132, 82, 48),
				opaqueRgb(94, 158, 73),
				opaqueRgb(125, 125, 125)
		));
	}

	public static int red(int argb) {
		return (argb >>> 16) & 0xFF;
	}

	public static int green(int argb) {
		return (argb >>> 8) & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int clampColor(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
