package ezbuild.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingTest {

    @Test
    void sha256IsStableAndHex() {
        assertThat(Hashing.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void codesAvoidLookAlikeCharacters() {
        String code = Hashing.code(200);
        assertThat(code).hasSize(200).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void constantTimeEquals() {
        assertThat(Hashing.constantTimeEquals("a", "a")).isTrue();
        assertThat(Hashing.constantTimeEquals("a", "b")).isFalse();
        assertThat(Hashing.constantTimeEquals(null, "b")).isFalse();
    }
}
