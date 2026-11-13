package ru.ticket.journey;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.apache.commons.csv.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operator-owned UTF-8 CSV. Invalid fields are skipped; broken CSV syntax rolls back the import. */
@Service
public class MysticPlaceImporter {
  private static final Logger log=LoggerFactory.getLogger(MysticPlaceImporter.class);
  private static final List<String> HEADERS=List.of("name","region","city","latitude","longitude","category","description","source_url");
  private final MysticPlaceRepository repository;
  public MysticPlaceImporter(MysticPlaceRepository repository){this.repository=repository;}

  @Transactional(rollbackFor=IOException.class)
  public int importFromCsv(InputStream csvStream) throws IOException {
    var batch=new ArrayList<MysticPlace>();var seen=new HashSet<String>();int imported=0,skipped=0;
    try(var reader=new PushbackReader(new InputStreamReader(csvStream,StandardCharsets.UTF_8),1)){
      int first=reader.read();if(first!=-1&&first!=0xfeff)reader.unread(first);
      try(var parser=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get().parse(reader)){
        if(parser.getHeaderNames().size()!=HEADERS.size()||!new HashSet<>(parser.getHeaderNames()).equals(new HashSet<>(HEADERS)))throw new IOException("Invalid mystic CSV headers");
        for(var row:parser){
          if(row.getRecordNumber()>25000)throw new IOException("Mystic CSV exceeds 25000 rows");
          try {
            if(!row.isConsistent())throw new IllegalArgumentException();
            if("name".equals(row.get("name"))&&"latitude".equals(row.get("latitude"))){skipped++;continue;}
            String name=text(row,"name",200,true);
            double lat=Double.parseDouble(row.get("latitude").strip()),lon=Double.parseDouble(row.get("longitude").strip());
            if(!Double.isFinite(lat)||Math.abs(lat)>85||!Double.isFinite(lon)||Math.abs(lon)>=180)throw new IllegalArgumentException();
            String key=Normalizer.normalize(name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е')+"|"+lat+"|"+lon;
            String region=text(row,"region",100,false),city=text(row,"city",100,false),category=text(row,"category",50,false),description=text(row,"description",5000,false);
            String source=text(row,"source_url",2000,false);
            if(!source.isBlank()){
              URI uri=URI.create(source);
              if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();
            }
            if(!seen.add(key)){skipped++;continue;}
            var place=repository.findByNameAndLatitudeAndLongitude(name,lat,lon).orElseGet(MysticPlace::new);
            place.setName(name);place.setLatitude(lat);place.setLongitude(lon);place.setRegion(region);place.setCity(city);
            place.setCategory(category);place.setDescription(description);place.setSourceUrl(source);batch.add(place);
            if(batch.size()==200){repository.saveAll(batch);repository.flush();imported+=batch.size();batch.clear();}
          }catch(IllegalArgumentException invalid){skipped++;log.warn("mystic.import invalid_row={}",row.getRecordNumber());}
        }
        if(!batch.isEmpty()){repository.saveAll(batch);repository.flush();imported+=batch.size();}
      }catch(UncheckedIOException invalid){throw new IOException("Malformed mystic CSV",invalid);}
    }
    log.info("Imported {} mystic places; skipped {} rows",imported,skipped);
    return imported;
  }
  private static String text(CSVRecord row,String key,int max,boolean required){
    String value=row.get(key).strip();
    if(value.length()>max||required&&value.isBlank()||value.chars().anyMatch(c->c==0))throw new IllegalArgumentException();
    return value;
  }
}
