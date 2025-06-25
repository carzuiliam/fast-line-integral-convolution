package org.carzuiliam.fastlic.builder;

import org.carzuiliam.fastlic.utils.FlowField;
import org.carzuiliam.fastlic.utils.Vector2D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class LICBuilder {

    private FlowField.Type flowFieldType;

    private int squareFlowFieldSize;
    private int discreteFilterSize;
    private float lowPassFilterLength;
    private float lineSquareClipMax;
    private float vectorComponentMinimum;

    private BufferedImage inputImage;

    public LICBuilder() {
        this.flowFieldType = FlowField.Type.SADDLE;
        this.squareFlowFieldSize = 400;
        this.discreteFilterSize = 2048;
        this.lowPassFilterLength = 10.0f;
        this.lineSquareClipMax = 100000.0f;
        this.vectorComponentMinimum = 0.05f;
        this.inputImage = null;
    }

    public LICBuilder setFlowFieldType(FlowField.Type _type) {
        this.flowFieldType = _type;
        return this;
    }

    public LICBuilder setSquareFlowFieldSize(int _value) {
        this.squareFlowFieldSize = _value;
        return this;
    }

    public LICBuilder setDiscreteFilterSize(int _value) {
        this.discreteFilterSize = _value;
        return this;
    }

    public LICBuilder setLowPassFilterLength(float _value) {
        this.lowPassFilterLength = _value;
        return this;
    }

    public LICBuilder setLineSquareClipMax(float _value) {
        this.lineSquareClipMax = _value;
        return this;
    }

    public LICBuilder setVectorComponentMinimum(float _value) {
        this.vectorComponentMinimum = _value;
        return this;
    }

    public LICBuilder setInputImage(String _resourceName) throws IOException {
        InputStream input = getClass().getClassLoader().getResourceAsStream(_resourceName);

        if (input == null) {
            throw new FileNotFoundException("File " + _resourceName + " not found.");
        }

        this.inputImage = ImageIO.read(input);
        return this;
    }

    public void generate(String _filename) throws IOException {
        int width;
        int height;
        byte[] inputTexture;

        if (this.inputImage != null) {
            width = this.inputImage.getWidth();
            height = this.inputImage.getHeight();
            inputTexture = readImageToByteArray(this.inputImage);
        } else {
            width = this.squareFlowFieldSize;
            height = this.squareFlowFieldSize;
            inputTexture = new byte[width * height];
            this.makeWhiteNoise(width, height, inputTexture);
            this.writeByteArrayToJPG(width, height, inputTexture, "noise.jpg");
        }

        float[] lut0 = new float[this.discreteFilterSize];
        float[] lut1 = new float[this.discreteFilterSize];
        byte[] outputImage = new byte[width * height];

        Vector2D[] vectors = FlowField.generateFlowField(width, height, this.flowFieldType);

        this.normalizeVectors(vectors);
        this.generateBoxFilterLUT(this.discreteFilterSize, lut0, lut1);
        this.flowImagingLIC(width, height, vectors, inputTexture, outputImage, lut0, lut1, this.lowPassFilterLength);
        this.writeByteArrayToJPG(width, height, outputImage, _filename);
    }

    private byte[] readImageToByteArray(BufferedImage _image) {
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

    private void makeWhiteNoise(int _width, int _height, byte[] _noise) {
        Random rand = new Random();

        for (int j = 0; j < _height; j++) {
            for (int i = 0; i < _width; i++) {
                int randomValue = rand.nextInt();

                randomValue = ((randomValue & 0xff) + ((randomValue & 0xff00) >> 8)) & 0xff;
                _noise[j * _width + i] = (byte) randomValue;
            }
        }
    }

    private void normalizeVectors(Vector2D[] _vectors) {
        for (Vector2D vec : _vectors) {
            vec.normalize();
        }
    }

    private void generateBoxFilterLUT(int _size, float[] _lut0, float[] _lut1) {
        for (int i = 0; i < _size; i++) {
            _lut0[i] = i;
            _lut1[i] = i;
        }
    }

    private void flowImagingLIC(
            int _width, int _height,
            Vector2D[] _vectors,
            byte[] _noise, byte[] _image,
            float[] _lut0, float[] _lut1, float _kernelLength
    ) {
        int advectsMax = (int) (_kernelLength * 3);
        float len2ID = (this.discreteFilterSize - 1) / _kernelLength;

        for (int j = 0; j < _height; j++) {
            for (int i = 0; i < _width; i++) {

                float[] textureAccum = new float[2];
                float[] weightAccum = new float[2];

                for (int dir = 0; dir < 2; dir++) {
                    int advects = 0;
                    float currentLength = 0.0f;

                    float x = i + 0.5f;
                    float y = j + 0.5f;
                    float[] weightLUT = (dir == 0) ? _lut0 : _lut1;

                    while (currentLength < _kernelLength && advects < advectsMax) {
                        int vecIdx = ((int) y * _width + (int) x);
                        Vector2D vec = _vectors[vecIdx];

                        float vx = vec.getX();
                        float vy = vec.getY();

                        if (vx == 0 && vy == 0) {
                            if (advects == 0) {
                                textureAccum[dir] = 0;
                                weightAccum[dir] = 1;
                            }
                            break;
                        }

                        vx = (dir == 0) ? vx : -vx;
                        vy = (dir == 0) ? vy : -vy;

                        float segmentLength = this.lineSquareClipMax;

                        if (vx < -this.vectorComponentMinimum) {
                            segmentLength = (int) x - x / vx;
                        }

                        if (vx > this.vectorComponentMinimum) {
                            segmentLength = Math.min(segmentLength, ((int) (x + 1.5f) - x) / vx);
                        }

                        if (vy < -this.vectorComponentMinimum) {
                            segmentLength = Math.min(segmentLength, ((int) y - y) / vy);
                        }

                        if (vy > this.vectorComponentMinimum) {
                            segmentLength = Math.min(segmentLength, ((int) (y + 1.5f) - y) / vy);
                        }

                        float previousLength = currentLength;
                        currentLength += segmentLength;
                        segmentLength += 0.0004f;

                        if (currentLength > _kernelLength) {
                            segmentLength = _kernelLength - previousLength;
                            currentLength = _kernelLength;
                        }

                        float x1 = x + vx * segmentLength;
                        float y1 = y + vy * segmentLength;

                        float sx = (x + x1) * 0.5f;
                        float sy = (y + y1) * 0.5f;

                        int texIdx = ((int) sy) * _width + (int) sx;
                        texIdx = Math.max(0, Math.min(texIdx, _noise.length - 1));
                        float texVal = Byte.toUnsignedInt(_noise[texIdx]);

                        float weightAcc = weightLUT[(int) (currentLength * len2ID)];
                        float sampleWeight = weightAcc - weightAccum[dir];

                        weightAccum[dir] = weightAcc;
                        textureAccum[dir] += texVal * sampleWeight;

                        advects++;
                        x = x1;
                        y = y1;

                        if (x < 0 || x >= _width || y < 0 || y >= _height) break;
                    }
                }

                float texVal = (textureAccum[0] + textureAccum[1]) / (weightAccum[0] + weightAccum[1]);
                texVal = Math.max(0.0f, Math.min(255.0f, texVal));

                _image[j * _width + i] = (byte) texVal;
            }
        }
    }

    private void writeByteArrayToJPG(int _width, int _height, byte[] _image, String _filename) throws IOException {
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
