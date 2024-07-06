FROM eclipse-temurin:21-jdk-alpine

# download usermod and groupmod
RUN apk --no-cache add shadow runuser gcompat

# initialize user as needed
RUN adduser -Du 1001 -s /bin/sh abc

# set the work dir
WORKDIR /app/

# Copy wordlist
COPY wordlist.txt .

# Copy the entrypoint script to run as a specific user
COPY entrypoint.sh .

# Fix permissions
RUN chmod +x entrypoint.sh

# Copy the jar files
COPY target/quarkus-app/lib/ ./lib/
COPY target/quarkus-app/*.jar .
COPY target/quarkus-app/app/ ./app/
COPY target/quarkus-app/quarkus/ ./quarkus/

ENTRYPOINT ["./entrypoint.sh"]

EXPOSE 8443
