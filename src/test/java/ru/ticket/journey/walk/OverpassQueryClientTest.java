package ru.ticket.journey.walk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverpassQueryClientTest {
  @Test void retriesAnOverloadedInstanceWithoutChangingTheQuery() throws Exception {
    var remote = mock(WalkRemote.class);
    var response = new ObjectMapper().readTree("{\"elements\":[]}");
    when(remote.post(eq("primary"), anyString())).thenThrow(new IOException("HTTP 502"));
    when(remote.post(eq("secondary"), anyString())).thenReturn(response);
    var source = new OverpassQueryClient(remote, List.of("primary", "secondary", "unused"));
    assertSame(response, source.query("[out:json];node(1);out body;"));
    String form = "data=" + WalkRemote.encode("[out:json];node(1);out body;");
    verify(remote).post("primary", form);
    verify(remote).post("secondary", form);
    verifyNoMoreInteractions(remote);
  }

  @Test void anIncompleteResponseDoesNotMasqueradeAsAnEmptyArea() throws Exception {
    var remote = mock(WalkRemote.class);
    when(remote.post(anyString(), anyString())).thenReturn(new ObjectMapper().readTree("{\"elements\":[],\"remark\":\"timed out\"}"));
    assertThrows(IOException.class, () -> new OverpassQueryClient(remote, List.of("primary", "secondary")).query("query"));
    verify(remote, times(2)).post(anyString(), anyString());
  }
}
