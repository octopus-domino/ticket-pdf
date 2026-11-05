package ru.ticket.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.auth.repository.UserRepository;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;

/** Own-profile operations: identity and email come from the authenticated account. */
@RestController
@RequestMapping("/api/profile")
public class ClientProfileController {
  private final ClientProfileRepository profiles;
  private final UserRepository users;

  public ClientProfileController(ClientProfileRepository profiles, UserRepository users) {
    this.profiles = profiles;
    this.users = users;
  }

  public record View(
      String id, String name, String email, String organization,
      String position, String phone, String avatar) {}

  // Email changes require a separate verified flow and are not profile edits.
  public record Update(
      @NotBlank @Size(max = 100) String name,
      @NotNull @Size(max = 200) String organization,
      @NotNull @Size(max = 100) String position,
      @NotNull @Size(max = 50) String phone,
      @NotNull @Size(max = 1_000_000) String avatar) {}

  @GetMapping
  public View read(@AuthenticationPrincipal UserIdPrincipal principal) {
    var user = users.findById(principal.userId())
        .orElseThrow(() -> ApiException.notFound("User"));
    var profile = profiles.findById(principal.userId()).orElseGet(ClientProfile::new);
    return new View(
        principal.userId(), user.getName(), user.getEmail(), profile.organization,
        profile.position, profile.phone, profile.avatar);
  }

  @PutMapping
  @Transactional
  public View save(
      @AuthenticationPrincipal UserIdPrincipal principal,
      @Valid @RequestBody Update request) {
    ProfileImageValidator.validate(request.avatar());
    var user = users.findById(principal.userId())
        .orElseThrow(() -> ApiException.notFound("User"));
    var profile = profiles.findById(principal.userId()).orElseGet(ClientProfile::new);
    profile.userId = principal.userId();
    profile.organization = request.organization().trim();
    profile.position = request.position().trim();
    profile.phone = request.phone().trim();
    profile.avatar = request.avatar();
    profiles.save(profile);

    // Account name and profile fields must commit together.
    user.setName(request.name().trim());
    users.save(user);
    return new View(
        user.getId(), user.getName(), user.getEmail(), profile.organization,
        profile.position, profile.phone, profile.avatar);
  }
}
