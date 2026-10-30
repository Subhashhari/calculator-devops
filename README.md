# calculator-devops

A small Calculator REST API (Java 21, Spring Boot 4, Maven) wrapped in a fully automated CI/CD pipeline:
**GitHub → Jenkins → Maven → Docker → Docker Hub → Docker Compose (Nginx + 2 API replicas)**.

A push to `main` triggers Jenkins through a GitHub webhook. Jenkins builds, tests, packages, containerizes, pushes and redeploys the app with no manual steps, then emails the result.

- Docker Hub image: `https://hub.docker.com/r/<dockerhub-user>/calculator-api`

---

## Architecture

```
git push ──► GitHub ──webhook──► Jenkins (Jenkinsfile)
                                   │ Checkout → Build → Test → Docker Build → Push → Deploy
                                   │                          │               │        │
                                   │                          ▼               ▼        ▼
                                   │                    local image ──► Docker Hub   docker compose up -d --wait
                                   └─► email (success / failure)                       │
                                                                                       ▼
                                          host:8090 ──► nginx ──► calculator replica 1 (:8080)
                                                              └─► calculator replica 2 (:8080)
```

## API

| Operation | Endpoint | Example response |
|---|---|---|
| Add | `GET /api/v1/add?a=2&b=3` | `{"operation":"add","a":2.0,"b":3.0,"result":5.0}` |
| Subtract | `GET /api/v1/subtract?a=2&b=3` | `{"operation":"subtract","a":2.0,"b":3.0,"result":-1.0}` |
| Multiply | `GET /api/v1/multiply?a=4&b=5` | `{"operation":"multiply","a":4.0,"b":5.0,"result":20.0}` |
| Divide | `GET /api/v1/divide?a=10&b=4` | `{"operation":"divide","a":10.0,"b":4.0,"result":2.5}` |
| Health | `GET /actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |

Invalid input returns **HTTP 400** with a structured body:

```json
{"timestamp":"2026-10-30T06:46:20.158Z","status":400,"error":"Bad Request",
 "message":"Division by zero is not allowed","path":"/api/v1/divide"}
```

| Case | Message |
|---|---|
| `b=0` on divide | `Division by zero is not allowed` |
| missing parameter | `Missing required parameter 'b'` |
| non-numeric parameter | `Parameter 'a' must be a number` |
| `NaN` / `Infinity` | `Operands must be finite numbers` |
| result overflows `double` | `Result is out of range` |

## Build, test, run

Requirements: JDK 21, Docker with Compose v2. Maven is optional because `./mvnw` downloads it.

```bash
./mvnw test                       # unit + MockMvc integration tests -> target/surefire-reports/*.xml
./mvnw clean package              # -> target/calculator.jar
java -jar target/calculator.jar   # http://localhost:8080
```

### Docker

```bash
docker build -t calculator-api .
docker run --rm -p 8085:8080 calculator-api
curl "localhost:8085/api/v1/add?a=2&b=3"
```

The Dockerfile is multi-stage. A Maven/JDK 21 stage builds the JAR, with dependencies cached in their own layer. A `eclipse-temurin:21-jre-alpine` runtime stage runs only the JAR as the non-root user `app`, with `EXPOSE 8080` and a `HEALTHCHECK` on `/actuator/health`.

### Docker Compose

```bash
docker compose up -d --wait                                 # uses image calculator-api:latest
DOCKER_IMAGE=<dockerhub-user>/calculator-api IMAGE_TAG=12 \
  docker compose up -d --wait                               # a specific pipeline build
docker compose ps
curl -i "localhost:8090/api/v1/add?a=2&b=3"                 # X-Upstream header shows which replica answered
docker compose down
```

| Setting | Value |
|---|---|
| Services | `calculator` (2 replicas, not published) and `nginx` (published on `PROXY_PORT`, default 8090) |
| Network | `calc-net` (bridge) |
| Health checks | API: `/actuator/health`. Nginx: `/nginx-health`. Nginx starts only after the API is healthy. |
| Restart policy | `unless-stopped` |
| Env variables | `DOCKER_IMAGE`, `IMAGE_TAG`, `PROXY_PORT`, `SPRING_PROFILES_ACTIVE`, `JAVA_OPTS` |

Nginx resolves `calculator` through Docker DNS and keeps re-resolving it (`server calculator:8080 resolve`), so it load-balances across every replica and follows new container IPs after a redeploy.

## CI/CD pipeline (Jenkinsfile)

| Stage | What it does |
|---|---|
| Checkout | `checkout scm` of the pushed commit |
| Build | `mvn -B clean package -DskipTests` |
| Test | `mvn -B test`. JUnit XML is published, and a failing test fails the build, so nothing gets deployed. |
| Docker Build | `docker build -t <user>/calculator-api:<BUILD_NUMBER> -t …:latest .` |
| Push | `docker login --password-stdin` with Jenkins credential `dockerhub-creds`, then push both tags |
| Deploy | `docker compose up -d --wait --remove-orphans`, then a smoke test through Nginx |
| post | Email on success or failure (Email Extension plugin), `docker logout`, prune dangling images |

- **Trigger:** GitHub webhook (`githubPush()`), with `pollSCM('H/5 * * * *')` as a fallback.
- **Idempotent:** the project name is fixed and `disableConcurrentBuilds()` is set. `up -d` only recreates containers whose image changed, so re-running a build leaves the running stack intact.
- **No secrets in the repo:** the Docker Hub token and SMTP password live only in the Jenkins Credentials store.

### Jenkins setup (one-off)

1. Install the plugins Git, GitHub, Pipeline, Pipeline: Stage View, JUnit, Credentials Binding, Email Extension and Timestamper.
2. Under *Tools*, add JDK `JDK-21` and Maven `Maven-3.9`.
3. Add credentials `dockerhub-creds` (Docker Hub username + access token) and `gmail-smtp` (Gmail address + app password).
4. In *System → Extended E-mail Notification*, set `smtp.gmail.com:465`, SSL on, credential `gmail-smtp`, and *Default Recipients* = your email.
5. Give Jenkins access to Docker: `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins`.
6. Create a Pipeline job `calculator-devops` using *Pipeline script from SCM*, branch `*/main`, and tick *GitHub hook trigger for GITScm polling*.
7. Add a GitHub webhook pointing to `https://<public-jenkins-url>/github-webhook/` (for example through an ngrok tunnel).

## Repository layout

```
src/main/java/com/spe/calculator/   controller · service · dto · exception (global 400 handler)
src/test/java/com/spe/calculator/   CalculatorServiceTest (unit) · CalculatorControllerTest (MockMvc)
nginx/nginx.conf                    reverse proxy / load balancer
Dockerfile · .dockerignore          multi-stage image
docker-compose.yml                  nginx + 2 API replicas
Jenkinsfile                         declarative CI/CD pipeline
```
