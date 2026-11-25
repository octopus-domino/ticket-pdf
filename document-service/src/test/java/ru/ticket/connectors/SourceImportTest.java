package ru.ticket.connectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.clientapi.SecretCipher;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.datamodel.domain.DataModelEntity;
import ru.ticket.schema.repository.SchemaRepository;
import ru.ticket.schema.domain.*;

class SourceImportTest {
  @Test void oneCPreviewMappingUpsertsAndRejectsCrossOwnerTokensAndDuplicateIds() throws Exception {
    var mapper=new ObjectMapper();var sources=mock(SourceConnectionRepository.class);var http=mock(SourceHttpClient.class);var schemas=mock(SchemaRepository.class);var models=mock(DataModelRepository.class);
    var cipher=new SecretCipher("test-encryption-key-long-enough-for-tests-123456");
    var service=new SourceImportService(sources,http,cipher,mapper,schemas,models);var source=new SourceConnection();source.userId="owner";source.name="1C";source.encryptedUrl=cipher.encrypt("https://example.test/data");source.encryptedAuth=cipher.encrypt("");
    var schema=new SchemaEntity("owner","Data",List.of(new FieldDef("code","string",true,null,null),new FieldDef("amount","number",true,null,null)));
    when(sources.findByIdAndUserId(source.id,"owner")).thenReturn(Optional.of(source));when(sources.lockOwned(source.id,"owner")).thenReturn(Optional.of(source));when(sources.lockOwned(source.id,"other")).thenReturn(Optional.of(source));when(schemas.findByIdAndUserId(schema.getId(),"owner")).thenReturn(Optional.of(schema));
    when(http.fetch(anyString(),anyString())).thenReturn(mapper.readTree("{\"value\":[{\"Ref_Key\":\"00001\",\"Total\":\"123,45\"}]}"));
    when(models.save(any())).thenAnswer(call->call.getArgument(0));
    var preview=service.preview("owner",source.id);var request=new SourceImportService.ImportRequest(preview.token(),schema.getId(),"Ref_Key",null,Map.of("code","Ref_Key","amount","Total"),Map.of(),0);
    assertThrows(RuntimeException.class,()->service.apply("other",source.id,request));
    assertEquals(1,service.apply("owner",source.id,request).get("created"));
    var captor=org.mockito.ArgumentCaptor.forClass(DataModelEntity.class);verify(models).save(captor.capture());var model=captor.getValue();assertEquals("00001",model.getValues().get("code"));assertEquals(123.45,((Number)model.getValues().get("amount")).doubleValue());
    when(models.findByIdAndUserId(model.getId(),"owner")).thenReturn(Optional.of(model));assertEquals(1,service.apply("owner",source.id,request).get("updated"));
    when(http.fetch(anyString(),anyString())).thenReturn(mapper.readTree("[{\"Ref_Key\":\"same\",\"Total\":1},{\"Ref_Key\":\"same\",\"Total\":2}]"));var duplicate=service.preview("owner",source.id);
    clearInvocations(models);assertThrows(RuntimeException.class,()->service.apply("owner",source.id,new SourceImportService.ImportRequest(duplicate.token(),schema.getId(),"Ref_Key",null,request.mapping(),Map.of(),0)));verify(models,never()).save(any());
  }
  @Test void originAllowlistRejectsRedirectTargetsCredentialsAndUnexpectedPorts(){
    var client=new SourceHttpClient(new ObjectMapper(),"https://one-c.example");assertEquals("https://one-c.example",client.validate("https://one-c.example/odata?$format=json"));
    for(String url:List.of("http://one-c.example/data","https://one-c.example:444/data","https://one-c.example.evil/data","https://user:password@one-c.example/data","file:///private","https://one-c.example/data#fragment"))assertThrows(RuntimeException.class,()->client.validate(url));
  }
}
