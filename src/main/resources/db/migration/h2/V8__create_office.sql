CREATE TABLE OFFICE (
  ID BIGINT GENERATED BY DEFAULT AS IDENTITY,
  NAME VARCHAR(255),
  ADDRESS_ID BIGINT,
  DEPARTMENT_ID BIGINT,
  PRIMARY KEY (ID)
);

ALTER TABLE OFFICE ADD CONSTRAINT FKGA73HDTPB67TWLR9C35357TYT FOREIGN KEY (ADDRESS_ID) REFERENCES ADDRESS;