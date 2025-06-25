package org.carzuiliam.fastlic.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtils {

    public static byte[] readImageToByteArray(BufferedImage _image) {
        int width = _image.getWidth();
        int height = _image.getHeight();
        byte[] data = new byte[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = _image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xff;

                data[y * width + x] = (byte) gray;
            }
        }

        return data;
    }

    public static void writeByteArrayToJPG(int _width, int _height, byte[] _image, String _filename) throws IOException {
        String outputDir = "target/output";
        new File(outputDir).mkdirs();

        File outputFile = new File(outputDir, _filename);
        BufferedImage img = new BufferedImage(_width, _height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < _height; y++) {
            for (int x = 0; x < _width; x++) {
                int value = Byte.toUnsignedInt(_image[y * _width + x]);
                int rgb = (value << 16) | (value << 8) | value;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "jpg", outputFile);
    }
}
