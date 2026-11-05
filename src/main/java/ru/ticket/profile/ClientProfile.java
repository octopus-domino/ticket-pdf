package ru.ticket.profile;
import jakarta.persistence.*;
@Entity @Table(name="client_profiles")
public class ClientProfile {
  @Id @Column(name="user_id",length=36) public String userId;
  @Column(nullable=false,length=200) public String organization="";
  @Column(nullable=false,length=100) public String position="";
  @Column(nullable=false,length=50) public String phone="";
  @Lob @Column(nullable=false) public String avatar="";
}
