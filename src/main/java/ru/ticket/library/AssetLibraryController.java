package ru.ticket.library;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController @RequestMapping("/api/libraries")
public class AssetLibraryController {
  private final AssetLibraryService service;

  public AssetLibraryController(AssetLibraryService service){
    this.service = service;
  }

  public record Create(String name){}

  public record Save(long revision, JsonNode items){}

  public record Member(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email, @jakarta.validation.constraints.NotBlank String role){}
  @GetMapping public List<Map<String, Object>> list(@AuthenticationPrincipal UserIdPrincipal me){
    return service.list(me.userId());
  }
  @PostMapping public Map<String, Object> create(@AuthenticationPrincipal UserIdPrincipal me, @RequestBody Create body){
    return service.create(me.userId(), body.name());
  }
  @GetMapping("/{id}") public Map<String, Object> read(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id){
    return service.read(me.userId(), id);
  }
  @PutMapping("/{id}") public Map<String, Object> save(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id, @RequestBody Save body){
    return service.save(me.userId(), id, body.revision(), body.items());
  }
  @PostMapping("/{id}/members") public void member(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id, @jakarta.validation.Valid @RequestBody Member body){
    service.member(me.userId(), id, body.email(), body.role());
  }
}
