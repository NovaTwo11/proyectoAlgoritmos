package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PreprocessingOptions {
    @Builder.Default private boolean keepPlus = true;
    @Builder.Default private boolean useBigrams = false;
    @Builder.Default private boolean removeNumbers = true;
    @Builder.Default private int minTokenLength = 3;
}

