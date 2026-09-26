package io.github.tsndre.minijava.store.config;

import io.github.tsndre.minijava.store.util.GoDecimal;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;

/** The pieces that make Jackson read and write JSON exactly like the Go service. */
final class GoJson {

    private GoJson() {
    }

    /** Decimals as JSON strings without trailing zeros, like shopspring/decimal ("15000.5"). */
    static SimpleModule decimalModule() {
        return new SimpleModule("go-decimal")
                .addSerializer(BigDecimal.class, new DecimalSerializer())
                .addDeserializer(BigDecimal.class, new DecimalDeserializer());
    }

    /**
     * Go's encoder escapes &lt;, &gt; and &amp; (so JSON is safe inside HTML) as well as U+2028 and
     * U+2029, which JavaScript does not allow unescaped in string literals.
     */
    static final class HtmlSafeEscapes extends CharacterEscapes {

        private static final SerializableString LINE_SEPARATOR = new SerializedString("\\u2028");
        private static final SerializableString PARAGRAPH_SEPARATOR = new SerializedString("\\u2029");

        private final int[] asciiEscapes;

        HtmlSafeEscapes() {
            asciiEscapes = standardAsciiEscapesForJSON();
            asciiEscapes['<'] = ESCAPE_STANDARD;
            asciiEscapes['>'] = ESCAPE_STANDARD;
            asciiEscapes['&'] = ESCAPE_STANDARD;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return switch (ch) {
                case 0x2028 -> LINE_SEPARATOR;
                case 0x2029 -> PARAGRAPH_SEPARATOR;
                default -> null;
            };
        }
    }

    private static final class DecimalSerializer extends StdSerializer<BigDecimal> {

        DecimalSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(GoDecimal.format(value));
        }
    }

    /** Reads a decimal from a JSON string or number, as Go's decimal.UnmarshalJSON does. */
    private static final class DecimalDeserializer extends StdDeserializer<BigDecimal> {

        DecimalDeserializer() {
            super(BigDecimal.class);
        }

        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonToken token = p.currentToken();
            if (token != JsonToken.VALUE_STRING && !token.isNumeric()) {
                return (BigDecimal) ctxt.handleUnexpectedToken(BigDecimal.class, p);
            }
            String text = p.getString();
            return GoDecimal.parse(text).orElseThrow(() ->
                    ctxt.weirdStringException(text, BigDecimal.class, "not a decimal"));
        }
    }
}
