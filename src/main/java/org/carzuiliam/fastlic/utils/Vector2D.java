package org.carzuiliam.fastlic.utils;

public class Vector2D {
    private float x;
    private float y;

    public Vector2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public void normalize() {
        float mag = (float) Math.sqrt(this.x * this.x + this.y * this.y);

        if (mag != 0) {
            this.x /= mag;
            this.y /= mag;
        }
    }
}

