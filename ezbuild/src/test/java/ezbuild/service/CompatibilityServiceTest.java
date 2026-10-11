package ezbuild.service;

import ezbuild.dto.CompatReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ezbuild.Fixtures.find;
import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityServiceTest {

    private CompatibilityService compat;

    @BeforeEach
    void setUp() {
        compat = new CompatibilityService();
    }

    @Test
    @DisplayName("A matching AM5 build is compatible")
    void validBuild() {
        CompatReport r = compat.check(List.of(find("cpu-06"), find("mb-05"), find("ram-05"), find("gpu-06"),
                find("ssd-02"), find("psu-03"), find("case-05")), true);
        assertThat(r.ok()).isTrue();
        assertThat(r.errors()).isEmpty();
        assertThat(r.estimatedWattage()).isEqualTo(88 + 145 + 75); // 65 W TDP x 1.35 peak when there's no pmax spec
    }

    @Test
    void socketMismatch() {
        CompatReport r = compat.check(List.of(find("cpu-06"), find("mb-03")), false);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("socket"));
    }

    @Test
    void memoryTypeMismatch() {
        CompatReport r = compat.check(List.of(find("mb-05"), find("ram-02")), false);
        assertThat(r.errors()).anyMatch(e -> e.contains("DDR4"));
    }

    @Test
    void cpuWithoutGraphicsNeedsGpu() {
        CompatReport r = compat.check(List.of(find("cpu-02"), find("mb-03"), find("ram-02")), false);
        assertThat(r.errors()).anyMatch(e -> e.contains("graphics card"));
    }

    @Test
    void gpuTooLongForCase() {
        CompatReport r = compat.check(List.of(find("gpu-12"), find("case-01")), false);
        assertThat(r.errors()).anyMatch(e -> e.contains("longer than the case"));
    }

    @Test
    void formFactor() {
        assertThat(CompatibilityService.caseFits("mATX", "ATX")).isFalse();
        assertThat(CompatibilityService.caseFits("ATX", "mATX")).isTrue();
    }

    @Test
    void weakPsuIsAnError() {
        CompatReport r = compat.check(List.of(find("cpu-14"), find("gpu-12"), find("psu-01")), false);
        assertThat(r.errors()).anyMatch(e -> e.contains("too weak"));
    }

    @Test
    void recommendedPsuRoundsUpToStandardSize() {
        int w = compat.recommendedPsu(find("cpu-06"), find("gpu-06"));
        assertThat(w).isEqualTo(550); // (88 + 145 + 75) x 1.35 = 416 -> GPU minimum 550
    }

    @Test
    void missingPartsWhenCompleteRequired() {
        CompatReport r = compat.check(List.of(find("cpu-06")), true);
        assertThat(r.errors()).anyMatch(e -> e.startsWith("Missing"));
    }

    @Test
    void lga1700SupportsBothMemoryTypes() {
        assertThat(CompatibilityService.cpuSupports(find("cpu-12"), "DDR4")).isTrue();
        assertThat(CompatibilityService.cpuSupports(find("cpu-12"), "DDR5")).isTrue();
        assertThat(CompatibilityService.cpuSupports(find("cpu-06"), "DDR4")).isFalse();
    }

    @Test
    void i7WithRx9070GreNeeds750WattsNot650() {
        var cpu = ezbuild.Fixtures.p("cpu-15", ezbuild.model.ProductCategory.CPU, "Intel", "Intel Core i7-14700K", 50500, 95,
                "socket", "LGA1700", "tdp", 125, "pmax", 253, "igpu", true, "ram", "DDR4/DDR5", "gaming", 91);
        var gpu = ezbuild.Fixtures.p("gpu-11", ezbuild.model.ProductCategory.GPU, "PowerColor", "Radeon RX 9070 GRE 12GB", 88999, 80,
                "vram", 12, "tdp", 220, "len", 300, "psu", 650, "gaming", 80);
        var psu650 = ezbuild.Fixtures.p("psu-03", ezbuild.model.ProductCategory.PSU, "Corsair", "CX650", 8500, 0, "watt", 650);
        var psu750 = ezbuild.Fixtures.p("psu-04", ezbuild.model.ProductCategory.PSU, "Gigabyte", "P750GM", 12500, 0, "watt", 750, "eff", "80+ Gold");
        assertThat(compat.recommendedPsu(cpu, gpu)).isEqualTo(750); // (253 + 220 + 75) x 1.35 = 740
        assertThat(compat.check(List.of(cpu, gpu, psu650), false).errors()).anyMatch(e -> e.contains("too weak"));
        assertThat(compat.check(List.of(cpu, gpu, psu750), false).errors()).isEmpty();
    }

    @Test
    void severeBottleneckIsFlagged() {
        var cpu = ezbuild.Fixtures.p("cpu-11", ezbuild.model.ProductCategory.CPU, "Intel", "Intel Core i3-12100F", 9500, 41,
                "socket", "LGA1700", "tdp", 58, "gaming", 55);
        var gpu = ezbuild.Fixtures.p("gpu-11", ezbuild.model.ProductCategory.GPU, "PowerColor", "Radeon RX 9070 GRE 12GB", 88999, 80,
                "tdp", 220, "gaming", 80);
        assertThat(CompatibilityService.bottleneckRatio(cpu, gpu)).isLessThan(0.75);
        assertThat(CompatibilityService.bottleneck(cpu, gpu)).startsWith("Bottleneck");
    }
}
