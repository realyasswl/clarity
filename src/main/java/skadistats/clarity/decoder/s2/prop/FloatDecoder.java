package skadistats.clarity.decoder.s2.prop;

import skadistats.clarity.decoder.BitStream;
import skadistats.clarity.decoder.s2.FieldDecoder;
import skadistats.clarity.model.s2.Field;

public class FloatDecoder implements FieldDecoder<Float> {

    @Override
    public Float decode(BitStream bs, Field f) {
        return (float)(f.getBitCount() != null ? bs.readUInt(f.getBitCount()) : bs.readVarUInt());
        //return (float)bs.readVarSInt();
    }
}