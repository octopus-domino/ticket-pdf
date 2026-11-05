package ru.ticket.connectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController @RequestMapping("/api/connectors")
public class SourceController {
  private final SourceImportService service;

  public SourceController(SourceImportService service){
    this.service = service;
  }

  public record Request(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 30) String provider,
  @NotBlank @Size(max = 4000) String url, @NotBlank String auth, @Size(max = 1000) String username, @Size(max = 3000) String password){}
  @GetMapping public List<SourceImportService.View> list(@AuthenticationPrincipal UserIdPrincipal me){
    return service.list(me.userId());
  }
  @PostMapping public SourceImportService.View create(@AuthenticationPrincipal UserIdPrincipal me, @Valid @RequestBody Request r){
    return service.create(me.userId(), r.name(), r.provider(), r.url(), r.auth(), r.username() == null?"":r.username(), r.password() == null?"":r.password());
  }
  @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id){
    service.delete(me.userId(), id);
  }
  @PostMapping("/{id}/preview") public SourceImportService.Preview preview(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id){
    return service.preview(me.userId(), id);
  }
  @PostMapping("/{id}/import") public Map<String, Integer> apply(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id, @Valid @RequestBody SourceImportService.ImportRequest request){
    return service.apply(me.userId(), id, request);
  }
}
