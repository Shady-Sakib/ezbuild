package ezbuild.api;

import ezbuild.controller.CurrentUser;
import ezbuild.dto.BuildPlan;
import ezbuild.dto.CartView;
import ezbuild.model.Build;
import ezbuild.model.BuildSource;
import ezbuild.model.User;
import ezbuild.service.BuildService;
import ezbuild.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/builds")
@RequiredArgsConstructor
public class BuildApiController {

    private final BuildService buildService;
    private final CartService cartService;
    private final CurrentUser currentUser;

    public record PartList(@NotEmpty @Size(max = 12) List<String> productIds) { }

    public record SaveBuild(@Size(max = 60) String name, BuildSource source, @NotEmpty @Size(max = 12) List<String> productIds,
                            @Size(max = 300) String note) { }

    /** Public live compatibility check used by the builder page. */
    @PostMapping("/validate")
    public BuildPlan validate(@Valid @RequestBody PartList body) {
        return buildService.validate(body.productIds());
    }

    @GetMapping
    public List<Build> list(Authentication auth) {
        return buildService.list(currentUser.require(auth).getId());
    }

    @PostMapping
    public ResponseEntity<Build> save(@Valid @RequestBody SaveBuild body, Authentication auth) {
        User me = currentUser.require(auth);
        Build b = buildService.save(me.getId(), body.name(), body.source() == null ? BuildSource.MANUAL : body.source(),
                body.productIds(), body.note());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(b.getId()).toUri();
        return ResponseEntity.created(location).body(b);
    }

    @GetMapping("/{id}")
    public Build get(@PathVariable String id, Authentication auth) {
        return buildService.getOwned(id, currentUser.require(auth).getId());
    }

    @GetMapping("/shared/{code}")
    public Build shared(@PathVariable String code) {
        Build b = buildService.getShared(code);
        b.setUserId(null);
        return b;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        buildService.delete(id, currentUser.require(auth).getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/to-cart")
    public CartView toCart(@PathVariable String id, Authentication auth) {
        User me = currentUser.require(auth);
        return cartService.addAll(me.getId(), buildService.productIds(buildService.getOwned(id, me.getId())));
    }
}
