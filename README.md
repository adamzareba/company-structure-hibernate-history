# Spring Boot + Hibernate + Hibernate Envers 

Example Spring Boot + Hibernate + Hibernate Envers project for demonstration purposes of audit mechanism. 

## Getting started

To run application:

```mvn package && java -jar target\company-structure-hibernate-history-1.0-SNAPSHOT.jar```

### Prerequisites:
- Java 8
- Maven
- H2/PostgreSQL

It is possible to run application in one of two profiles:
- h2
- postgres

depending on database engine chose for testing. 

### Testing database schema
![database-schema](src/main/docs/db_schema.png)

### Configuration
Separated database schema is configured to be used for audit:
```properties
spring.jpa.properties.org.hibernate.envers.default_schema=audit
```

Audited entities are annotated with `@Audited`. 
```java
@Entity
@Table(name = "COMPANY", uniqueConstraints = {@UniqueConstraint(columnNames = {"NAME"})})
@Audited
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Company implements Serializable
```

Not audited entities need to be exluded from related entities. Since `Car` entity is not audited, it needs to be excluded from relationship like below:
```java
@OneToMany(cascade = CascadeType.ALL, mappedBy = "company", fetch = FetchType.LAZY, orphanRemoval = true)
@JsonManagedReference
@NotAudited
private Set<Car> cars = new HashSet<>();
```

### Implementation

Enhanced `RevisionEntity`:
```java
@Entity
@RevisionEntity(AuditRevisionListener.class)
@Table(name = "REVINFO", schema = "audit")
@AttributeOverrides({
        @AttributeOverride(name = "timestamp", column = @Column(name = "REVTSTMP")),
        @AttributeOverride(name = "id", column = @Column(name = "REV"))})
@Getter
@Setter
public class AuditRevisionEntity extends DefaultRevisionEntity {

    @Column(name = "USERNAME", nullable = false)
    private String username;
}
```

Enhanced `RevisionListener`: 
```java
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity audit = (AuditRevisionEntity) revisionEntity;
        audit.setUsername("admin");
    }
}
```
