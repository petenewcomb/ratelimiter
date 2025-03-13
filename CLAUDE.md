# CLAUDE.md - Guidelines for Rate Limiter Project

## Build & Test Commands
- Build project: `./gradlew build`
- Run tests: `./gradlew test`
- Run single test: `./gradlew test --tests org.example.ratelimiter.RateLimiterTest.testMethodName`
- Run server: `./gradlew :server:run`
- Format code: `./gradlew spotlessApply`
- Check formatting: `./gradlew spotlessCheck`

## Code Style Guidelines
- **Formatting**: Google Java Format (1.25.2) enforced via Spotless
- **Java Version**: 21
- **Naming**: Classes = PascalCase, methods/variables = camelCase
- **Error Handling**: Use Objects.requireNonNull for parameters, throw RuntimeExceptions with clear messages
- **Structure**: 
  - Multi-module Gradle project (ratelimiter, server)
  - Follow builder pattern for complex objects
- **Testing**: JUnit 5 with descriptive test method names and clear assertions