package org.carzuiliam.fastlic.builder;

import org.carzuiliam.fastlic.utils.FlowField;
import org.carzuiliam.fastlic.utils.ImageUtils;
import org.carzuiliam.fastlic.utils.Vector2D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class FastLICBuilder {

    private FlowField.Type flowFieldType;

    private int squareFlowFieldSize;
    private int discreteFilterSize;
    private float lowPassFilterLength;
    private float lineSquareClipMax;
    private float vectorComponentMinimum;

    private BufferedImage inputImage;

    public FastLICBuilder() {
        this.flowFieldType = FlowField.Type.SADDLE;
        this.squareFlowFieldSize = 400;
        this.discreteFilterSize = 2048;
        this.lowPassFilterLength = 10.0f;
        this.lineSquareClipMax = 100000.0f;
        this.vectorComponentMinimum = 0.05f;
        this.inputImage = null;
    }

    public FastLICBuilder setFlowFieldType(FlowField.Type _type) {
        this.flowFieldType = _type;
        return this;
    }

    public FastLICBuilder setSquareFlowFieldSize(int _value) {
        this.squareFlowFieldSize = _value;
        return this;
    }

    public FastLICBuilder setDiscreteFilterSize(int _value) {
        this.discreteFilterSize = _value;
        return this;
    }

    public FastLICBuilder setLowPassFilterLength(float _value) {
        this.lowPassFilterLength = _value;
        return this;
    }

    public FastLICBuilder setLineSquareClipMax(float _value) {
        this.lineSquareClipMax = _value;
        return this;
    }

    public FastLICBuilder setVectorComponentMinimum(float _value) {
        this.vectorComponentMinimum = _value;
        return this;
    }

    public FastLICBuilder setInputImage(String _resourceName) throws IOException {
        if (_resourceName == null) {
            this.inputImage = null;
            return this;
        }

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
            inputTexture = ImageUtils.readImageToByteArray(this.inputImage);
        } else {
            width = this.squareFlowFieldSize;
            height = this.squareFlowFieldSize;
            inputTexture = this.makeWhiteNoise(width, height);

            ImageUtils.writeByteArrayToJPG(width, height, inputTexture, "noise.jpg");
        }

        byte[] outputImage = new byte[width * height];
        float[] lut0 = this.generateBoxFilterLUT();
        float[] lut1 = this.generateBoxFilterLUT();
        Vector2D[] vectors = FlowField.generateFlowField(width, height, this.flowFieldType);

        this.normalizeVectors(vectors);
        this.flowImagingLIC(width, height, vectors, inputTexture, outputImage, lut0, lut1);
        this.applyGaussianBlur(width, height, outputImage, 3, 1.0f);

        ImageUtils.writeByteArrayToJPG(width, height, outputImage, _filename);
    }

    private byte[] makeWhiteNoise(int _width, int _height) {
        byte[] whiteNoise = new byte[_width * _height];
        Random rand = new Random();

        for (int j = 0; j < _height; j++) {
            for (int i = 0; i < _width; i++) {
                int randomValue = rand.nextInt();

                randomValue = ((randomValue & 0xff) + ((randomValue & 0xff00) >> 8)) & 0xff;
                whiteNoise[j * _width + i] = (byte) randomValue;
            }
        }

        return whiteNoise;
    }

    private void normalizeVectors(Vector2D[] _vectors) {
        for (Vector2D vec : _vectors) {
            vec.normalize();
        }
    }

    private float[] generateBoxFilterLUT() {
        float[] lut = new float[this.discreteFilterSize];

        for (int i = 0; i < this.discreteFilterSize; i++) {
            lut[i] = i;
        }

        return lut;
    }

    private void flowImagingLIC(
            int _width, int _height,
            Vector2D[] _vectors,
            byte[] _noise, byte[] _image,
            float[] _lut0, float[] _lut1
    ) {
        int advectsMax = (int) (this.lowPassFilterLength * 3);
        float len2ID = (this.discreteFilterSize - 1) / this.lowPassFilterLength;

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

                    while (currentLength < this.lowPassFilterLength && advects < advectsMax) {
                        int vecIdx = ((int) y) * _width + (int) x;
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

                        if (currentLength > this.lowPassFilterLength) {
                            segmentLength = this.lowPassFilterLength - previousLength;
                            currentLength = this.lowPassFilterLength;
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

    private void applyGaussianBlur(int _width, int _height, byte[] _image, int _kernelRadius, float _sigma) {
        float[] kernel = createGaussianKernel(_kernelRadius, _sigma);
        byte[] temp = new byte[_width * _height];

        for (int y = 0; y < _height; y++) {
            for (int x = 0; x < _width; x++) {
                float sum = 0;
                float weightSum = 0;

                for (int k = -_kernelRadius; k <= _kernelRadius; k++) {
                    int px = Math.min(Math.max(x + k, 0), _width - 1);
                    float weight = kernel[k + _kernelRadius];

                    sum += Byte.toUnsignedInt(_image[y * _width + px]) * weight;
                    weightSum += weight;
                }

                temp[y * _width + x] = (byte) (sum / weightSum);
            }
        }

        for (int y = 0; y < _height; y++) {
            for (int x = 0; x < _width; x++) {
                float sum = 0;
                float weightSum = 0;

                for (int k = -_kernelRadius; k <= _kernelRadius; k++) {
                    int py = Math.min(Math.max(y + k, 0), _height - 1);
                    float weight = kernel[k + _kernelRadius];

                    sum += Byte.toUnsignedInt(temp[py * _width + x]) * weight;
                    weightSum += weight;
                }

                _image[y * _width + x] = (byte) (sum / weightSum);
            }
        }
    }

    private float[] createGaussianKernel(int _radius, float _sigma) {
        int size = _radius * 2 + 1;
        float[] kernel = new float[size];
        float sum = 0;
        float invTwoSigmaSq = 1.0f / (2.0f * _sigma * _sigma);

        for (int i = -_radius; i <= _radius; i++) {
            float val = (float) Math.exp(-i * i * invTwoSigmaSq);

            kernel[i + _radius] = val;
            sum += val;
        }

        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }

        return kernel;
    }
}
