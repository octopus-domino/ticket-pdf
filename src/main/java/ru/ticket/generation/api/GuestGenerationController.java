package ru.ticket.generation.api;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.domain.FieldDef;

/** Stateless rendering only: no identity, repositories, jobs, archive, email or stored payload. */
@RestController @RequestMapping("/api/guest/generate")
public class GuestGenerationController {
  private final GenerationService service;public GuestGenerationController(GenerationService service){this.service=service;}
  public static class Request extends GenerationRequest {@jakarta.validation.constraints.Size(max=500) public List<FieldDef> fields=List.of();}
  @PostMapping("/sync") public ResponseEntity<byte[]> render(@Valid @RequestBody Request request){return render(request,null);}
  @PostMapping("/preview") public ResponseEntity<byte[]> preview(@Valid @RequestBody Request request,@RequestParam(defaultValue="0") int page){return render(request,page);}
  private ResponseEntity<byte[]> render(Request request,Integer page){
    if(request.inlineSlides==null||request.inlineSlides.isEmpty()||request.items.size()>100||request.items.stream().anyMatch(i->i.dataModelId!=null)||request.templateId!=null||request.schemaId!=null)throw ApiException.badRequest("Guest generation requires inline pages and 1..100 inline data rows");
    int pages=request.inlineSlides.size();
    if(page!=null){if(page<0||page>=pages||request.items.size()!=1)throw ApiException.badRequest("Invalid preview page");request.inlineSlides=List.of(request.inlineSlides.get(page));request.format="svg";request.packAs="single";}
    var source=new GenerationSource(new GenerationCatalog.Template(request.inlineSlides,null,request.fields==null?List.of():request.fields,request.bindings==null?List.of():request.bindings),Map.of());
    var result=service.generateDocuments("guest",request,(i,c,f)->{},source);
    if(result.documents().isEmpty())throw ApiException.badRequest("All items failed: "+result.issues());
    var response=GenerationController.bytesResponse(service.pack(request.packAs,request.format,result.documents()),request.items.size()-result.issues().size(),result.issues());
    if(page==null)return response;
    var headers=new org.springframework.http.HttpHeaders();headers.putAll(response.getHeaders());headers.set("X-Preview-Pages",String.valueOf(pages));return new ResponseEntity<>(response.getBody(),headers,response.getStatusCode());
  }
}
