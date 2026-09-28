package io.github.tsndre.minijava.store.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The forms Go's uuid.Parse accepts and rejects. */
class UuidsTest {

    private static final UUID ID = UUID.fromString("0b9f2c4e-a1b2-4c3d-8e9f-001122334455");

    @ParameterizedTest
    @ValueSource(strings = {
            "0b9f2c4e-a1b2-4c3d-8e9f-001122334455",
            "0B9F2C4E-A1B2-4C3D-8E9F-001122334455",
            "{0b9f2c4e-a1b2-4c3d-8e9f-001122334455}",
            "(0b9f2c4e-a1b2-4c3d-8e9f-001122334455)",
            "urn:uuid:0b9f2c4e-a1b2-4c3d-8e9f-001122334455",
            "URN:UUID:0b9f2c4e-a1b2-4c3d-8e9f-001122334455",
            "0b9f2c4ea1b24c3d8e9f001122334455",
    })
    void accepted(String value) {
        assertThat(Uuids.parse(value)).contains(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "1-1-1-1-1",
            "not-a-uuid",
            "0b9f2c4e-a1b2-4c3d-8e9f-00112233445",
            "0b9f2c4e_a1b2_4c3d_8e9f_001122334455",
            "0b9f2c4e-a1b2-4c3d-8e9f-00112233445g",
            "urn:uuix:0b9f2c4e-a1b2-4c3d-8e9f-001122334455",
            "0b9f2c4e-a1b2-4c3d-8e9f-٠٠1122334455",
    })
    void rejected(String value) {
        assertThat(Uuids.parse(value)).isEmpty();
    }
}
