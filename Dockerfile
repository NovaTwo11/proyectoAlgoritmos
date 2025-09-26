# Dockerfile
FROM openjdk:17-jdk-slim

LABEL maintainer="Universidad del Quindío - Proyecto Algoritmos"

# Instalar dependencias del sistema
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Crear directorio de trabajo
WORKDIR /app

# Copiar archivos de Maven
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Descargar dependencias
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente
COPY src src

# Compilar aplicación
RUN ./mvnw clean package -DskipTests

# Crear directorios para datos
RUN mkdir -p /app/data/input /app/data/output

# Exponer puerto
EXPOSE 8080

# Comando de inicio
CMD ["java", "-jar", "target/proyectoAlgoritmos-0.0.1-SNAPSHOT.jar"]