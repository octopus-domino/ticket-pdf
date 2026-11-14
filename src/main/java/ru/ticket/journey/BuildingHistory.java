package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name="building_history")
public class BuildingHistory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(length=50) private String osmId;
  @Column(columnDefinition="TEXT") private String address;
  @Column(columnDefinition="TEXT") private String name;
  @Column(length=200) private String constructionPeriod;
  private Integer constructionYear;
  @Column(length=300) private String architect;
  @Column(length=100) private String style;
  @Column(length=100) private String heritageStatus;
  @Column(nullable=false,length=50) private String source;
  @Column(columnDefinition="TEXT") private String sourceUrl;
  @JsonIgnore @JdbcTypeCode(SqlTypes.GEOMETRY) @Column(columnDefinition="geometry") private Geometry geometry;
  private Instant createdAt;
  private Instant updatedAt;
  @PrePersist void created(){createdAt=Instant.now();updatedAt=createdAt;}
  @PreUpdate void updated(){updatedAt=Instant.now();}
  public Long getId(){return id;}
  public String getOsmId(){return osmId;} public void setOsmId(String v){osmId=v;}
  public String getAddress(){return address;} public void setAddress(String v){address=v;}
  public String getName(){return name;} public void setName(String v){name=v;}
  public String getConstructionPeriod(){return constructionPeriod;} public void setConstructionPeriod(String v){constructionPeriod=v;}
  public Integer getConstructionYear(){return constructionYear;} public void setConstructionYear(Integer v){constructionYear=v;}
  public String getArchitect(){return architect;} public void setArchitect(String v){architect=v;}
  public String getStyle(){return style;} public void setStyle(String v){style=v;}
  public String getHeritageStatus(){return heritageStatus;} public void setHeritageStatus(String v){heritageStatus=v;}
  public String getSource(){return source;} public void setSource(String v){source=v;}
  public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
  public Geometry getGeometry(){return geometry;} public void setGeometry(Geometry v){geometry=v;}
}
