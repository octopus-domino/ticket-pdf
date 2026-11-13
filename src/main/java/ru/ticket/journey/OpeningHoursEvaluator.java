package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Conservative interpretation: unsupported syntax and missing weekdays remain unknown. */
@Component
public class OpeningHoursEvaluator {
  public enum Status { OPEN, CLOSED, UNKNOWN }
  private static final List<String> DAYS=List.of("Mon","Tue","Wed","Thu","Fri","Sat","Sun");
  private final ObjectMapper mapper;
  public OpeningHoursEvaluator(ObjectMapper mapper){this.mapper=mapper;}
  public Status evaluate(String expression,LocalDateTime visitTime){
    if(visitTime==null)return Status.UNKNOWN;
    var schedule=OsmOpeningHours.parse(expression,mapper);
    var day=schedule.path(DAYS.get(visitTime.getDayOfWeek().getValue()-1));
    if(!day.has("working_hours"))return Status.UNKNOWN;
    int minute=visitTime.getHour()*60+visitTime.getMinute();
    for(var interval:day.path("working_hours")){
      if(minute>=minutes(interval.path("from").asText())&&minute<minutes(interval.path("to").asText()))return Status.OPEN;
    }
    return Status.CLOSED;
  }
  private static int minutes(String value){var parts=value.split(":");return Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);}
}
