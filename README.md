# ProtVar Back-end
This module publishes the REST API services for ProtVar.
This application internally calls UniProt rest API's as well as PDBe API to map protein, gene, variation
and structural data together.

## Introduction
It provides various endpoints but response structure remains same.
These endpoints can be accessed programmatically, as well as ProtVar website (frontend - https://github.com/ebi-uniprot/protvar-fe) 
calls these endpoints to display data on screen.

It is a spring boot application. 
The Main class is uk.ac.ebi.protvar.ApplicationMainClass

## Running the application locally
1. Clone repo
2. Crete file src/main/resources/application-local.properties
3. Override following three properties
   1. spring.datasource.url
   2. spring.datasource.username
   3. spring.datasource.password
4. Start the application using mvn spring-boot:run

## Load/Stress Testing
1. We are using gatling to test application
2. Use mvn gatling:test -Dgatling.simulationClass=uk.ac.ebi.protvar.gatling.simulations.BasicSimulation
3. or mvn gatling:test

## Deployment

To deploy to development environment:
- Merge or commit changes directly to the `dev` branch.
- Gitlab CI/CD pipeline will execute deployment to the dev environment.
- It may take a couple of minutes for the Gitlab mirror repository to refresh from the Github repository.
- To check deployment:
  - either check Gitlab CI/CD
  - or run `helm status protvar-be` and check the last deployed to confirm.

To deploy to production environment:
- Merge or commit changes to the main branch.
- The same automated CI/CD process as above will execute for production.