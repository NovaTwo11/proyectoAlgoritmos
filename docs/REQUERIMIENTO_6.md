# Requerimiento 1: Automatización de Proceso de Descarga de Datos

## Descripción
Automatización completa del proceso de descarga de información desde dos bases de datos científicas, unificación de registros y detección de duplicados.

## Objetivos
1. Descarga automática desde dos fuentes de datos
2. Unificación en un solo archivo sin duplicados
3. Archivo separado con registros duplicados eliminados
4. Proceso completamente automático de búsqueda a generación de archivos

## Componentes

### DataAutomationService
- **Ubicación**: `service.requirement1.DataAutomationService`
- **Responsabilidad**: Orquestar el proceso completo de descarga y unificación

### Servicios Relacionados
- `DataDownloaderService`: Descarga desde fuentes externas
- `DataUnificationService`: Unificación de registros
- `DuplicateDetectionService`: Detección de duplicados

## Flujo de Proceso

```
1. Entrada: Query de búsqueda
   ↓
2. Descarga desde Fuente 1 (ej: DBLP)
   ↓
3. Descarga desde Fuente 2 (ej: OpenAlex)
   ↓
4. Unificación de registros
   ↓
5. Detección de duplicados
   ↓
6. Salida 1: Archivo unificado (sin duplicados)
   Salida 2: Archivo de duplicados eliminados
```

## Algoritmos de Detección de Duplicados

### Por implementar:
- Comparación por hash de título + autor
- Similitud de strings (Levenshtein)
- Comparación de DOI/ISBN/ISSN

## Archivos de Salida

### 1. resultados_unificados.csv
Contiene todos los registros únicos con información completa:
- ID único
- Título
- Autores
- Abstract
- Palabras clave
- Revista/Conferencia
- Año, DOI, etc.

### 2. resultados_duplicados.csv
Contiene los registros que fueron eliminados por duplicación:
- ID del registro duplicado
- ID del registro original mantenido
- Razón de duplicación
- Similitud calculada

## Endpoints REST

### POST /api/v1/requirement1/download-and-unify
Ejecuta el proceso completo
```json
{
  "searchQuery": "generative artificial intelligence education"
}
```

### GET /api/v1/requirement1/status
Obtiene el estado del proceso en ejecución

## Métricas de Calidad
- Tiempo total de procesamiento
- Número de registros descargados por fuente
- Número de registros unificados
- Número de duplicados detectados
- Precisión de detección de duplicados

## TODO: Implementación
- [ ] Configurar conexión a fuentes de datos
- [ ] Implementar lógica de descarga paralela
- [ ] Mejorar algoritmos de detección de duplicados
- [ ] Implementar sistema de caché
- [ ] Agregar reintentos automáticos en caso de fallo

