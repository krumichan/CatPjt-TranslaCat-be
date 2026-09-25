package jp.co.translacat.domain.accountbook.common.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Exact monetary wire format, including zero and currencies with tiny minor units.
 */
public final class DecimalStringSerializer extends JsonSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeString(value.toPlainString());
    }
}
