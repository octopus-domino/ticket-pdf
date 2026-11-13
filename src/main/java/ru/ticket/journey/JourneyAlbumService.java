package ru.ticket.journey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.clientapi.SecretCipher;

/** Append under a row lock, reorder with a revision: concurrent guests never overwrite each other's photos. */
@Service
public class JourneyAlbumService {
  record Picture(String id, String imageId, String caption, String contributor, String editHash) {}
  public record Photo(String id, String caption, String contributor) {}
  public record State(long revision, List<Photo> photos, boolean canManage) {}
  public record Added(State album, String photoId, String editKey) {}
  public record Input(String image, String caption, String contributor) {}
  public record Edit(String caption, String contributor) {}
  public record Order(long revision, List<String> ids) {}
  private final JourneyAlbumRepository albums;
  private final JourneyService documents;
  private final JourneyMediaService media;
  private final SecretCipher cipher;
  private final ObjectMapper mapper;
  public JourneyAlbumService(JourneyAlbumRepository albums, JourneyService documents, JourneyMediaService media, SecretCipher cipher, ObjectMapper mapper) {
    this.albums = albums; this.documents = documents; this.media = media; this.cipher = cipher; this.mapper = mapper;
  }
  @Transactional(readOnly = true)
  public State get(String id, String owner, String managementKey) {
    document(id); var row = albums.findById(id).orElse(null);
    boolean manager = manager(id, owner, managementKey);
    return row == null ? new State(0, List.of(), manager) : state(row, manager);
  }
  @Transactional
  public String invitation(String id, String owner, String managementKey, boolean rotate) {
    document(id); requireManager(id, owner, managementKey);
    // Locking the publication serializes lazy album initialization too.
    var row = albums.lockById(id).orElse(null);
    if (row == null) { row = new JourneyAlbum(); row.id = id; rotate = true; }
    if (rotate) { String token = token(); row.invitationHash = SecretCipher.hash(token); row.invitationCipher = cipher.encrypt(token); albums.saveAndFlush(row); }
    return cipher.decrypt(row.invitationCipher);
  }
  @Transactional
  public Added add(String id, String invitation, Input input) {
    document(id); validateText(input == null ? null : input.caption(), input == null ? null : input.contributor());
    var row = locked(id);
    if (!PublicationAccess.keyMatches(row.invitationHash, invitation)) throw ApiException.forbidden("Invalid album contribution link");
    var pictures = pictures(row); if (pictures.size() >= 40) throw ApiException.badRequest("An album can contain up to 40 photos");
    if (input.image() == null || input.image().length() > 420000) throw ApiException.badRequest("Album photo is too large");
    var asset = media.save(input.image());
    String editKey = token(), photoId = UUID.randomUUID().toString().replace("-", "");
    pictures.add(new Picture(photoId, asset.id(), input.caption().strip(), input.contributor().strip(), SecretCipher.hash(editKey)));
    write(row, pictures); return new Added(state(row, false), photoId, editKey);
  }
  @Transactional
  public State edit(String id, String photoId, String owner, String managementKey, String editKey, Edit input, boolean remove) {
    document(id); var row = locked(id); var pictures = pictures(row);
    var photo = pictures.stream().filter(item -> item.id().equals(photoId)).findFirst().orElseThrow(() -> ApiException.notFound("Photo"));
    boolean manager = manager(id, owner, managementKey);
    if (!manager && !PublicationAccess.keyMatches(photo.editHash(), editKey)) throw ApiException.forbidden("Only the contributor or organiser can change this photo");
    if (remove) pictures.remove(photo);
    else {
      validateText(input == null ? null : input.caption(), input == null ? null : input.contributor());
      pictures.set(pictures.indexOf(photo), new Picture(photo.id(), photo.imageId(), input.caption().strip(), input.contributor().strip(), photo.editHash()));
    }
    write(row, pictures); return state(row, manager);
  }
  @Transactional
  public State reorder(String id, String owner, String managementKey, Order order) {
    document(id); requireManager(id, owner, managementKey); var row = locked(id); var pictures = pictures(row);
    if (order == null || !Objects.equals(row.version, order.revision())) throw ApiException.conflict("The album has changed. Refresh it before reordering");
    var byId = new LinkedHashMap<String, Picture>(); pictures.forEach(photo -> byId.put(photo.id(), photo));
    if (order.ids() == null || order.ids().size() != pictures.size() || !new HashSet<>(order.ids()).equals(byId.keySet()))
      throw ApiException.badRequest("Album order must contain every photo once");
    write(row, order.ids().stream().map(byId::get).toList()); return state(row, true);
  }
  @Transactional(readOnly = true)
  public JourneyMedia photo(String id, String photoId) {
    document(id); var row = albums.findById(id).orElseThrow(() -> ApiException.notFound("Album"));
    return media.get(pictures(row).stream().filter(photo -> photo.id().equals(photoId)).findFirst().orElseThrow(() -> ApiException.notFound("Photo")).imageId());
  }
  @Transactional(readOnly = true)
  public List<Map<String, Object>> model(String id) {
    var row = albums.findById(id).orElse(null); if (row == null) return List.of();
    return pictures(row).stream().map(photo -> {
      var image = media.get(photo.imageId());
      return Map.<String, Object>of("image", "data:" + image.contentType + ";base64," + Base64.getEncoder().encodeToString(image.content),
          "caption", photo.caption(), "contributor", photo.contributor(), "font", JourneyAlbumTemplate.handwriting(photo.caption() + photo.contributor()));
    }).toList();
  }
  @Transactional
  public void copyModel(String id, Object input) {
    document(id);
    if (!(input instanceof List<?> rows) || rows.size() > 40) throw ApiException.badRequest("Invalid album photos");
    var row = new JourneyAlbum(); row.id = id; String invitation = token();
    row.invitationHash = SecretCipher.hash(invitation); row.invitationCipher = cipher.encrypt(invitation);
    var pictures = new ArrayList<Picture>();
    for (var value : rows) {
      if (!(value instanceof Map<?, ?> fields) || !(fields.get("image") instanceof String image)
          || !(fields.get("caption") instanceof String caption) || !(fields.get("contributor") instanceof String contributor))
        throw ApiException.badRequest("Invalid album photo");
      validateText(caption, contributor);
      if (image.length() > 420000) throw ApiException.badRequest("Album photo is too large");
      var asset = media.save(image);
      pictures.add(new Picture(UUID.randomUUID().toString().replace("-", ""), asset.id(), caption, contributor, SecretCipher.hash(token())));
    }
    write(row, pictures);
  }
  private JourneyService.View document(String id) {
    var view = documents.get(id); if (view.kind() != JourneyService.Kind.ALBUM) throw ApiException.badRequest("This document is not an album"); return view;
  }
  private boolean manager(String id, String owner, String key) {
    if (key != null && !key.isBlank()) { documents.manage(id, key); return true; }
    if (owner == null) return false;
    try { documents.manageOwned(id, owner); return true; } catch (ApiException ex) { return false; }
  }
  private void requireManager(String id, String owner, String key) {
    if (!manager(id, owner, key)) throw ApiException.forbidden("Only the organiser can manage this album");
    documents.lockForAlbum(id);
  }
  private JourneyAlbum locked(String id) { return albums.lockById(id).orElseThrow(() -> ApiException.notFound("Album")); }
  private List<Picture> pictures(JourneyAlbum row) {
    try { return new ArrayList<>(mapper.readValue(row.pictures, new TypeReference<List<Picture>>() {})); }
    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
  }
  private void write(JourneyAlbum row, List<Picture> pictures) {
    try { row.pictures = mapper.writeValueAsString(pictures); albums.saveAndFlush(row); }
    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
  }
  private State state(JourneyAlbum row, boolean manager) { return new State(row.version == null ? 0 : row.version,
      pictures(row).stream().map(photo -> new Photo(photo.id(), photo.caption(), photo.contributor())).toList(), manager); }
  private static String token() { return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""); }
  private static void validateText(String caption, String name) {
    if (caption == null || caption.length() > 300 || name == null || name.length() > 100) throw ApiException.badRequest("Check the photo caption and contributor name");
  }
}
