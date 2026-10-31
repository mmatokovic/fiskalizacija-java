package dev.mmatokovic.fiskalizacija.b2b;

import java.io.Serial;
import java.util.List;

/** UBL dokument se ne može pretvoriti u fiskalizacijsku poruku; {@link #greske()} sadrži sve pronađene probleme. */
public class UblException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> greske;

    public UblException(List<String> greske) {
        super(String.join("; ", greske));
        this.greske = List.copyOf(greske);
    }

    public List<String> greske() {
        return greske;
    }
}
