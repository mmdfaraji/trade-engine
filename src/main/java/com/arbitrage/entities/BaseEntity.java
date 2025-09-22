package com.arbitrage.entities;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public abstract class BaseEntity implements Cloneable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version protected Integer version;

  @Column(updatable = false)
  @CreationTimestamp
  protected Date createdAt;

  @Column @UpdateTimestamp protected Date updatedAt;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
