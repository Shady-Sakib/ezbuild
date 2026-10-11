package ezbuild.build;

import ezbuild.Fixtures;
import ezbuild.model.ProductCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartResolverTest {

    private final PartResolver resolver = new PartResolver();

    @Test
    void resolvesCpuByModel() {
        var r = resolver.resolve(List.of("ryzen 5 7600"), Fixtures.catalogue());
        assertThat(r.locked().get(ProductCategory.CPU).getId()).isEqualTo("cpu-06");
    }

    @Test
    void intelModelWithDash() {
        var r = resolver.resolve(List.of("i5-12400f"), Fixtures.catalogue());
        assertThat(r.locked().get(ProductCategory.CPU).getId()).isEqualTo("cpu-12");
    }

    @Test
    void gpuModelDoesNotMatchTiOrOtherNumbers() {
        var r = resolver.resolve(List.of("rtx 5060"), Fixtures.catalogue());
        assertThat(r.locked().get(ProductCategory.GPU).getId()).isEqualTo("gpu-06");
    }

    @Test
    void unknownPartIsReported() {
        var r = resolver.resolve(List.of("rtx 9999"), Fixtures.catalogue());
        assertThat(r.unresolved()).containsExactly("rtx 9999");
    }
}
