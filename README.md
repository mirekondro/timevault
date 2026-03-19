# My Application README

<<<<<<< Updated upstream
<<<<<<< Updated upstream
- [ ] TODO Replace or update this README with instructions relevant to your application
=======
TimeVault is a local-first web application for Hack Esbjerg 2026. It captures URLs, text, and images into a SQLite archive, writes a three-sentence AI context note, rescues dead links from the Wayback Machine, and stores Denmark Today vibe capsules from Danish news feeds.
=======
TimeVault is a local-first web application for Hack Esbjerg 2026. It captures URLs, text, and images into a SQLite archive, writes a three-sentence AI context note, rescues dead links from the Wayback Machine, and stores Denmark Today vibe capsules from Danish news feeds.

Built with **Spring Boot** and **Vaadin** for a modern web experience.
>>>>>>> Stashed changes

Built with **Spring Boot** and **Vaadin** for a modern web experience.
>>>>>>> Stashed changes

To start the application in development mode, import it into your IDE and run the `Application` class. 
You can also start the application from the command line by running: 

```bash
<<<<<<< Updated upstream
<<<<<<< Updated upstream
./mvnw
```

To build the application in production mode, run:
=======
./mvnw spring-boot:run
```

=======
./mvnw spring-boot:run
```

>>>>>>> Stashed changes
Then open your browser to: **http://localhost:8080**

## Build
>>>>>>> Stashed changes

```bash
./mvnw package
```

<<<<<<< Updated upstream
<<<<<<< Updated upstream
To build a Docker image, run:
=======
=======
>>>>>>> Stashed changes
Run the built jar:
```bash
java -jar target/timevault-1.0-SNAPSHOT.jar
```

## AI Providers

TimeVault works without an API key by falling back to a local context generator. To use a real model, set one of these environment variables before launching:
>>>>>>> Stashed changes

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```

## Getting Started

The [Quick Start](https://vaadin.com/docs/v25/getting-started/quick-start) tutorial helps you get started with Vaadin in 
around 10 minutes. This tutorial walks you through building a simple application, introducing the core concepts along 
the way.
