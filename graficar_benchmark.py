import pandas as pd
import matplotlib.pyplot as plt
import os

def generar_grafico(csv_path, tamaño_interes=50000):
    """
    Genera un gráfico de barras con los tiempos de ejecución
    de todos los algoritmos de ordenamiento para un tamaño específico.
    """

    if not os.path.exists(csv_path):
        print(f"❌ No se encontró el archivo CSV: {csv_path}")
        return

    # Leer CSV asegurando que decimal sea '.'
    df = pd.read_csv(csv_path, decimal='.')

    print(f"✅ CSV leído exitosamente: {len(df)} registros")
    print(f"Columnas detectadas: {list(df.columns)}")

    # Asegurar que la columna de tiempo esté en float
    try:
        df["Tiempo (ms)"] = pd.to_numeric(df["Tiempo (ms)"], errors="coerce")
    except Exception as e:
        print("⚠️ Error convirtiendo Tiempo (ms) a número:", e)

    # Filtrar por tamaño
    if tamaño_interes not in df["Tamaño"].unique():
        print(f"⚠️ El tamaño {tamaño_interes} no existe en el CSV.")
        print(f"Tamaños disponibles: {df['Tamaño'].unique()}")
        return

    df_size = df[df["Tamaño"] == tamaño_interes].copy()

    # Filtrar solo algoritmos válidos
    df_size = df_size[df_size["Tiempo (ms)"] > 0]

    if df_size.empty:
        print("⚠️ No hay datos válidos para graficar este tamaño.")
        return

    # Ordenar por tiempo ascendente
    df_size = df_size.sort_values("Tiempo (ms)")

    # Gráfico de barras
    plt.figure(figsize=(12, 7))
    bars = plt.bar(df_size["Método de ordenamiento"],
                   df_size["Tiempo (ms)"],
                   color="skyblue", edgecolor="black")

    # Etiquetas
    plt.title(f"Tiempos de Algoritmos de Ordenamiento\nTamaño del Array: {tamaño_interes:,} elementos",
              fontsize=14, fontweight="bold")
    plt.xlabel("Algoritmos de Ordenamiento", fontsize=12, fontweight="bold")
    plt.ylabel("Tiempo de Ejecución (ms)", fontsize=12, fontweight="bold")
    plt.xticks(rotation=45, ha="right")

    # Mostrar valores encima de cada barra
    for bar, tiempo in zip(bars, df_size["Tiempo (ms)"]):
        plt.text(bar.get_x() + bar.get_width()/2., bar.get_height() + max(1, bar.get_height()*0.01),
                 f'{tiempo:.2f} ms',
                 ha='center', va='bottom', fontsize=9, fontweight="bold")

    plt.tight_layout()

    # Guardar como PNG
    output_file = f"benchmark_general_{tamaño_interes}.png"
    plt.savefig(output_file, dpi=300)
    print(f"📈 Gráfico guardado en: {output_file}")

    plt.show()


def main():
    """
    Script principal para graficar benchmarks.
    Elige un tamaño de array en la variable tamaño_interes.
    """
    print("🚀 GRAFICADOR DE BENCHMARK - ALGORITMOS DE ORDENAMIENTO")

    # Ruta del CSV generado en Java
    csv_path = "C:/Users/holda/IdeaProjects/proyectoAlgoritmos/mi_benchmark.csv"

    # Selecciona el tamaño a graficar (cambiar si quieres otro)
    tamaño_interes = 50000

    generar_grafico(csv_path, tamaño_interes)


if __name__ == "__main__":
    main()