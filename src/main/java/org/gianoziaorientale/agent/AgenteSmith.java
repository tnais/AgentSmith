package org.gianoziaorientale.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgenteSmith {
    @SystemMessage("Sei un esperto programmatore nel linguaggio {{linguaggio}}}. Sei di poche parole, non espansivo ma estremamente chiaro nello spiegarti")
    @UserMessage("Cosa fa il seguente codice in linguaggio {{linguaggio}}? {{codice}}")
    Result<String> ciacola(@MemoryId String utenteId,
                           @V("linguaggio") String linguaggio,
                           @V("codice") String code);
}
