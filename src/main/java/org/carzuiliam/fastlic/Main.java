package org.carzuiliam.fastlic;

import org.carzuiliam.fastlic.builder.FastLICBuilder;
import org.carzuiliam.fastlic.builder.LICBuilder;
import org.carzuiliam.fastlic.utils.FlowField;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        LICBuilder licBuilder = new LICBuilder()
                //.setInputImage("lena.jpg")
                .setFlowFieldType(FlowField.Type.SADDLE)
                .setSquareFlowFieldSize(400)
                .setDiscreteFilterSize(2048)
                .setLowPassFilterLength(10.0f)
                .setLineSquareClipMax(100000.0f)
                .setVectorComponentMinimum(0.05f);

        licBuilder.generate("lic.jpg");

        FastLICBuilder fastLICBuilder = new FastLICBuilder()
                //.setInputImage("lena.jpg")
                .setFlowFieldType(FlowField.Type.SADDLE)
                .setSquareFlowFieldSize(400)
                .setDiscreteFilterSize(2048)
                .setLowPassFilterLength(8.0f)
                .setLineSquareClipMax(100000.0f)
                .setVectorComponentMinimum(0.05f);

        fastLICBuilder.generate("fastlic.jpg");
    }
}
