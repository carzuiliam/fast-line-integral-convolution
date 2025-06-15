package org.carzuiliam.fastlic.utils;

public class FlowField {

    public enum Type {
        SADDLE,
        CENTER,
        SOURCE,
        SINK,
        SPIRAL_SOURCE,
        SPIRAL_SINK
    }

    public static Vector2D[] generateFlowField(int width, int height, Type type) {
        Vector2D[] flowField = new Vector2D[width * height];

        float cx = width / 2.0f;
        float cy = height / 2.0f;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                float x = i - cx;
                float y = j - cy;

                Vector2D vector = switch (type) {
                    case SADDLE -> saddle(x, y);
                    case CENTER -> center(x, y);
                    case SOURCE -> source(x, y);
                    case SINK -> sink(x, y);
                    case SPIRAL_SOURCE -> spiralSource(x, y);
                    case SPIRAL_SINK -> spiralSink(x, y);
                };

                flowField[j * width + i] = vector;
            }
        }

        return flowField;
    }

    private static Vector2D saddle(float x, float y) {
        return new Vector2D(x, -y);
    }

    private static Vector2D center(float x, float y) {
        return new Vector2D(-y, x);
    }

    private static Vector2D source(float x, float y) {
        return new Vector2D(x, y);
    }

    private static Vector2D sink(float x, float y) {
        return new Vector2D(-x, -y);
    }

    private static Vector2D spiralSource(float x, float y) {
        return new Vector2D(x - y, x + y);
    }

    private static Vector2D spiralSink(float x, float y) {
        return new Vector2D(-x - y, y - x);
    }
}
