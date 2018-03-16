# Audit entities with Hibernate Envers
A lot of applications require tracking or logging changes performed on business data. In case of relational database we can use low-level solution by triggering database table operations. But if you’re building Java application you have easier approach from high level point – Hibernate Envers.

In this post we are going to demonstrate auditing mechanism for Hibernate persistent entities on example Spring boot + Hibernate Envers project. Auditing will be shown for data in relational databases - example configurations prepared for H2 and PostgreSQL database engines.

## Application
Below diagram shows relationships between data tables. Our main object type is Company that we will want to audit together with relationships except Car entity.

![database-schema](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/db_schema.png)

These are example REST endpoints that let to modify data for our tests: 

```java
@RestController
@RequestMapping("/company")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public @ResponseBody
    List<Company> getAll() {
        return companyService.getAll();
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public @ResponseBody
    Company get(@PathVariable Long id) {
        return companyService.get(id);
    }

    @RequestMapping(value = "/filter", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public @ResponseBody
    Company get(@RequestParam String name) {
        return companyService.get(name);
    }

    @RequestMapping(method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public ResponseEntity<?> create(@RequestBody Company company) {
        companyService.create(company);
        HttpHeaders headers = new HttpHeaders();
        ControllerLinkBuilder linkBuilder = linkTo(methodOn(CompanyController.class).get(company.getId()));
        headers.setLocation(linkBuilder.toUri());
        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public void update(@RequestBody Company company) {
        companyService.update(company);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}
```

Notice that we won’t modify logic of business code, we won’t change any service responsible for running database operations itself. 

## Configure Hibernate Envers
First of all we will setup separate database schema to store audited records, since we’re using Spring boot we can do it like below:
```properties
spring.jpa.properties.org.hibernate.envers.default_schema=audit
```
Some other useful properties to be set you can find in [EnversSettings](https://docs.jboss.org/hibernate/orm/5.2/javadocs/org/hibernate/envers/configuration/EnversSettings.html).
Then just add the [@Audited](https://docs.jboss.org/hibernate/orm/5.2/javadocs/org/hibernate/envers/Audited.html) annotation either on an @Entity (to audit the whole entity) or on specific @Columns (if you need to audit specific properties only). It allows for Hibernate Envers to audit the values during create, update and delete operations. 
For all entities except Car we have declare @Audited:

```java
@Entity
@Table(name = "COMPANY", uniqueConstraints = {@UniqueConstraint(columnNames = {"NAME"})})
@Audited
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Company implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id = null;

    @Column(name = "NAME", nullable = false)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company", fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    private Set<Department> departments = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company", fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @NotAudited
    private Set<Car> cars = new HashSet<>();

    public void setDepartments(Set<Department> departments) {
        this.departments.clear();
        if (departments != null) {
            this.departments.addAll(departments);
        }
    }

    public void setCars(Set<Car> cars) {
        this.cars.clear();
        if (cars != null) {
            this.cars.addAll(cars);
        }
    }
}
```

Since Company contains relationship to Cars, we have to exclude it with [@NotAudited](https://docs.jboss.org/hibernate/orm/5.2/javadocs/org/hibernate/envers/NotAudited.html) annotation.

## Revision Information – custom revision entity
Hibernate Envers use REVINFO table to store revision information. A row is inserted into this table on each new revision, that is, on each commit of a transaction, which changes audited data. By default following data is stored:
* revision number
* revision creation timestamp

In our example we want to store additional information like – username. The second step that needs to be performed is to implement listener - AuditRevisionListener - to populate additional field to RevisionEntitity.

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

Below is the source code of listener:

```java
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity audit = (AuditRevisionEntity) revisionEntity;
        audit.setUsername("admin");
    }
}
```

## Setup history tables
For all entities annotated with @Audited, we have to create respective database table. By default, Hibernate Envers uses following pattern for audit tables – “TABLENAME_AUD”, but it’s configurable to change “_AUD” suffix (by using @AuditTable or setting org.hibernate.envers.audit_table_suffix property). 

Each audit table will store:
* the primary key of entity, 
* all audited fields,
* revision number – revision that comes from REVINFO table
* revision type – numeric value of entity operation type, Envers us three values like 0 (add), 1 (update), 2 (delete)

![database-audit-schema](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/db_audit_schema.png)

## Business data operations
To check content of tracking database we will use below database query:

```sql
SELECT REVINFO.REV, REVINFO.REVTSTMP, REVINFO.USERNAME,
 COMPANY_AUD.ID, COMPANY_AUD.NAME, 
 CASE 
    WHEN COMPANY_AUD.REVTYPE = 0 THEN 'add' 
    WHEN COMPANY_AUD.REVTYPE = 1 THEN 'mod' 
    WHEN COMPANY_AUD.REVTYPE = 2 THEN 'del' 
 ELSE NULL END AS REVTYPE,
 DEPARTMENT_AUD.ID, DEPARTMENT_AUD.NAME, DEPARTMENT_AUD.COMPANY_ID,
 CASE 
    WHEN DEPARTMENT_AUD.REVTYPE = 0 THEN 'add' 
    WHEN DEPARTMENT_AUD.REVTYPE = 1 THEN 'mod' 
    WHEN DEPARTMENT_AUD.REVTYPE = 2 THEN 'del' 
 ELSE NULL END AS REVTYPE,
 EMPLOYEE_AUD.ID, EMPLOYEE_AUD.NAME, EMPLOYEE_AUD.SURNAME, EMPLOYEE_AUD.ADDRESS_ID, EMPLOYEE_AUD.DEPARTMENT_ID,
 CASE 
    WHEN EMPLOYEE_AUD.REVTYPE = 0 THEN 'add' 
    WHEN EMPLOYEE_AUD.REVTYPE = 1 THEN 'mod' 
    WHEN EMPLOYEE_AUD.REVTYPE = 2 THEN 'del' 
 ELSE NULL END AS REVTYPE,
 ADDRESS_AUD.ID, ADDRESS_AUD.HOUSE_NUMBER, ADDRESS_AUD.STREET, ADDRESS_AUD.ZIP_CODE,
 CASE 
    WHEN ADDRESS_AUD.REVTYPE = 0 THEN 'add' 
    WHEN ADDRESS_AUD.REVTYPE = 1 THEN 'mod' 
    WHEN ADDRESS_AUD.REVTYPE = 2 THEN 'del' 
 ELSE NULL END AS REVTYPE
 FROM AUDIT.REVINFO
 LEFT JOIN AUDIT.COMPANY_AUD ON REVINFO.REV = COMPANY_AUD.REV
 LEFT JOIN AUDIT.DEPARTMENT_AUD ON REVINFO.REV = DEPARTMENT_AUD.REV
 LEFT JOIN AUDIT.EMPLOYEE_AUD ON REVINFO.REV = EMPLOYEE_AUD.REV
 LEFT JOIN AUDIT.ADDRESS_AUD ON REVINFO.REV = ADDRESS_AUD.REV;
```

### Create
Let’s see what happen once we insert data. First time we will insert simple data, just standalone company object without children objects in structure:
```
curl -X POST \
  http://localhost:8080/company \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{
    "name": "Fanta"
}'
```

Here’s the database operations log from application log:
```log
2018-03-16 13:31:32.894 DEBUG 16136 --- [nio-8080-exec-1] org.hibernate.SQL                        : 
    select
        nextval ('hibernate_sequence')
2018-03-16 13:31:32.972 DEBUG 16136 --- [nio-8080-exec-1] org.hibernate.SQL                        : 
    insert 
    into
        company
        (name, id) 
    values
        (?, ?)
2018-03-16 13:31:32.990 DEBUG 16136 --- [nio-8080-exec-1] org.hibernate.SQL                        : 
    select
        nextval ('hibernate_sequence')
2018-03-16 13:31:32.994 DEBUG 16136 --- [nio-8080-exec-1] org.hibernate.SQL                        : 
    insert 
    into
        audit.revinfo
        (revtstmp, username, rev) 
    values
        (?, ?, ?)
2018-03-16 13:31:32.997 DEBUG 16136 --- [nio-8080-exec-1] org.hibernate.SQL                        : 
    insert 
    into
        audit.company_aud
        (revtype, name, id, rev) 
    values
        (?, ?, ?, ?)
```

Without any additional code that would handle audit schema operations Hibernate Envers populates automatically data. Like you can see adding new company was tracked in COMPANY_AUD table in revision number 8:
![create-simple](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/create_simple.png)

If we will create company with structure of child objects, like below:
```
curl -X POST \
  http://localhost:8080/company \
  -H 'content-type: application/json' \
  -d '{
    "name": "7up",
    "departments": [
        {
            "name": "Administrative Accounting",
            "employees": [
                {
                    "name": "Jim",
                    "surname": "Lahey"
                }
            ]
        }
    ]
}'
```

then one revision will be assigned to multiple objects. In example below, revision equal to 13 contains information for new:
* company,
* employee,
* department

![create-extended](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/create_extended.png)

### Update
Example below shows simple update:
```
  http://localhost:8080/company/ \
  -H 'content-type: application/json' \
  -d '{
 "id": 8,
    "name": "FantaUpdated"
}'
```


```log
2018-03-16 14:02:55.512 DEBUG 16136 --- [nio-8080-exec-5] org.hibernate.SQL                        : 
    update
        company 
    set
        name=? 
    where
        id=?
2018-03-16 14:02:55.516 DEBUG 16136 --- [nio-8080-exec-5] org.hibernate.SQL                        : 
    select
        nextval ('hibernate_sequence')
2018-03-16 14:02:55.518 DEBUG 16136 --- [nio-8080-exec-5] org.hibernate.SQL                        : 
    insert 
    into
        audit.revinfo
        (revtstmp, username, rev) 
    values
        (?, ?, ?)
2018-03-16 14:02:55.518 DEBUG 16136 --- [nio-8080-exec-5] org.hibernate.SQL                        : 
    insert 
    into
        audit.company_aud
        (revtype, name, id, rev) 
    values
        (?, ?, ?, ?)
```

This time revision 14 was created. REVTYPE for Company is equal to 1 which means modification:
![update-simple](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/update_simple.png)

The same situation for multiple objects update:
```
curl -X PUT \
  http://localhost:8080/company \
  -H 'content-type: application/json' \
  -d '{
 "id": 13,
    "name": "7upUpdated",
    "departments": [
        {
         "id": 14,
            "name": "Administrative Accounting Updated",
            "employees": [
                {
                 "id": 15,
                    "name": "Jim Updated",
                    "surname": "Lahey Updated"
                }
            ]
        }
    ]
}'
```

![update-extended](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/update_extended.png)

### Delete
Let’s see check behavior in case of delete operations:

```
curl -X DELETE http://localhost:8080/company/8
```

```log
2018-03-16 14:04:11.288 DEBUG 16136 --- [nio-8080-exec-8] org.hibernate.SQL                        :
    delete
    from
        company
    where
        id=?
2018-03-16 14:04:11.293 DEBUG 16136 --- [nio-8080-exec-8] org.hibernate.SQL                        :
    select
        nextval ('hibernate_sequence')
2018-03-16 14:04:11.294 DEBUG 16136 --- [nio-8080-exec-8] org.hibernate.SQL                        :
    insert
    into
        audit.revinfo
        (revtstmp, username, rev)
    values
        (?, ?, ?)
2018-03-16 14:04:11.295 DEBUG 16136 --- [nio-8080-exec-8] org.hibernate.SQL                        :
    insert
    into
        audit.company_aud
        (revtype, name, id, rev) 
    values
        (?, ?, ?, ?)
```

New REVTYPE was tracked with value equal to 2:
![delete-simple](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/delete_simple.png)

The same situation for multiple delete: 
```
curl -X DELETE http://localhost:8080/company/10
```

![delete-extended](https://raw.githubusercontent.com/adamzareba/company-structure-hibernate-history/master/src/main/docs/delete_extended.png)

## Summary
Hibernate Envers provides very easy and powerful mechanism for tracking business data. Introducing Envers to your project doesn’t need any business code modifications and refactoring – you can define behavior just with annotations.
The source code for above listings can be found in the GitHub project [company-structure-hibernate-history](https://github.com/adamzareba/company-structure-hibernate-history).

