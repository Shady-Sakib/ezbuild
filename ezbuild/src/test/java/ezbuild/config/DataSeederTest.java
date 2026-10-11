package ezbuild.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Seed parsing needs no database (full @SpringBootTest runs need MongoDB). */
class DataSeederTest {

    @Test
    void csvParserHandlesQuotedCommas() {
        assertThat(DataSeeder.parseCsvLine("cpu-01,CPU,AMD,\"Ryzen 5, boxed\",10500"))
                .containsExactly("cpu-01", "CPU", "AMD", "Ryzen 5, boxed", "10500");
    }

    @Test
    void specsAreTyped() {
        Map<String, Object> specs = DataSeeder.parseSpecs("socket=AM5;tdp=65;igpu=true;ram=DDR4/DDR5");
        assertThat(specs.get("socket")).isEqualTo("AM5");
        assertThat(specs.get("tdp")).isEqualTo(65);
        assertThat(specs.get("igpu")).isEqualTo(true);
        assertThat(specs.get("ram")).isEqualTo("DDR4/DDR5");
    }
}
