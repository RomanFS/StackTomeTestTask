## Project set-up: ##

1. Check application.conf on correct session token and other needed parameters
2. Make sure you have access to the traffic web-site:
   ```ping https://web.vstat.info/stacktome.com```

## Commands to run in with docker: ##

```
docker compose build
docker compose up
```

## Run locally: ##

```
docker compose up redis -d
sbt run
```

## Request domains data: ##

```
curl http://localhost:8080/domains
```