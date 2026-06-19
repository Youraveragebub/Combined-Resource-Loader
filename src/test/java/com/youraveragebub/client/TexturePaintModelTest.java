package com.youraveragebub.client;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TexturePaintModelTest {
	@Test
	void penChangesOnePixel() {
		TexturePaintModel model = new TexturePaintModel(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));

		model.pen(2, 1, 0xFF112233);

		assertEquals(0xFF112233, model.pixel(2, 1));
		assertEquals(0x00000000, model.pixel(1, 1));
	}

	@Test
	void brushColorsCircularArea() {
		TexturePaintModel model = new TexturePaintModel(new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB));

		model.brush(2, 2, 3, 0xFFAA5500);

		assertEquals(0xFFAA5500, model.pixel(2, 2));
		assertEquals(0xFFAA5500, model.pixel(1, 2));
		assertEquals(0xFFAA5500, model.pixel(3, 2));
		assertEquals(0x00000000, model.pixel(0, 0));
	}

	@Test
	void eraserMakesPixelsTransparent() {
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(1, 1, 0xFFFFFFFF);
		TexturePaintModel model = new TexturePaintModel(image);

		model.erase(1, 1, 1);

		assertEquals(0x00000000, model.pixel(1, 1));
	}

	@Test
	void fillColorsConnectedAreaOnly() {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			image.setRGB(1, y, 0xFF000000);
		}
		TexturePaintModel model = new TexturePaintModel(image);

		model.fill(0, 1, 0xFF44AAFF);

		assertEquals(0xFF44AAFF, model.pixel(0, 0));
		assertEquals(0xFF44AAFF, model.pixel(0, 2));
		assertEquals(0xFF000000, model.pixel(1, 1));
		assertEquals(0x00000000, model.pixel(2, 1));
	}

	@Test
	void convertsRgbAndHexValues() {
		int color = TexturePaintModel.opaqueRgb(300, -4, 17);

		assertEquals(0xFFFF0011, color);
		assertEquals("#FF0011", TexturePaintModel.toHex(color));
		assertEquals(0xFF3366AA, TexturePaintModel.fromHex("#3366aa", 0));
	}
}
