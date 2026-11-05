package ru.ticket.library;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.auth.repository.UserRepository;
import ru.ticket.common.ApiException;

/** Explicit, versioned library exchange; no automatic upload of browser assets. */
@Service
public class AssetLibraryService {
  private final AssetLibraryRepository repo; private final UserRepository users; private final ObjectMapper mapper;
  public AssetLibraryService(AssetLibraryRepository repo,UserRepository users,ObjectMapper mapper){this.repo=repo;this.users=users;this.mapper=mapper;}
  private JsonNode json(String value){try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid library storage",e);}}
  private String role(AssetLibrary l,String user){return l.userId.equals(user)?"owner":json(l.members).path(user).asText("");}
  private AssetLibrary owned(String user,String id,boolean lock){var l=(lock?repo.locked(id):repo.findById(id)).orElseThrow(()->ApiException.notFound("Library"));if(role(l,user).isEmpty())throw ApiException.notFound("Library");return l;}
  private Map<String,Object> view(AssetLibrary l,String user){return Map.of("id",l.id,"name",l.name,"revision",l.revision,"role",role(l,user),"items",json(l.payload));}
  public List<Map<String,Object>> list(String user){return repo.findAllProjectedBy().stream().filter(l->l.getUserId().equals(user)||json(l.getMembers()).has(user)).map(l->Map.<String,Object>of("id",l.getId(),"name",l.getName(),"revision",l.getRevision(),"role",l.getUserId().equals(user)?"owner":json(l.getMembers()).path(user).asText())).toList();}
  public Map<String,Object> read(String user,String id){return view(owned(user,id,false),user);}
  @Transactional public Map<String,Object> create(String user,String name){if(name==null||name.isBlank()||name.length()>200)throw ApiException.badRequest("Invalid library name");if(repo.findByUserId(user).size()>=10)throw ApiException.badRequest("Maximum 10 libraries");var l=new AssetLibrary();l.userId=user;l.name=name.trim();return view(repo.save(l),user);}
  private void safe(JsonNode node,int depth,boolean symbol,String path){
    if(depth>30)throw ApiException.badRequest("Library nesting is too deep");
    if(node.isObject()){var fields=node.fields();while(fields.hasNext()){var field=fields.next();String key=field.getKey();
      if(Set.of("__proto__","prototype","constructor").contains(key)&&!(key.equals("prototype")&&symbol&&path.equals("payload")))throw ApiException.badRequest("Unsafe library field");
      safe(field.getValue(),depth+1,symbol,path.isEmpty()?key:path+"."+key);
    }}else if(node.isArray())for(var child:node)safe(child,depth+1,symbol,path+"[]");
  }
  @Transactional public Map<String,Object> save(String user,String id,long revision,JsonNode items){
    var l=owned(user,id,true);String role=role(l,user);if(role.equals("viewer"))throw ApiException.forbidden("Read-only library");
    if(l.revision!=revision)throw ApiException.conflict("Library changed on another device; reload before saving");
    if(items==null||!items.isArray()||items.size()>500||items.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>10*1024*1024)throw ApiException.badRequest("Library limit: 500 items and 10 MB");for(var item:items)safe(item,0,item.path("kind").asText().equals("symbol"),"");
    var ids=new HashSet<String>();var next=new HashMap<String,JsonNode>();
    for(var item:items){String itemId=item.path("id").asText();if(itemId.isBlank()||itemId.length()>200||!ids.add(itemId)||item.path("name").asText().isBlank()||item.path("name").asText().length()>200||!Set.of("folder","stamp","style","symbol","template","brand").contains(item.path("kind").asText()))throw ApiException.badRequest("Invalid library item");next.put(itemId,item);}
    for(var item:items){var seen=new HashSet<String>();JsonNode current=item;while(!current.path("folderId").asText().isEmpty()){String parent=current.path("folderId").asText();if(!seen.add(parent)||seen.size()>20||!next.containsKey(parent)||!next.get(parent).path("kind").asText().equals("folder"))throw ApiException.badRequest("Invalid library folder tree");current=next.get(parent);}}
    if(!role.equals("owner")){
      var old=new HashMap<String,JsonNode>();for(var item:json(l.payload))old.put(item.path("id").asText(),item);
      for(var item:items)if(item.path("locked").asBoolean()&&!old.containsKey(item.path("id").asText()))throw ApiException.forbidden("Only the owner can lock items");
      for(var entry:old.entrySet()){
        JsonNode item=entry.getValue(),current=item;boolean locked=false;var seen=new HashSet<String>();
        while(current!=null&&seen.add(current.path("id").asText())){locked|=current.path("locked").asBoolean();current=old.get(current.path("folderId").asText());}
        JsonNode replacement=next.get(entry.getKey());if(locked&&!item.equals(replacement)||replacement!=null&&item.path("locked").asBoolean()!=replacement.path("locked").asBoolean())throw ApiException.forbidden("Only the owner can change protected assets");
      }
      for(var item:items){JsonNode parent=next.get(item.path("folderId").asText());while(parent!=null){if(parent.path("locked").asBoolean()&&!item.equals(old.get(item.path("id").asText())))throw ApiException.forbidden("Protected folder");parent=next.get(parent.path("folderId").asText());}}
    }
    l.payload=items.toString();l.revision++;return view(repo.save(l),user);
  }
  @Transactional public void member(String user,String id,String email,String role){var l=owned(user,id,true);if(!l.userId.equals(user))throw ApiException.forbidden("Owner required");if(!Set.of("editor","viewer","remove").contains(role))throw ApiException.badRequest("Invalid role");var target=users.findByEmailIgnoreCase(email).orElseThrow(()->ApiException.notFound("Registered user"));if(target.getId().equals(user))throw ApiException.badRequest("Owner role cannot be changed");var members=(ObjectNode)json(l.members);if(role.equals("remove"))members.remove(target.getId());else {if(members.size()>=50&&!members.has(target.getId()))throw ApiException.badRequest("Maximum 50 members");members.put(target.getId(),role);}l.members=members.toString();l.revision++;repo.save(l);}
}
