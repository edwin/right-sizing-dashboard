# Project guidelines

## Goals
This is a dashboard application, built with Quarkus. The goals are to trigger a stress test, monitor the result closely, and recommend the best configuration for the application.

## Tools
- Openshift Container Platform 4
- Locust

## Stack
- Java 21, Maven, Quarkus
- JUnit 5 + AssertJ for tests

## Commands
- Build: `mvn clean build -s settings.xml`
- Run tests: `mvn test -s settings.xml`

## Conventions
- Package by feature, not by layer
- Constructor injection only, no field injection
- Use SLF4J for logging, never System.out
- Every new public service method needs a unit test

## Do
- Use Red Hat build of Quarkus
- Use the project's settings.xml to configure Maven

## Don't
- Don't add new dependencies without asking
- Don't change the database schema without a migration file