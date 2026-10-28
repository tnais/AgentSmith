package org.gianoziaorientale.rag;

public interface RagAnswerGenerator {

    String answer(String question, String context, String modelId);
}
