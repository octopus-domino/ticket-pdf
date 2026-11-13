package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="mystic_places",uniqueConstraints=@UniqueConstraint(name="uq_mystic_place",columnNames={"name","latitude","longitude"}))
public class MysticPlace {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false,length=200) private String name;
  @Column(length=100) private String region;
  @Column(length=100) private String city;
  @Column(nullable=false) private double latitude;
  @Column(nullable=false) private double longitude;
  @Column(length=50) private String category;
  @Column(columnDefinition="TEXT") private String description;
  @Column(columnDefinition="TEXT") private String sourceUrl;
  private Instant createdAt;
  private Instant updatedAt;

  @PrePersist void created(){createdAt=Instant.now();updatedAt=createdAt;}
  @PreUpdate void updated(){updatedAt=Instant.now();}
  public Long getId(){return id;}
  public String getName(){return name;} public void setName(String value){name=value;}
  public String getRegion(){return region;} public void setRegion(String value){region=value;}
  public String getCity(){return city;} public void setCity(String value){city=value;}
  public double getLatitude(){return latitude;} public void setLatitude(double value){latitude=value;}
  public double getLongitude(){return longitude;} public void setLongitude(double value){longitude=value;}
  public String getCategory(){return category;} public void setCategory(String value){category=value;}
  public String getDescription(){return description;} public void setDescription(String value){description=value;}
  public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String value){sourceUrl=value;}
  public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
