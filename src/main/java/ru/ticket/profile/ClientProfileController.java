package ru.ticket.profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.auth.repository.UserRepository;
import ru.ticket.common.ApiException;

@RestController @RequestMapping("/api/profile")
public class ClientProfileController {
  private final ClientProfileRepository profiles;private final UserRepository users;
  public ClientProfileController(ClientProfileRepository profiles,UserRepository users){this.profiles=profiles;this.users=users;}
  public record View(String id,String name,String email,String organization,String position,String phone,String avatar){}
  public record Update(@NotBlank @Size(max=100) String name,@NotNull @Size(max=200) String organization,@NotNull @Size(max=100) String position,@NotNull @Size(max=50) String phone,@NotNull @Size(max=1000000) String avatar){}
  @GetMapping public View read(@AuthenticationPrincipal UserIdPrincipal me){var user=users.findById(me.userId()).orElseThrow(()->ApiException.notFound("User"));var p=profiles.findById(me.userId()).orElseGet(ClientProfile::new);return new View(me.userId(),user.getName(),user.getEmail(),p.organization,p.position,p.phone,p.avatar);}
  @PutMapping @Transactional public View save(@AuthenticationPrincipal UserIdPrincipal me,@Valid @RequestBody Update request){
    validateAvatar(request.avatar());var user=users.findById(me.userId()).orElseThrow(()->ApiException.notFound("User"));var profile=profiles.findById(me.userId()).orElseGet(ClientProfile::new);profile.userId=me.userId();profile.organization=request.organization().trim();profile.position=request.position().trim();profile.phone=request.phone().trim();profile.avatar=request.avatar();profiles.save(profile);user.setName(request.name().trim());users.save(user);return new View(user.getId(),user.getName(),user.getEmail(),profile.organization,profile.position,profile.phone,profile.avatar);
  }
  private static void validateAvatar(String avatar){
    if(avatar.isEmpty())return;
    if(!avatar.matches("^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$"))throw ApiException.badRequest("Profile image must be an embedded PNG or JPEG");
    try{byte[] bytes=java.util.Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',')+1));try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException();var reader=readers.next();try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>1024||h>1024)throw new IllegalArgumentException();}finally{reader.dispose();}}}catch(Exception e){throw ApiException.badRequest("Profile image is invalid or exceeds 1024 pixels");}
  }
}
